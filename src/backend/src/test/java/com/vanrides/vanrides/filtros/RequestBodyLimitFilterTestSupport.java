package com.vanrides.vanrides.filtros;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class RequestBodyLimitFilterTestSupport {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    protected static final int MAX_BYTES = 4;

    protected RequestBodyLimitFilter filter() {
        return new RequestBodyLimitFilter(OBJECT_MAPPER, MAX_BYTES);
    }

    protected RequestBodyLimitFilter productionFilter(SimpleMeterRegistry registry) {
        return new RequestBodyLimitFilter(
                OBJECT_MAPPER,
                MAX_BYTES,
                new RateLimitMetrics(registry, 100)
        );
    }

    protected MockHttpServletResponse apply(
            RequestBodyLimitFilter filter,
            TrackingRequest request,
            RequestAssertion assertion
    ) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response,
                (servletRequest, ignored) -> assertion.accept((HttpServletRequest) servletRequest));
        return response;
    }

    protected void assertPayloadTooLarge(MockHttpServletResponse response) throws Exception {
        assertEquals(413, response.getStatus());
        JsonNode error = OBJECT_MAPPER.readTree(response.getContentAsByteArray());
        assertEquals(RequestBodyLimitFilter.PAYLOAD_TOO_LARGE_CODE, error.path("code").asText());
        assertTrue(error.path("message").asText().contains("limite"));
        assertFalse(response.isCommitted());
    }

    protected void assertBodyRejectionMetric(SimpleMeterRegistry registry, String reason) {
        assertEquals(1.0, registry.get(RateLimitMetrics.BODY_REJECTIONS_METRIC)
                .tag("reason", reason)
                .counter()
                .count());
    }

    protected TrackingRequest request(String method, byte[] body, long declaredLength) {
        TrackingRequest request = new TrackingRequest(body, declaredLength);
        request.setMethod(method);
        request.setRequestURI("/login");
        request.setServletPath("/login");
        request.setContentType("application/json");
        return request;
    }

    protected byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    protected interface RequestAssertion {
        void accept(HttpServletRequest request) throws IOException;
    }

    protected static final class TrackingRequest extends MockHttpServletRequest {

        private final long declaredLength;
        private final TrackingServletInputStream inputStream;

        private TrackingRequest(byte[] body, long declaredLength) {
            this.declaredLength = declaredLength;
            this.inputStream = new TrackingServletInputStream(body);
        }

        @Override
        public int getContentLength() {
            if (declaredLength < 0 || declaredLength > Integer.MAX_VALUE) {
                return -1;
            }
            return (int) declaredLength;
        }

        @Override
        public long getContentLengthLong() {
            return declaredLength;
        }

        @Override
        public ServletInputStream getInputStream() {
            return inputStream;
        }

        protected int bytesRead() {
            return inputStream.bytesRead;
        }
    }

    private static final class TrackingServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;
        private int bytesRead;

        private TrackingServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            int value = delegate.read();
            if (value >= 0) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            int read = delegate.read(bytes, offset, length);
            if (read > 0) {
                bytesRead += read;
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("não usado pelo teste bloqueante");
        }
    }

    protected static final class ZeroProgressServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;
        private boolean zeroReturned;

        protected ZeroProgressServletInputStream(byte[] body) {
            delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (!zeroReturned) {
                zeroReturned = true;
                return 0;
            }
            return delegate.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("não usado pelo teste bloqueante");
        }
    }
}
