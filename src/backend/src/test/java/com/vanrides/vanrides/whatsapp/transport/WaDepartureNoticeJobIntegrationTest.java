package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaDepartureNoticeJobIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_departure_notice_job");
    private static final Instant TWENTY_NINE_BEFORE = Instant.parse("2030-01-02T08:31:00Z");
    private static final Instant THIRTY_ONE_BEFORE = Instant.parse("2030-01-02T08:29:00Z");
    private static final String HEADER = "Lista da viagem de hoje, quarta (02/01), ida às 06:00 (Ida habitual)";
    private static final String LAST_LINE = "\nPara iniciar a rota, envie INICIAR SAÍDA.";
    private WaAttendanceCalendarTestSupport f;

    @BeforeEach
    void setup() {
        f = new WaAttendanceCalendarTestSupport(POSTGRES);
    }

    @Test
    void queuesOneListTwentyNineMinutesBeforeDepartureWithTheExactText() {
        UUID bruno = f.member(f.routine, true), carla = f.member(f.routine, true), diego = f.member(f.routine, true);
        name(f.passenger, "Ana");
        name(bruno, "Bruno");
        name(carla, "Carla");
        name(diego, "Diego");
        f.materialize();
        answer(f.passenger, "GOING");
        answer(bruno, "GOING");
        answer(diego, "NOT_GOING");
        f.h.time.set(TWENTY_NINE_BEFORE);
        assertEquals(1, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        var notice = notice();
        assertEquals(HEADER + "\nConfirmados (2): Ana, Bruno\nPendentes, continuam previstos (1): Carla\nNão vão (1): Diego"
                + LAST_LINE, notice.get("text"));
        assertEquals(f.driver, notice.get("recipient_identity_id"));
        assertEquals("REMINDER", notice.get("purpose"));
        assertEquals("driver-attendance-v1", notice.get("policy_version"));
        assertEquals("pre-departure", notice.get("reminder_window"));
        assertNull(notice.get("command_id"));
        assertEquals(scheduledAt(), ((Timestamp) notice.get("expires_at")).toInstant());
        assertEquals(notice.get("expires_at"), notice.get("deadline_at"));
        assertEquals("DEPARTURE", notice.get("kind"));
        assertEquals(f.h.jdbc.queryForObject("SELECT id FROM wa.integration_inbox WHERE identity_id=?", UUID.class, f.driver),
                notice.get("recipient_source_event_id"));
    }

    @Test
    void anOperationalExclusionCountsAsNotGoingAndAnInclusionKeepsThePassengerPending() {
        UUID bruno = f.member(f.routine, true), carla = f.member(f.routine, true);
        name(f.passenger, "Ana");
        name(bruno, "Bruno");
        name(carla, "Carla");
        f.materialize();
        answer(f.passenger, "GOING");
        override(bruno, true);
        override(carla, false);
        f.h.time.set(TWENTY_NINE_BEFORE);
        assertEquals(1, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        assertEquals(HEADER + "\nConfirmados (1): Ana\nPendentes, continuam previstos (1): Bruno\nNão vão (1): Carla" + LAST_LINE,
                notice().get("text"));
    }

    @Test
    void nothingIsQueuedThirtyOneMinutesBeforeOrAtDeparture() {
        f.materialize();
        f.h.time.set(THIRTY_ONE_BEFORE);
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        f.h.time.set(scheduledAt());
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        assertEquals(0, notices());
    }

    @Test
    void aSecondTickNeverDuplicatesTheList() {
        f.materialize();
        f.h.time.set(TWENTY_NINE_BEFORE);
        var first = f.tick("DUE_REMINDERS", 100, null);
        assertEquals(1, first.path("processed").asInt());
        assertEquals(first, f.tick("DUE_REMINDERS", 100, null));
        f.nextWindow();
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        assertEquals(1, notices());
        assertEquals(1, f.count("SELECT count(*) FROM wa.driver_attendance_notice"));
    }

    @Test
    void aStartedRunQueuesNothing() {
        f.materialize();
        f.h.jdbc.update("""
                INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_json,snapshot_at)
                SELECT ?,id,routine_id,driver_id,local_date,'STARTING','attendance-test','{}'::jsonb,? FROM wa.routine_occurrence
                WHERE routine_id=? AND local_date='2030-01-02'
                """, UUID.randomUUID(), Timestamp.from(TWENTY_NINE_BEFORE), f.routine);
        f.h.time.set(TWENTY_NINE_BEFORE);
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        assertEquals(0, notices());
    }

    @Test
    void aDriverWithoutAnInboundMessageGetsNothing() {
        f.materialize();
        f.h.jdbc.update("UPDATE wa.integration_inbox SET payload_json=payload_json-'senderAliasId' WHERE identity_id=?", f.driver);
        f.h.time.set(TWENTY_NINE_BEFORE);
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        assertEquals(0, notices());
    }

    @Test
    void aRoutineWithoutRosterMembersGetsNothing() {
        f.materialize();
        f.h.time.set(TWENTY_NINE_BEFORE);
        f.h.jdbc.update("UPDATE wa.driver_passenger_link SET state='CLOSED',valid_until=? WHERE passenger_id=?",
                Timestamp.from(f.h.clock.now()), f.passenger);
        f.tick("DUE_REMINDERS", 100, null);
        assertEquals(0, notices());
        f.nextWindow();
        f.h.jdbc.update("UPDATE wa.routine_member SET state='REMOVED' WHERE routine_id=?", f.routine);
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        assertEquals(0, notices());
    }

    @Test
    void ineligibleOccurrencesRoutinesAndDriversGetNothing() {
        f.materialize();
        f.h.time.set(TWENTY_NINE_BEFORE);
        record Change(String description, String sql, Object id) {}
        var changes = List.of(
                new Change("motorista pausado", "INSERT INTO wa.conversation_state(identity_id,stage) VALUES (?,'PAUSED')", f.driver),
                new Change("rotina pausada", "UPDATE wa.routine SET state='PAUSED' WHERE id=?", f.routine),
                new Change("outro dia da semana", "UPDATE wa.routine SET weekdays=ARRAY[1,2,4,5]::smallint[] WHERE id=?", f.routine),
                new Change("ocorrência cancelada", "UPDATE wa.routine_occurrence SET state='CANCELED' WHERE routine_id=?", f.routine),
                new Change("ocorrência fora do calendário", "UPDATE wa.routine_occurrence SET calendar_eligible=false WHERE routine_id=?", f.routine),
                new Change("motorista sem acesso", "UPDATE wa.profile SET driver_enabled=false WHERE identity_id=?", f.driver),
                new Change("motorista fora da coorte", "UPDATE wa.identity SET test_cohort_id=NULL WHERE id=?", f.driver));
        for (var change : changes) {
            f.h.transaction.executeWithoutResult(tx -> {
                f.h.jdbc.update(change.sql(), change.id());
                assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt(), change.description());
                assertEquals(0, notices(), change.description());
                tx.setRollbackOnly();
            });
        }
    }

    @Test
    void aLongRosterFallsBackToCountsAndPointsToThePresenceList() {
        String longName = "N".repeat(80);
        for (int i = 0; i < 30; i++) {
            name(f.member(f.routine, true), longName + i);
        }
        f.materialize();
        long members = f.count("SELECT count(*) FROM wa.routine_member WHERE routine_id=?", f.routine);
        f.h.time.set(TWENTY_NINE_BEFORE);
        assertEquals(1, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        String text = (String) notice().get("text");
        assertEquals(HEADER + "\nPendentes, continuam previstos: " + members + "\nEnvie PRESENÇAS para ver os nomes." + LAST_LINE, text);
    }

    @Test
    void reminderPagingStaysCorrectWhenBothKindsAreDue() {
        UUID evening = f.routine("Volta noturna", "RETURN", "20:30", "America/Sao_Paulo");
        f.member(evening, true);
        f.materialize();
        f.h.time.set(Instant.parse("2030-01-03T23:05:00Z"));
        int processed = 0, pages = 0;
        String cursor = null;
        do {
            var page = f.tick("DUE_REMINDERS", 1, cursor);
            processed += page.path("processed").asInt();
            cursor = page.path("nextCursor").isNull() ? null : page.path("nextCursor").asText();
            pages++;
        } while (cursor != null && pages < 10);
        assertNull(cursor);
        assertEquals(3, processed);
        assertEquals(3, pages);
        assertEquals(2, f.count("SELECT count(*) FROM wa.message_outbox WHERE reminder_window='previous-day'"));
        assertEquals(1, notices());
        assertEquals(3, f.count("SELECT count(*) FROM wa.attendance_job_page WHERE job_type='DUE_REMINDERS'"));
        f.nextWindow();
        assertEquals(0, f.tick("DUE_REMINDERS", 1, null).path("processed").asInt());
    }

    private void name(UUID passenger, String name) {
        f.h.jdbc.update("UPDATE wa.profile SET display_name=? WHERE identity_id=?", name, passenger);
    }

    private void answer(UUID passenger, String state) {
        f.h.jdbc.update("""
                UPDATE wa.attendance SET state=?,actor_identity_id=passenger_id,answered_at=?
                WHERE passenger_id=? AND local_date='2030-01-02'
                """, state, Timestamp.from(f.h.clock.now()), passenger);
    }

    private void override(UUID passenger, boolean included) {
        f.h.jdbc.update("""
                INSERT INTO wa.occurrence_roster_override(id,occurrence_id,routine_id,driver_id,passenger_id,local_date,included,
                  revision,reason,actor_identity_id,source_inbox_event_id,created_at)
                SELECT ?,o.id,o.routine_id,o.driver_id,?,o.local_date,?,1,'Ajuste sintético',o.driver_id,
                  ?,?
                FROM wa.routine_occurrence o WHERE o.routine_id=? AND o.local_date='2030-01-02'
                """, UUID.randomUUID(), passenger, included, f.h.event(f.driver, WaAttendanceCalendarTestSupport.json()),
                Timestamp.from(f.h.clock.now()), f.routine);
    }

    private Instant scheduledAt() {
        return f.h.jdbc.queryForObject("SELECT scheduled_at FROM wa.routine_occurrence WHERE routine_id=? AND local_date='2030-01-02'",
                Timestamp.class, f.routine).toInstant();
    }

    private long notices() {
        return f.count("SELECT count(*) FROM wa.message_outbox WHERE reminder_window='pre-departure'");
    }

    private Map<String, Object> notice() {
        assertEquals(1, notices());
        return f.h.jdbc.queryForMap("""
                SELECT o.*,o.payload_json->>'text' AS text,n.kind,n.deadline_at,n.recipient_source_event_id
                FROM wa.message_outbox o JOIN wa.driver_attendance_notice n ON n.outbox_id=o.id
                WHERE o.reminder_window='pre-departure'
                """);
    }
}
