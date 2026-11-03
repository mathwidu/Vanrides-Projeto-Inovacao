package com.vanrides.vanrides.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

final class EmailVerificationConcurrencyIntegrationTestSupport {

    private static final long OPERATION_TIMEOUT_SECONDS = 15;
    private static final String KNOWN_LAST_SENT_AT = "2026-07-17 10:15:30+00";

    private final PostgreSQLContainer<?> postgres;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;

    EmailVerificationConcurrencyIntegrationTestSupport(
            PostgreSQLContainer<?> postgres,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactions) {
        this.postgres = postgres;
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = transactions;
    }

    Seed seed(String suffix) throws Exception {
        String originalEmail = suffix + ".old@example.com";
        String newEmail = suffix + ".new@example.com";
        String rawToken = "confirmation-token-" + suffix;
        String tokenHash = sha256(rawToken);
        try (Connection connection = LoginIdentityPostgresTestSupport.open(postgres)) {
            long companyId = LoginIdentityPostgresTestSupport.defaultCompanyId(connection);
            long passengerId = LoginIdentityPostgresTestSupport.insertPassenger(
                    connection,
                    companyId,
                    originalEmail
            );
            assertThat(LoginIdentityPostgresTestSupport.update(connection, """
                    UPDATE passageiro
                    SET email_verification_last_sent_at = CAST(? AS TIMESTAMP WITH TIME ZONE)
                    WHERE id = ?
                    """, KNOWN_LAST_SENT_AT, passengerId)).isOne();
            LoginIdentityPostgresTestSupport.update(connection, """
                    INSERT INTO email_verification_token (
                        user_role, user_id, email_snapshot, token_hash,
                        expires_at, created_at
                    ) VALUES (
                        'PASSENGER', ?, ?, ?,
                        CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP
                    )
                    """, passengerId, originalEmail, tokenHash);
            return new Seed(companyId, passengerId, originalEmail, newEmail, rawToken, tokenHash);
        }
    }

    <T> T inTransaction(TransactionCallback<T> callback) {
        return transactions.execute(callback);
    }

    int currentBackendPid() {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class)
        );
    }

    void assertAccount(long passengerId, String email, boolean verified) {
        assertThat(jdbcTemplate.queryForMap("""
                SELECT email, email_verified
                FROM passageiro
                WHERE id = ?
                """, passengerId))
                .containsEntry("email", email)
                .containsEntry("email_verified", verified);
    }

    void assertVerificationLastSentAtPreserved(Seed seed) {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT email_verification_last_sent_at = CAST(? AS TIMESTAMP WITH TIME ZONE)
                FROM passageiro
                WHERE id = ?
                """, Boolean.class, KNOWN_LAST_SENT_AT, seed.passengerId())).isTrue();
    }

    void assertAccountDeleted(long passengerId) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM passageiro WHERE id = ?",
                Long.class,
                passengerId
        )).isZero();
    }

    void assertTokenConsumed(String tokenHash) {
        assertThat(jdbcTemplate.queryForObject("""
                SELECT consumed_at IS NOT NULL
                FROM email_verification_token
                WHERE token_hash = ?
                """, Boolean.class, tokenHash)).isTrue();
    }

    void assertOwner(String email, long passengerId) {
        assertThat(ownerExists(email, passengerId)).isTrue();
    }

    void assertOwnerDeleted(String email, long passengerId) {
        assertThat(ownerExists(email, passengerId)).isFalse();
    }

    void expireToken(String tokenHash) {
        assertThat(jdbcTemplate.update("""
                UPDATE email_verification_token
                SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE token_hash = ?
                """, tokenHash)).isOne();
    }

    void seedActiveRefreshToken(Seed seed, String refreshHash) {
        assertThat(jdbcTemplate.update("""
                INSERT INTO refresh_token (
                    token_hash, user_id, user_role, user_email, company_id,
                    expires_at, revoked, created_at, updated_at
                ) VALUES (
                    ?, ?, 'PASSENGER', ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day',
                    FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, refreshHash, seed.passengerId(), seed.originalEmail(), seed.companyId()))
                .isOne();
    }

    StateFingerprint fullStateFingerprint(Seed seed) {
        return stateFingerprint(seed, false);
    }

    StateFingerprint stateFingerprintIgnoringTokenConsumption(Seed seed) {
        return stateFingerprint(seed, true);
    }

    boolean isTokenConsumed(String tokenHash) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT consumed_at IS NOT NULL
                FROM email_verification_token
                WHERE token_hash = ?
                """, Boolean.class, tokenHash));
    }

    private StateFingerprint stateFingerprint(Seed seed, boolean ignoreTokenConsumption) {
        String tokenProjection = ignoreTokenConsumption
                ? "(to_jsonb(token_row) - 'consumed_at')"
                : "to_jsonb(token_row)";
        return new StateFingerprint(
                fingerprint("""
                        SELECT to_jsonb(passenger)::text
                        FROM passageiro passenger
                        WHERE passenger.id = ?
                        """, seed.passengerId()),
                fingerprint("""
                        SELECT COALESCE(
                            jsonb_agg(to_jsonb(identity_row)
                                ORDER BY identity_row.normalized_email),
                            '[]'::jsonb
                        )::text
                        FROM (
                            SELECT * FROM login_identity
                            WHERE user_role = 'PASSENGER' AND user_id = ?
                        ) identity_row
                        """, seed.passengerId()),
                fingerprint("""
                        SELECT COALESCE(
                            jsonb_agg(to_jsonb(refresh_row) ORDER BY refresh_row.id),
                            '[]'::jsonb
                        )::text
                        FROM (
                            SELECT * FROM refresh_token
                            WHERE user_role = 'PASSENGER' AND user_id = ?
                        ) refresh_row
                        """, seed.passengerId()),
                fingerprint("""
                        SELECT COALESCE(
                            jsonb_agg(%s ORDER BY token_row.id),
                            '[]'::jsonb
                        )::text
                        FROM (
                            SELECT * FROM email_verification_token
                            WHERE user_role = 'PASSENGER' AND user_id = ?
                        ) token_row
                        """.formatted(tokenProjection), seed.passengerId())
        );
    }

    private String fingerprint(String query, Object... args) {
        return sha256(Objects.requireNonNull(
                jdbcTemplate.queryForObject(query, String.class, args)
        ));
    }

    void await(CountDownLatch latch) {
        try {
            if (!latch.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating transaction", exception);
        }
    }

    private boolean ownerExists(String email, long passengerId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM login_identity
                    WHERE normalized_email = ?
                      AND user_role = 'PASSENGER'
                      AND user_id = ?
                )
                """, Boolean.class, email, passengerId));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record Seed(
            long companyId,
            long passengerId,
            String originalEmail,
            String newEmail,
            String rawToken,
            String tokenHash) {
    }

    record StateFingerprint(
            String account,
            String identities,
            String refreshTokens,
            String verificationTokens) {
    }
}
