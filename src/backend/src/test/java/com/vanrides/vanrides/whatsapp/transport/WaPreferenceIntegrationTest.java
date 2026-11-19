package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaConversationTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class WaPreferenceIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_preferences")
        .withTmpFs(Map.of("/var/lib/postgresql/data", "rw,size=128m"));
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    private WaConversationTestSupport c;
    @BeforeEach void reset() { c = new WaConversationTestSupport(DATABASE.fixture()); }

    @Test void anAppliedPreferenceReplyCanBeClaimedAndAuthorizedOnlyForItsOwnPassenger() {
        UUID actor = passenger(), other = passenger();
        String recipient = c.string("SELECT alias_value FROM wa.identity_alias WHERE identity_id=?", actor);
        c.h.jdbc.update("UPDATE wa.profile SET preferences_json='{\"APPROACH_MESSAGES\":true}' WHERE identity_id=?", actor);
        var event = c.text(actor, "DESLIGAR AVISOS CHEGADA");
        var result = c.execute(event, "SET_PREFERENCE", preference("APPROACH_MESSAGES", false)).requireSuccess();
        assertThat(result.path("outcome").asText()).isEqualTo("APPLIED");
        assertThat(result.path("responseIds")).hasSize(1);
        c.finish(event);

        var items = c.outboxClaim().path("items");
        assertThat(items).hasSize(1);
        var item = (ObjectNode) items.get(0);
        UUID box = UUID.fromString(item.path("outboxId").asText());
        assertThat(box.toString()).isEqualTo(result.path("responseIds").get(0).asText());
        c.h.jdbc.update("UPDATE wa.message_outbox SET recipient_identity_id=? WHERE id=?", other, box);
        assertThatThrownBy(() -> c.authorize(item)).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE));
        c.h.jdbc.update("UPDATE wa.message_outbox SET recipient_identity_id=? WHERE id=?", actor, box);
        var authorized = c.authorize(item);
        assertThat(authorized.path("recipient").path("address").asText()).isEqualTo(recipient);
        assertThat(authorized.path("content").path("text").asText()).startsWith("Avisos de chegada desligados.");
    }

    @Test void anActivePassengerChangesOnlyTheChosenPreferenceAndRepeatingTheValueDoesNotChangeTheVersion() {
        UUID actor = passenger();
        var event = c.text(actor, "LIGAR AVISOS CHEGADA");
        assertThat(c.commands.context(c.worker, event.id(), event.token()).path("allowedCommands").toString())
            .isEqualTo("[\"RESPOND_TO_MESSAGE\",\"SET_PREFERENCE\"]");
        assertThat(c.commands.context(c.worker, event.id(), event.token()).path("options")).isEmpty();
        ObjectNode body = c.body(event, "SET_PREFERENCE", preference("APPROACH_MESSAGES", true));
        ObjectNode result = c.execute(event, body, "synthetic-preference-first").requireSuccess();
        assertThat(result.path("outcome").asText()).isEqualTo("APPLIED");
        assertThat(result.path("entityVersion").asLong()).isEqualTo(2);
        assertThat(c.execute(event, body, "synthetic-preference-replay").requireSuccess()).isEqualTo(result);
        assertThat(c.number("SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id=?", event.id())).isEqualTo(1);
        c.finish(event);
        var repeat = c.text(actor, "LIGAR AVISOS CHEGADA");
        ObjectNode unchanged = c.execute(repeat, "SET_PREFERENCE", preference("APPROACH_MESSAGES", true)).requireSuccess();
        assertThat(unchanged.path("outcome").asText()).isEqualTo("NO_CHANGE");
        assertThat(unchanged.path("entityVersion").asLong()).isEqualTo(2);
        assertThat(c.string("SELECT preferences_json::text FROM wa.profile WHERE identity_id=?", actor))
            .contains("\"APPROACH_MESSAGES\": true").doesNotContain("ATTENDANCE_REMINDERS");
        assertThat(c.string("SELECT display_name FROM wa.profile WHERE identity_id=?", actor)).isEqualTo("Pessoa sintética");
    }

    @Test void twoExplicitChoicesPreserveOldKeysAndMissingFalseRemainsTheDefault() {
        UUID actor = passenger();
        assertThat(send(actor, "DESLIGAR AVISOS CHEGADA", "APPROACH_MESSAGES", false).path("outcome").asText()).isEqualTo("NO_CHANGE");
        assertThat(c.string("SELECT preferences_json::text FROM wa.profile WHERE identity_id=?", actor)).isEqualTo("{}");
        c.h.jdbc.update("UPDATE wa.profile SET preferences_json='{\"ATTENDANCE_REMINDERS\":false}' WHERE identity_id=?", actor);
        send(actor, "LIGAR AVISOS CHEGADA", "APPROACH_MESSAGES", true);
        assertThat(c.string("SELECT preferences_json::text FROM wa.profile WHERE identity_id=?", actor))
            .contains("\"APPROACH_MESSAGES\": true", "\"ATTENDANCE_REMINDERS\": false");
        send(actor, "\uFEFFDESLIGAR\u00A0AVISOS CHEGADA!", "APPROACH_MESSAGES", false);
        assertThat(c.string("SELECT preferences_json::text FROM wa.profile WHERE identity_id=?", actor))
            .contains("\"APPROACH_MESSAGES\": false", "\"ATTENDANCE_REMINDERS\": false");
    }

    @Test void presenceReminderTextsAreNotAPreferenceCommandAndNeverChangeTheProfile() {
        UUID actor = passenger();
        for (String text : new String[] {"LIGAR AVISOS PRESENÇA", "DESLIGAR AVISOS PRESENÇA"}) {
            var event = c.text(actor, text);
            assertThat(c.commands.context(c.worker, event.id(), event.token()).path("allowedCommands").toString())
                .doesNotContain("SET_PREFERENCE");
            assertThat(c.execute(event, "SET_PREFERENCE", preference("APPROACH_MESSAGES", true)).error())
                .isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
            c.finish(event);
        }
        assertThat(c.string("SELECT preferences_json::text FROM wa.profile WHERE identity_id=?", actor)).isEqualTo("{}");
        assertThat(c.number("SELECT version FROM wa.profile WHERE identity_id=?", actor)).isEqualTo(1);
    }

    @Test void thePresenceReminderPreferenceIsNoLongerAcceptedAsAPayload() {
        UUID actor = passenger();
        var event = c.text(actor, "LIGAR AVISOS CHEGADA");
        assertThatThrownBy(() -> c.execute(event, "SET_PREFERENCE", preference("ATTENDANCE_REMINDERS", true)))
            .isInstanceOfSatisfying(WaProblem.class, error -> assertThat(error.code()).isEqualTo(WaProblem.Code.INVALID_PAYLOAD));
        assertThat(c.string("SELECT preferences_json::text FROM wa.profile WHERE identity_id=?", actor)).isEqualTo("{}");
    }

    @Test void theAuthenticatedMessageCannotBeInverted() {
        UUID actor = passenger();
        var forged = preference("APPROACH_MESSAGES", false);
        var event = c.text(actor, "LIGAR AVISOS CHEGADA");
        assertThat(c.execute(event, "SET_PREFERENCE", forged).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        c.finish(event);
        var stale = c.textAt(actor, "LIGAR AVISOS CHEGADA", c.h.clock.now().minusSeconds(301));
        assertThat(c.execute(stale, "SET_PREFERENCE", preference("APPROACH_MESSAGES", true)).error()).isEqualTo(WaProblem.Code.INTENT_EXPIRED);
        assertThat(c.string("SELECT preferences_json::text FROM wa.profile WHERE identity_id=?", actor)).isEqualTo("{}");
        assertThat(c.number("SELECT version FROM wa.profile WHERE identity_id=?", actor)).isEqualTo(1);
    }

    @Test void draftsAndPausedSupportAreNeverAdvancedOrResumedByAPreferenceRequest() {
        UUID actor = passenger();
        c.h.jdbc.update("""
            INSERT INTO wa.conversation_state(identity_id,stage,context_json,version)
            VALUES (?,'PASSENGER_SETUP','{"conversationOwner":"PASSENGER_SETUP","passengerDraft":{"stage":"POINT","sentinel":"synthetic"}}'::jsonb,1)
            """, actor);
        String draft = c.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?", actor);
        var blocked = c.text(actor, "LIGAR AVISOS CHEGADA");
        assertThat(c.commands.context(c.worker, blocked.id(), blocked.token()).path("allowedCommands").toString())
            .isEqualTo("[\"RESPOND_TO_MESSAGE\"]");
        assertThat(c.execute(blocked, "SET_PREFERENCE", preference("APPROACH_MESSAGES", true)).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(c.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?", actor)).isEqualTo(draft);
        c.finish(blocked);
        var support = c.text(actor, "FALAR COM SUPORTE");
        c.execute(support, "REQUEST_SUPPORT", json().put("reason", "HELP").put("detail", "Pedido sintético de suporte.")).requireSuccess();
        c.finish(support);
        String paused = c.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?", actor);
        var refused = c.text(actor, "DESLIGAR AVISOS CHEGADA");
        assertThat(c.execute(refused, "SET_PREFERENCE", preference("APPROACH_MESSAGES", false)).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(c.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?", actor)).isEqualTo(paused);
        assertThat(c.string("SELECT stage FROM wa.conversation_state WHERE identity_id=?", actor)).isEqualTo("PAUSED");
        assertThat(c.number("SELECT count(*) FROM wa.support_case WHERE identity_id=? AND state='OPEN'", actor)).isEqualTo(1);
    }

    @Test void driverAndMissingNoticeCannotAcquireThePassengerPreferenceCapability() {
        UUID actor = passenger();
        c.h.jdbc.update("UPDATE wa.profile SET role='DRIVER' WHERE identity_id=?", actor);
        for (boolean missingNotice : new boolean[] {false, true}) {
            if (missingNotice) c.h.jdbc.update("UPDATE wa.profile SET role='PASSENGER',notice_version=NULL,notice_accepted_at=NULL WHERE identity_id=?", actor);
            var event = c.text(actor, "LIGAR AVISOS CHEGADA");
            assertThat(c.commands.context(c.worker, event.id(), event.token()).path("allowedCommands").toString())
                .isEqualTo("[\"RESPOND_TO_MESSAGE\"]");
            assertThat(c.execute(event, "SET_PREFERENCE", preference("APPROACH_MESSAGES", true)).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
            c.finish(event);
        }
        assertThat(c.string("SELECT preferences_json::text FROM wa.profile WHERE identity_id=?", actor)).isEqualTo("{}");
    }

    private ObjectNode send(UUID actor, String text, String preference, boolean enabled) {
        var event = c.text(actor, text);
        ObjectNode result = c.execute(event, "SET_PREFERENCE", preference(preference, enabled)).requireSuccess();
        c.finish(event); return result;
    }
    private UUID passenger() {
        UUID actor = c.actor();
        c.h.jdbc.update("""
            INSERT INTO wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at,version)
            VALUES (?,'PASSENGER','ACTIVE','Pessoa sintética','wa-mvp-v1',?,1)
            """, actor, Timestamp.from(c.h.clock.now()));
        return actor;
    }
    private static ObjectNode preference(String preference, boolean enabled) {
        return json().put("preference", preference).put("enabled", enabled);
    }
}
