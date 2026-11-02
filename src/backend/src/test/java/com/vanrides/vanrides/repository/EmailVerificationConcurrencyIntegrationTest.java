package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.service.EmailVerificationException;
import com.vanrides.vanrides.service.EmailVerificationService;
import com.vanrides.vanrides.service.LoginUserDirectoryService;
import com.vanrides.vanrides.service.PassageiroService;
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.assertBlocked;
import static com.vanrides.vanrides.repository.LoginIdentityPostgresConcurrencyTestSupport.shutdown;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("local")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.baseline-on-migrate=false",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.show-sql=false",
                "vanrides.auth.jwt-secret=email-confirmation-race-test-secret-32-characters",
                "vanrides.rate-limit.enabled=false",
                "vanrides.push.provider=none"
        }
)
@Testcontainers(disabledWithoutDocker = true)
class EmailVerificationConcurrencyIntegrationTest {

    private static final long OPERATION_TIMEOUT_SECONDS = 15;

    @Container
    static PostgreSQLContainer<?> postgres = LoginIdentityPostgresTestSupport.postgres(
            "vanrides_email_confirmation_race_test"
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
    private PassageiroService passageiroService;

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
    void shouldLetCommittedEmailChangeFinishPendingAfterConfirmationLocksFirst()
            throws Exception {
        EmailVerificationConcurrencyIntegrationTestSupport.Seed seed =
                support.seed("confirm-first");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch confirmationReady = new CountDownLatch(1);
        CountDownLatch releaseConfirmation = new CountDownLatch(1);
        CompletableFuture<Integer> changePid = new CompletableFuture<>();
        try {
            Future<LoginUserDirectoryService.LoginUserSnapshot> confirmation = executor.submit(
                    () -> support.inTransaction(status -> {
                        LoginUserDirectoryService.LoginUserSnapshot result =
                                emailVerificationService.confirm(seed.rawToken());
                        confirmationReady.countDown();
                        support.await(releaseConfirmation);
                        return result;
                    })
            );
            assertThat(confirmationReady.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Void> change = executor.submit(() -> support.inTransaction(status -> {
                changePid.complete(support.currentBackendPid());
                passageiroService.updatePendingPassengerEmail(
                        seed.passengerId(), seed.newEmail());
                return null;
            }));
            assertBlocked(postgres, changePid, change);

            releaseConfirmation.countDown();
            assertThat(confirmation.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .emailVerified()).isTrue();
            change.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            support.assertAccount(seed.passengerId(), seed.newEmail(), false);
            support.assertTokenConsumed(seed.tokenHash());
            support.assertOwner(seed.newEmail(), seed.passengerId());
        } finally {
            releaseConfirmation.countDown();
            shutdown(executor);
        }
    }

    @Test
    void shouldRejectConfirmationAfterConcurrentEmailChangeCommits() throws Exception {
        EmailVerificationConcurrencyIntegrationTestSupport.Seed seed =
                support.seed("change-commit");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch changeReady = new CountDownLatch(1);
        CountDownLatch releaseChange = new CountDownLatch(1);
        CompletableFuture<Integer> confirmationPid = new CompletableFuture<>();
        try {
            Future<Void> change = executor.submit(() -> support.inTransaction(status -> {
                passageiroService.updatePendingPassengerEmail(
                        seed.passengerId(), seed.newEmail());
                changeReady.countDown();
                support.await(releaseChange);
                return null;
            }));
            assertThat(changeReady.await(5, TimeUnit.SECONDS)).isTrue();

            Future<ConfirmationOutcome> confirmation = executor.submit(() -> {
                try {
                    return support.inTransaction(status -> {
                        confirmationPid.complete(support.currentBackendPid());
                        emailVerificationService.confirm(seed.rawToken());
                        return new ConfirmationOutcome(true, null);
                    });
                } catch (EmailVerificationException exception) {
                    return new ConfirmationOutcome(false, exception.getCode());
                }
            });
            assertBlocked(postgres, confirmationPid, confirmation);

            releaseChange.countDown();
            change.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(confirmation.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo(new ConfirmationOutcome(
                            false,
                            "INVALID_EMAIL_VERIFICATION_TOKEN"
                    ));

            support.assertAccount(seed.passengerId(), seed.newEmail(), false);
            support.assertTokenConsumed(seed.tokenHash());
            support.assertOwner(seed.newEmail(), seed.passengerId());
        } finally {
            releaseChange.countDown();
            shutdown(executor);
        }
    }

    @Test
    void shouldConfirmOriginalEmailAfterConcurrentEmailChangeRollsBack() throws Exception {
        EmailVerificationConcurrencyIntegrationTestSupport.Seed seed =
                support.seed("change-rollback");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch changeReady = new CountDownLatch(1);
        CountDownLatch releaseChange = new CountDownLatch(1);
        CompletableFuture<Integer> confirmationPid = new CompletableFuture<>();
        try {
            Future<Void> change = executor.submit(() -> support.inTransaction(status -> {
                passageiroService.updatePendingPassengerEmail(
                        seed.passengerId(), seed.newEmail());
                changeReady.countDown();
                support.await(releaseChange);
                status.setRollbackOnly();
                return null;
            }));
            assertThat(changeReady.await(5, TimeUnit.SECONDS)).isTrue();

            Future<LoginUserDirectoryService.LoginUserSnapshot> confirmation = executor.submit(
                    () -> support.inTransaction(status -> {
                        confirmationPid.complete(support.currentBackendPid());
                        return emailVerificationService.confirm(seed.rawToken());
                    })
            );
            assertBlocked(postgres, confirmationPid, confirmation);

            releaseChange.countDown();
            change.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(confirmation.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .satisfies(snapshot -> {
                        assertThat(snapshot.email()).isEqualTo(seed.originalEmail());
                        assertThat(snapshot.emailVerified()).isTrue();
                    });

            support.assertAccount(seed.passengerId(), seed.originalEmail(), true);
            support.assertTokenConsumed(seed.tokenHash());
            support.assertOwner(seed.originalEmail(), seed.passengerId());
        } finally {
            releaseChange.countDown();
            shutdown(executor);
        }
    }

    @Test
    void shouldLetEmailChangeCommitAfterConcurrentConfirmationRollsBack()
            throws Exception {
        EmailVerificationConcurrencyIntegrationTestSupport.Seed seed =
                support.seed("confirm-rollback");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch confirmationReady = new CountDownLatch(1);
        CountDownLatch releaseConfirmation = new CountDownLatch(1);
        CompletableFuture<Integer> changePid = new CompletableFuture<>();
        try {
            Future<LoginUserDirectoryService.LoginUserSnapshot> confirmation = executor.submit(
                    () -> support.inTransaction(status -> {
                        LoginUserDirectoryService.LoginUserSnapshot result =
                                emailVerificationService.confirm(seed.rawToken());
                        confirmationReady.countDown();
                        support.await(releaseConfirmation);
                        status.setRollbackOnly();
                        return result;
                    })
            );
            assertThat(confirmationReady.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Void> change = executor.submit(() -> support.inTransaction(status -> {
                changePid.complete(support.currentBackendPid());
                passageiroService.updatePendingPassengerEmail(
                        seed.passengerId(), seed.newEmail());
                return null;
            }));
            assertBlocked(postgres, changePid, change);

            releaseConfirmation.countDown();
            confirmation.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            change.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            support.assertAccount(seed.passengerId(), seed.newEmail(), false);
            support.assertTokenConsumed(seed.tokenHash());
            support.assertOwner(seed.newEmail(), seed.passengerId());
        } finally {
            releaseConfirmation.countDown();
            shutdown(executor);
        }
    }

    @Test
    void shouldSerializeHardDeleteAfterConfirmationWithoutDeadlock() throws Exception {
        EmailVerificationConcurrencyIntegrationTestSupport.Seed seed =
                support.seed("confirm-delete");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch confirmationReady = new CountDownLatch(1);
        CountDownLatch releaseConfirmation = new CountDownLatch(1);
        CompletableFuture<Integer> deletePid = new CompletableFuture<>();
        try {
            Future<LoginUserDirectoryService.LoginUserSnapshot> confirmation = executor.submit(
                    () -> support.inTransaction(status -> {
                        LoginUserDirectoryService.LoginUserSnapshot result =
                                emailVerificationService.confirm(seed.rawToken());
                        confirmationReady.countDown();
                        support.await(releaseConfirmation);
                        return result;
                    })
            );
            assertThat(confirmationReady.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Void> deletion = executor.submit(() -> support.inTransaction(status -> {
                deletePid.complete(support.currentBackendPid());
                passageiroService.deletarPassageiro(seed.passengerId(), seed.companyId());
                return null;
            }));
            assertBlocked(postgres, deletePid, deletion);

            releaseConfirmation.countDown();
            confirmation.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            deletion.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            support.assertAccountDeleted(seed.passengerId());
            support.assertTokenConsumed(seed.tokenHash());
            support.assertOwnerDeleted(seed.originalEmail(), seed.passengerId());
        } finally {
            releaseConfirmation.countDown();
            shutdown(executor);
        }
    }

    private record ConfirmationOutcome(boolean success, String code) {
    }
}
