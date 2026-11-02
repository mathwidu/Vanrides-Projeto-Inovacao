package com.vanrides.vanrides.filtros;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public final class RateLimitMetrics {

    static final String REQUESTS_METRIC = "vanrides.rate.limit.requests";
    static final String ENTRIES_METRIC = "vanrides.rate.limit.entries";
    static final String EXPIRED_EVICTIONS_METRIC = "vanrides.rate.limit.expired.evictions";
    static final String CAPACITY_METRIC = "vanrides.rate.limit.capacity";
    static final String CAPACITY_REJECTIONS_METRIC = "vanrides.rate.limit.capacity.rejections";
    static final String FORWARDED_INVALID_METRIC = "vanrides.rate.limit.forwarded.invalid";
    static final String BODY_REJECTIONS_METRIC = "vanrides.request_body.rejections";

    private enum Source {
        DIRECT,
        PROXY_PEER,
        TRUSTED_PROXY
    }

    private enum Outcome {
        ALLOWED,
        BLOCKED
    }

    private enum Reason {
        NONE,
        LIMIT,
        CAPACITY
    }

    private enum ForwardedInvalidReason {
        INVALID_REMOTE_ADDRESS,
        MULTIPLE_FORWARDED_HEADERS,
        EMPTY_FORWARDED_HEADER,
        FORWARDED_HEADER_TOO_LARGE,
        TOO_MANY_FORWARDED_HOPS,
        EMPTY_FORWARDED_HOP,
        INVALID_FORWARDED_ADDRESS
    }

    private enum BodyRejectionReason {
        CONTENT_LENGTH,
        STREAM,
        FORM_URLENCODED,
        MULTIPART_VARIANT
    }

    private final MeterRegistry registry;
    private final BoundedRateLimitStore rateLimitStore;
    private final Map<BoundedRateLimitStore.Route, AtomicInteger> entries =
            new EnumMap<>(BoundedRateLimitStore.Route.class);
    private final Map<RequestMetricKey, Counter> requestCounters = new ConcurrentHashMap<>();
    private final Map<BoundedRateLimitStore.Route, Counter> expiredCounters = new ConcurrentHashMap<>();
    private final Map<CapacityMetricKey, Counter> capacityRejectionCounters = new ConcurrentHashMap<>();
    private final Map<ForwardedInvalidReason, Counter> forwardedInvalidCounters = new ConcurrentHashMap<>();
    private final Map<BodyRejectionReason, Counter> bodyRejectionCounters = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitMetrics(
            ObjectProvider<MeterRegistry> registries,
            @Value("${vanrides.rate-limit.max-entries-per-route:10000}") int capacityPerRoute,
            BoundedRateLimitStore rateLimitStore
    ) {
        this(
                registries.orderedStream().findFirst().orElse(null),
                capacityPerRoute,
                Objects.requireNonNull(rateLimitStore, "rateLimitStore must not be null")
        );
    }

    public RateLimitMetrics(
            ObjectProvider<MeterRegistry> registries,
            int capacityPerRoute
    ) {
        this(registries.orderedStream().findFirst().orElse(null), capacityPerRoute, null);
    }

    RateLimitMetrics(SimpleMeterRegistry registry, int capacityPerRoute) {
        this(
                (MeterRegistry) Objects.requireNonNull(registry, "registry must not be null"),
                capacityPerRoute,
                null
        );
    }

    RateLimitMetrics(
            SimpleMeterRegistry registry,
            int capacityPerRoute,
            BoundedRateLimitStore rateLimitStore
    ) {
        this(
                (MeterRegistry) Objects.requireNonNull(registry, "registry must not be null"),
                capacityPerRoute,
                Objects.requireNonNull(rateLimitStore, "rateLimitStore must not be null")
        );
    }

    private RateLimitMetrics(
            MeterRegistry registry,
            int capacityPerRoute,
            BoundedRateLimitStore rateLimitStore
    ) {
        if (capacityPerRoute <= 0) {
            throw new IllegalArgumentException("rate limit capacity per route must be positive");
        }
        this.registry = registry;
        this.rateLimitStore = rateLimitStore;
        for (BoundedRateLimitStore.Route route : BoundedRateLimitStore.Route.values()) {
            AtomicInteger routeEntries = new AtomicInteger();
            entries.put(route, routeEntries);
            if (registry != null) {
                Gauge.builder(ENTRIES_METRIC, route, this::currentEntries)
                        .tag("route", tag(route))
                        .register(registry);
                Gauge.builder(CAPACITY_METRIC, () -> capacityPerRoute)
                        .tag("route", tag(route))
                        .register(registry);
            }
        }
    }

    public void record(
            BoundedRateLimitStore.Route route,
            String sourceValue,
            String reasonValue,
            BoundedRateLimitStore.Decision decision
    ) {
        Objects.requireNonNull(route, "route must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
        Source source = parse(Source.class, sourceValue, "source");
        Reason reason = parse(Reason.class, reasonValue, "reason");
        Outcome outcome = decision.allowed() ? Outcome.ALLOWED : Outcome.BLOCKED;
        validateReason(reason, decision);

        if (rateLimitStore == null) {
            entries.get(route).set(decision.entries());
        }
        if (registry == null) {
            return;
        }

        RequestMetricKey requestKey = new RequestMetricKey(route, source, outcome, reason);
        requestCounters.computeIfAbsent(requestKey, this::registerRequestCounter).increment();
        if (decision.expiredEntries() > 0) {
            expiredCounters.computeIfAbsent(route, this::registerExpiredCounter)
                    .increment(decision.expiredEntries());
        }
        if (decision.capacityRejected()) {
            CapacityMetricKey capacityKey = new CapacityMetricKey(route, source);
            capacityRejectionCounters.computeIfAbsent(capacityKey, this::registerCapacityCounter)
                    .increment();
        }
    }

    public void recordForwardedInvalid(String reasonValue) {
        ForwardedInvalidReason reason = parse(
                ForwardedInvalidReason.class,
                reasonValue,
                "forwarded invalid reason"
        );
        if (registry != null) {
            forwardedInvalidCounters.computeIfAbsent(reason, this::registerForwardedInvalidCounter)
                    .increment();
        }
    }

    public void recordBodyRejected(String reasonValue) {
        BodyRejectionReason reason = parse(BodyRejectionReason.class, reasonValue, "body rejection reason");
        if (registry != null) {
            bodyRejectionCounters.computeIfAbsent(reason, this::registerBodyRejectionCounter)
                    .increment();
        }
    }

    private Counter registerRequestCounter(RequestMetricKey key) {
        return registry.counter(REQUESTS_METRIC, List.of(
                Tag.of("route", tag(key.route)),
                Tag.of("source", tag(key.source)),
                Tag.of("outcome", tag(key.outcome)),
                Tag.of("reason", tag(key.reason))
        ));
    }

    private Counter registerExpiredCounter(BoundedRateLimitStore.Route route) {
        return registry.counter(EXPIRED_EVICTIONS_METRIC, "route", tag(route));
    }

    private Counter registerCapacityCounter(CapacityMetricKey key) {
        return registry.counter(CAPACITY_REJECTIONS_METRIC,
                "route", tag(key.route),
                "source", tag(key.source),
                "outcome", tag(Outcome.BLOCKED),
                "reason", tag(Reason.CAPACITY));
    }

    private Counter registerForwardedInvalidCounter(ForwardedInvalidReason reason) {
        return registry.counter(FORWARDED_INVALID_METRIC, "reason", tag(reason));
    }

    private Counter registerBodyRejectionCounter(BodyRejectionReason reason) {
        return registry.counter(BODY_REJECTIONS_METRIC, "reason", tag(reason));
    }

    private double currentEntries(BoundedRateLimitStore.Route route) {
        return rateLimitStore == null
                ? entries.get(route).get()
                : rateLimitStore.entries(route);
    }

    private static void validateReason(Reason reason, BoundedRateLimitStore.Decision decision) {
        Reason expected = decision.allowed()
                ? Reason.NONE
                : decision.capacityRejected() ? Reason.CAPACITY : Reason.LIMIT;
        if (reason != expected) {
            throw new IllegalArgumentException("reason does not match rate limit decision");
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported " + field, exception);
        }
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private record RequestMetricKey(
            BoundedRateLimitStore.Route route,
            Source source,
            Outcome outcome,
            Reason reason
    ) {
    }

    private record CapacityMetricKey(BoundedRateLimitStore.Route route, Source source) {
    }
}
