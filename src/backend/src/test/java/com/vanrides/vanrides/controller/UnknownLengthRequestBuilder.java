package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.filtros.RequestCorrelationFilter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.RequestBuilder;

import java.io.ByteArrayInputStream;

final class UnknownLengthRequestBuilder implements RequestBuilder {

    private static final String ORIGIN = "https://app.vanrides.test";

    private final String path;
    private final byte[] body;
    private final String requestId;
    private final String authorization;
    private TrackingUnknownLengthRequest request;

    UnknownLengthRequestBuilder(String path, byte[] body, String requestId) {
        this(path, body, requestId, null);
    }

    UnknownLengthRequestBuilder(
            String path,
            byte[] body,
            String requestId,
            String authorization
    ) {
        this.path = path;
        this.body = body.clone();
        this.requestId = requestId;
        this.authorization = authorization;
    }

    @Override
    public MockHttpServletRequest buildRequest(ServletContext servletContext) {
        request = new TrackingUnknownLengthRequest(servletContext, body);
        request.setMethod("POST");
        request.setRequestURI(path);
        request.setServletPath(path);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.addHeader("Origin", ORIGIN);
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, requestId);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    int bytesRead() {
        return request == null ? 0 : request.bytesRead();
    }

    private static final class TrackingUnknownLengthRequest extends MockHttpServletRequest {

        private final TrackingInputStream inputStream;

        private TrackingUnknownLengthRequest(ServletContext servletContext, byte[] body) {
            super(servletContext);
            this.inputStream = new TrackingInputStream(body);
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        @Override
        public ServletInputStream getInputStream() {
            return inputStream;
        }

        private int bytesRead() {
            return inputStream.bytesRead;
        }
    }

    private static final class TrackingInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;
        private int bytesRead;

        private TrackingInputStream(byte[] body) {
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
            throw new UnsupportedOperationException("unused in blocking WebMvc test");
        }
    }
}
