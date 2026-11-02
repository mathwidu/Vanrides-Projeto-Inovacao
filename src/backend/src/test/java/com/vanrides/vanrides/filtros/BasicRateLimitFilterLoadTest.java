package com.vanrides.vanrides.filtros;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class BasicRateLimitFilterLoadTest {

    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final byte[] TEST_SALT = new byte[] {
            0, 1, 2, 3, 4, 5, 6, 7,
            8, 9, 10, 11, 12, 13, 14, 15,
            16, 17, 18, 19, 20, 21, 22, 23,
            24, 25, 26, 27, 28, 29, 30, 31
    };

    @Test
    void shouldAllowExactlyNConcurrentRequestsAndKeepNPlusOneBlocked() throws Exception {
        int limit = 32;
        Fixture fixture = fixture(limit, 64);
        List<Callable<FilterResult>> calls = new ArrayList<>();
        for (int index = 0; index < limit * 4; index++) {
            calls.add(() -> execute(fixture.filter(), "POST", "/login", "198.51.100.10"));
        }

        List<FilterResult> results = runConcurrently(calls, 32);
        long allowed = results.stream().filter(FilterResult::chainInvoked).count();
        long blocked = results.stream().filter(result -> result.status() == 429).count();

        assertThat(allowed).isEqualTo(limit);
        assertThat(blocked).isEqualTo(calls.size() - limit);
        assertThat(results.stream().filter(result -> result.status() == 429))
                .allMatch(result -> "60".equals(result.retryAfter()));
        assertThat(fixture.store().entries(BoundedRateLimitStore.Route.LOGIN)).isEqualTo(1);

        FilterResult afterLoad = execute(fixture.filter(), "POST", "/login", "198.51.100.10");
        assertThat(afterLoad.status()).isEqualTo(429);
        assertThat(afterLoad.chainInvoked()).isFalse();
        assertThat(afterLoad.retryAfter()).isEqualTo("60");
    }

    @Test
    void shouldKeepExactCapacityUnderConcurrentHighCardinality() throws Exception {
        int capacity = 64;
        int clients = 1_024;
        Fixture fixture = fixture(10_000, capacity);
        List<Callable<FilterResult>> calls = new ArrayList<>(clients);
        for (int index = 0; index < clients; index++) {
            String remoteAddress = "198.18.%d.%d".formatted(index / 256, index % 256);
            calls.add(() -> execute(fixture.filter(), "POST", "/login", remoteAddress));
        }

        List<FilterResult> results = runConcurrently(calls, 32);
        long allowed = results.stream().filter(FilterResult::chainInvoked).count();
        long capacityRejected = results.stream()
                .filter(result -> result.status() == 429)
                .count();

        assertThat(allowed).isEqualTo(capacity);
        assertThat(capacityRejected).isEqualTo(clients - capacity);
        assertThat(fixture.store().entries(BoundedRateLimitStore.Route.LOGIN)).isEqualTo(capacity);
        assertThat(fixture.registry().get(RateLimitMetrics.CAPACITY_REJECTIONS_METRIC)
                .tag("route", "login")
                .tag("source", "direct")
                .tag("outcome", "blocked")
                .tag("reason", "capacity")
                .counter().count()).isEqualTo(clients - capacity);
    }

    @Test
    void shouldKeepBlockedCounterSaturatedUnderRepeatedLoad() {
        AtomicLong ticker = new AtomicLong();
        BoundedRateLimitStore store = new BoundedRateLimitStore(1, WINDOW, 2, ticker::get);
        String digest = "0123456789abcdef0123456789abcdef";

        assertThat(store.acquire(BoundedRateLimitStore.Route.LOGIN, digest).allowed()).isTrue();
        for (int index = 0; index < 100_000; index++) {
            BoundedRateLimitStore.Decision decision = store.acquire(
                    BoundedRateLimitStore.Route.LOGIN,
                    digest
            );
            assertThat(decision.allowed()).isFalse();
            assertThat(decision.capacityRejected()).isFalse();
            assertThat(decision.retryAfterSeconds()).isEqualTo(60);
        }

        assertThat(store.entries(BoundedRateLimitStore.Route.LOGIN)).isEqualTo(1);
    }

    @Test
    void shouldNotCreateCardinalityFromDynamicPreviewTokens() throws Exception {
        int requests = 2_000;
        Fixture fixture = fixture(requests + 1, 2);

        for (int index = 0; index < requests; index++) {
            FilterResult result = execute(
                    fixture.filter(),
                    "GET",
                    "/driver-onboarding/invites/token-%d/preview".formatted(index),
                    "203.0.113.20"
            );
            assertThat(result.chainInvoked()).isTrue();
            assertThat(result.status()).isEqualTo(200);
        }

        assertThat(fixture.store().entries(BoundedRateLimitStore.Route.DRIVER_INVITE_PREVIEW))
                .isEqualTo(1);
        assertThat(fixture.store().entries(BoundedRateLimitStore.Route.DRIVER_INVITE_REDEEM))
                .isZero();
    }

    private static Fixture fixture(int limit, int capacity) {
        AtomicLong ticker = new AtomicLong();
        BoundedRateLimitStore store = new BoundedRateLimitStore(limit, WINDOW, capacity, ticker::get);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimitMetrics metrics = new RateLimitMetrics(registry, capacity);
        TrustedProxyClientAddressResolver resolver = new TrustedProxyClientAddressResolver(
                "",
                1024,
                10,
                TEST_SALT
        );
        BasicRateLimitFilter filter = new BasicRateLimitFilter(
                new ObjectMapper(),
                true,
                new SensitiveRequestPolicy(),
                resolver,
                store,
                metrics
        );
        return new Fixture(filter, store, registry);
    }

    private static FilterResult execute(
            BasicRateLimitFilter filter,
            String method,
            String path,
            String remoteAddress
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(remoteAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        return new FilterResult(
                response.getStatus(),
                chain.getRequest() != null,
                response.getHeader("Retry-After")
        );
    }

    private static List<FilterResult> runConcurrently(
            List<Callable<FilterResult>> calls,
            int threads
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<FilterResult>> futures = new ArrayList<>(calls.size());
        try {
            for (Callable<FilterResult> call : calls) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<FilterResult> results = new ArrayList<>(futures.size());
            for (Future<FilterResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private record Fixture(
            BasicRateLimitFilter filter,
            BoundedRateLimitStore store,
            SimpleMeterRegistry registry
    ) {
    }

    private record FilterResult(int status, boolean chainInvoked, String retryAfter) {
    }
}
