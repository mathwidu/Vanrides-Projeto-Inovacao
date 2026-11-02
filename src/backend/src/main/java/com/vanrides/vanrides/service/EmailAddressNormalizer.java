package com.vanrides.vanrides.service;

import com.vanrides.vanrides.exception.ApiStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class EmailAddressNormalizer {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_ASCII_BYTES = 254;

    public Optional<String> tryNormalize(String rawEmail) {
        if (rawEmail == null) {
            return Optional.empty();
        }

        int start = 0;
        int end = rawEmail.length();
        while (start < end && rawEmail.charAt(start) == ' ') {
            start++;
        }
        while (end > start && rawEmail.charAt(end - 1) == ' ') {
            end--;
        }

        String candidate = rawEmail.substring(start, end);
        if (candidate.length() < MIN_LENGTH || candidate.length() > MAX_ASCII_BYTES) {
            return Optional.empty();
        }

        int atCount = 0;
        int atIndex = -1;
        for (int index = 0; index < candidate.length(); index++) {
            char character = candidate.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                return Optional.empty();
            }
            if (character == '@') {
                atCount++;
                atIndex = index;
            }
        }

        if (atCount != 1 || atIndex == 0 || atIndex == candidate.length() - 1) {
            return Optional.empty();
        }

        return Optional.of(candidate.toLowerCase(Locale.ROOT));
    }

    public String normalizeRequired(String rawEmail) {
        return tryNormalize(rawEmail).orElseThrow(() -> new ApiStatusException(
                HttpStatus.BAD_REQUEST,
                "INVALID_EMAIL",
                "Informe um e-mail válido."
        ));
    }
}
