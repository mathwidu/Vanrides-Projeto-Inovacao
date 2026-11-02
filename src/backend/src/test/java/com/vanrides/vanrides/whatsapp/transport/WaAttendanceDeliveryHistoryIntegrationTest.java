package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceDeliveryHistoryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_attendance_delivery_history");
    private WaAttendanceDeliveryTestSupport f;

    @BeforeEach
    void setup() { f = new WaAttendanceDeliveryTestSupport(POSTGRES); }

    @Test
    void resultAndCallbackRemainRecordableAfterCancellationOptOutAndHumanSupport() {
        var reminder = f.reminder();
        var reservation = f.authorize(f.claim(reminder));
        f.optOut(reminder.passenger());
        f.pause(reminder.passenger());
        f.h.jdbc.update("UPDATE wa.attendance_question SET state='CLOSED' WHERE id=?", reminder.question());
        f.h.jdbc.update("UPDATE wa.driver_passenger_link SET state='CLOSED',valid_until=CURRENT_TIMESTAMP WHERE id=?", reminder.link());
        assertThat(f.submitted(reservation, "synthetic_accepted_before_cancel").path("state").asText()).isEqualTo("SUBMITTED");
        UUID callback = f.callback("synthetic_accepted_before_cancel", "READ");
        assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.state(reminder)).isEqualTo("READ");
        assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.integration_inbox WHERE id=?", String.class, callback)).isEqualTo("COMPLETED");
        assertThat(f.attempts(reminder)).isEqualTo(1);
    }

    @Test
    void callbackBeforeSubmissionResultWaitsForItsMatchingProviderId() {
        var reminder = f.reminder();
        var reservation = f.authorize(f.claim(reminder));
        UUID callback = f.callback("synthetic_callback_first", "DELIVERY_ACK");
        f.claim();
        assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.integration_inbox WHERE id=?", String.class, callback)).isEqualTo("PENDING");
        assertThat(f.submitted(reservation, "synthetic_callback_first").path("state").asText()).isEqualTo("DELIVERED");
        assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.integration_inbox WHERE id=?", String.class, callback)).isEqualTo("COMPLETED");
    }

    @Test
    void unknownSubmissionNeverBecomesASecondAttemptAfterRestart() {
        var reminder = f.reminder();
        var reservation = f.authorize(f.claim(reminder));
        f.f.outbox.result(f.f.delivery, reminder.outbox(), json("{}").put("attemptToken", reservation.path("attemptToken").asText())
                .put("outcome", "UNKNOWN").put("failureCode", "TIMEOUT"));
        var restarted = f.h.proxy(new WaOutboxService(f.h.jdbc, f.h.access,
                new WaDeliveryReconciler(f.h.jdbc, f.h.clock), f.h.settings, f.h.clock));
        assertThat(restarted.claim(f.f.delivery, json("{\"batchLimit\":10,\"leaseSeconds\":60}")).path("items")).isEmpty();
        assertThat(f.state(reminder)).isEqualTo("UNKNOWN");
        assertThat(f.attempts(reminder)).isEqualTo(1);
    }

    @Test
    void abandonedReservationCanRecordALateKnownResultWithoutResubmitting() {
        var reminder = f.reminder();
        var reservation = f.authorize(f.claim(reminder));
        f.h.time.advance(11);
        assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.state(reminder)).isEqualTo("UNKNOWN");
        assertThat(f.submitted(reservation, "synthetic_late_submission_ack").path("state").asText()).isEqualTo("SUBMITTED");
        assertThat(f.attempts(reminder)).isEqualTo(1);
    }

    @Test
    void exactSubmissionReplayReturnsItsStoredResponseAfterAStateChange() {
        var reminder = f.reminder();
        var reservation = f.authorize(f.claim(reminder));
        var first = f.submitted(reservation, "synthetic_replayed_result");
        f.answer(reminder);
        f.callback("synthetic_replayed_result", "READ");
        f.claim();
        assertThat(f.submitted(reservation, "synthetic_replayed_result")).isEqualTo(first);
        assertThat(f.state(reminder)).isEqualTo("READ");
        assertThat(f.attempts(reminder)).isEqualTo(1);
    }

    @Test
    void providerCitationIsResolvedOnlyForItsAuthenticatedRecipientAndScope() {
        var reminder = f.reminder();
        f.submitted(f.authorize(f.claim(reminder)), "synthetic_quoted_question");
        assertThat(WaAttendanceQuotes.resolveQuestion(f.h.jdbc, reminder.passenger(), INSTANCE,
                "transport-test", "synthetic_quoted_question")).contains(reminder.question());
        assertThat(WaAttendanceQuotes.resolveQuestion(f.h.jdbc, reminder.driver(), INSTANCE,
                "transport-test", "synthetic_quoted_question")).isEmpty();
        assertThat(WaAttendanceQuotes.resolveQuestion(f.h.jdbc, reminder.passenger(), "another-instance",
                "transport-test", "synthetic_quoted_question")).isEmpty();
        assertThat(WaAttendanceQuotes.resolveQuestion(f.h.jdbc, reminder.passenger(), INSTANCE,
                "another-environment", "synthetic_quoted_question")).isEmpty();
    }

    @Test
    void closedOldQuestionRemainsTheCitationTargetAfterANewQuestionOpens() {
        var reminder = f.reminder();
        f.submitted(f.authorize(f.claim(reminder)), "synthetic_old_question");
        f.h.jdbc.update("UPDATE wa.attendance_question SET state='CLOSED' WHERE id=?", reminder.question());
        UUID replacement = UUID.randomUUID();
        f.h.jdbc.update("""
            INSERT INTO wa.attendance_question(id,attendance_id,occurrence_id,routine_id,driver_id,passenger_id,
              local_date,member_id,link_id,pickup_point_id,instance_id,environment,job_credential_id,
              link_version,member_version,pickup_version,schedule_hash,intent_hash,routine_name,direction,local_time,timezone,
              opens_at,reminder_at,send_until,answer_until)
            SELECT ?,attendance_id,occurrence_id,routine_id,driver_id,passenger_id,local_date,member_id,link_id,
              pickup_point_id,instance_id,environment,job_credential_id,link_version,member_version,pickup_version,
              schedule_hash,intent_hash,routine_name,direction,local_time,timezone,opens_at,reminder_at,send_until,answer_until
            FROM wa.attendance_question WHERE id=?
            """, replacement, reminder.question());
        f.h.jdbc.update("UPDATE wa.attendance SET question_ref=? WHERE id=?", replacement, reminder.attendance());
        assertThat(WaAttendanceQuotes.resolveQuestion(f.h.jdbc, reminder.passenger(), INSTANCE,
                "transport-test", "synthetic_old_question")).contains(reminder.question());
    }

    @Test
    void unknownMalformedOrUnacceptedCitationCannotResolveToAQuestion() {
        var reminder = f.reminder();
        f.authorize(f.claim(reminder));
        for (String citation : new String[]{"synthetic_not_accepted", "event_" + UUID.randomUUID(), "quote with spaces", "", WaCrypto.randomToken()}) {
            assertThat(WaAttendanceQuotes.resolveQuestion(f.h.jdbc, reminder.passenger(), INSTANCE,
                    "transport-test", citation)).isEmpty();
        }
    }
}
