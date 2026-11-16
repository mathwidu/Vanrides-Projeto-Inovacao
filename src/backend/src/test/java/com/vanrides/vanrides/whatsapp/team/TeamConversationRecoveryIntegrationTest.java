package com.vanrides.vanrides.whatsapp.team;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.transport.WaTransportTestDatabase;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.vanrides.vanrides.whatsapp.team.TeamTestDriverTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class TeamConversationRecoveryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_team_recovery").withUsername("vanrides_test").withPassword("synthetic-team-db");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    private static final String PN = "12025550101@s.whatsapp.net";
    private final WaTeamEvolution.Contact contact = new WaTeamEvolution.Contact(PN, PN, null);
    private TeamTestDriverTestSupport s;
    private TransactionTemplate tx;
    private UUID identity;

    @BeforeEach
    void reset() {
        s = new TeamTestDriverTestSupport(DATABASE.nextDataSource());
        tx = new TransactionTemplate(new DataSourceTransactionManager(s.jdbc.getDataSource()));
        identity = s.normalDriver();
        s.jdbc.update("""
            INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value)
            VALUES (?,?,'EVOLUTION',?,'PN',?)
            """, UUID.randomUUID(), identity, INSTANCE, PN);
        s.jdbc.update("INSERT INTO wa.conversation_state(identity_id) VALUES (?)", identity);
    }

    @Test
    void resumeClosesTheSelectedCaseOnceAndPreservesProfileConsentPermissionsAndDraft() {
        UUID support = pause();
        var before = s.jdbc.queryForMap("SELECT * FROM wa.profile WHERE identity_id=?", identity);
        assertThat(s.admissions.status(contact).path("supportCaseId").asText()).isEqualTo(support.toString());
        var result = resume(support, LIVE);
        assertThat(result.path("conversationPaused").asBoolean()).isFalse();
        assertThat(result.has("supportCaseId")).isFalse();
        var after = s.jdbc.queryForMap("SELECT * FROM wa.profile WHERE identity_id=?", identity);
        for (String key : new String[]{"role", "status", "notice_version", "notice_accepted_at", "driver_enabled"})
            assertThat(after.get(key)).as(key).isEqualTo(before.get(key));
        assertThat(s.jdbc.queryForObject("SELECT count(*) FROM wa.driver_authorization WHERE driver_id=?", Long.class, identity)).isOne();
        var state = s.jdbc.queryForMap("SELECT stage,question_ref,context_json::text FROM wa.conversation_state WHERE identity_id=?", identity);
        assertThat(state).containsEntry("stage", "WELCOME").containsEntry("question_ref", null);
        var context = WaJson.object((String) state.get("context_json"));
        assertThat(context.path("driverDraft").path("name").asText()).isEqualTo("Motorista Sintético");
        assertThat(context.path("driverDraft").has("confirmation")).isFalse();
        assertThat(context.has("supportCaseId")).isFalse();
        assertThat(s.jdbc.queryForMap("SELECT state,closed_at FROM wa.support_case WHERE id=?", support))
            .containsEntry("state", "CLOSED").doesNotContainEntry("closed_at", null);
        assertThat(resume(support, LIVE)).isEqualTo(result);
        assertThat(s.jdbc.queryForList("SELECT actor_kind,action,reason_code,resource_id FROM wa.audit_event"))
            .singleElement().satisfies(row -> assertThat(row).containsEntry("actor_kind", "OPERATOR")
                .containsEntry("action", "RESUME_CONVERSATION").containsEntry("reason_code", "TEAM_GITHUB_314")
                .containsEntry("resource_id", support));
    }

    @Test
    void revokedBrowserSessionRollsBackBothStateAndAudit() {
        UUID support = pause();
        assertThatThrownBy(() -> resume(support, () -> { throw WaProblem.unauthorized(); })).isInstanceOf(WaProblem.class);
        assertThat(s.admissions.status(contact).path("conversationPaused").asBoolean()).isTrue();
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.support_case WHERE id=?", String.class, support)).isEqualTo("OPEN");
        assertThat(s.count("wa.audit_event")).isZero();
    }

    @Test
    void retryOfAnOlderCaseCannotResumeANewerPause() {
        UUID old = pause();
        resume(old, LIVE);
        UUID current = pause();
        assertThat(resume(old, LIVE).path("supportCaseId").asText()).isEqualTo(current.toString());
        assertThat(s.jdbc.queryForObject("SELECT state FROM wa.support_case WHERE id=?", String.class, current)).isEqualTo("OPEN");
        assertThat(s.count("wa.audit_event")).isOne();
    }

    @Test
    void wrongCaseWrongCohortAndInactiveProfileNeverRestoreAccess() {
        UUID support = pause();
        assertThatThrownBy(() -> resume(UUID.randomUUID(), LIVE)).isInstanceOf(WaProblem.class);
        for (String status : new String[]{"SUSPENDED", "CLOSED"}) {
            s.jdbc.update("UPDATE wa.profile SET status=? WHERE identity_id=?", status, identity);
            assertThatThrownBy(() -> resume(support, LIVE)).isInstanceOf(WaProblem.class);
        }
        s.jdbc.update("UPDATE wa.profile SET status='ACTIVE' WHERE identity_id=?", identity);
        UUID otherCohort = UUID.randomUUID();
        s.jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'other-environment',false)", otherCohort);
        s.jdbc.update("UPDATE wa.identity SET test_cohort_id=? WHERE id=?", otherCohort, identity);
        assertThatThrownBy(() -> resume(support, LIVE)).isInstanceOfSatisfying(WaTeamProblem.class,
            problem -> assertThat(problem.code).isEqualTo(WaTeamProblem.Code.ADMISSION_CONFLICT));
        assertThat(s.jdbc.queryForObject("SELECT stage FROM wa.conversation_state WHERE identity_id=?", String.class, identity)).isEqualTo("PAUSED");
        assertThat(s.count("wa.audit_event")).isZero();
    }

    @Test
    void resumeWaitsForTheProfileWithoutBlockingBusinessIdentityReferences() throws Exception {
        UUID support = pause();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var pending = tx.execute(status -> {
                s.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE", identity);
                var future = executor.submit(() -> resume(support, LIVE));
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                boolean fenced = false;
                while (System.nanoTime() < deadline) {
                    s.jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    fenced = Boolean.TRUE.equals(s.jdbc.queryForObject("""
                        SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database()
                          AND pid<>pg_backend_pid() AND wait_event_type='Lock'
                          AND query LIKE 'SELECT role,status FROM wa.profile%')
                        """, Boolean.class));
                    if (fenced) break;
                    try { Thread.sleep(10); } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted);
                    }
                }
                assertThat(fenced).as("resume must wait for profile before identity").isTrue();
                s.jdbc.execute("SET LOCAL lock_timeout='1s'");
                s.jdbc.update("""
                    INSERT INTO wa.audit_event(id,actor_identity_id,actor_kind,action,correlation_id)
                    VALUES (?,?,'DRIVER','SYNTHETIC_BUSINESS_COMMAND',?)
                    """, UUID.randomUUID(), identity, UUID.randomUUID().toString());
                return future;
            });
            assertThat(pending.get(5, java.util.concurrent.TimeUnit.SECONDS).path("conversationPaused").asBoolean()).isFalse();
        } finally { executor.shutdownNow(); }
    }

    private ObjectNode resume(UUID support, Runnable live) {
        return tx.execute(status -> s.admissions.resumeConversation(GITHUB, contact, support, UUID.randomUUID(), live));
    }

    private UUID pause() {
        UUID support = UUID.randomUUID();
        s.jdbc.update("""
            INSERT INTO wa.support_case(id,identity_id,deduplication_key,reason_code,correlation_id)
            VALUES (?,?,?,'REQUESTED_SUPPORT',?)
            """, support, identity, support.toString(), UUID.randomUUID().toString());
        var context = WaJson.MAPPER.createObjectNode().put("supportCaseId", support.toString());
        context.putObject("driverDraft").put("name", "Motorista Sintético").put("confirmation", "expired-draft");
        s.jdbc.update("""
            UPDATE wa.conversation_state SET stage='PAUSED',question_ref=?,context_json=?::jsonb WHERE identity_id=?
            """, UUID.randomUUID(), context.toString(), identity);
        return support;
    }
}
