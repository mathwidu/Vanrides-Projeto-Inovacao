package com.vanrides.vanrides.filtros;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitMetricsTest {

    @Test
    void shouldReadEntryGaugeFromStoreWhenDecisionMetricsArriveOutOfOrder() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BoundedRateLimitStore store = new BoundedRateLimitStore(
                10,
                Duration.ofMinutes(1),
                25,
                System::nanoTime
        );
        RateLimitMetrics metrics = new RateLimitMetrics(registry, 25, store);
        BoundedRateLimitStore.Route route = BoundedRateLimitStore.Route.LOGIN;

        BoundedRateLimitStore.Decision first = store.acquire(route, "0".repeat(32));
        BoundedRateLimitStore.Decision second = store.acquire(route, "1".repeat(32));
        metrics.record(route, "direct", "none", second);
        metrics.record(route, "direct", "none", first);

        assertThat(registry.get(RateLimitMetrics.ENTRIES_METRIC)
                .tag("route", "login")
                .gauge()
                .value()).isEqualTo(2);
    }

    @Test
    void shouldRecordRequestsEntriesExpiryAndCapacityWithClosedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimitMetrics metrics = new RateLimitMetrics(registry, 25);
        BoundedRateLimitStore.Route route = BoundedRateLimitStore.Route.LOGIN;

        metrics.record(route, " direct ", "none", decision(true, false, 2, 3));

        assertThat(registry.get(RateLimitMetrics.REQUESTS_METRIC)
                .tags(
                        "route", "login",
                        "source", "direct",
                        "outcome", "allowed",
                        "reason", "none"
                )
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(RateLimitMetrics.ENTRIES_METRIC)
                .tag("route", "login")
                .gauge()
                .value()).isEqualTo(3);
        assertThat(registry.get(RateLimitMetrics.EXPIRED_EVICTIONS_METRIC)
                .tag("route", "login")
                .counter()
                .count()).isEqualTo(2);
        assertThat(registry.get(RateLimitMetrics.CAPACITY_METRIC)
                .tag("route", "login")
                .gauge()
                .value()).isEqualTo(25);
    }

    @Test
    void shouldRecordLimitAndCapacityBlocksWithoutKeyMaterial() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimitMetrics metrics = new RateLimitMetrics(registry, 10);

        metrics.record(
                BoundedRateLimitStore.Route.REFRESH,
                "trusted-proxy",
                "limit",
                decision(false, false, 0, 1)
        );
        metrics.record(
                BoundedRateLimitStore.Route.CHANGE_EMAIL,
                "proxy peer",
                "capacity",
                decision(false, true, 0, 10)
        );

        assertThat(registry.get(RateLimitMetrics.REQUESTS_METRIC)
                .tags(
                        "route", "refresh",
                        "source", "trusted_proxy",
                        "outcome", "blocked",
                        "reason", "limit"
                )
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(RateLimitMetrics.CAPACITY_REJECTIONS_METRIC)
                .tags(
                        "route", "change_email",
                        "source", "proxy_peer",
                        "outcome", "blocked",
                        "reason", "capacity"
                )
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .allSatisfy(tag -> assertThat(tag.getValue())
                                .doesNotContain("@", ".", ":")));
    }

    @Test
    void shouldRecordOnlyAllowlistedForwardedAndBodyReasons() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimitMetrics metrics = new RateLimitMetrics(registry, 10);

        metrics.recordForwardedInvalid(" multiple-forwarded-headers ");
        metrics.recordForwardedInvalid("INVALID_FORWARDED_ADDRESS");
        metrics.recordBodyRejected("content length");
        metrics.recordBodyRejected("STREAM");
        metrics.recordBodyRejected("form-urlencoded");
        metrics.recordBodyRejected("multipart variant");

        assertThat(registry.get(RateLimitMetrics.FORWARDED_INVALID_METRIC)
                .tag("reason", "multiple_forwarded_headers")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(RateLimitMetrics.FORWARDED_INVALID_METRIC)
                .tag("reason", "invalid_forwarded_address")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(RateLimitMetrics.BODY_REJECTIONS_METRIC)
                .tag("reason", "content_length")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(RateLimitMetrics.BODY_REJECTIONS_METRIC)
                .tag("reason", "stream")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(RateLimitMetrics.BODY_REJECTIONS_METRIC)
                .tag("reason", "form_urlencoded")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(RateLimitMetrics.BODY_REJECTIONS_METRIC)
                .tag("reason", "multipart_variant")
                .counter()
                .count()).isEqualTo(1);
    }

    @Test
    void shouldRejectUnknownOrInconsistentTagValuesBeforeRegistration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimitMetrics metrics = new RateLimitMetrics(registry, 10);
        BoundedRateLimitStore.Route route = BoundedRateLimitStore.Route.LOGIN;

        assertThatThrownBy(() -> metrics.record(route, "203.0.113.10", "none",
                decision(true, false, 0, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.record(route, "direct", "attacker-value",
                decision(true, false, 0, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.record(route, "direct", "capacity",
                decision(true, false, 0, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.recordForwardedInvalid("raw-header-value"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.recordBodyRejected("multipart-file-name"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(registry.find(RateLimitMetrics.REQUESTS_METRIC).meters()).isEmpty();
        assertThat(registry.find(RateLimitMetrics.FORWARDED_INVALID_METRIC).meters()).isEmpty();
        assertThat(registry.find(RateLimitMetrics.BODY_REJECTIONS_METRIC).meters()).isEmpty();
    }

    @Test
    void shouldFailFastForInvalidCapacity() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        assertThatThrownBy(() -> new RateLimitMetrics(registry, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BoundedRateLimitStore.Decision decision(
            boolean allowed,
            boolean capacityRejected,
            int expiredEntries,
            int entries
    ) {
        return new BoundedRateLimitStore.Decision(
                allowed,
                capacityRejected,
                allowed ? 0 : 60,
                expiredEntries,
                entries
        );
    }
}
