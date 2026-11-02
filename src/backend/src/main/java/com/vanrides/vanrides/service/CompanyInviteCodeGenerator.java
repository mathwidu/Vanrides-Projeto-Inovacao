package com.vanrides.vanrides.service;

import com.vanrides.vanrides.repository.CompanyInviteCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Random;

@Component
public class CompanyInviteCodeGenerator {

    private static final String INVITE_CODE_PREFIX = "INV";
    private static final char[] INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final CompanyInviteCodeRepository companyInviteCodeRepository;
    private final Random random;

    @Autowired
    public CompanyInviteCodeGenerator(CompanyInviteCodeRepository companyInviteCodeRepository) {
        this(companyInviteCodeRepository, new SecureRandom());
    }

    CompanyInviteCodeGenerator(CompanyInviteCodeRepository companyInviteCodeRepository, Random random) {
        this.companyInviteCodeRepository = companyInviteCodeRepository;
        this.random = random;
    }

    public String generateUniqueCode() {
        int year = OffsetDateTime.now().getYear();
        for (int attempt = 0; attempt < 20; attempt++) {
            String suffix = randomToken(4);
            String code = "%s-%d-%s".formatted(INVITE_CODE_PREFIX, year, suffix);
            if (companyInviteCodeRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código de convite único.");
    }

    private String randomToken(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(INVITE_ALPHABET[random.nextInt(INVITE_ALPHABET.length)]);
        }
        return builder.toString();
    }
}
