package com.vanrides.vanrides.service;

import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.vanrides.vanrides.service.PostgresLockTestSupport.OPERATION_TIMEOUT_SECONDS;
import static com.vanrides.vanrides.service.PostgresLockTestSupport.START_TIMEOUT_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("local")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.baseline-on-migrate=false",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.show-sql=false",
                "vanrides.auth.jwt-secret=passenger-delete-race-test-secret-32-characters",
                "vanrides.rate-limit.enabled=false",
                "vanrides.push.provider=none"
        }
)
@Testcontainers(disabledWithoutDocker = true)
class PassengerTenantDeleteConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vanrides_passenger_tenant_delete_race_test")
            .withUsername("vanrides_test")
            .withPassword("vanrides_test");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PassageiroService passageiroService;

    @Autowired
    private PassengerCompanyLinker passengerCompanyLinker;

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PassengerTenantDeleteTestFixture fixture;
    private PostgresLockTestSupport concurrency;

    @BeforeEach
    void resetDatabase(@Autowired PlatformTransactionManager transactionManager) {
        fixture = new PassengerTenantDeleteTestFixture(
                postgres,
                passengerCompanyLinker,
                passageiroRepository,
                companyRepository,
                motoristaRepository,
                jdbcTemplate
        );
        fixture.resetDatabase();
        concurrency = new PostgresLockTestSupport(
                postgres,
                jdbcTemplate,
                transactionManager
        );
    }

    @Test
    void shouldReturnNotFoundAfterCommittedReassignmentWinsTheRowLock() throws Exception {
        PassengerTenantDeleteSeed seed = fixture.seed("reassignment-first");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch reassignmentReady = new CountDownLatch(1);
        CountDownLatch releaseReassignment = new CountDownLatch(1);
        CompletableFuture<Integer> deletePid = new CompletableFuture<>();
        try {
            Future<Void> reassignment = executor.submit(() -> concurrency.inTransaction(status -> {
                fixture.reassignWithRealLinkerAndFlush(seed);
                reassignmentReady.countDown();
                concurrency.await(releaseReassignment);
                return null;
            }));
            assertThat(reassignmentReady.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();

            Future<DeleteOutcome> deletion = executor.submit(() -> {
                try {
                    concurrency.inTransaction(status -> {
                        deletePid.complete(concurrency.currentBackendPid());
                        passageiroService.deletarPassageiro(
                                seed.passengerId(), seed.companyAId());
                        return null;
                    });
                    return new DeleteOutcome(true, null);
                } catch (ResponseStatusException exception) {
                    return new DeleteOutcome(false, exception.getStatusCode().value());
                }
            });
            concurrency.assertBlocked(deletePid, deletion);

            releaseReassignment.countDown();
            reassignment.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(deletion.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo(new DeleteOutcome(false, 404));

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT company_id FROM passageiro WHERE id = ?",
                    Long.class,
                    seed.passengerId()
            )).isEqualTo(seed.companyBId());
            assertThat(jdbcTemplate.update(
                    "DELETE FROM passageiro WHERE id = ? AND company_id = ?",
                    seed.passengerId(),
                    seed.companyAId()
            )).as("old tenant SQL predicate must be re-evaluated after the wait").isZero();
            fixture.assertActiveAccountAndArtifacts(seed);
        } finally {
            releaseReassignment.countDown();
            concurrency.shutdown(executor);
        }
    }

    @Test
    void shouldMakeConcurrentLinkerWaitAndNeverResurrectAfterDeleteCommits()
            throws Exception {
        PassengerTenantDeleteSeed seed = fixture.seed("delete-first");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch deleteReady = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        CompletableFuture<Integer> linkerPid = new CompletableFuture<>();
        try {
            Future<Void> deletion = executor.submit(() -> concurrency.inTransaction(status -> {
                passageiroService.deletarPassageiro(
                        seed.passengerId(), seed.companyAId());
                deleteReady.countDown();
                concurrency.await(releaseDelete);
                return null;
            }));
            assertThat(deleteReady.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<LinkOutcome> linker = executor.submit(() -> {
                try {
                    concurrency.inTransaction(status -> {
                        linkerPid.complete(concurrency.currentBackendPid());
                        fixture.reassignWithRealLinkerAndFlush(seed);
                        return null;
                    });
                    return new LinkOutcome(true, null);
                } catch (ObjectOptimisticLockingFailureException exception) {
                    return new LinkOutcome(false, exception.getClass().getName());
                }
            });
            concurrency.assertBlocked(linkerPid, linker);

            releaseDelete.countDown();
            deletion.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(linker.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo(new LinkOutcome(
                            false,
                            ObjectOptimisticLockingFailureException.class.getName()
                    ));

            fixture.assertDeletedAccountAndInvalidatedArtifacts(seed);
        } finally {
            releaseDelete.countDown();
            concurrency.shutdown(executor);
        }
    }

    @Test
    void shouldRollbackAccountAndEveryArtifactWhenForeignKeyRejectsDelete() {
        PassengerTenantDeleteSeed seed = fixture.seed("foreign-key-rollback");
        jdbcTemplate.update("""
                INSERT INTO company_link_request (
                    passageiro_id, company_id, status, source, requested_at, updated_at
                ) VALUES (?, ?, 'PENDING', 'PUBLIC_REQUEST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, seed.passengerId(), seed.companyAId());

        assertThatThrownBy(() -> passageiroService.deletarPassageiro(
                seed.passengerId(), seed.companyAId()))
                .satisfies(failure -> assertThat(fixture.sqlState(failure)).isEqualTo("23503"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM passageiro WHERE id = ?",
                Long.class,
                seed.passengerId()
        )).isEqualTo(1L);
        fixture.assertActiveAccountAndArtifacts(seed);
    }

    private record DeleteOutcome(boolean deleted, Integer status) {
    }

    private record LinkOutcome(boolean committed, String failureType) {
    }
}
