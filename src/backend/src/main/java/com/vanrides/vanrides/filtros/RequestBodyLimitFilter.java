package com.vanrides.vanrides.filtros;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanrides.vanrides.controller.ApiErrorResponseWriter;
import com.vanrides.vanrides.exception.RequestBodyCapacityException;
import com.vanrides.vanrides.exception.RequestBodyTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
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
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    static final String PAYLOAD_TOO_LARGE_CODE = RequestBodyTooLargeException.CODE;
    static final String PAYLOAD_TOO_LARGE_MESSAGE = RequestBodyTooLargeException.MESSAGE;
    static final String UNSUPPORTED_BODY_MEDIA_TYPE_CODE = "UNSUPPORTED_BODY_MEDIA_TYPE";
    static final String UNSUPPORTED_BODY_MEDIA_TYPE_MESSAGE =
            "Use application/json ou multipart/form-data neste serviço.";
    private static final int COPY_BUFFER_BYTES = 8192;

    private final ObjectMapper objectMapper;
    private final int maxBytes;
    private final RateLimitMetrics metrics;
    private final SensitiveRequestPolicy sensitiveRequestPolicy;
    private final RequestBodyReadAdmissionController admissionController;

    @Autowired
    public RequestBodyLimitFilter(
            ObjectMapper objectMapper,
            @Value("${vanrides.request-body.max-bytes:1048576}") int maxBytes,
            RateLimitMetrics metrics,
            SensitiveRequestPolicy sensitiveRequestPolicy,
            RequestBodyReadAdmissionController admissionController
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        if (maxBytes <= 0 || maxBytes == Integer.MAX_VALUE) {
            throw invalidMaxBytes();
        }
        this.maxBytes = maxBytes;
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.sensitiveRequestPolicy = Objects.requireNonNull(
                sensitiveRequestPolicy, "sensitiveRequestPolicy");
        this.admissionController = Objects.requireNonNull(admissionController, "admissionController");
    }

    public RequestBodyLimitFilter(
            ObjectMapper objectMapper,
            int maxBytes,
            RateLimitMetrics metrics
    ) {
        this(
                objectMapper,
                maxBytes,
                metrics,
                new SensitiveRequestPolicy(),
                new RequestBodyReadAdmissionController(32, (io.micrometer.core.instrument.MeterRegistry) null)
        );
    }

    RequestBodyLimitFilter(ObjectMapper objectMapper, int maxBytes) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        if (maxBytes <= 0 || maxBytes == Integer.MAX_VALUE) {
            throw invalidMaxBytes();
        }
        this.maxBytes = maxBytes;
        this.metrics = null;
        this.sensitiveRequestPolicy = new SensitiveRequestPolicy();
        this.admissionController = new RequestBodyReadAdmissionController(
                32,
                (io.micrometer.core.instrument.MeterRegistry) null
        );
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (CorsUtils.isPreFlightRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getContentLengthLong() > maxBytes) {
            writePayloadTooLarge(response, "CONTENT_LENGTH");
            return;
        }

        if (isFormUrlEncoded(request)) {
            writeUnsupportedBodyMediaType(response, "FORM_URLENCODED");
            return;
        }

        if (isUnsupportedMultipartVariant(request)) {
            writeUnsupportedBodyMediaType(response, "MULTIPART_VARIANT");
            return;
        }

        if (requiresContainerBodyParsing(request)) {
            withAdmission(request, response, filterChain);
            return;
        }

        if (sensitiveRequestPolicy.routeFor(request).isEmpty()) {
            LimitedRequestBodyHttpServletRequest limitedRequest =
                    new LimitedRequestBodyHttpServletRequest(
                            request,
                            maxBytes,
                            admissionController,
                            () -> recordBodyRejected("STREAM")
                    );
            try {
                filterChain.doFilter(limitedRequest, response);
            } catch (RequestBodyCapacityException exception) {
                if (response.isCommitted()) {
                    throw exception;
                }
                response.resetBuffer();
                writeCapacityUnavailable(response, exception);
            } catch (RequestBodyTooLargeException exception) {
                if (response.isCommitted()) {
                    throw exception;
                }
                response.resetBuffer();
                writePayloadTooLargeResponse(response);
            }
            return;
        }

        BufferedBody body;
        try (RequestBodyReadAdmissionController.Lease ignored = admissionController.tryAcquire()) {
            body = readAtMostLimitPlusOne(request.getInputStream());
        } catch (RequestBodyCapacityException exception) {
            writeCapacityUnavailable(response, exception);
            return;
        }
        if (body.length() > maxBytes) {
            writePayloadTooLarge(response, "STREAM");
            return;
        }
        filterChain.doFilter(
                new BoundedRequestBodyHttpServletRequest(request, body.bytes(), body.length()),
                response
        );
    }

    private boolean requiresContainerBodyParsing(HttpServletRequest request) {
        return "multipart/form-data".equals(requestMediaType(request));
    }

    private boolean isFormUrlEncoded(HttpServletRequest request) {
        return "application/x-www-form-urlencoded".equals(requestMediaType(request));
    }

    private boolean isUnsupportedMultipartVariant(HttpServletRequest request) {
        String mediaType = requestMediaType(request);
        return mediaType.startsWith("multipart/") && !"multipart/form-data".equals(mediaType);
    }

    private String requestMediaType(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private void withAdmission(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws IOException, ServletException {
        RequestBodyReadAdmissionController.Lease admission;
        try {
            admission = admissionController.tryAcquire();
        } catch (RequestBodyCapacityException exception) {
            writeCapacityUnavailable(response, exception);
            return;
        }
        try (admission) {
            filterChain.doFilter(request, response);
        }
    }

    private BufferedBody readAtMostLimitPlusOne(ServletInputStream inputStream) throws IOException {
        int readLimit = Math.addExact(maxBytes, 1);
        byte[] buffer = new byte[Math.min(COPY_BUFFER_BYTES, readLimit)];
        int size = 0;

        while (size < readLimit) {
            if (size == buffer.length) {
                int doubled = buffer.length <= readLimit / 2 ? buffer.length * 2 : readLimit;
                int nextCapacity = Math.min(readLimit, Math.max(size + 1, doubled));
                buffer = Arrays.copyOf(buffer, nextCapacity);
            }
            int read = inputStream.read(buffer, size, buffer.length - size);
            if (read < 0) {
                break;
            }
            if (read == 0) {
                int value = inputStream.read();
                if (value < 0) {
                    break;
                }
                buffer[size++] = (byte) value;
                continue;
            }
            size += read;
        }
        return new BufferedBody(size == 0 ? new byte[0] : buffer, size);
    }

    private void writePayloadTooLarge(HttpServletResponse response, String reason) throws IOException {
        recordBodyRejected(reason);
        writePayloadTooLargeResponse(response);
    }

    private void writePayloadTooLargeResponse(HttpServletResponse response) throws IOException {
        ApiErrorResponseWriter.write(
                objectMapper,
                response,
                HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                PAYLOAD_TOO_LARGE_CODE,
                PAYLOAD_TOO_LARGE_MESSAGE
        );
    }

    private void writeUnsupportedBodyMediaType(
            HttpServletResponse response,
            String reason
    ) throws IOException {
        recordBodyRejected(reason);
        ApiErrorResponseWriter.write(
                objectMapper,
                response,
                HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                UNSUPPORTED_BODY_MEDIA_TYPE_CODE,
                UNSUPPORTED_BODY_MEDIA_TYPE_MESSAGE
        );
    }

    private void recordBodyRejected(String reason) {
        if (metrics != null) {
            metrics.recordBodyRejected(reason);
        }
    }

    private void writeCapacityUnavailable(
            HttpServletResponse response,
            RequestBodyCapacityException exception
    ) throws IOException {
        exception.getHeaders().forEach((name, values) ->
                values.forEach(value -> response.addHeader(name, value)));
        ApiErrorResponseWriter.write(
                objectMapper,
                response,
                exception.getStatusCode().value(),
                exception.getApiCode(),
                exception.getReason()
        );
    }

    private static IllegalArgumentException invalidMaxBytes() {
        return new IllegalArgumentException(
                "vanrides.request-body.max-bytes deve ficar entre 1 e 2147483646"
        );
    }

    private record BufferedBody(byte[] bytes, int length) {
    }
}
