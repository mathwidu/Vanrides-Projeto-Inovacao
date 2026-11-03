package com.vanrides.vanrides.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class ResendEmailDeliveryGateway {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${vanrides.email.from:}")
    private String from;

    @Value("${vanrides.email.resend.api-key:}")
    private String apiKey;

    public boolean isConfigured() {
        return !sanitizeApiKeyForHeader(apiKey).isBlank() && from != null && !from.isBlank();
    }

    public void sendEmail(String toEmail, String subject, String htmlBody, String textBody) {
        if (!isConfigured()) {
            throw new IllegalStateException("Resend não configurado. Defina vanrides.email.from e vanrides.email.resend.api-key.");
        }

        String sanitizedApiKey = sanitizeApiKeyForHeader(apiKey);
        String payload = toJsonPayload(toEmail, subject, htmlBody, textBody);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + sanitizedApiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Falha ao enviar e-mail via Resend. Status=" + response.statusCode() + ", body=" + response.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha de IO ao enviar e-mail via Resend.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envio de e-mail interrompido.", e);
        }
    }

    private String toJsonPayload(String toEmail, String subject, String htmlBody, String textBody) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "from", from.trim(),
                    "to", List.of(toEmail),
                    "subject", subject,
                    "html", htmlBody,
                    "text", textBody
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar payload do Resend.", e);
        }
    }

    static String sanitizeApiKeyForHeader(String rawApiKey) {
        if (rawApiKey == null) {
            return "";
        }

        int start = 0;
        int end = rawApiKey.length();
        while (start < end) {
            int codePoint = rawApiKey.codePointAt(start);
            if (!isBoundaryJunk(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = rawApiKey.codePointBefore(end);
            if (!isBoundaryJunk(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }

        String sanitized = rawApiKey.substring(start, end);
        if (sanitized.codePoints().anyMatch(ResendEmailDeliveryGateway::isHeaderUnsafeCodePoint)) {
            throw new IllegalStateException("Resend API key contem caracteres invalidos para header HTTP.");
        }
        return sanitized;
    }

    private static boolean isBoundaryJunk(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isWhitespace(codePoint)
                || Character.isISOControl(codePoint)
                || type == Character.SPACE_SEPARATOR
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static boolean isHeaderUnsafeCodePoint(int codePoint) {
        return isBoundaryJunk(codePoint) || codePoint == 127;
    }
}
