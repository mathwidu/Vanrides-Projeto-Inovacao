package com.vanrides.vanrides.filtros;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedRateLimitStoreTest {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    @Test
    void shouldSaturateAtLimitPlusOneAndKeepTheSameRetryAfter() {
        AtomicLong ticker = new AtomicLong();
        BoundedRateLimitStore store = store(2, WINDOW, 10, ticker::get);

        BoundedRateLimitStore.Decision first = acquire(store, 1);
        BoundedRateLimitStore.Decision second = acquire(store, 1);
        BoundedRateLimitStore.Decision limitPlusOne = acquire(store, 1);
        for (int index = 0; index < 100; index++) {
            acquire(store, 1);
        }
        BoundedRateLimitStore.Decision saturated = acquire(store, 1);

        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isTrue();
        assertThat(limitPlusOne.allowed()).isFalse();
        assertThat(limitPlusOne.capacityRejected()).isFalse();
        assertThat(limitPlusOne.retryAfterSeconds()).isEqualTo(60);
        assertThat(saturated.allowed()).isFalse();
        assertThat(saturated.retryAfterSeconds()).isEqualTo(60);
        assertThat(saturated.entries()).isEqualTo(1);
    }

    @Test
    void shouldExpireTheFixedWindowExactlyAtItsBoundary() {
        AtomicLong ticker = new AtomicLong();
        BoundedRateLimitStore store = store(1, WINDOW, 10, ticker::get);

        assertThat(acquire(store, 1).allowed()).isTrue();
        ticker.set(WINDOW.toNanos() - 1);
        BoundedRateLimitStore.Decision beforeBoundary = acquire(store, 1);

        assertThat(beforeBoundary.allowed()).isFalse();
        assertThat(beforeBoundary.retryAfterSeconds()).isEqualTo(1);

        ticker.set(WINDOW.toNanos());
        BoundedRateLimitStore.Decision atBoundary = acquire(store, 1);

        assertThat(atBoundary.allowed()).isTrue();
        assertThat(atBoundary.expiredEntries()).isEqualTo(1);
        assertThat(atBoundary.entries()).isEqualTo(1);
    }

    @Test
    void shouldRejectANewDigestAtCapacityWithoutEvictingAnActiveEntry() {
        AtomicLong ticker = new AtomicLong();
        BoundedRateLimitStore store = store(5, WINDOW, 2, ticker::get);
        acquire(store, 1);
        ticker.set(Duration.ofSeconds(10).toNanos());
        acquire(store, 2);

        ticker.set(Duration.ofSeconds(20).toNanos());
        BoundedRateLimitStore.Decision capacityRejected = acquire(store, 3);

        assertThat(capacityRejected.allowed()).isFalse();
        assertThat(capacityRejected.capacityRejected()).isTrue();
        assertThat(capacityRejected.retryAfterSeconds()).isEqualTo(40);
        assertThat(capacityRejected.expiredEntries()).isZero();
        assertThat(capacityRejected.entries()).isEqualTo(2);
        assertThat(store.entries(BoundedRateLimitStore.Route.LOGIN)).isEqualTo(2);

        ticker.set(WINDOW.toNanos());
        BoundedRateLimitStore.Decision admitted = acquire(store, 3);

        assertThat(admitted.allowed()).isTrue();
        assertThat(admitted.expiredEntries()).isEqualTo(1);
        assertThat(admitted.entries()).isEqualTo(2);
    }

    @Test
    void shouldKeepCapacityIndependentForEveryRoutePartition() {
        AtomicLong ticker = new AtomicLong();
        BoundedRateLimitStore store = store(5, WINDOW, 1, ticker::get);

        BoundedRateLimitStore.Decision login = store.acquire(
                BoundedRateLimitStore.Route.LOGIN,
                digest(1)
        );
        BoundedRateLimitStore.Decision refresh = store.acquire(
                BoundedRateLimitStore.Route.REFRESH,
                digest(2)
        );
        BoundedRateLimitStore.Decision secondLoginKey = store.acquire(
                BoundedRateLimitStore.Route.LOGIN,
                digest(3)
        );

        assertThat(login.allowed()).isTrue();
        assertThat(refresh.allowed()).isTrue();
        assertThat(secondLoginKey.capacityRejected()).isTrue();
    }

    @Test
    void shouldNormalizeUppercaseHexWithoutCreatingAnotherEntry() {
        AtomicLong ticker = new AtomicLong();
        BoundedRateLimitStore store = store(1, WINDOW, 2, ticker::get);

        assertThat(store.acquire(BoundedRateLimitStore.Route.LOGIN, digest(10).toUpperCase()).allowed())
                .isTrue();
        BoundedRateLimitStore.Decision second = store.acquire(
                BoundedRateLimitStore.Route.LOGIN,
                digest(10)
        );

        assertThat(second.allowed()).isFalse();
        assertThat(second.capacityRejected()).isFalse();
        assertThat(second.entries()).isEqualTo(1);
    }

    @Test
    void shouldSerializeConcurrentAcquisitionsWithinAPartition() throws Exception {
        AtomicLong ticker = new AtomicLong();
        BoundedRateLimitStore store = store(10, WINDOW, 10, ticker::get);
        List<Callable<BoundedRateLimitStore.Decision>> calls = IntStream.range(0, 64)
                .mapToObj(ignored -> (Callable<BoundedRateLimitStore.Decision>) () -> acquire(store, 1))
                .toList();
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Future<BoundedRateLimitStore.Decision>> results = executor.invokeAll(calls);
            long allowed = results.stream()
                    .map(BoundedRateLimitStoreTest::get)
                    .filter(BoundedRateLimitStore.Decision::allowed)
                    .count();

            assertThat(allowed).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldFailFastForInvalidConfigurationAndInput() {
        AtomicLong ticker = new AtomicLong();

        assertThatThrownBy(() -> store(0, WINDOW, 1, ticker::get))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store(1, Duration.ZERO, 1, ticker::get))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store(1, WINDOW, 0, ticker::get))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store(1, WINDOW, 1, null))
                .isInstanceOf(NullPointerException.class);

        BoundedRateLimitStore store = store(1, WINDOW, 1, ticker::get);
        assertThatThrownBy(() -> store.acquire(null, digest(1)))
                .isInstanceOf(NullPointerException.class);
        assertThat(store.acquire(BoundedRateLimitStore.Route.LOGIN, "a".repeat(32)).allowed())
                .isTrue();
        assertThatThrownBy(() -> store.acquire(BoundedRateLimitStore.Route.LOGIN, "a".repeat(31)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.acquire(BoundedRateLimitStore.Route.LOGIN, "a".repeat(33)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.acquire(BoundedRateLimitStore.Route.LOGIN, "g".repeat(32)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.acquire(BoundedRateLimitStore.Route.LOGIN, " " + "a".repeat(32)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectARegressingTicker() {
        AtomicInteger invocation = new AtomicInteger();
        LongSupplier ticker = () -> invocation.getAndIncrement() == 0 ? 10 : 9;
        BoundedRateLimitStore store = store(2, WINDOW, 2, ticker);
        acquire(store, 1);

        assertThatThrownBy(() -> acquire(store, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("monotonic");
    }

    private static BoundedRateLimitStore store(
            int limit,
            Duration window,
            int capacity,
            LongSupplier ticker
    ) {
        return new BoundedRateLimitStore(limit, window, capacity, ticker);
    }

    private static BoundedRateLimitStore.Decision acquire(BoundedRateLimitStore store, int key) {
        return store.acquire(BoundedRateLimitStore.Route.LOGIN, digest(key));
    }

    private static String digest(int key) {
        return String.format("%032x", key);
    }

    private static BoundedRateLimitStore.Decision get(Future<BoundedRateLimitStore.Decision> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
