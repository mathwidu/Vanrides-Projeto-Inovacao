package com.vanrides.vanrides.filtros;

import com.vanrides.vanrides.exception.RequestBodyCapacityException;
import com.vanrides.vanrides.exception.RequestBodyTooLargeException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class LimitedRequestBodyHttpServletRequest extends HttpServletRequestWrapper {

    private static final int SKIP_BUFFER_BYTES = 8192;

    private final int maxBytes;
    private final RequestBodyReadAdmissionController admissionController;
    private final Runnable overflowCallback;
    private ServletInputStream limitedInputStream;
    private BufferedReader limitedReader;
    private boolean inputStreamRequested;
    private boolean readerRequested;

    LimitedRequestBodyHttpServletRequest(
            HttpServletRequest request,
            int maxBytes,
            RequestBodyReadAdmissionController admissionController,
            Runnable overflowCallback
    ) {
        super(Objects.requireNonNull(request, "request"));
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes deve ser positivo");
        }
        this.maxBytes = maxBytes;
        this.admissionController = Objects.requireNonNull(admissionController, "admissionController");
        this.overflowCallback = Objects.requireNonNull(overflowCallback, "overflowCallback");
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (readerRequested) {
            throw new IllegalStateException("getReader() já foi chamado para esta requisição");
        }
        inputStreamRequested = true;
        if (limitedInputStream == null) {
            limitedInputStream = new SizeLimitedServletInputStream(
                    super.getInputStream(),
                    maxBytes,
                    admissionController,
                    overflowCallback
            );
        }
        return limitedInputStream;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (inputStreamRequested) {
            throw new IllegalStateException("getInputStream() já foi chamado para esta requisição");
        }
        readerRequested = true;
        if (limitedReader == null) {
            limitedInputStream = new SizeLimitedServletInputStream(
                    super.getInputStream(),
                    maxBytes,
                    admissionController,
                    overflowCallback
            );
            limitedReader = new BufferedReader(new InputStreamReader(limitedInputStream, requestCharset()));
        }
        return limitedReader;
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

    private static final class SizeLimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final int maxBytes;
        private final RequestBodyReadAdmissionController admissionController;
        private final Runnable overflowCallback;
        private final AtomicBoolean overflowReported = new AtomicBoolean();
        private final AtomicBoolean overflowed = new AtomicBoolean();
        private final AtomicBoolean capacityRejected = new AtomicBoolean();
        private final Object readLock = new Object();
        private int bytesRead;

        private SizeLimitedServletInputStream(
                ServletInputStream delegate,
                int maxBytes,
                RequestBodyReadAdmissionController admissionController,
                Runnable overflowCallback
        ) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.maxBytes = maxBytes;
            this.admissionController = admissionController;
            this.overflowCallback = overflowCallback;
        }

        @Override
        public int read() throws IOException {
            synchronized (readLock) {
                ensureReadable();
                if (bytesRead < maxBytes) {
                    int value = readOneFromDelegate();
                    if (value >= 0) {
                        bytesRead++;
                    }
                    return value;
                }

                int overflow = readOneFromDelegate();
                if (overflow < 0) {
                    return -1;
                }
                rejectOverflow();
                return -1;
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            synchronized (readLock) {
                ensureReadable();
                if (bytesRead < maxBytes) {
                    int allowed = Math.min(length, maxBytes - bytesRead);
                    int read = readFromDelegate(bytes, offset, allowed);
                    if (read > 0) {
                        bytesRead += read;
                    }
                    return read;
                }

                int overflow = readOneFromDelegate();
                if (overflow < 0) {
                    return -1;
                }
                rejectOverflow();
                return -1;
            }
        }

        @Override
        public long skip(long bytes) throws IOException {
            if (bytes <= 0) {
                return 0;
            }
            byte[] buffer = new byte[(int) Math.min(SKIP_BUFFER_BYTES, bytes)];
            long skipped = 0;
            while (skipped < bytes) {
                int read = read(buffer, 0, (int) Math.min(buffer.length, bytes - skipped));
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    int value = read();
                    if (value < 0) {
                        break;
                    }
                    skipped++;
                    continue;
                }
                skipped += read;
            }
            return skipped;
        }

        @Override
        public int available() throws IOException {
            synchronized (readLock) {
                if (overflowed.get() || capacityRejected.get()) {
                    return 0;
                }
                int remainingWithProbe = Math.max(0, maxBytes - bytesRead) + 1;
                return Math.min(delegate.available(), remainingWithProbe);
            }
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean isFinished() {
            return overflowed.get() || capacityRejected.get() || delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }

        private void rejectOverflow() {
            overflowed.set(true);
            if (overflowReported.compareAndSet(false, true)) {
                overflowCallback.run();
            }
            throw new RequestBodyTooLargeException();
        }

        private int readOneFromDelegate() throws IOException {
            try (RequestBodyReadAdmissionController.Lease ignored = admissionController.tryAcquire()) {
                return delegate.read();
            } catch (RequestBodyCapacityException exception) {
                capacityRejected.set(true);
                throw exception;
            }
        }

        private int readFromDelegate(byte[] bytes, int offset, int length) throws IOException {
            try (RequestBodyReadAdmissionController.Lease ignored = admissionController.tryAcquire()) {
                return delegate.read(bytes, offset, length);
            } catch (RequestBodyCapacityException exception) {
                capacityRejected.set(true);
                throw exception;
            }
        }

        private void ensureReadable() {
            if (overflowed.get()) {
                rejectOverflow();
            }
            if (capacityRejected.get()) {
                throw new RequestBodyCapacityException();
            }
        }
    }
}
