package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** An explicit current inbox message changes one preference of its own passenger. */
final class WaPreferenceConversation {
    private record Choice(String preference, boolean enabled) { }
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    WaPreferenceConversation(JdbcTemplate jdbc, WaClock clock) { this.jdbc = jdbc; this.clock = clock; }

    boolean recognizes(WaInboxAccess.Context event) { return choice(event) != null; }
    boolean eligible(WaProfileState actor) {
        return actor.role().equals("PASSENGER") && actor.status().equals("ACTIVE")
            && WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion())
            && WaConversationOwnership.owner(jdbc, actor.identityId()).equals("READY");
    }
    WaDecision execute(WaProfileState actor, WaInboxAccess.Context event, ObjectNode payload) {
        Choice choice = choice(event);
        if (!eligible(actor) || choice == null || !choice.preference().equals(payload.path("preference").asText())
                || choice.enabled() != payload.path("enabled").booleanValue())
            return WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
        int changed = jdbc.update("""
            UPDATE wa.profile SET preferences_json=preferences_json||jsonb_build_object(?,?::boolean),
              version=version+1,updated_at=? WHERE identity_id=?
              AND COALESCE(preferences_json->?,'false'::jsonb)<>to_jsonb(?::boolean)
            """, choice.preference(), choice.enabled(), Timestamp.from(clock.now()), actor.identityId(), choice.preference(), choice.enabled());
        return WaDecision.ok(Map.of("changed", changed == 1));
    }
    String response(ObjectNode payload) {
        String name = payload.path("preference").asText().equals("APPROACH_MESSAGES") ? "de chegada" : "de presença";
        return "Avisos " + name + (payload.path("enabled").booleanValue() ? " ligados." : " desligados.")
            + " A outra preferência de avisos foi mantida. Mensagens já enviadas podem chegar depois.";
    }
    private static Choice choice(WaInboxAccess.Context event) {
        if (!event.normalizedKind().equals("TEXT") || !event.normalizedPayload().path("text").isTextual()) return null;
        return switch (WaConversationPolicy.normalize(event.normalizedPayload().path("text").textValue())) {
            case "ligar avisos chegada" -> new Choice("APPROACH_MESSAGES", true);
            case "desligar avisos chegada" -> new Choice("APPROACH_MESSAGES", false);
            case "ligar avisos presenca" -> new Choice("ATTENDANCE_REMINDERS", true);
            case "desligar avisos presenca" -> new Choice("ATTENDANCE_REMINDERS", false);
            default -> null;
        };
    }
}
