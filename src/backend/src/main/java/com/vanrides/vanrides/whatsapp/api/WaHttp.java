package com.vanrides.vanrides.whatsapp.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public final class WaHttp {
    public static final String ROUTE = WaHttp.class.getName() + ".route";
    public static final String PRINCIPAL = WaHttp.class.getName() + ".principal";
    public static final String PROOF = WaHttp.class.getName() + ".proof";
    public static final String BODY = WaHttp.class.getName() + ".body";
    public static final String PATH = WaHttp.class.getName() + ".path";
    public static final String CORRELATION = WaHttp.class.getName() + ".correlation";
    private WaHttp() { }
    public static String correlation(HttpServletRequest request) {
        Object existing = request.getAttribute(CORRELATION);
        if (existing instanceof String value) return value;
        String value = UUID.randomUUID().toString(); request.setAttribute(CORRELATION, value); return value;
    }
    public static String header(HttpServletRequest request, String name, boolean required) {
        var values = Collections.list(request.getHeaders(name));
        if (values.size() > 1 || required && values.isEmpty()) throw WaProblem.invalid();
        return values.isEmpty() ? null : values.get(0);
    }
    public static String idempotency(HttpServletRequest request) {
        String key = header(request, "Idempotency-Key", true);
        if (!key.matches("[A-Za-z0-9._:-]{8,128}")) throw WaProblem.invalid();
        return key;
    }
    public static Map<String, String> error(HttpServletRequest request, WaProblem problem) {
        return Map.of("code", problem.code().name(), "message", "Não foi possível concluir esta operação.",
            "correlationId", correlation(request));
    }
    public static void writeError(HttpServletRequest request, HttpServletResponse response, WaProblem problem) throws IOException {
        response.setStatus(problem.code().status()); response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8"); response.setHeader("Cache-Control", "no-store");
        if (request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null)
            response.setHeader("Connection", "close");
        response.setHeader("X-Correlation-Id", correlation(request));
        if (problem.nonce() != null) response.setHeader("DPoP-Nonce", problem.nonce());
        if (problem.code() == WaProblem.Code.RATE_LIMITED) response.setHeader("Retry-After", "60");
        // The existing unread-body valve may dispatch /error after committing a rejection.
        // Match its byte-oriented writer and bound the body so the error page cannot append JSON.
        byte[] bytes = WaJson.write(error(request, problem)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }
}
