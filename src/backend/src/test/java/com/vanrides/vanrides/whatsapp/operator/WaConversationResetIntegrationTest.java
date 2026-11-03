package com.vanrides.vanrides.whatsapp.operator;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class WaConversationResetIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_conversation_reset").withUsername("vanrides_test").withPassword("synthetic-reset-password");

    @Test void resetDeletesOnlyItsOwnSupportCasesAndPausedConversationBeforeIdentity() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(source);
        Flyway flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean(); flyway.migrate();
        UUID ownBatch = UUID.randomUUID(), otherBatch = UUID.randomUUID(), own = UUID.randomUUID(), other = UUID.randomUUID();
        for (UUID batch : new UUID[]{ownBatch, otherBatch}) jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'conversation-reset-test',true)", batch);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?),(?,?)", own, ownBatch, other, otherBatch);
        for (UUID actor : new UUID[]{own, other}) {
            UUID event = UUID.randomUUID(), support = UUID.randomUUID();
            jdbc.update("INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category) VALUES (?,?,'SYNTHETIC',?,'synthetic-message','MESSAGE')",
                event, actor, "synthetic-reset-" + actor);
            jdbc.update("INSERT INTO wa.support_case(id,identity_id,deduplication_key,reason_code,correlation_id) VALUES (?,?,'synthetic-support','OTHER','synthetic-correlation')", support, actor);
            jdbc.update("INSERT INTO wa.conversation_state(identity_id,stage,last_event_id,context_json) VALUES (?,'PAUSED',?,?::jsonb)", actor, event,
                WaJson.MAPPER.createObjectNode().put("supportCaseId", support.toString()).put("sourceInboxEventId", event.toString()).toString());
        }
        var reset = new WaOperatorReset(jdbc);
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status ->
            reset.reset(new WaOperatorService.Actor(UUID.randomUUID(), ownBatch, "conversation-reset-test", Instant.parse("2030-01-01T12:00:00Z"))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.identity WHERE id=?", Long.class, own)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.conversation_state WHERE identity_id=?", Long.class, own)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.support_case WHERE identity_id=?", Long.class, own)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.support_case WHERE identity_id=? AND state='OPEN'", Long.class, other)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.conversation_state WHERE identity_id=? AND stage='PAUSED'", Long.class, other)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.identity WHERE id=?", Long.class, other)).isEqualTo(1);
    }
}
