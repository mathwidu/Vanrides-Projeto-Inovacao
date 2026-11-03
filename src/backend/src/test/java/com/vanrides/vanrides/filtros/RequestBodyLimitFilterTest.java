package com.vanrides.vanrides.filtros;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestBodyLimitFilterTest extends RequestBodyLimitFilterTestSupport {

    @Test
    void shouldReplayBodyBelowLimitWithUpdatedLengths() throws Exception {
        TrackingRequest request = request("POST", bytes("abc"), 3);
        AtomicReference<HttpServletRequest> forwarded = new AtomicReference<>();

        apply(filter(), request, (wrapped) -> {
            forwarded.set(wrapped);
            assertEquals(3, wrapped.getContentLength());
            assertEquals(3L, wrapped.getContentLengthLong());
            assertArrayEquals(bytes("abc"), wrapped.getInputStream().readAllBytes());
        });

        assertTrue(forwarded.get() instanceof BoundedRequestBodyHttpServletRequest);
        assertEquals(3, request.bytesRead());
    }

    @Test
    void shouldAllowBodyExactlyAtLimit() throws Exception {
        TrackingRequest request = request("POST", bytes("abcd"), 4);
        AtomicBoolean chainCalled = new AtomicBoolean();

        apply(filter(), request, wrapped -> {
            chainCalled.set(true);
            assertArrayEquals(bytes("abcd"), wrapped.getInputStream().readAllBytes());
        });

        assertTrue(chainCalled.get());
        assertEquals(4, request.bytesRead());
    }

    @Test
    void shouldRejectDeclaredLengthAboveLimitWithoutReadingOrCallingChain() throws Exception {
        TrackingRequest request = request("POST", bytes("oversized"), 5);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicBoolean chainCalled = new AtomicBoolean();
        MockHttpServletResponse response = apply(
                productionFilter(registry), request, ignored -> chainCalled.set(true));

        assertPayloadTooLarge(response);
        assertBodyRejectionMetric(registry, "content_length");
        assertFalse(chainCalled.get());
        assertEquals(0, request.bytesRead());
    }

    @Test
    void shouldRejectUnknownChunkedBodyAfterReadingOnlyLimitPlusOne() throws Exception {
        TrackingRequest request = request("POST", bytes("abcdefghij"), -1);
        request.addHeader("Transfer-Encoding", "chunked");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicBoolean chainCalled = new AtomicBoolean();
        MockHttpServletResponse response = apply(
                productionFilter(registry), request, ignored -> chainCalled.set(true));

        assertPayloadTooLarge(response);
        assertBodyRejectionMetric(registry, "stream");
        assertFalse(chainCalled.get());
        assertEquals(MAX_BYTES + 1, request.bytesRead());
    }

    @Test
    void shouldReplayUnknownChunkedBodyWithinLimit() throws Exception {
        TrackingRequest request = request("PATCH", bytes("abc"), -1);
        request.addHeader("Transfer-Encoding", "chunked");
        AtomicBoolean chainCalled = new AtomicBoolean();

        apply(filter(), request, wrapped -> {
            chainCalled.set(true);
            assertArrayEquals(bytes("abc"), wrapped.getInputStream().readAllBytes());
        });

        assertTrue(chainCalled.get());
        assertEquals(3, request.bytesRead());
    }

    @Test
    void shouldReplayReaderAndSupportReadAndSkip() throws Exception {
        TrackingRequest request = request("POST", bytes("abcdef"), 6);
        BoundedRequestBodyHttpServletRequest wrapped =
                new BoundedRequestBodyHttpServletRequest(request, bytes("abcdef"));
        ServletInputStream stream = wrapped.getInputStream();
        byte[] pair = new byte[2];

        assertEquals('a', stream.read());
        assertEquals(2L, stream.skip(2));
        assertEquals(2, stream.read(pair, 0, pair.length));
        assertArrayEquals(bytes("de"), pair);
        assertEquals('f', stream.read());
        assertEquals(-1, stream.read());
        assertTrue(stream.isFinished());
        assertEquals("abcdef", wrapped.getReader().readLine());
        assertEquals(6, wrapped.getContentLength());
        assertEquals(6L, wrapped.getContentLengthLong());
    }

    @Test
    void shouldSkipCorsPreflightWithoutReading() throws Exception {
        TrackingRequest request = request("OPTIONS", bytes("oversized"), 100);
        request.addHeader("Origin", "https://app.example");
        request.addHeader("Access-Control-Request-Method", "POST");
        AtomicReference<HttpServletRequest> forwarded = new AtomicReference<>();

        apply(filter(), request, forwarded::set);

        assertSame(request, forwarded.get());
        assertEquals(0, request.bytesRead());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    void shouldRejectKnownOversizedBodyForEveryNonPreflightMethod(String method) throws Exception {
        TrackingRequest request = request(method, bytes("oversized"), 100);
        AtomicBoolean chainCalled = new AtomicBoolean();

        MockHttpServletResponse response = apply(
                filter(), request, ignored -> chainCalled.set(true));

        assertPayloadTooLarge(response);
        assertFalse(chainCalled.get());
        assertEquals(0, request.bytesRead());
    }

    @Test
    void shouldReplayEmptyBody() throws Exception {
        TrackingRequest request = request("POST", new byte[0], -1);

        apply(filter(), request, wrapped -> {
            assertEquals(0, wrapped.getContentLength());
            assertEquals(-1, wrapped.getInputStream().read());
        });

        assertEquals(0, request.bytesRead());
    }

    @Test
    void shouldNotPreReadUnknownMultipartBody() throws Exception {
        TrackingRequest request = request("POST", bytes("multipart-content"), -1);
        request.setContentType("multipart/form-data; boundary=vanrides");
        AtomicReference<HttpServletRequest> forwarded = new AtomicReference<>();

        apply(filter(), request, forwarded::set);

        assertSame(request, forwarded.get());
        assertEquals(0, request.bytesRead());
    }

    @Test
    void shouldRejectKnownMultipartLengthAboveLimitWithoutReading() throws Exception {
        TrackingRequest request = request("POST", bytes("multipart-content"), 5);
        request.setContentType("multipart/form-data; boundary=vanrides");
        AtomicBoolean chainCalled = new AtomicBoolean();
        MockHttpServletResponse response = apply(filter(), request, ignored -> chainCalled.set(true));

        assertPayloadTooLarge(response);
        assertFalse(chainCalled.get());
        assertEquals(0, request.bytesRead());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "application/x-www-form-urlencoded",
            "Application/X-WWW-Form-Urlencoded; charset=UTF-8"
    })
    void shouldRejectUnsupportedFormUrlEncodedBodyWithoutReading(String contentType) throws Exception {
        TrackingRequest request = request("POST", bytes("a=b"), -1);
        request.setContentType(contentType);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicBoolean chainCalled = new AtomicBoolean();

        MockHttpServletResponse response = apply(
                productionFilter(registry), request, ignored -> chainCalled.set(true));

        assertEquals(415, response.getStatus());
        JsonNode error = OBJECT_MAPPER.readTree(response.getContentAsByteArray());
        assertEquals(RequestBodyLimitFilter.UNSUPPORTED_BODY_MEDIA_TYPE_CODE,
                error.path("code").asText());
        assertFalse(response.isCommitted());
        assertBodyRejectionMetric(registry, "form_urlencoded");
        assertFalse(chainCalled.get());
        assertEquals(0, request.bytesRead());
    }

    @Test
    void shouldRejectUnsupportedMultipartVariantWithoutContainerParsing() throws Exception {
        TrackingRequest request = request("POST", bytes("multipart-content"), -1);
        request.setContentType("multipart/mixed; boundary=vanrides");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicBoolean chainCalled = new AtomicBoolean();

        MockHttpServletResponse response = apply(
                productionFilter(registry), request, ignored -> chainCalled.set(true));

        assertEquals(415, response.getStatus());
        assertEquals(RequestBodyLimitFilter.UNSUPPORTED_BODY_MEDIA_TYPE_CODE,
                OBJECT_MAPPER.readTree(response.getContentAsByteArray()).path("code").asText());
        assertFalse(response.isCommitted());
        assertBodyRejectionMetric(registry, "multipart_variant");
        assertFalse(chainCalled.get());
        assertEquals(0, request.bytesRead());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MAX_VALUE})
    void shouldRejectNonPositiveConfiguration(int maxBytes) {
        assertThrows(IllegalArgumentException.class,
                () -> new RequestBodyLimitFilter(OBJECT_MAPPER, maxBytes));
    }

}
