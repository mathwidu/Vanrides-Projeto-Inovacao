package com.vanrides.vanrides.filtros;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanrides.vanrides.exception.RequestBodyCapacityException;
import com.vanrides.vanrides.exception.RequestBodyTooLargeException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestBodyLimitAdmissionTest extends RequestBodyLimitFilterTestSupport {

    @Test
    void shouldNotReadBodyWhenAnUnmatchedRouteDoesNotConsumeIt() throws Exception {
        TrackingRequest request = request("POST", bytes("never-read"), -1);
        request.setRequestURI("/inexistente");
        request.setServletPath("/inexistente");
        AtomicReference<jakarta.servlet.http.HttpServletRequest> forwarded = new AtomicReference<>();

        apply(filter(), request, forwarded::set);

        assertTrue(forwarded.get() instanceof LimitedRequestBodyHttpServletRequest);
        assertEquals(0, request.bytesRead());
    }

    @Test
    void shouldLimitLazyStreamWithoutResettingAcrossRepeatedAccess() throws Exception {
        TrackingRequest request = request("POST", bytes("abcdefghij"), -1);
        request.setRequestURI("/account/me");
        request.setServletPath("/account/me");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RequestBodyReadAdmissionController admission =
                new RequestBodyReadAdmissionController(1, registry);
        RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
                OBJECT_MAPPER,
                MAX_BYTES,
                new RateLimitMetrics(registry, 100),
                new SensitiveRequestPolicy(),
                admission
        );

        AtomicReference<ServletInputStream> limitedStream = new AtomicReference<>();
        MockHttpServletResponse response = apply(filter, request, wrapped -> {
            assertSame(wrapped.getInputStream(), wrapped.getInputStream());
            limitedStream.set(wrapped.getInputStream());
            assertEquals('a', wrapped.getInputStream().read());
            wrapped.getInputStream().skip(10);
        });

        assertPayloadTooLarge(response);
        assertEquals(MAX_BYTES + 1, request.bytesRead());
        assertEquals(0, admission.activeReads());
        assertBodyRejectionMetric(registry, "stream");

        int bytesAfterFirstOverflow = request.bytesRead();
        try (RequestBodyReadAdmissionController.Lease ignored = admission.tryAcquire()) {
            assertThrows(RequestBodyTooLargeException.class, limitedStream.get()::read);
            assertThrows(RequestBodyTooLargeException.class,
                    () -> limitedStream.get().read(new byte[2]));
            assertThrows(RequestBodyTooLargeException.class, () -> limitedStream.get().skip(2));
        }
        assertEquals(bytesAfterFirstOverflow, request.bytesRead());
        assertBodyRejectionMetric(registry, "stream");
    }

    @Test
    void shouldMakeProgressWhenBulkReadReturnsZeroDuringSkip() throws Exception {
        ZeroProgressServletInputStream source = new ZeroProgressServletInputStream(bytes("abc"));
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public ServletInputStream getInputStream() {
                return source;
            }
        };
        LimitedRequestBodyHttpServletRequest limited = new LimitedRequestBodyHttpServletRequest(
                request,
                MAX_BYTES,
                new RequestBodyReadAdmissionController(1, null),
                () -> {
                }
        );

        assertEquals(2L, limited.getInputStream().skip(2));
        assertEquals('c', limited.getInputStream().read());
        assertEquals(-1, limited.getInputStream().read());
    }

    @Test
    void shouldFailFastWhenBodyReadCapacityIsExhausted() throws Exception {
        TrackingRequest request = request("POST", bytes("abc"), 3);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RequestBodyReadAdmissionController admission =
                new RequestBodyReadAdmissionController(1, registry);
        RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
                OBJECT_MAPPER,
                MAX_BYTES,
                new RateLimitMetrics(registry, 100),
                new SensitiveRequestPolicy(),
                admission
        );
        AtomicBoolean chainCalled = new AtomicBoolean();

        MockHttpServletResponse response;
        try (RequestBodyReadAdmissionController.Lease ignored = admission.tryAcquire()) {
            response = apply(filter, request, wrapped -> chainCalled.set(true));
        }

        assertEquals(503, response.getStatus());
        assertEquals("1", response.getHeader("Retry-After"));
        assertFalse(response.isCommitted());
        JsonNode error = OBJECT_MAPPER.readTree(response.getContentAsByteArray());
        assertEquals(RequestBodyCapacityException.CODE, error.path("code").asText());
        assertFalse(chainCalled.get());
        assertEquals(0, request.bytesRead());
        assertEquals(0, admission.activeReads());
    }

    @Test
    void shouldNormalizeLazyCapacityRejectionOutsideSpringMvc() throws Exception {
        TrackingRequest request = request("POST", bytes("abc"), 3);
        request.setRequestURI("/account/me");
        request.setServletPath("/account/me");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RequestBodyReadAdmissionController admission =
                new RequestBodyReadAdmissionController(1, registry);
        RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
                OBJECT_MAPPER,
                MAX_BYTES,
                new RateLimitMetrics(registry, 100),
                new SensitiveRequestPolicy(),
                admission
        );
        AtomicReference<ServletInputStream> limitedStream = new AtomicReference<>();

        MockHttpServletResponse response;
        try (RequestBodyReadAdmissionController.Lease ignored = admission.tryAcquire()) {
            response = apply(filter, request, wrapped -> {
                limitedStream.set(wrapped.getInputStream());
                wrapped.getInputStream().read();
            });
        }

        assertEquals(503, response.getStatus());
        assertEquals("1", response.getHeader("Retry-After"));
        assertFalse(response.isCommitted());
        assertEquals(RequestBodyCapacityException.CODE,
                OBJECT_MAPPER.readTree(response.getContentAsByteArray()).path("code").asText());
        assertEquals(0, request.bytesRead());
        assertEquals(0, admission.activeReads());
        assertThrows(RequestBodyCapacityException.class, limitedStream.get()::read);
        assertEquals(0, request.bytesRead());
    }

    @Test
    void shouldPropagateDownstreamCapacityFailureAfterMultipartResponseCommit() throws Exception {
        TrackingRequest request = request("POST", bytes("multipart-content"), -1);
        request.setContentType("multipart/form-data; boundary=vanrides");
        RequestBodyReadAdmissionController admission =
                new RequestBodyReadAdmissionController(1, null);
        RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
                OBJECT_MAPPER,
                MAX_BYTES,
                new RateLimitMetrics(new SimpleMeterRegistry(), 100),
                new SensitiveRequestPolicy(),
                admission
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestBodyCapacityException downstream = new RequestBodyCapacityException();

        RequestBodyCapacityException thrown = assertThrows(
                RequestBodyCapacityException.class,
                () -> filter.doFilterInternal(request, response, (servletRequest, servletResponse) -> {
                    servletResponse.getWriter().write("original");
                    servletResponse.flushBuffer();
                    throw downstream;
                })
        );

        assertSame(downstream, thrown);
        assertTrue(response.isCommitted());
        assertEquals("original", response.getContentAsString());
        assertEquals(0, admission.activeReads());
    }
}
