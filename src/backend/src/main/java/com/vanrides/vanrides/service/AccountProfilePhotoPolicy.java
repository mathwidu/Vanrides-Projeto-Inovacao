package com.vanrides.vanrides.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

public final class AccountProfilePhotoPolicy {

    public static final int MAX_DATA_URI_LENGTH = 768 * 1024;
    public static final int MAX_URL_LENGTH = 2048;

    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private AccountProfilePhotoPolicy() {
    }

    public static String validateAndNormalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        if (isHttpsImageUrl(normalized)) {
            return normalized;
        }

        if (isLocalAvatarUrl(normalized)) {
            return normalized;
        }

        if (normalized.startsWith("data:")) {
            validateDataUri(normalized);
            return normalized;
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Foto de perfil deve ser uma URL de avatar válida ou uma imagem JPG, PNG ou WebP."
        );
    }

    private static boolean isLocalAvatarUrl(String value) {
        if (value.length() > MAX_URL_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL da foto de perfil é grande demais.");
        }
        if (!value.startsWith("/uploads/avatars/") || value.contains("..") || value.contains("\\")) {
            return false;
        }

        String path = value.toLowerCase(Locale.ROOT);
        return path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".png")
                || path.endsWith(".webp");
    }

    private static boolean isHttpsImageUrl(String value) {
        if (value.length() > MAX_URL_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL da foto de perfil é grande demais.");
        }

        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            return false;
        }

        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".png")
                || path.endsWith(".webp");
    }

    private static void validateDataUri(String value) {
        if (value.length() > MAX_DATA_URI_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Foto de perfil ficou grande demais. Escolha uma imagem menor."
            );
        }

        int commaIndex = value.indexOf(',');
        if (commaIndex < 1) {
            throw unsupportedImagePayload();
        }

        String header = value.substring(0, commaIndex).toLowerCase(Locale.ROOT);
        String payload = value.substring(commaIndex + 1);
        String mimeType = header
                .replaceFirst("^data:", "")
                .replaceFirst(";base64$", "");

        if (!header.startsWith("data:image/")
                || !header.endsWith(";base64")
                || !SUPPORTED_MIME_TYPES.contains(mimeType)) {
            throw unsupportedImagePayload();
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(payload);
            if (decoded.length == 0) {
                throw unsupportedImagePayload();
            }
        } catch (IllegalArgumentException exception) {
            throw unsupportedImagePayload();
        }
    }

    private static ResponseStatusException unsupportedImagePayload() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Foto de perfil deve ser JPG, PNG ou WebP."
        );
    }
}
