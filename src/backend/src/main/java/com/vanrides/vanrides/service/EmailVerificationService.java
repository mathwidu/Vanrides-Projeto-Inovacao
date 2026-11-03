package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.EmailVerificationToken;
import com.vanrides.vanrides.repository.EmailVerificationTokenRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private LoginUserDirectoryService loginUserDirectoryService;

    @Autowired
    private EmailDeliveryGateway emailDeliveryGateway;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private LoginIdentityService loginIdentityService;

    @Value("${vanrides.email.verification.token-ttl-minutes:1440}")
    private long tokenTtlMinutes;

    @Value("${vanrides.email.verification.resend-cooldown-minutes:15}")
    private long resendCooldownMinutes;

    @Value("${vanrides.email.verification.public-base-url:}")
    private String publicBaseUrl =
            "http://localhost:8080/auth/email-verifications/confirm";

    @Transactional
    public void sendVerificationIfAllowed(UserRole role, Long userId, String email) {
        LoginUserDirectoryService.LoginUserSnapshot snapshot =
                lockCurrentOwner(role, userId, email).orElse(null);
        if (snapshot == null || !snapshot.accessConfigured()
                || snapshot.emailVerified()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<EmailVerificationToken> previouslyPendingTokens =
                emailVerificationTokenRepository
                        .findAllByUserRoleAndUserIdAndConsumedAtIsNull(
                                role.claimValue(),
                                userId
                        );

        String rawToken = generateRawToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserRole(role.claimValue());
        token.setUserId(userId);
        token.setEmailSnapshot(snapshot.email());
        token.setTokenHash(hashToken(rawToken));
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(tokenTtlMinutes)));
        emailVerificationTokenRepository.saveAndFlush(token);

        String verificationUrl = buildVerificationUrl(rawToken);
        try {
            emailDeliveryGateway.sendEmail(
                    snapshot.email(),
                    "Confirme seu e-mail no VanRides",
                    buildHtmlBody(verificationUrl, snapshot.email()),
                    buildTextBody(verificationUrl, snapshot.email())
            );
        } catch (RuntimeException e) {
            token.setConsumedAt(Instant.now());
            emailVerificationTokenRepository.saveAndFlush(token);
            log.warn(
                    "Falha ao enviar e-mail de verificação; token novo foi invalidado e links anteriores foram preservados. Tipo={}",
                    e.getClass().getSimpleName()
            );
            return;
        }

        Instant consumedAt = Instant.now();
        for (EmailVerificationToken previousToken : previouslyPendingTokens) {
            previousToken.setConsumedAt(consumedAt);
        }
        emailVerificationTokenRepository.flush();
        loginUserDirectoryService.recordVerificationEmailSent(role, userId, now);
    }

    @Transactional(dontRollbackOn = EmailVerificationException.class)
    public LoginUserDirectoryService.LoginUserSnapshot confirm(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }

        EmailVerificationTokenRepository.ConfirmationCandidate candidate =
                emailVerificationTokenRepository
                        .findConfirmationCandidateByTokenHash(hashToken(rawToken))
                        .orElseThrow(this::invalidToken);

        UserRole role;
        try {
            role = UserRole.fromValue(candidate.getUserRole());
        } catch (IllegalArgumentException exception) {
            throw consumeAndInvalidate(lockCandidate(candidate));
        }

        LoginUserDirectoryService.LoginUserSnapshot snapshot = loginUserDirectoryService
                .lockAccountByRoleAndId(role, candidate.getUserId())
                .orElse(null);
        boolean ownerMatches = snapshot != null
                && loginIdentityService.lockExactOwnerForEmailVerification(
                                candidate.getEmailSnapshot(),
                                role,
                                candidate.getUserId()
                        )
                        .filter(candidate.getEmailSnapshot()::equals)
                        .isPresent();

        EmailVerificationToken token = lockCandidate(candidate);
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw consumeAndInvalidate(token);
        }
        if (!ownerMatches || !token.getEmailSnapshot().equals(snapshot.email())) {
            throw consumeAndInvalidate(token);
        }

        OffsetDateTime verifiedAt = OffsetDateTime.now(ZoneOffset.UTC);
        loginUserDirectoryService.markVerified(role, token.getUserId(), verifiedAt);
        revokePendingTokens(role, token.getUserId());
        return loginUserDirectoryService.requireByRoleAndId(role, token.getUserId());
    }

    private EmailVerificationToken lockCandidate(
            EmailVerificationTokenRepository.ConfirmationCandidate candidate) {
        EmailVerificationToken token = emailVerificationTokenRepository
                .findUnconsumedByIdForUpdate(candidate.getId())
                .orElseThrow(this::invalidToken);
        if (!Objects.equals(candidate.getUserRole(), token.getUserRole())
                || !Objects.equals(candidate.getUserId(), token.getUserId())
                || !Objects.equals(candidate.getEmailSnapshot(), token.getEmailSnapshot())
                || !Objects.equals(candidate.getTokenHash(), token.getTokenHash())) {
            throw consumeAndInvalidate(token);
        }
        return token;
    }

    @Transactional
    public void resendForEmail(String email) {
        LoginUserDirectoryService.LoginUserSnapshot snapshot = loginUserDirectoryService.findByEmail(email);
        if (snapshot == null || snapshot.emailVerified() || !snapshot.accessConfigured()) {
            return;
        }
        if (snapshot.emailVerificationLastSentAt() != null
                && snapshot.emailVerificationLastSentAt().plusMinutes(resendCooldownMinutes).isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            return;
        }
        try {
            sendVerificationIfAllowed(snapshot.role(), snapshot.userId(), snapshot.email());
        } catch (IllegalArgumentException ignored) {
            // A conta pode ter sido removida entre a busca opaca e o envio.
            // O contrato de reenvio permanece indistinguível de uma conta inexistente.
        }
    }

    public void assertVerifiedAccess(AuthenticatedUser user) {
        LoginUserDirectoryService.LoginUserSnapshot snapshot = loginUserDirectoryService.requireByRoleAndId(user.getRoleEnum(), user.getUserId());
        if (!loginIdentityService.isOwnedBy(user.getEmail(), user.getRoleEnum(), user.getUserId())) {
            throw new IllegalArgumentException("Usuário autenticável não encontrado.");
        }
        if (!snapshot.accessConfigured()) {
            throw new IllegalArgumentException("Usuário sem acesso configurado.");
        }
        if (!snapshot.emailVerified()) {
            throw new EmailVerificationException(
                    "EMAIL_NOT_VERIFIED",
                    "O e-mail informado ainda não foi verificado.",
                    snapshot.email(),
                    403
            );
        }
    }

    @Transactional
    public void markPendingAndRevokeSessions(UserRole role, Long userId) {
        requireLockedOwner(role, userId);
        loginUserDirectoryService.markPending(role, userId);
        revokePendingTokens(role, userId);
        refreshTokenService.revokeAllForUser(userId, role.claimValue());
    }

    @Transactional
    public void revokeAccountArtifacts(UserRole role, Long userId) {
        requireLockedOwner(role, userId);
        revokePendingTokens(role, userId);
        refreshTokenService.revokeAllForUser(userId, role.claimValue());
    }

    private void revokePendingTokens(UserRole role, Long userId) {
        Instant now = Instant.now();
        for (EmailVerificationToken token : emailVerificationTokenRepository.findAllByUserRoleAndUserIdAndConsumedAtIsNull(role.claimValue(), userId)) {
            token.setConsumedAt(now);
        }
        emailVerificationTokenRepository.flush();
    }

    private Optional<LoginUserDirectoryService.LoginUserSnapshot> lockCurrentOwner(
            UserRole role,
            Long userId,
            String expectedEmail) {
        return loginUserDirectoryService.lockAccountByRoleAndId(role, userId)
                .filter(snapshot -> Objects.equals(expectedEmail, snapshot.email()))
                .filter(snapshot -> loginIdentityService
                        .lockExactOwnerForEmailVerification(
                                snapshot.email(),
                                snapshot.role(),
                                snapshot.userId()
                        )
                        .filter(snapshot.email()::equals)
                        .isPresent());
    }

    private LoginUserDirectoryService.LoginUserSnapshot requireLockedOwner(
            UserRole role,
            Long userId) {
        LoginUserDirectoryService.LoginUserSnapshot snapshot = loginUserDirectoryService
                .lockAccountByRoleAndId(role, userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Identidade global de login inconsistente."
                ));
        boolean ownerMatches = loginIdentityService
                .lockExactOwnerForEmailVerification(
                        snapshot.email(),
                        snapshot.role(),
                        snapshot.userId()
                )
                .filter(snapshot.email()::equals)
                .isPresent();
        if (!ownerMatches) {
            throw new IllegalStateException("Identidade global de login inconsistente.");
        }
        return snapshot;
    }

    private String buildVerificationUrl(String rawToken) {
        String trimmedBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
        if (trimmedBaseUrl.isEmpty()) {
            throw new IllegalStateException(
                    "Base publica de confirmacao de e-mail nao configurada."
            );
        }
        return trimmedBaseUrl + "#token="
                + UriUtils.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String buildHtmlBody(String verificationUrl, String email) {
        String escapedEmail = HtmlUtils.htmlEscape(email);
        return """
                <p>Olá,</p>
                <p>Recebemos uma solicitação para ativar o acesso do e-mail <strong>%s</strong> no VanRides.</p>
                <p><a href="%s">Clique aqui para confirmar seu e-mail</a></p>
                <p>Se você não solicitou esse cadastro, ignore esta mensagem.</p>
                """.formatted(escapedEmail, HtmlUtils.htmlEscape(verificationUrl));
    }

    private String buildTextBody(String verificationUrl, String email) {
        return """
                Ola,

                Recebemos uma solicitacao para ativar o acesso do email %s no VanRides.
                Confirme seu email no link abaixo:
                %s

                Se voce nao solicitou esse cadastro, ignore esta mensagem.
                """.formatted(email, verificationUrl);
    }

    private String generateRawToken() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }

    private String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    private EmailVerificationException invalidToken() {
        return new EmailVerificationException(
                "INVALID_EMAIL_VERIFICATION_TOKEN",
                "Token de verificação inválido, expirado ou já utilizado.",
                null,
                400
        );
    }

    private EmailVerificationException consumeAndInvalidate(EmailVerificationToken token) {
        token.setConsumedAt(Instant.now());
        emailVerificationTokenRepository.save(token);
        return invalidToken();
    }
}
