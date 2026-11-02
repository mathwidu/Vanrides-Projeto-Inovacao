package com.vanrides.vanrides.whatsapp.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.util.UUID;

import static com.vanrides.vanrides.whatsapp.persistence.WhatsappPostgresTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class WhatsappSchemaInvariantIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = postgres("vanrides_wa_invariants");

    @BeforeEach
    void resetDatabase() throws Exception {
        resetTo(postgres, "26");
    }

    @Test
    void shouldScopeOpaqueAliasesByProviderInstanceAndType() throws Exception {
        try (Connection connection = open(postgres)) {
            UUID first = identity(connection, null);
            UUID second = identity(connection, null);
            String insert = """
                    INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value)
                    VALUES (?,?,'SYNTHETIC',?,'LID','opaque-unchanged')
                    """;
            update(connection, insert, UUID.randomUUID(), first, INSTANCE);
            assertConstraint("23505", "uk_identity_alias", () -> update(connection, insert,
                    UUID.randomUUID(), second, INSTANCE));
            update(connection, insert, UUID.randomUUID(), second, "other-synthetic-instance");
            assertThat(number(connection, "SELECT count(*) FROM wa.identity_alias WHERE verified_phone IS NULL"))
                    .isEqualTo(2);
        }
    }

    @Test
    void shouldDeduplicateMessagesSeparatelyFromStatusProgressionAndEchoes() throws Exception {
        try (Connection connection = open(postgres)) {
            UUID actor = identity(connection, null);
            UUID event = inbox(connection, actor);
            assertConstraint("23505", "uk_inbox_message", () -> update(connection, """
                    INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category)
                    VALUES (?,?,'SYNTHETIC',?,?,'MESSAGE')
                    """, UUID.randomUUID(), actor, INSTANCE, event.toString()));
            String callback = """
                    INSERT INTO wa.integration_inbox(id,provider,instance_id,message_id,event_key,category)
                    VALUES (?,'SYNTHETIC',?,?,?,'STATUS')
                    """;
            update(connection, callback, UUID.randomUUID(), INSTANCE, event.toString(), "delivered-v1");
            update(connection, callback, UUID.randomUUID(), INSTANCE, event.toString(), "read-v2");
            assertConstraint("23505", "uk_inbox_callback", () -> update(connection, callback,
                    UUID.randomUUID(), INSTANCE, event.toString(), "read-v2"));
            update(connection, """
                    INSERT INTO wa.integration_inbox(id,provider,instance_id,message_id,event_key,category,state)
                    VALUES (?,'SYNTHETIC',?,?,'echo-v1','ECHO','SKIPPED')
                    """, UUID.randomUUID(), INSTANCE, event.toString());
            assertThat(number(connection, "SELECT count(*) FROM wa.integration_inbox")).isEqualTo(4);
        }
    }

    @Test
    void shouldBindCommandIdempotencyToActorAndOriginalInboxEvent() throws Exception {
        try (Connection connection = open(postgres)) {
            UUID actor = identity(connection, null);
            UUID other = identity(connection, null);
            UUID event = inbox(connection, actor);
            UUID anotherEvent = inbox(connection, actor);
            command(connection, UUID.randomUUID(), actor, event, "request-one");
            assertConstraint("23505", "uk_command_inbox_event", () -> command(connection,
                    UUID.randomUUID(), actor, event, "changed-client-key"));
            assertConstraint("23505", "uk_command_actor_key", () -> command(connection,
                    UUID.randomUUID(), actor, anotherEvent, "request-one"));
            assertConstraint("23503", "fk_command_inbox_actor", () -> command(connection,
                    UUID.randomUUID(), other, anotherEvent, "other-actor-key"));
            command(connection, UUID.randomUUID(), other, inbox(connection, other), "request-one");
            assertThat(number(connection, "SELECT count(*) FROM wa.command_receipt")).isEqualTo(2);
        }
    }

    @Test
    void shouldRequireCorrectProfileRolesAndOnlyOneApprovedLinkPerPassenger() throws Exception {
        try (Connection connection = open(postgres)) {
            UUID driver = identity(connection, "DRIVER");
            UUID otherDriver = identity(connection, "DRIVER");
            UUID passenger = identity(connection, "PASSENGER");
            assertConstraint("23503", "fk_authorization_driver", () -> update(connection,
                    "INSERT INTO wa.driver_authorization(driver_id) VALUES (?)", passenger));
            assertConstraint("23503", "fk_link_passenger", () -> link(connection, driver, otherDriver, "REQUESTED"));
            UUID first = link(connection, driver, passenger, "APPROVED");
            assertConstraint("23505", "uk_link_active_passenger", () -> link(connection,
                    otherDriver, passenger, "APPROVED"));
            update(connection, """
                    UPDATE wa.driver_passenger_link SET state='CLOSED',valid_until=CURRENT_TIMESTAMP WHERE id=?
                    """, first);
            link(connection, otherDriver, passenger, "APPROVED");
            assertThat(number(connection, "SELECT count(*) FROM wa.driver_passenger_link WHERE state='APPROVED'"))
                    .isEqualTo(1);
        }
    }

    @Test
    void shouldIsolateRoutineMembersAndAttendanceByTheirOwnersAndOccurrence() throws Exception {
        try (Connection connection = open(postgres)) {
            Fixture fixture = fixture(connection, "PLANNED");
            UUID otherDriver = identity(connection, "DRIVER");
            UUID otherRoutine = routine(connection, otherDriver);
            assertConstraint("23503", "fk_member_routine_owner", () -> update(connection, """
                    INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order)
                    VALUES (?,?,?,?,?,?,1)
                    """, UUID.randomUUID(), otherRoutine, fixture.driver(), fixture.passenger(),
                    fixture.link(), fixture.pickup()));
            String attendance = """
                    INSERT INTO wa.attendance(id,routine_id,passenger_id,local_date,state,actor_identity_id,answered_at)
                    VALUES (?,?,?,?,'GOING',?,CURRENT_TIMESTAMP)
                    """;
            assertConstraint("23514", "ck_attendance_author", () -> update(connection, attendance,
                    UUID.randomUUID(), fixture.routine(), fixture.passenger(), DATE, otherDriver));
            update(connection, attendance, UUID.randomUUID(), fixture.routine(), fixture.passenger(), DATE,
                    fixture.passenger());
            assertConstraint("23505", "uk_attendance_occurrence_passenger", () -> update(connection, attendance,
                    UUID.randomUUID(), fixture.routine(), fixture.passenger(), DATE, fixture.passenger()));
        }
    }

    @Test
    void shouldReserveStartFailedRunsAndFreezeOccurrenceSnapshotsAndTerminalStates() throws Exception {
        try (Connection connection = open(postgres)) {
            Fixture fixture = fixture(connection, "START_FAILED");
            UUID secondOccurrence = occurrence(connection, fixture.routine(), fixture.driver(), DATE.plusDays(1));
            assertConstraint("23505", "uk_run_active_driver", () -> run(connection, secondOccurrence,
                    fixture.routine(), fixture.driver(), DATE.plusDays(1), "STARTING"));
            assertConstraint("23505", "uk_occurrence_routine_date", () -> occurrence(connection,
                    fixture.routine(), fixture.driver(), DATE));
            assertConstraint("23514", "ck_run_immutable_snapshot", () -> update(connection,
                    "UPDATE wa.operation_run SET policy_version='changed-policy' WHERE id=?", fixture.run()));
            assertConstraint("23514", "ck_stop_immutable_snapshot", () -> update(connection,
                    "UPDATE wa.run_stop SET stop_order=2 WHERE id=?", fixture.stop()));
            update(connection, "UPDATE wa.operation_run SET state='COMPLETED' WHERE id=?", fixture.run());
            run(connection, secondOccurrence, fixture.routine(), fixture.driver(), DATE.plusDays(1), "STARTING");
            assertConstraint("23514", "ck_run_terminal", () -> update(connection,
                    "UPDATE wa.operation_run SET state='TRACKING' WHERE id=?", fixture.run()));
        }
    }

    @Test
    void shouldBindTrackingToRunDeviceAndGenerationAndDeduplicateBatchesAndSequences() throws Exception {
        try (Connection connection = open(postgres)) {
            Fixture fixture = fixture(connection, "TRACKING");
            UUID device = device(connection, fixture.driver(), "A".repeat(43));
            UUID stranger = identity(connection, "DRIVER");
            UUID strangerDevice = device(connection, stranger, "B".repeat(43));
            String sessionInsert = """
                    INSERT INTO wa.tracking_session(id,run_id,driver_id,device_id,generation,lease_expires_at)
                    VALUES (?,?,?,?,1,CURRENT_TIMESTAMP + INTERVAL '5 minutes')
                    """;
            assertConstraint("23503", "fk_tracking_device_owner", () -> update(connection, sessionInsert,
                    UUID.randomUUID(), fixture.run(), fixture.driver(), strangerDevice));
            UUID session = UUID.randomUUID();
            update(connection, sessionInsert, session, fixture.run(), fixture.driver(), device);
            String batchInsert = """
                    INSERT INTO wa.tracking_batch_receipt(session_id,generation,batch_id,request_hash,result_json)
                    VALUES (?,?,?,?, '{}'::jsonb)
                    """;
            assertConstraint("23503", "fk_batch_session_generation", () -> update(connection, batchInsert,
                    session, 2L, UUID.randomUUID(), hash("batch")));
            UUID batch = UUID.randomUUID();
            update(connection, batchInsert, session, 1L, batch, hash("batch"));
            assertConstraint("23505", "tracking_batch_receipt_pkey", () -> update(connection, batchInsert,
                    session, 1L, batch, hash("different-body")));
            String sampleInsert = """
                    INSERT INTO wa.tracking_sample_receipt(id,session_id,generation,batch_id,sequence,captured_at,outcome)
                    VALUES (?,?,1,?,5,CURRENT_TIMESTAMP,'ACCEPTED')
                    """;
            update(connection, sampleInsert, UUID.randomUUID(), session, batch);
            assertConstraint("23505", "uk_tracking_sample_sequence", () -> update(connection,
                    sampleInsert, UUID.randomUUID(), session, batch));
            assertThat(number(connection, """
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema='wa' AND table_name IN ('tracking_sample_receipt','tracking_batch_receipt')
                    AND column_name IN ('latitude','longitude','last_latitude','last_longitude')
                    """)).isZero();
        }
    }

    @Test
    void shouldFreezePairingKeyPkceEnvironmentAndDriverAndRejectProofReplay() throws Exception {
        try (Connection connection = open(postgres)) {
            UUID pairing = UUID.randomUUID();
            update(connection, """
                    INSERT INTO wa.pairing_challenge(id,public_jwk,jkt,pkce_challenge,platform,app_version,
                        environment,code_hash,expires_at)
                    VALUES (?,?::jsonb,?,?,'ANDROID','synthetic-1','test',?,CURRENT_TIMESTAMP + INTERVAL '5 minutes')
                    """, pairing, PUBLIC_JWK, "A".repeat(43), "B".repeat(43), hash("synthetic-code"));
            assertConstraint("23514", "ck_pairing_immutable_binding", () -> update(connection,
                    "UPDATE wa.pairing_challenge SET pkce_challenge=? WHERE id=?", "C".repeat(43), pairing));
            assertConstraint("23514", "ck_pairing_immutable_binding", () -> update(connection,
                    "UPDATE wa.pairing_challenge SET environment='other-test' WHERE id=?", pairing));
            UUID driver = identity(connection, "DRIVER");
            update(connection, """
                    UPDATE wa.pairing_challenge SET state='APPROVED',driver_id=?,approval_generation=1 WHERE id=?
                    """, driver, pairing);
            UUID stranger = identity(connection, "DRIVER");
            assertConstraint("23514", "ck_pairing_immutable_approval", () -> update(connection,
                    "UPDATE wa.pairing_challenge SET driver_id=? WHERE id=?", stranger, pairing));
            UUID strangerDevice = device(connection, stranger, "D".repeat(43));
            assertConstraint("23503", "fk_pairing_device_owner", () -> update(connection,
                    "UPDATE wa.pairing_challenge SET state='REDEEMED',device_id=? WHERE id=?", strangerDevice, pairing));
            UUID pairedDevice = device(connection, driver, "A".repeat(43));
            UUID anotherEnvironment = UUID.randomUUID();
            update(connection, """
                    INSERT INTO wa.pairing_challenge(id,public_jwk,jkt,pkce_challenge,platform,app_version,
                        environment,code_hash,expires_at,state,driver_id,approval_generation)
                    VALUES (?,?::jsonb,?,?,'ANDROID','synthetic-1','other-test',?,
                        CURRENT_TIMESTAMP + INTERVAL '5 minutes','APPROVED',?,1)
                    """, anotherEnvironment, PUBLIC_JWK, "A".repeat(43), "B".repeat(43),
                    hash("synthetic-other-environment-code"), driver);
            assertConstraint("23503", "fk_pairing_device_owner", () -> update(connection,
                    "UPDATE wa.pairing_challenge SET state='REDEEMED',device_id=? WHERE id=?",
                    pairedDevice, anotherEnvironment));
            update(connection, "UPDATE wa.pairing_challenge SET state='REDEEMED',device_id=? WHERE id=?",
                    pairedDevice, pairing);
            assertConstraint("23514", "ck_pairing_terminal", () -> update(connection,
                    "UPDATE wa.pairing_challenge SET state='APPROVED',device_id=NULL WHERE id=?", pairing));
            update(connection, """
                    INSERT INTO wa.proof_replay(jkt,jti,expires_at) VALUES (?, 'synthetic-jti',CURRENT_TIMESTAMP + INTERVAL '1 minute')
                    """, "A".repeat(43));
            assertConstraint("23505", "proof_replay_pkey", () -> update(connection, """
                    INSERT INTO wa.proof_replay(jkt,jti,expires_at) VALUES (?, 'synthetic-jti',CURRENT_TIMESTAMP + INTERVAL '1 minute')
                    """, "A".repeat(43)));
        }
    }

    @Test
    void shouldAllowOneActiveDeviceAndKeepRefreshRotationInsideItsDeviceFamily() throws Exception {
        try (Connection connection = open(postgres)) {
            UUID driver = identity(connection, "DRIVER");
            UUID old = device(connection, driver, "A".repeat(43));
            assertConstraint("23505", "uk_device_active_driver", () -> device(connection, driver, "B".repeat(43)));
            UUID otherDriver = identity(connection, "DRIVER");
            assertConstraint("23505", "uk_device_active_jkt", () -> device(connection, otherDriver, "A".repeat(43)));
            update(connection, "UPDATE wa.driver_device SET status='REVOKED',revoked_at=CURRENT_TIMESTAMP WHERE id=?", old);
            UUID current = device(connection, driver, "B".repeat(43));
            UUID family = UUID.randomUUID();
            UUID token = UUID.randomUUID();
            UUID next = UUID.randomUUID();
            String insert = """
                    INSERT INTO wa.device_token(id,device_id,token_hash,kind,family_id,expires_at)
                    VALUES (?,?,?,'REFRESH',?,CURRENT_TIMESTAMP + INTERVAL '1 day')
                    """;
            update(connection, insert, token, current, hash("refresh-one"), family);
            update(connection, insert, next, current, hash("refresh-two"), family);
            UUID strangerToken = UUID.randomUUID();
            update(connection, insert, strangerToken, old, hash("refresh-old"), UUID.randomUUID());
            assertConstraint("23503", "fk_device_token_rotation_family", () -> update(connection, """
                    UPDATE wa.device_token SET consumed_at=CURRENT_TIMESTAMP,rotated_to=? WHERE id=?
                    """, strangerToken, token));
            update(connection, "UPDATE wa.device_token SET consumed_at=CURRENT_TIMESTAMP,rotated_to=? WHERE id=?", next, token);
            assertThat(number(connection, "SELECT count(*) FROM wa.device_token WHERE consumed_at IS NOT NULL"))
                    .isEqualTo(1);
        }
    }

    @Test
    void shouldDeduplicateNoticePurposesWithoutPolicyVersionAndExpireEphemeralPins() throws Exception {
        try (Connection connection = open(postgres)) {
            Fixture fixture = fixture(connection, "TRACKING");
            outbox(connection, fixture.passenger(), "APPROACH", fixture.run(), fixture.stop(), null, null, null, null, "v1");
            assertConstraint("23505", "uk_outbox_approach", () -> outbox(connection, fixture.passenger(),
                    "APPROACH", fixture.run(), fixture.stop(), null, null, null, null, "v2"));
            outbox(connection, fixture.passenger(), "REMINDER", null, null, fixture.occurrence(), null, null, "morning", "v1");
            assertConstraint("23505", "uk_outbox_reminder", () -> outbox(connection, fixture.passenger(),
                    "REMINDER", null, null, fixture.occurrence(), null, null, "morning", "v2"));
            UUID command = UUID.randomUUID();
            command(connection, command, fixture.passenger(), fixture.event(), "location-question-one");
            outbox(connection, fixture.passenger(), "RESPONSE", null, null, null, command, "answer", null, "v1");
            assertConstraint("23505", "uk_outbox_response", () -> outbox(connection, fixture.passenger(),
                    "PIN", null, null, null, command, "answer", null, "v2"));
            UUID anotherCommand = UUID.randomUUID();
            command(connection, anotherCommand, fixture.passenger(), inbox(connection, fixture.passenger()), "location-question-two");
            UUID pin = UUID.randomUUID();
            update(connection, """
                    INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,command_id,response_part,policy_version,
                        payload_kind,payload_json,position_captured_at,expires_at)
                    VALUES (?,?,'PIN',?,'answer','v1','PIN','{"latitude":0,"longitude":0}'::jsonb,
                        CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '10 seconds')
                    """, pin, fixture.passenger(), anotherCommand);
            assertConstraint("23514", "ck_outbox_pin_deadline", () -> update(connection,
                    "UPDATE wa.message_outbox SET expires_at=position_captured_at + INTERVAL '21 seconds' WHERE id=?", pin));
            assertConstraint("23514", "ck_outbox_pin_purge", () -> update(connection,
                    "UPDATE wa.message_outbox SET state='SUBMITTED' WHERE id=?", pin));
            update(connection, "UPDATE wa.message_outbox SET state='SUBMITTED',payload_json='{}'::jsonb WHERE id=?", pin);
            assertThat(number(connection, "SELECT count(*) FROM wa.message_outbox WHERE purpose='PIN'"))
                    .isEqualTo(1);
        }
    }
}
