package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.privacy.WaPrivacyTestSupport.*;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaPrivacyErasureIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_privacy").withUsername("synthetic_privacy").withPassword("synthetic-privacy-test-only");
    private WaPrivacyTestSupport f;
    @BeforeEach void setup() { f = new WaPrivacyTestSupport(POSTGRES, true); }

    @Test void closedPassengerLosesEveryMappedCopyWithoutChangingTheOtherPassengerOrAttendance() {
        var other = coreFingerprint(f.second);
        String attendance = f.string("SELECT row_to_json(a)::text FROM wa.attendance a WHERE passenger_id=?", f.first);
        String otherSnapshot = participant(f.second), oldStop = f.string("SELECT physical_stop_key::text FROM wa.run_stop WHERE passenger_id=?", f.first);
        assertThat(f.service().close(SCOPE, f.first).status()).isEqualTo("CLOSED");
        assertThat(f.journal.records).hasSize(1);
        f.time.days(31);
        f.sweepTerminal();
        var result = f.service().erase(SCOPE, f.first);
        assertThat(result.status()).isEqualTo("ERASED");
        assertThat(coreFingerprint(f.second)).isEqualTo(other);
        assertThat(participant(f.second)).isEqualTo(otherSnapshot);
        assertThat(f.string("SELECT row_to_json(a)::text FROM wa.attendance a WHERE passenger_id=?", f.first)).isEqualTo(attendance);
        assertThat(f.string("SELECT physical_stop_key::text FROM wa.run_stop WHERE passenger_id=?", f.first)).isEqualTo(oldStop);
        assertThat(f.string("SELECT status FROM wa.profile WHERE identity_id=?", f.first)).isEqualTo("CLOSED");
        assertThat(personalCopies()).doesNotContain(NAME, REFERENCE, ALIAS, "12.3456");
        assertThat(f.count("identity")).isEqualTo(3);
        assertThat(f.count("audit_event")).isEqualTo(1);
        assertThat(f.string("SELECT stage FROM wa.conversation_state WHERE identity_id=?", f.first)).isEqualTo("PAUSED");
        assertThat(f.service().erase(SCOPE, f.first).status()).isEqualTo("ALREADY_ERASED");
        assertThat(f.journal.records).hasSize(1);
    }

    @Test void defaultDisabledDoesNotCloseOrCreateAnIntent() {
        assertThat(f.service(WaPrivacyPolicy.disabled(), f.journal).close(SCOPE, f.first).status()).isEqualTo("DISABLED");
        assertThat(f.string("SELECT status FROM wa.profile WHERE identity_id=?", f.first)).isEqualTo("ACTIVE");
        assertThat(f.count("privacy_erasure_ledger")).isZero();
    }

    @Test void aRunningTripBlocksClosureAndErasure() {
        f = new WaPrivacyTestSupport(POSTGRES, false);
        assertThat(f.service().close(SCOPE, f.first).status()).isEqualTo("ACTIVE_REFERENCE");
        assertThat(f.service().erase(SCOPE, f.first).status()).isEqualTo("ACTIVE_REFERENCE");
        assertThat(f.count("privacy_erasure_ledger")).isZero();
        assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?", f.first)).isEqualTo(NAME);
    }

    @Test void thirtyDaysAreMeasuredFromExplicitClosureAndAClosedLinkIsInsufficient() {
        f.jdbc.update("UPDATE wa.driver_passenger_link SET state='CLOSED',valid_until=valid_from WHERE passenger_id=?", f.first);
        f.time.days(40);
        assertThat(f.service().erase(SCOPE, f.first).status()).isEqualTo("NOT_CLOSED");
        f.service().close(SCOPE, f.first);
        f.time.days(29);
        assertThat(f.service().erase(SCOPE, f.first).status()).isEqualTo("NOT_DUE");
        assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?", f.first)).isEqualTo(NAME);
    }

    @Test void missingOrUnverifiedJournalNeverAuthorizesSubjectErasure() {
        var denied = f.service(WaPrivacyPolicy.syntheticTests(), WaPrivacyJournal.unavailable());
        assertThat(denied.close(SCOPE, f.first).status()).isEqualTo("JOURNAL_PENDING");
        f.time.days(31);
        assertThat(denied.erase(SCOPE, f.first).status()).isEqualTo("JOURNAL_PENDING");
        assertThat(f.string("SELECT state FROM wa.privacy_erasure_ledger")).isEqualTo("INTENT");
        assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?", f.first)).isEqualTo(NAME);
        f.sweepTerminal();
        assertThat(f.service().erase(SCOPE, f.first).status()).isEqualTo("ERASED");
    }

    @Test void guardsRejectBypassChangedOperationalFactsAndRehydration() {
        assertThatThrownBy(() -> f.jdbc.update("UPDATE wa.run_stop SET latitude_snapshot=NULL,longitude_snapshot=NULL,privacy_erased_at=? WHERE passenger_id=?", f.timestamp(), f.first))
            .isInstanceOf(DataIntegrityViolationException.class);
        f.service().close(SCOPE, f.first); f.time.days(31); f.sweepTerminal(); f.service().erase(SCOPE, f.first);
        assertThatThrownBy(() -> f.jdbc.update("UPDATE wa.run_stop SET stop_order=99 WHERE passenger_id=?", f.first))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> f.jdbc.update("UPDATE wa.profile SET display_name='rehydrated' WHERE identity_id=?", f.first))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> f.jdbc.update("UPDATE wa.identity_alias SET alias_value=? WHERE identity_id=?", ALIAS, f.first))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> f.jdbc.update("UPDATE wa.operation_run SET snapshot_json=jsonb_set(snapshot_json,ARRAY['passengerNames',?],to_jsonb(?::text),true) WHERE id=?",
            f.first.toString(), NAME, f.run)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> f.jdbc.update("DELETE FROM wa.privacy_erasure_ledger")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void anotherScopeCannotCloseOrEraseTheSubject() {
        for (var other : java.util.List.of(new WaPrivacyService.Scope("another-environment", INSTANCE),
            new WaPrivacyService.Scope(ENV, "another-instance"))) {
            assertThat(f.service().close(other, f.first).status()).isEqualTo("OUT_OF_SCOPE");
        }
        assertThat(f.count("privacy_erasure_ledger")).isZero();
    }

    @Test void driverErasureProjectsItsLabelsAndDestinationsWithoutChangingPassengerFields() {
        String firstParticipant = participant(f.first), secondParticipant = participant(f.second);
        f.service().close(SCOPE, f.driver); f.time.days(31); f.sweepTerminal();
        assertThat(f.service().erase(SCOPE, f.driver).status()).isEqualTo("ERASED");
        assertThat(participant(f.first)).isEqualTo(firstParticipant);
        assertThat(participant(f.second)).isEqualTo(secondParticipant);
        assertThat(f.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", f.run)).doesNotContain("ROTINA_DRIVER_PRIVADA");
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.attendance_question WHERE routine_name IS NOT NULL", Long.class)).isZero();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.common_pickup_point WHERE name IS NOT NULL OR latitude IS NOT NULL", Long.class)).isZero();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.routine WHERE name IS NOT NULL OR destination_latitude IS NOT NULL", Long.class)).isZero();
    }

    @Test void aForgedAcknowledgementCannotMarkTheIntentJournaled() {
        WaPrivacyJournal forged = new WaPrivacyJournal() {
            public Ack append(Entry entry) { return new Ack(entry.eventId(), "0".repeat(64), 1, "f".repeat(64)); }
            public boolean verify(Entry entry, Ack acknowledgement) { return true; }
        };
        var service = f.service(WaPrivacyPolicy.syntheticTests(), forged);
        assertThat(service.close(SCOPE, f.first).status()).isEqualTo("JOURNAL_PENDING");
        f.time.days(31);
        assertThat(service.erase(SCOPE, f.first).status()).isEqualTo("JOURNAL_PENDING");
        assertThat(f.string("SELECT state FROM wa.privacy_erasure_ledger")).isEqualTo("INTENT");
        assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?", f.first)).isEqualTo(NAME);
    }

    @Test void anOccupiedCopyWriterDefersBeforeChangingAnyPayload() throws Exception {
        f.service().close(SCOPE, f.first); f.time.days(31); f.sweepTerminal();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try {
            var writer = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(f.transactions).execute(tx -> {
                f.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE", f.driver);
                locked.countDown();
                try { if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Synthetic lock timeout"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
                return true;
            }));
            assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(f.service().erase(SCOPE, f.first).status()).isEqualTo("COPY_WRITER_BUSY");
            assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?", f.first)).isEqualTo(NAME);
            assertThat(f.string("SELECT state FROM wa.privacy_erasure_ledger")).isEqualTo("JOURNALED");
            release.countDown(); writer.get(10, java.util.concurrent.TimeUnit.SECONDS);
            f.sweepTerminal();
            assertThat(f.service().erase(SCOPE, f.first).status()).isEqualTo("ERASED");
        } finally {
            release.countDown(); executor.shutdownNow();
            assertThat(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void pendingPinIsNotErasedBeforeJournalAndClosedIdentityCannotAuthorizeIt() {
        UUID command = UUID.randomUUID(), box = UUID.randomUUID();
        f.jdbc.update("""
            INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,
              request_hash,result_json,created_at,expires_at)
            VALUES (?,?,?,'synthetic-private-pin','GET_DRIVER_LOCATION',?,'{}',?,?)
            """, command, f.first, f.inboxes.get(f.first), "b".repeat(64), f.timestamp(),
            java.sql.Timestamp.from(f.time.now.plusSeconds(86400)));
        f.jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,command_id,response_part,policy_version,
              payload_kind,payload_json,position_captured_at,created_at,not_before,expires_at)
            VALUES (?,?,'PIN',?,'synthetic-pin','driver-location-v1','PIN',?::jsonb,?,?,?,?)
            """, box, f.first, command, "{\"coordinates\":{\"latitude\":12.3456,\"longitude\":45.6789}}",
            f.timestamp(), f.timestamp(), f.timestamp(), java.sql.Timestamp.from(f.time.now.plusSeconds(10)));
        String payload = f.string("SELECT payload_json::text FROM wa.message_outbox WHERE id=?", box);
        var settings = new com.vanrides.vanrides.whatsapp.config.WaSettings(new org.springframework.mock.env.MockEnvironment()
            .withProperty("vanrides.wa.environment", ENV));
        var access = new com.vanrides.vanrides.whatsapp.transport.WaTransportAccess(f.jdbc, settings, f.clock);
        assertThat(access.allowedIdentity(f.first)).isTrue();
        var denied = f.service(WaPrivacyPolicy.syntheticTests(), WaPrivacyJournal.unavailable());
        assertThat(denied.close(SCOPE, f.first).status()).isEqualTo("JOURNAL_PENDING");
        assertThat(access.allowedIdentity(f.first)).isFalse();
        assertThat(f.string("SELECT payload_json::text FROM wa.message_outbox WHERE id=?", box)).isEqualTo(payload);
        assertThat(f.string("SELECT state FROM wa.message_outbox WHERE id=?", box)).isEqualTo("PENDING");
        f.time.days(31);
        assertThat(denied.erase(SCOPE, f.first).status()).isEqualTo("JOURNAL_PENDING");
        assertThat(f.string("SELECT payload_json::text FROM wa.message_outbox WHERE id=?", box)).isEqualTo(payload);
        f.sweepTerminal();
        assertThat(f.service().erase(SCOPE, f.first).status()).isEqualTo("ERASED");
        assertThat(f.string("SELECT payload_json::text FROM wa.message_outbox WHERE id=?", box)).isEqualTo("{}");
        assertThat(f.string("SELECT state FROM wa.message_outbox WHERE id=?", box)).isEqualTo("EXPIRED");
    }

    private com.fasterxml.jackson.databind.node.ObjectNode coreFingerprint(UUID subject) {
        var value = WaJson.object(fingerprint(subject)); value.remove("outbox"); return value;
    }

    private String fingerprint(UUID subject) {
        return f.string("""
            SELECT jsonb_build_object('profile',(SELECT to_jsonb(p) FROM wa.profile p WHERE identity_id=?),
              'alias',(SELECT to_jsonb(a) FROM wa.identity_alias a WHERE identity_id=?),
              'point',(SELECT to_jsonb(p) FROM wa.pickup_point p WHERE passenger_id=?),
              'stop',(SELECT to_jsonb(s) FROM wa.run_stop s WHERE passenger_id=?),
              'outbox',(SELECT to_jsonb(o) FROM wa.message_outbox o WHERE recipient_identity_id=?))::text
            """, subject, subject, subject, subject, subject);
    }
    private String participant(UUID subject) {
        return f.string("SELECT value::text FROM wa.operation_run r,jsonb_array_elements(r.snapshot_json->'participants') WHERE r.id=? AND value->>'passenger'=?",
            f.run, subject.toString());
    }
    private String personalCopies() {
        return fingerprint(f.first) + f.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", f.run)
            + f.string("SELECT payload_json::text FROM wa.integration_inbox WHERE id=?", f.inboxes.get(f.first))
            + f.string("SELECT payload_json::text FROM wa.message_outbox WHERE recipient_identity_id=?", f.driver)
            + f.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?", f.first)
            + f.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?", f.driver)
            + f.string("SELECT row_to_json(o)::text FROM wa.occurrence_roster_override o WHERE passenger_id=?", f.first);
    }
}
