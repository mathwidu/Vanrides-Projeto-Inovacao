package com.vanrides.vanrides.filtros;

import com.vanrides.vanrides.exception.RequestBodyCapacityException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class RequestBodyReadAdmissionControllerTest {

    @Test
    void shouldFailFastAtCapacityAndReleaseIdempotently() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RequestBodyReadAdmissionController controller =
                new RequestBodyReadAdmissionController(1, registry);

        RequestBodyReadAdmissionController.Lease first = controller.tryAcquire();

        assertThat(controller.activeReads()).isOne();
        assertThat(registry.get(RequestBodyReadAdmissionController.ACTIVE_READS_METRIC)
                .gauge().value()).isEqualTo(1.0);
        assertThatThrownBy(controller::tryAcquire)
                .isInstanceOf(RequestBodyCapacityException.class)
                .satisfies(exception -> assertThat(
                        ((RequestBodyCapacityException) exception).getHeaders().getFirst("Retry-After")
                ).isEqualTo("1"));
        assertThat(registry.get(RequestBodyReadAdmissionController.CAPACITY_REJECTIONS_METRIC)
                .counter().count()).isEqualTo(1.0);

        first.close();
        first.close();

        assertThat(controller.activeReads()).isZero();
        try (RequestBodyReadAdmissionController.Lease ignored = controller.tryAcquire()) {
            assertThat(controller.activeReads()).isOne();
        }
        assertThat(controller.activeReads()).isZero();
    }

    @Test
    void shouldExposeConfiguredCapacityWithoutIdentityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new RequestBodyReadAdmissionController(32, registry);

        assertThat(registry.get(RequestBodyReadAdmissionController.CAPACITY_METRIC)
                .gauge().value()).isEqualTo(32.0);
        assertThat(registry.get(RequestBodyReadAdmissionController.CAPACITY_METRIC)
                .gauge().getId().getTags()).isEmpty();
    }

    @Test
    void shouldBoundConcurrentSlowReadersWithoutWaitingForCapacity() throws Exception {
        int capacity = 4;
        RequestBodyReadAdmissionController controller =
                new RequestBodyReadAdmissionController(capacity, (io.micrometer.core.instrument.MeterRegistry) null);
        CountDownLatch admitted = new CountDownLatch(capacity);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(capacity);
        List<Future<?>> readers = new ArrayList<>();

        try {
            for (int index = 0; index < capacity; index++) {
                readers.add(executor.submit(() -> {
                    try (RequestBodyReadAdmissionController.Lease ignored = controller.tryAcquire()) {
                        admitted.countDown();
                        release.await();
                    }
                    return null;
                }));
            }
            assertThat(admitted.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(controller.activeReads()).isEqualTo(capacity);

            assertTimeoutPreemptively(Duration.ofMillis(250), () ->
                    assertThatThrownBy(controller::tryAcquire)
                            .isInstanceOf(RequestBodyCapacityException.class));
        } finally {
            release.countDown();
            for (Future<?> reader : readers) {
                reader.get();
            }
            executor.shutdownNow();
        }

        assertThat(controller.activeReads()).isZero();
    }

    @Test
    void shouldReserveTomcatThreadsOutsideBodyReadBulkhead() {
        RequestBodyReadAdmissionController controller =
                new RequestBodyReadAdmissionController(
                        184,
                        16,
                        200,
                        (io.micrometer.core.instrument.MeterRegistry) null
                );

        assertThat(controller.activeReads()).isZero();
        assertThatThrownBy(() -> new RequestBodyReadAdmissionController(
                185,
                16,
                200,
                (io.micrometer.core.instrument.MeterRegistry) null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-concurrent-reads")
                .hasMessageContaining("reserved-request-threads")
                .hasMessageContaining("server.tomcat.threads.max");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 200})
    void shouldRejectInvalidReservedThreadConfiguration(int reservedThreads) {
        assertThatThrownBy(() -> new RequestBodyReadAdmissionController(
                1,
                reservedThreads,
                200,
                (io.micrometer.core.instrument.MeterRegistry) null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved-request-threads")
                .hasMessageContaining("server.tomcat.threads.max");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 10001})
    void shouldRejectInvalidCapacity(int capacity) {
        assertThatThrownBy(() -> new RequestBodyReadAdmissionController(
                capacity,
                (io.micrometer.core.instrument.MeterRegistry) null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-concurrent-reads");
    }
}
