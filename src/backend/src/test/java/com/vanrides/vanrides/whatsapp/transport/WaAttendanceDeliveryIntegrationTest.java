package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceDeliveryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_attendance_delivery");
    private WaAttendanceDeliveryTestSupport f;

    @BeforeEach
    void setup() { f = new WaAttendanceDeliveryTestSupport(POSTGRES); }

    @Test
    void jobReminderUsesItsAuthenticatedRecipientWithoutInventingACommand() {
        var reminder = f.reminder();
        var lease = f.claim(reminder);
        assertThat(lease.path("purpose").asText()).isEqualTo("ATTENDANCE_REMINDER");
        assertThat(lease.has("recipient")).isFalse();
        assertThat(lease.has("content")).isFalse();
        var reservation = f.authorize(lease);
        assertThat(reservation.path("recipient").path("address").asText())
                .isEqualTo(f.f.address(reminder.passenger()));
        assertThat(reservation.path("content").path("text").asText()).contains("VOU", "NÃO VOU");
        assertThat(f.h.jdbc.queryForObject("SELECT command_id FROM wa.message_outbox WHERE id=?",
                UUID.class, reminder.outbox())).isNull();
        assertThat(f.attempts(reminder)).isEqualTo(1);
    }

    @Test
    void otherInstanceCannotClaimOrCancelTheReminder() {
        var reminder = f.reminder();
        var worker = f.h.principal(WaPrincipal.Kind.DELIVERY_WORKER, "other-synthetic-instance");
        assertThat(f.f.outbox.claim(worker, json("{\"batchLimit\":10,\"leaseSeconds\":60}")).path("items")).isEmpty();
        assertThat(f.state(reminder)).isEqualTo("PENDING");
        assertThat(f.claim(reminder).path("outboxId").asText()).isEqualTo(reminder.outbox().toString());
    }

    @Test
    void optOutAndAnAlreadyAnsweredQuestionPreventAFirstReminder() {
        var reminder = f.reminder();
        f.h.transaction.executeWithoutResult(tx -> {
            f.optOut(reminder.passenger());
            assertThat(f.claim().path("items")).isEmpty();
            assertThat(f.state(reminder)).isEqualTo("CANCELED");
            tx.setRollbackOnly();
        });
        f.answer(reminder);
        assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.state(reminder)).isEqualTo("CANCELED");
        assertThat(f.attempts(reminder)).isZero();
    }

    @Test
    void canceledOrChangedTransportDataCannotSendAStaleQuestion() {
        var reminder = f.reminder();
        record Change(String description, String sql, UUID id) {}
        var changes = List.of(
            new Change("rotina pausada", "UPDATE wa.routine SET state='PAUSED' WHERE id=?", reminder.routine()),
            new Change("ocorrência cancelada", "UPDATE wa.routine_occurrence SET state='CANCELED' WHERE id=?", reminder.occurrence()),
            new Change("participação removida", "UPDATE wa.routine_member SET state='REMOVED' WHERE id=?", reminder.member()),
            new Change("vínculo encerrado", "UPDATE wa.driver_passenger_link SET state='CLOSED',valid_until=CURRENT_TIMESTAMP WHERE id=?", reminder.link()),
            new Change("ponto retirado", "UPDATE wa.pickup_point SET state='RETIRED' WHERE id=?", reminder.point()),
            new Change("outros dias", "UPDATE wa.routine_member SET weekdays=ARRAY[2,4]::smallint[] WHERE id=?", reminder.member()),
            new Change("horário alterado", "UPDATE wa.routine SET local_time='07:00' WHERE id=?", reminder.routine()),
            new Change("novo ponto versionado", "UPDATE wa.pickup_point SET version=version+1 WHERE id=?", reminder.point()));
        for (var change : changes) {
            f.h.transaction.executeWithoutResult(tx -> {
                f.h.jdbc.update(change.sql(), change.id());
                assertThat(f.claim().path("items")).as(change.description()).isEmpty();
                assertThat(f.state(reminder)).isEqualTo("CANCELED");
                assertThat(f.attempts(reminder)).isZero();
                tx.setRollbackOnly();
            });
        }
    }

    @Test
    void changedProfilesCohortAndSourceAliasBlockDisclosure() {
        var reminder = f.reminder();
        record Change(String description, String sql, UUID id) {}
        var changes = List.of(
            new Change("passageiro suspenso", "UPDATE wa.profile SET status='SUSPENDED' WHERE identity_id=?", reminder.passenger()),
            new Change("motorista sem acesso", "UPDATE wa.profile SET driver_enabled=false WHERE identity_id=?", reminder.driver()),
            new Change("aviso incompatível", "UPDATE wa.profile SET notice_version='other' WHERE identity_id=?", reminder.passenger()),
            new Change("outra coorte", "UPDATE wa.identity SET test_cohort_id=NULL WHERE id=?", reminder.passenger()),
            new Change("origem incompatível", "UPDATE wa.integration_inbox SET payload_json=payload_json-'senderAliasId' WHERE id=?", reminder.source()),
            new Change("tipo não suportado", "UPDATE wa.identity_alias SET alias_type='JID' WHERE id=?", reminder.alias()));
        for (var change : changes) {
            f.h.transaction.executeWithoutResult(tx -> {
                f.h.jdbc.update(change.sql(), change.id());
                assertThat(f.claim().path("items")).as(change.description()).isEmpty();
                assertThat(f.state(reminder)).isEqualTo("CANCELED");
                tx.setRollbackOnly();
            });
        }
    }

    @Test
    void startCutoffAndHumanSupportSuppressNewReminders() {
        var reminder = f.reminder();
        f.h.transaction.executeWithoutResult(tx -> {
            f.start(reminder);
            assertThat(f.claim().path("items")).isEmpty();
            tx.setRollbackOnly();
        });
        f.pause(reminder.passenger());
        assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.state(reminder)).isEqualTo("CANCELED");
    }

    @Test
    void unrelatedRoutineVersionDoesNotLoseThePromisedReminder() {
        var reminder = f.reminder();
        f.h.jdbc.update("UPDATE wa.routine SET version=version+1,stop_order_pending=true WHERE id=?", reminder.routine());
        assertThat(f.authorize(f.claim(reminder)).path("recipient").path("address").asText())
                .isEqualTo(f.f.address(reminder.passenger()));
    }

    @Test
    void expirationDoesNotCreateAnAttemptOrAReplacement() {
        var reminder = f.reminder();
        f.h.time.advance(1801);
        assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.state(reminder)).isEqualTo("EXPIRED");
        assertThat(f.attempts(reminder)).isZero();
    }

    @Test
    void aNearLinkDeadlineRestrictsTheSubmissionPermit() {
        var reminder = f.reminder();
        Instant deadline = f.h.clock.now().plusSeconds(4);
        f.h.jdbc.update("UPDATE wa.driver_passenger_link SET valid_until=? WHERE id=?",
                WaAttendanceDeliveryTestSupport.ts(deadline), reminder.link());
        assertThat(Instant.parse(f.authorize(f.claim(reminder)).path("submitBefore").asText())).isEqualTo(deadline);
    }

    @Test
    void attendanceActorResponsesKeepTheCommandOriginAndRespectSupportPause() {
        UUID actor = f.f.actor(false), command = f.f.command(actor, "ADVANCE_ATTENDANCE"), box = UUID.randomUUID();
        f.h.transaction.executeWithoutResult(tx -> WaFoundationResponses.saveAttendance(
                f.h.jdbc, box, command, actor, "Qual data e sentido você quer responder?", f.h.clock.now()));
        ObjectNode lease = f.f.claim(box);
        assertThat(lease.path("purpose").asText()).isEqualTo("COMMAND_RESPONSE");
        f.pause(actor);
        assertThat(f.decision(lease).error()).isEqualTo(com.vanrides.vanrides.whatsapp.api.WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(f.h.jdbc.queryForObject("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?", Long.class, box)).isZero();
    }
}
