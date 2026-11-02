package com.vanrides.vanrides.filtros;

import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.MappingMatch;

import java.util.regex.Pattern;

final class ApplicationRequestPath {

    private static final Pattern MATRIX_PARAMETER = Pattern.compile(";[^/]*");

    private ApplicationRequestPath() {
    }

    static String resolve(HttpServletRequest request) {
        HttpServletMapping mapping = request.getHttpServletMapping();
        MappingMatch mappingMatch = mapping == null ? null : mapping.getMappingMatch();

        String path;
        if (mappingMatch == MappingMatch.PATH) {
            path = request.getPathInfo();
            if (path == null || path.isBlank()) {
                path = "/";
            }
        } else if (mappingMatch != null) {
            path = request.getServletPath();
        } else {
            String pathInfo = request.getPathInfo();
            path = pathInfo == null || pathInfo.isBlank()
                    ? request.getServletPath()
                    : pathInfo;
        }

        if (path == null || path.isBlank()) {
            path = requestUriWithoutContextPath(request);
        }
        if (path == null || path.isBlank()) {
            return "/";
        }

        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        String withoutMatrixParameters = MATRIX_PARAMETER.matcher(path).replaceAll("");
        return withoutMatrixParameters.isBlank() ? "/" : withoutMatrixParameters;
    }

    private static String requestUriWithoutContextPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (requestUri == null || requestUri.isBlank()) {
            return requestUri;
        }

        String contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isBlank()) {
            return requestUri;
        }
        if (requestUri.equals(contextPath)) {
            return "/";
        }
        String contextPrefix = contextPath.endsWith("/") ? contextPath : contextPath + "/";
        if (requestUri.startsWith(contextPrefix)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }
}
