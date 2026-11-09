package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.commands.WaConversationResponses;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.vanrides.vanrides.whatsapp.transport.WaConversationTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class WaConversationCommandsIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_conversation_commands");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    private WaConversationTestSupport c;
    @BeforeEach void reset() { c = new WaConversationTestSupport(DATABASE.fixture()); }

    @Test void helpIsAtomicAndReplayableWithoutCreatingAProfileOrLeakingInternalFields() {
        UUID actor = c.actor(); Lease event = c.text(actor, "AJUDA");
        ObjectNode context = c.commands.context(c.worker, event.id(), event.token());
        assertThat(context.path("registrationStep").asText()).isEqualTo("WELCOME");
        assertThat(context.path("eventFreshness").asText()).isEqualTo("CURRENT");
        assertThat(context.toString()).doesNotContain("Pessoa", "@", "Hash", "senderAliasId", "latitude", "longitude");
        ObjectNode body = c.body(event, "RESPOND_TO_MESSAGE", reply("HELP"));
        ObjectNode first = c.execute(event, body, "synthetic-help-001").requireSuccess();
        assertThat(first.path("outcome").asText()).isEqualTo("NO_CHANGE");
        assertThat(c.execute(event, body, "synthetic-help-other-key").requireSuccess()).isEqualTo(first);
        assertThat(c.number("SELECT count(*) FROM wa.profile")).isZero();
        assertThat(c.number("SELECT count(*) FROM wa.command_receipt")).isEqualTo(1);
        assertThat(c.number("SELECT count(*) FROM wa.message_outbox WHERE policy_version='conversation-v1' AND response_part='conversation'")).isEqualTo(1);
        assertThat(c.number("SELECT count(*) FROM wa.support_case")).isZero();
    }

    @Test void unicodeWhitespaceSupportIsAdvertisedByRealContextAndCreatesItsCaseReplyAndPause() {
        UUID actor = c.actor(); Lease event = c.text(actor, "\uFEFFFALAR\u00A0COM\u202FSUPORTE!\u3000");
        ObjectNode context = c.commands.context(c.worker, event.id(), event.token());
        assertThat(context.path("eventFreshness").asText()).isEqualTo("CURRENT");
        assertThat(context.path("allowedCommands").toString()).contains("\"REQUEST_SUPPORT\"");
        ObjectNode result = c.execute(event, "REQUEST_SUPPORT", json().put("reason", "HELP")
            .put("detail", "Pedido explícito sintético com separadores Unicode.")).requireSuccess();
        assertThat(result.path("outcome").asText()).isEqualTo("APPLIED");
        assertThat(c.number("SELECT count(*) FROM wa.support_case WHERE identity_id=? AND state='OPEN'", actor)).isEqualTo(1);
        assertThat(c.number("SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=?", actor)).isEqualTo(1);
        assertThat(c.number("SELECT count(*) FROM wa.profile WHERE identity_id=?", actor)).isZero();
        assertThat(c.commands.context(c.worker, event.id(), event.token()).path("registrationStep").asText()).isEqualTo("PAUSED");
        c.finish(event);
        assertThat(c.string("SELECT state FROM wa.integration_inbox WHERE id=?", event.id())).isEqualTo("COMPLETED");
    }

    @Test void unicodeWhitespaceRegistrationKeepsTheNameAndAdvertisesExplicitConsentAfterTheNotice() {
        UUID actor = c.actor(); Lease first = c.text(actor, "\uFEFFOlá!\u00A0");
        c.execute(first, "START_ONBOARDING", json()).requireSuccess(); c.finish(first);
        Lease name = c.text(actor, "\u00A0Me\u00A0chamo Pessoa Sintética\u00A0");
        assertThat(c.commands.context(c.worker, name.id(), name.token()).path("registrationStep").asText()).isEqualTo("ASK_NAME");
        c.execute(name, "SET_PROFILE_NAME", json().put("name", "Pessoa Sintética")).requireSuccess(); c.finish(name);
        Lease accepted = c.text(actor, "\u00A0ACEITO!\uFEFF");
        ObjectNode context = c.commands.context(c.worker, accepted.id(), accepted.token());
        assertThat(context.path("registrationStep").asText()).isEqualTo("ASK_NOTICE");
        assertThat(context.path("allowedCommands").toString()).contains("\"ACCEPT_NOTICE\"");
        c.execute(accepted, "ACCEPT_NOTICE", json().put("noticeVersion", "wa-mvp-v1")).requireSuccess();
        assertThat(c.string("SELECT display_name FROM wa.profile WHERE identity_id=?", actor)).isEqualTo("Pessoa Sintética");
        assertThat(c.string("SELECT status FROM wa.profile WHERE identity_id=?", actor)).isEqualTo("ACTIVE");
        assertThat(c.number("SELECT count(*) FROM wa.command_receipt WHERE identity_id=?", actor)).isEqualTo(3);
        assertThat(c.number("SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=?", actor)).isEqualTo(3);
    }

    @Test void unsupportedMediaGetsAFixedReplyWhileItsUnknownTimestampCannotAuthorizeBusiness() {
        UUID actor = c.actor(); ObjectNode message = json(); message.putObject("liveLocationMessage").put("degreesLatitude", 0).put("degreesLongitude", 0);
        Lease event = c.claim(c.pending(actor, message, c.h.clock.now()));
        assertThat(event.message().path("type").asText()).isEqualTo("UNSUPPORTED");
        assertThat(c.commands.context(c.worker, event.id(), event.token()).path("eventFreshness").asText()).isEqualTo("UNKNOWN");
        c.execute(event, "RESPOND_TO_MESSAGE", reply("STATIC_PIN_REQUIRED")).requireSuccess();
        assertThat(c.number("SELECT count(*) FROM wa.profile")).isZero();
        assertThat(c.string("SELECT payload_json::text FROM wa.message_outbox")).doesNotContain("latitude", "longitude", "@", "senderAliasId");
        c.finish(event);
        Lease missing = c.textAt(actor, "Cadastrar", null);
        assertThat(c.execute(missing, "START_ONBOARDING", json()).error()).isEqualTo(WaProblem.Code.INTENT_EXPIRED);
        assertThat(c.number("SELECT count(*) FROM wa.profile")).isZero();
    }

    @Test void nameEmitsTheApprovedNoticeAndOnlyExplicitCurrentConsentActivatesTheProfile() {
        UUID actor = c.actor(); c.registeredName(actor);
        assertThat(c.string("SELECT context_json->>'noticeVersion' FROM wa.conversation_state WHERE identity_id=?", actor)).isEqualTo("wa-mvp-v1");
        assertThat(c.number("SELECT count(*) FROM wa.conversation_state WHERE identity_id=? AND stage='ASK_NOTICE' AND question_ref IS NOT NULL AND question_expires_at>?",
            actor, Timestamp.from(c.h.clock.now()))).isEqualTo(1);
        assertThat(c.string("SELECT payload_json->>'text' FROM wa.message_outbox WHERE payload_json->>'text' LIKE '%Neste teste do VanRides%'"))
            .contains(WaConversationResponses.NOTICE);
        assertThat(c.number("SELECT count(*) FROM wa.message_outbox WHERE payload_json->>'text' LIKE '%Neste teste do VanRides%' ")).isEqualTo(1);
        Lease accepted = c.text(actor, " áceÍto! ");
        ObjectNode result = c.execute(accepted, "ACCEPT_NOTICE", json().put("noticeVersion", "wa-mvp-v1")).requireSuccess();
        assertThat(result.path("entityVersion").asLong()).isEqualTo(3);
        assertThat(c.string("SELECT status FROM wa.profile WHERE identity_id=?", actor)).isEqualTo("ACTIVE");
        assertThat(c.commands.context(c.worker, accepted.id(), accepted.token()).path("registrationStep").asText()).isEqualTo("READY");
    }

    @Test void aWorkerCannotTurnNegationOrMissingNoticeIntoConsent() {
        UUID actor = c.actor(); c.registeredName(actor);
        Lease negative = c.text(actor, "NÃO ACEITO");
        assertThat(c.execute(negative, "ACCEPT_NOTICE", json().put("noticeVersion", "wa-mvp-v1")).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(c.number("SELECT count(*) FROM wa.profile WHERE identity_id=? AND notice_accepted_at IS NOT NULL", actor)).isZero();
        c.finish(negative);
        c.h.jdbc.update("DELETE FROM wa.conversation_state WHERE identity_id=?", actor);
        Lease missing = c.text(actor, "ACEITO");
        assertThat(c.execute(missing, "ACCEPT_NOTICE", json().put("noticeVersion", "wa-mvp-v1")).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(c.number("SELECT count(*) FROM wa.profile WHERE status='ACTIVE'")).isZero();
    }

    @Test void consentOlderThanARenewedNoticeIsRejectedButSameSecondPrecisionRemainsUsable() {
        UUID actor = c.actor(); c.registeredName(actor); var old = c.h.clock.now();
        c.h.time.advance(10);
        Lease help = c.text(actor, "AJUDA"); c.execute(help, "RESPOND_TO_MESSAGE", reply("HELP")).requireSuccess(); c.finish(help);
        Lease previous = c.textAt(actor, "ACEITO", old);
        assertThat(c.execute(previous, "ACCEPT_NOTICE", json().put("noticeVersion", "wa-mvp-v1")).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        c.finish(previous);
        Lease current = c.textAt(actor, "ACEITO", c.h.clock.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        c.execute(current, "ACCEPT_NOTICE", json().put("noticeVersion", "wa-mvp-v1")).requireSuccess();
        assertThat(c.string("SELECT status FROM wa.profile WHERE identity_id=?", actor)).isEqualTo("ACTIVE");
    }

    @Test void expiredNoticeRequiresANewIssuedPromptAndNeverSilentlyChangesAcceptedVersion() {
        UUID actor = c.actor(); c.registeredName(actor); c.h.time.advance(301);
        Lease expired = c.text(actor, "ACEITO");
        assertThat(c.execute(expired, "ACCEPT_NOTICE", json().put("noticeVersion", "wa-mvp-v1")).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        c.finish(expired);
        Lease repeat = c.text(actor, "Continuar"); c.execute(repeat, "RESPOND_TO_MESSAGE", reply("CONTINUE")).requireSuccess(); c.finish(repeat);
        Lease fresh = c.text(actor, "ACEITO"); c.execute(fresh, "ACCEPT_NOTICE", json().put("noticeVersion", "wa-mvp-v1")).requireSuccess(); c.finish(fresh);
        Lease help = c.text(actor, "AJUDA"); c.execute(help, "RESPOND_TO_MESSAGE", reply("HELP")).requireSuccess();
        assertThat(c.string("SELECT notice_version FROM wa.profile WHERE identity_id=?", actor)).isEqualTo("wa-mvp-v1");
        assertThat(c.number("SELECT count(*) FROM wa.conversation_state WHERE identity_id=? AND question_ref IS NOT NULL", actor)).isZero();
    }

    @Test void anInboxInviteHashCanBeConsumedWithoutRevealingTheCodeAndIsBoundToItsActor() {
        UUID owner = c.actor(), stranger = c.actor(); String invite = c.invite(owner);
        Lease foreign = c.text(stranger, invite);
        assertThat(foreign.message().path("type").asText()).isEqualTo("UNSUPPORTED");
        assertThat(c.execute(foreign, "CLAIM_DRIVER_INVITE", json()).error()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL); c.finish(foreign);
        Lease own = c.text(owner, invite);
        assertThat(c.stored(own.id()).toString()).doesNotContain(invite);
        c.execute(own, "CLAIM_DRIVER_INVITE", json()).requireSuccess();
        assertThat(c.string("SELECT role FROM wa.profile WHERE identity_id=?", owner)).isEqualTo("DRIVER");
        assertThat(c.number("SELECT count(*) FROM wa.profile WHERE identity_id=?", stranger)).isZero();
        assertThat(c.number("SELECT count(*) FROM wa.driver_bootstrap_invite WHERE consumed_at IS NOT NULL")).isEqualTo(1);
    }

    @Test void rawCodeCompatibilityStillRequiresTheSameHashAndInvalidHashNeverPromotes() {
        UUID actor = c.actor(); String invite = c.invite(actor);
        Lease wrong = c.text(actor, invite);
        assertThat(c.execute(wrong, "CLAIM_DRIVER_INVITE", json().put("inviteCode", com.vanrides.vanrides.whatsapp.auth.WaCrypto.randomToken())).error())
            .isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED); c.finish(wrong);
        Lease malformed = c.text(actor, invite);
        c.h.jdbc.update("UPDATE wa.integration_inbox SET payload_json=jsonb_set(payload_json,'{driverInviteCodeHash}','\"invalid\"') WHERE id=?", malformed.id());
        assertThat(c.execute(malformed, "CLAIM_DRIVER_INVITE", json()).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED); c.finish(malformed);
        Lease valid = c.text(actor, invite); c.execute(valid, "CLAIM_DRIVER_INVITE", json().put("inviteCode", invite)).requireSuccess();
        assertThat(c.number("SELECT count(*) FROM wa.driver_authorization")).isEqualTo(1);
    }

    @Test void pairingUsesOnlyTheInboxHashAndAnApprovedChallengeCannotMoveToAnotherDriver() throws Exception {
        UUID owner = c.actor(), stranger = c.actor(); c.driver(owner); c.driver(stranger);
        ObjectNode begin = c.beginPairing(); String code = begin.path("userCode").asText();
        Lease own = c.text(owner, code); c.execute(own, "CONFIRM_PAIRING", json()).requireSuccess(); c.finish(own);
        Lease foreign = c.text(stranger, code);
        assertThat(c.execute(foreign, "CONFIRM_PAIRING", json()).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(c.h.jdbc.queryForObject("SELECT driver_id FROM wa.pairing_challenge WHERE id=?", UUID.class, WaJson.uuid(begin, "pairingId"))).isEqualTo(owner);
        assertThat(c.stored(own.id()).toString()).doesNotContain(code);
    }

    @Test void controlledRejectionGetsExactlyOneAtomicReplyAndDoesNotPermitAnotherCommandOnTheSameInbox() {
        UUID actor = c.actor(); Lease event = c.text(actor, com.vanrides.vanrides.whatsapp.auth.WaCrypto.randomToken());
        ObjectNode body = c.body(event, "CLAIM_DRIVER_INVITE", json());
        assertThat(c.execute(event, body, "synthetic-invalid-invite").error()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(c.execute(event, body, "synthetic-invalid-invite-replay").error()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(c.number("SELECT count(*) FROM wa.command_receipt")).isEqualTo(1);
        assertThat(c.number("SELECT count(*) FROM wa.message_outbox")).isEqualTo(1);
        assertThat(c.number("SELECT sum(attempts) FROM wa.actor_attempt_window")).isEqualTo(1);
        assertThatThrownBy(() -> c.execute(event, "RESPOND_TO_MESSAGE", reply("DRIVER_INVITE_REQUIRED"))).isInstanceOf(WaProblem.class);
    }

    @Test void templatesAreClosedAndFutureOperationsStillFailWithoutReceipt() {
        UUID actor = c.actor(); Lease event = c.text(actor, "Uma dúvida");
        assertThatThrownBy(() -> c.execute(event, "RESPOND_TO_MESSAGE", reply("arbitrary"))).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.INVALID_PAYLOAD));
        assertThatThrownBy(() -> c.execute(event, "RESPOND_TO_MESSAGE", reply("CLARIFY").put("text", "forged"))).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.INVALID_PAYLOAD));
        assertThatThrownBy(() -> c.execute(event, "RESPOND_TO_MESSAGE", reply("RUN_STARTED"))).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.INVALID_PAYLOAD));
        assertThatThrownBy(() -> c.execute(event, "FINISH_RUN", json())).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.DEPENDENCY_UNAVAILABLE));
        assertThatThrownBy(() -> c.execute(event, "REQUEST_START", json())).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.INVALID_PAYLOAD));
        assertThat(c.number("SELECT count(*) FROM wa.command_receipt")).isZero();
        assertThat(c.number("SELECT count(*) FROM wa.message_outbox")).isZero();
        assertThat(c.number("SELECT count(*) FROM wa.start_intent")).isZero();
        assertThat(c.number("SELECT count(*) FROM wa.operation_run")).isZero();
        assertThat(c.number("SELECT count(*) FROM wa.tracking_session")).isZero();
    }

    @Test void startWithoutReviewedReferencesOrPairedDeviceOnlyRecordsAControlledRejection() {
        UUID actor = c.actor(); c.driver(actor);
        Lease event = c.text(actor, "INICIAR SAÍDA");
        ObjectNode context = c.commands.context(c.worker, event.id(), event.token());
        assertThat(context.path("allowedCommands").toString()).doesNotContain("\"REQUEST_START\"", "\"REQUEST_RESUME\"");
        assertThat(c.number("SELECT count(*) FROM wa.driver_device WHERE driver_id=?", actor)).isZero();
        ObjectNode payload = json().put("occurrenceRef", "run_occurrence_unreviewed")
            .put("confirmationRef", "run_confirmation_unreviewed");
        var result = c.execute(event, "REQUEST_START", payload);
        assertThat(result.error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(c.number("SELECT count(*) FROM wa.start_intent")).isZero();
        assertThat(c.number("SELECT count(*) FROM wa.operation_run")).isZero();
        assertThat(c.number("SELECT count(*) FROM wa.tracking_session")).isZero();
        // A well-formed but unauthorized command gets one fixed denial, not a start authorization.
        assertThat(c.number("SELECT count(*) FROM wa.command_receipt WHERE identity_id=? AND command_type='REQUEST_START'", actor)).isEqualTo(1);
        assertThat(c.number("SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=? AND policy_version='run-start-v1'", actor)).isEqualTo(1);
        assertThat(c.number("SELECT count(*) FROM wa.message_outbox WHERE start_payload_ciphertext IS NOT NULL")).isZero();
    }
}
