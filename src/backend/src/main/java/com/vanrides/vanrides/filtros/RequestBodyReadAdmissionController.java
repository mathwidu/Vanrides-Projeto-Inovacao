package com.vanrides.vanrides.filtros;

import com.vanrides.vanrides.exception.RequestBodyCapacityException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public final class RequestBodyReadAdmissionController {

    static final String ACTIVE_READS_METRIC = "vanrides.request_body.active_reads";
    static final String CAPACITY_METRIC = "vanrides.request_body.read_capacity";
    static final String CAPACITY_REJECTIONS_METRIC = "vanrides.request_body.capacity_rejections";
    public static final String UNREAD_CONNECTIONS_CLOSED_METRIC =
            "vanrides.request_body.unread_connections_closed";
    private static final int MAX_CONFIGURED_CAPACITY = 10_000;

    private final Semaphore permits;
    private final AtomicInteger activeReads = new AtomicInteger();
    private final Counter capacityRejections;
    private final Counter unreadConnectionsClosed;

    @Autowired
    public RequestBodyReadAdmissionController(
            @Value("${vanrides.request-body.max-concurrent-reads:32}") int capacity,
            @Value("${vanrides.request-body.reserved-request-threads:16}") int reservedRequestThreads,
            @Value("${server.tomcat.threads.max:200}") int maxTomcatThreads,
            ObjectProvider<MeterRegistry> registries
    ) {
        this(
                capacity,
                reservedRequestThreads,
                maxTomcatThreads,
                registries.orderedStream().findFirst().orElse(null)
        );
    }

    RequestBodyReadAdmissionController(int capacity, MeterRegistry registry) {
        this(capacity, 16, 200, registry);
    }

    RequestBodyReadAdmissionController(
            int capacity,
            int reservedRequestThreads,
            int maxTomcatThreads,
            MeterRegistry registry
    ) {
        if (reservedRequestThreads <= 0 || maxTomcatThreads <= reservedRequestThreads) {
            throw new IllegalArgumentException(
                    "vanrides.request-body.reserved-request-threads deve ser positivo e menor que "
                            + "server.tomcat.threads.max"
            );
        }
        int safeCapacity = Math.min(
                MAX_CONFIGURED_CAPACITY,
                maxTomcatThreads - reservedRequestThreads
        );
        if (capacity <= 0 || capacity > safeCapacity) {
            throw new IllegalArgumentException(
                    "vanrides.request-body.max-concurrent-reads deve ficar entre 1 e "
                            + safeCapacity
                            + " para reservar "
                            + reservedRequestThreads
                            + " das "
                            + maxTomcatThreads
                            + " threads do Tomcat; revise vanrides.request-body.reserved-request-threads "
                            + "e server.tomcat.threads.max"
            );
        }
        permits = new Semaphore(capacity);
        if (registry == null) {
            capacityRejections = null;
            unreadConnectionsClosed = null;
            return;
        }
        Gauge.builder(ACTIVE_READS_METRIC, activeReads, AtomicInteger::get)
                .register(registry);
        Gauge.builder(CAPACITY_METRIC, () -> capacity)
                .register(registry);
        capacityRejections = registry.counter(CAPACITY_REJECTIONS_METRIC);
        unreadConnectionsClosed = registry.counter(UNREAD_CONNECTIONS_CLOSED_METRIC);
    }

    Lease tryAcquire() {
        if (!permits.tryAcquire()) {
            if (capacityRejections != null) {
                capacityRejections.increment();
            }
            throw new RequestBodyCapacityException();
        }
        activeReads.incrementAndGet();
        return new Lease(permits, activeReads);
    }

    int activeReads() {
        return activeReads.get();
    }

    public void recordUnreadConnectionClosed() {
        if (unreadConnectionsClosed != null) {
            unreadConnectionsClosed.increment();
        }
    }

    static final class Lease implements AutoCloseable {

        private final Semaphore permits;
        private final AtomicInteger activeReads;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(Semaphore permits, AtomicInteger activeReads) {
            this.permits = permits;
            this.activeReads = activeReads;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                activeReads.decrementAndGet();
                permits.release();
            }
        }
    }
}
