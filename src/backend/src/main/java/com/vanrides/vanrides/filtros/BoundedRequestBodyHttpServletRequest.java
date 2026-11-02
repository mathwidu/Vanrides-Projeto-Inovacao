package com.vanrides.vanrides.filtros;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class BoundedRequestBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;
    private final int bodyLength;

    public BoundedRequestBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
        this(request, body, Objects.requireNonNull(body, "body").length);
    }

    BoundedRequestBodyHttpServletRequest(HttpServletRequest request, byte[] body, int bodyLength) {
        super(Objects.requireNonNull(request, "request"));
        this.body = Objects.requireNonNull(body, "body");
        if (bodyLength < 0 || bodyLength > body.length) {
            throw new IllegalArgumentException("bodyLength fora do buffer recebido");
        }
        this.bodyLength = bodyLength;
    }

    @Override
    public ServletInputStream getInputStream() {
        return new ReplayServletInputStream(body, bodyLength);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), requestCharset()));
    }

    @Override
    public int getContentLength() {
        return bodyLength;
    }

    @Override
    public long getContentLengthLong() {
        return bodyLength;
    }

    private Charset requestCharset() {
        String encoding = getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (IllegalArgumentException ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private static final class ReplayServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;
        private ReadListener readListener;
        private boolean allDataReadNotified;

        private ReplayServletInputStream(byte[] body, int bodyLength) {
            this.delegate = new ByteArrayInputStream(body, 0, bodyLength);
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            notifyAllDataReadIfNeeded();
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = delegate.read(bytes, offset, length);
            notifyAllDataReadIfNeeded();
            return read;
        }

        @Override
        public long skip(long bytes) throws IOException {
            long skipped = delegate.skip(bytes);
            notifyAllDataReadIfNeeded();
            return skipped;
        }

        @Override
        public int available() {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public synchronized void mark(int readLimit) {
            delegate.mark(readLimit);
        }

        @Override
        public synchronized void reset() {
            delegate.reset();
            allDataReadNotified = false;
        }

        @Override
        public boolean markSupported() {
            return true;
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
            Objects.requireNonNull(listener, "listener");
            if (readListener != null) {
                throw new IllegalStateException("ReadListener já configurado");
            }
            readListener = listener;
            try {
                if (isFinished()) {
                    notifyAllDataReadIfNeeded();
                } else {
                    listener.onDataAvailable();
                    notifyAllDataReadIfNeeded();
                }
            } catch (IOException | RuntimeException exception) {
                listener.onError(exception);
            }
        }

        private void notifyAllDataReadIfNeeded() throws IOException {
            if (readListener != null && isFinished() && !allDataReadNotified) {
                allDataReadNotified = true;
                readListener.onAllDataRead();
            }
        }
    }
}
