package com.vanrides.vanrides.whatsapp.operator;

import com.vanrides.vanrides.whatsapp.transport.WaAttendanceDeliveryTestSupport;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceDeliveryResetIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wa_attendance_delivery_reset").withUsername("wa_test").withPassword("synthetic-test-password");

    @Test
    void scopedResetRemovesMaterializationWithAndWithoutQuestionsAndPreservesAnotherCohort() {
        var fixture = new WaAttendanceDeliveryTestSupport(POSTGRES);
        var jdbc = fixture.jdbc();
        var own = fixture.reminder();
        var other = fixture.reminder();
        UUID otherCohort = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'transport-test',true)", otherCohort);
        jdbc.update("UPDATE wa.identity SET test_cohort_id=? WHERE id IN (?,?)", otherCohort, other.driver(), other.passenger());
        UUID occurrenceWithoutQuestion = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at)
            VALUES (?,?,?,'2030-01-04',?)
            """, occurrenceWithoutQuestion, own.routine(), own.driver(), Timestamp.from(fixture.now().plusSeconds(7200)));
        jdbc.update("""
            INSERT INTO wa.attendance(id,routine_id,passenger_id,local_date)
            VALUES (?,?,?,'2030-01-04')
            """, UUID.randomUUID(), own.routine(), own.passenger());
        UUID job = jdbc.queryForObject("SELECT id FROM wa.service_credential WHERE principal='SYSTEM_JOB'", UUID.class);
        jdbc.update("""
            INSERT INTO wa.attendance_job_page(id,environment,instance_id,job_type,window_key,page_key,
              batch_limit,credential_id,response_json,created_at)
            VALUES (?,'transport-test','synthetic-transport','MATERIALIZE_OCCURRENCES','synthetic-window','ROOT',100,?,'{}',?)
            """, UUID.randomUUID(), job, Timestamp.from(fixture.now()));
        jdbc.update("""
            INSERT INTO wa.attendance_job_cursor(id,environment,instance_id,job_type,window_key,last_key,expires_at,created_at)
            VALUES (?,'transport-test','synthetic-transport','MATERIALIZE_OCCURRENCES','synthetic-window','synthetic-last',?,?)
            """, UUID.randomUUID(), Timestamp.from(fixture.now().plusSeconds(60)), Timestamp.from(fixture.now()));
        // Both drivers have dated corrections. Only one cohort is being reset.
        for (var reminder : java.util.List.of(own, other)) {
            UUID event = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category)
                VALUES (?,?,'EVOLUTION','synthetic-transport',?,'MESSAGE')
                """, event, reminder.driver(), event.toString());
            jdbc.update("""
                INSERT INTO wa.occurrence_roster_override(id,occurrence_id,routine_id,driver_id,passenger_id,
                    local_date,included,revision,reason,actor_identity_id,source_inbox_event_id,created_at)
                SELECT ?,o.id,o.routine_id,o.driver_id,?,o.local_date,false,1,'motivo sintético',o.driver_id,?,CURRENT_TIMESTAMP
                FROM wa.routine_occurrence o WHERE o.id=?
                """, UUID.randomUUID(), reminder.passenger(), event, reminder.occurrence());
        }
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transactions.executeWithoutResult(tx -> new WaOperatorReset(jdbc).reset(
                new WaOperatorService.Actor(UUID.randomUUID(), fixture.cohort(), "transport-test", fixture.now())));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.identity WHERE test_cohort_id=?", Long.class, fixture.cohort())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.routine_occurrence WHERE driver_id=?", Long.class, own.driver())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.attendance WHERE passenger_id=?", Long.class, own.passenger())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.attendance_question WHERE driver_id=?", Long.class, own.driver())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=?", Long.class, own.passenger())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.attendance_question WHERE id=?", Long.class, other.question())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.message_outbox WHERE id=?", Long.class, other.outbox())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.occurrence_roster_override WHERE driver_id=?", Long.class, own.driver())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.occurrence_roster_override WHERE driver_id=?", Long.class, other.driver())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.attendance_job_page", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.attendance_job_cursor", Long.class)).isEqualTo(1);
    }
}
