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
        UUID device=UUID.randomUUID(),finishReplay=UUID.randomUUID();
        jdbc.update("INSERT INTO wa.profile(identity_id,role,status,display_name) VALUES (?,'DRIVER','ONBOARDING','Synthetic reset driver')",own);
        jdbc.update("INSERT INTO wa.driver_authorization(driver_id,generation) VALUES (?,1)",own);
        jdbc.update("INSERT INTO wa.driver_device(id,driver_id,jkt,public_jwk,generation,environment,platform,app_version) VALUES (?,?,repeat('A',43),'{}',1,'conversation-reset-test','ANDROID','synthetic-reset')",device,own);
        jdbc.update("INSERT INTO wa.operation_replay(id,operation_kind,scope_id,request_key,request_hash,response_ciphertext,expires_at) VALUES (?,'TRACK_FINISH',?,'synthetic-reset-finish',repeat('0',64),decode(repeat('00',29),'hex'),TIMESTAMPTZ '2030-01-02T00:00:00Z')",finishReplay,device);
        UUID offer=UUID.randomUUID(),intent=UUID.randomUUID(),routine=UUID.randomUUID(),occurrence=UUID.randomUUID(),command=UUID.randomUUID(),box=UUID.randomUUID();
        UUID event=jdbc.queryForObject("SELECT last_event_id FROM wa.conversation_state WHERE identity_id=?",UUID.class,own);
        jdbc.update("INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,destination_latitude,destination_longitude,state,policy_version) VALUES (?,?,'Trajeto sintético','OUTBOUND',ARRAY[2]::smallint[],'15:00','America/Sao_Paulo',0,0,'ACTIVE','wa-mvp-v1')",routine,own);
        jdbc.update("INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at) VALUES (?,?,?,'2030-01-01',TIMESTAMPTZ '2030-01-01T18:00:00Z')",occurrence,routine,own);
        jdbc.update("INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,request_hash,result_json,created_at,expires_at) VALUES (?,?,?,'synthetic-reset-offer','ADVANCE_RUN_SETUP',repeat('1',64),'{}',TIMESTAMPTZ '2030-01-01T12:00:00Z',TIMESTAMPTZ '2030-01-02T00:00:00Z')",command,own,event);
        jdbc.update("INSERT INTO wa.start_offer(id,driver_id,device_id,environment,created_at,expires_at) VALUES (?,?,?,'conversation-reset-test',TIMESTAMPTZ '2030-01-01T12:00:00Z',TIMESTAMPTZ '2030-01-01T12:05:00Z')",offer,own,device);
        jdbc.update("INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,command_id,response_part,policy_version,payload_json,not_before,expires_at,created_at) VALUES (?,?,'RESPONSE',?,'run-start','run-start-v1','{}',TIMESTAMPTZ '2030-01-01T12:00:00Z',TIMESTAMPTZ '2030-01-01T12:05:00Z',TIMESTAMPTZ '2030-01-01T12:00:00Z')",box,own,command);
        jdbc.update("INSERT INTO wa.start_intent(id,driver_id,occurrence_id,device_id,token_hash,environment,expires_at,created_at,offer_id,offer_position,request_command_id,delivery_outbox_id) VALUES (?,?,?,?,repeat('2',64),'conversation-reset-test',TIMESTAMPTZ '2030-01-01T12:05:00Z',TIMESTAMPTZ '2030-01-01T12:00:00Z',?,1,?,?)",intent,own,occurrence,device,offer,command,box);
        jdbc.update("UPDATE wa.message_outbox SET start_intent_id=?,start_payload_ciphertext=decode(repeat('00',29),'hex') WHERE id=?",intent,box);
        var reset = new WaOperatorReset(jdbc);
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status ->
            reset.reset(new WaOperatorService.Actor(UUID.randomUUID(), ownBatch, "conversation-reset-test", Instant.parse("2030-01-01T12:00:00Z"))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.identity WHERE id=?", Long.class, own)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.operation_replay WHERE id=?",Long.class,finishReplay)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.start_offer WHERE id=?",Long.class,offer)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.start_intent WHERE id=?",Long.class,intent)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.message_outbox WHERE id=?",Long.class,box)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.conversation_state WHERE identity_id=?", Long.class, own)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.support_case WHERE identity_id=?", Long.class, own)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.support_case WHERE identity_id=? AND state='OPEN'", Long.class, other)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.conversation_state WHERE identity_id=? AND stage='PAUSED'", Long.class, other)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.identity WHERE id=?", Long.class, other)).isEqualTo(1);
    }
}
