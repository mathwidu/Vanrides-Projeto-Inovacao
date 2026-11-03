package com.vanrides.vanrides.filtros;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanrides.vanrides.controller.ApiErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class BasicRateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMITED_CODE = "RATE_LIMITED";
    private static final String RATE_LIMITED_MESSAGE = "Muitas tentativas. Aguarde antes de tentar novamente.";
    private static final String INVALID_FORWARDED_CODE = "INVALID_FORWARDED_ADDRESS";
    private static final String INVALID_FORWARDED_MESSAGE = "Endereço de cliente encaminhado inválido.";

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final SensitiveRequestPolicy sensitiveRequestPolicy;
    private final TrustedProxyClientAddressResolver clientAddressResolver;
    private final BoundedRateLimitStore rateLimitStore;
    private final RateLimitMetrics metrics;

    @Autowired
    public BasicRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${vanrides.rate-limit.enabled:true}") boolean enabled,
            SensitiveRequestPolicy sensitiveRequestPolicy,
            TrustedProxyClientAddressResolver clientAddressResolver,
            BoundedRateLimitStore rateLimitStore,
            RateLimitMetrics metrics
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.enabled = enabled;
        this.sensitiveRequestPolicy = Objects.requireNonNull(sensitiveRequestPolicy, "sensitiveRequestPolicy");
        this.clientAddressResolver = Objects.requireNonNull(clientAddressResolver, "clientAddressResolver");
        this.rateLimitStore = Objects.requireNonNull(rateLimitStore, "rateLimitStore");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!enabled || CorsUtils.isPreFlightRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<BoundedRateLimitStore.Route> route = sensitiveRequestPolicy.routeFor(request);
        if (route.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        TrustedProxyClientAddressResolver.ClientIdentity identity;
        try {
            identity = clientAddressResolver.resolve(request);
        } catch (TrustedProxyClientAddressResolver.ResolutionException exception) {
            metrics.recordForwardedInvalid(exception.reason().name());
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    INVALID_FORWARDED_CODE, INVALID_FORWARDED_MESSAGE);
            return;
        }

        BoundedRateLimitStore.Decision decision = rateLimitStore.acquire(route.get(), identity.digest());
        String reason = decision.allowed() ? "NONE" : decision.capacityRejected() ? "CAPACITY" : "LIMIT";
        metrics.record(route.get(), identity.source().name(), reason, decision);
        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        writeError(response, 429, RATE_LIMITED_CODE, RATE_LIMITED_MESSAGE);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        ApiErrorResponseWriter.write(objectMapper, response, status, code, message);
    }
}
