package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.service.EmailVerificationException;
import com.vanrides.vanrides.service.EmailVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.assertBlocked;
import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.shutdown;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("local")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.baseline-on-migrate=false",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.show-sql=false",
                "vanrides.auth.jwt-secret=email-explicit-intent-test-secret-32-characters",
                "vanrides.rate-limit.enabled=false",
                "vanrides.push.provider=none"
        }
)
@Testcontainers(disabledWithoutDocker = true)
class EmailVerificationConfirmationConcurrencyIntegrationTest {

    private static final long OPERATION_TIMEOUT_SECONDS = 15;

    @Container
    static PostgreSQLContainer<?> postgres = LoginIdentityPostgresTestSupport.postgres(
            "vanrides_email_explicit_intent_test"
    );

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private EmailVerificationConcurrencyIntegrationTestSupport support;

    @BeforeEach
    void resetDatabase(@Autowired PlatformTransactionManager transactionManager) {
        LoginIdentityPostgresTestSupport.resetLatest(postgres);
        support = new EmailVerificationConcurrencyIntegrationTestSupport(
                postgres,
                jdbcTemplate,
                new TransactionTemplate(transactionManager)
        );
    }

    @Test
    void shouldAllowExactlyOneOfTwoConcurrentConfirmations() throws Exception {
        EmailVerificationConcurrencyIntegrationTestSupport.Seed seed =
                support.seed("same-token-race");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch winnerReady = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        CompletableFuture<Integer> loserPid = new CompletableFuture<>();
        try {
            Future<EmailVerificationConcurrencyIntegrationTestSupport.StateFingerprint> winner =
                    executor.submit(() -> support.inTransaction(status -> {
                        emailVerificationService.confirm(seed.rawToken());
                        support.assertVerificationLastSentAtPreserved(seed);
                        EmailVerificationConcurrencyIntegrationTestSupport.StateFingerprint state =
                                support.fullStateFingerprint(seed);
                        winnerReady.countDown();
                        support.await(releaseWinner);
                        return state;
                    })
            );
            assertThat(winnerReady.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ConfirmationOutcome> loser = executor.submit(() ->
                    support.inTransaction(status -> {
                        loserPid.complete(support.currentBackendPid());
                        try {
                            emailVerificationService.confirm(seed.rawToken());
                            return new ConfirmationOutcome(true, null);
                        } catch (EmailVerificationException exception) {
                            return new ConfirmationOutcome(false, exception.getCode());
                        }
                    })
            );
            assertBlocked(postgres, loserPid, loser);

            releaseWinner.countDown();
            EmailVerificationConcurrencyIntegrationTestSupport.StateFingerprint winnerState =
                    winner.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            List<ConfirmationOutcome> outcomes = List.of(
                    new ConfirmationOutcome(true, null),
                    loser.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );
            assertThat(outcomes).filteredOn(ConfirmationOutcome::success).hasSize(1);
            assertThat(outcomes)
                    .filteredOn(outcome -> !outcome.success())
                    .extracting(ConfirmationOutcome::code)
                    .containsExactly("INVALID_EMAIL_VERIFICATION_TOKEN");
            support.assertAccount(seed.passengerId(), seed.originalEmail(), true);
            support.assertVerificationLastSentAtPreserved(seed);
            support.assertTokenConsumed(seed.tokenHash());
            support.assertOwner(seed.originalEmail(), seed.passengerId());
            assertThat(support.fullStateFingerprint(seed)).isEqualTo(winnerState);
        } finally {
            releaseWinner.countDown();
            shutdown(executor);
        }
    }

    @Test
    void shouldRejectReplayWithoutChangingVerifiedIdentityOrSession() throws Exception {
        EmailVerificationConcurrencyIntegrationTestSupport.Seed seed =
                support.seed("committed-replay");
        String refreshHash = "refresh-committed-replay";
        support.seedActiveRefreshToken(seed, refreshHash);

        assertThat(emailVerificationService.confirm(seed.rawToken()).emailVerified()).isTrue();
        support.assertVerificationLastSentAtPreserved(seed);
        EmailVerificationConcurrencyIntegrationTestSupport.StateFingerprint committedState =
                support.fullStateFingerprint(seed);

        assertGenericInvalid(() -> emailVerificationService.confirm(seed.rawToken()));

        assertThat(support.fullStateFingerprint(seed)).isEqualTo(committedState);
        support.assertVerificationLastSentAtPreserved(seed);
        support.assertTokenConsumed(seed.tokenHash());
    }

    @Test
    void shouldConsumeExpiredTokenWithoutChangingPendingIdentityOrSession() throws Exception {
        EmailVerificationConcurrencyIntegrationTestSupport.Seed seed =
                support.seed("expired-terminal");
        String refreshHash = "refresh-expired-terminal";
        support.seedActiveRefreshToken(seed, refreshHash);
        support.expireToken(seed.tokenHash());
        EmailVerificationConcurrencyIntegrationTestSupport.StateFingerprint pendingState =
                support.stateFingerprintIgnoringTokenConsumption(seed);
        assertThat(support.isTokenConsumed(seed.tokenHash())).isFalse();

        assertGenericInvalid(() -> emailVerificationService.confirm(seed.rawToken()));

        assertThat(support.stateFingerprintIgnoringTokenConsumption(seed))
                .isEqualTo(pendingState);
        support.assertAccount(seed.passengerId(), seed.originalEmail(), false);
        support.assertVerificationLastSentAtPreserved(seed);
        support.assertTokenConsumed(seed.tokenHash());
        support.assertOwner(seed.originalEmail(), seed.passengerId());
    }

    private void assertGenericInvalid(Runnable confirmation) {
        assertThatThrownBy(confirmation::run)
                .isInstanceOfSatisfying(EmailVerificationException.class, exception -> {
                    assertThat(exception.getCode())
                            .isEqualTo("INVALID_EMAIL_VERIFICATION_TOKEN");
                    assertThat(exception.getEmail()).isNull();
                });
    }

    private record ConfirmationOutcome(boolean success, String code) {
    }
}
