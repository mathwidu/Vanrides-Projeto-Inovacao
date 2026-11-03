package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Drafts can be suspended, while short answers belong to one active conversation. */
final class WaConversationOwnership {
    private WaConversationOwnership() { }

    static String owner(JdbcTemplate jdbc, UUID actor) {
        var rows = jdbc.query("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",
            (rs, row) -> WaJson.object(rs.getString(1)), actor);
        return rows.isEmpty() ? "READY" : owner(rows.get(0));
    }

    static String owner(ObjectNode context) {
        String explicit = context.path("conversationOwner").asText();
        if (explicit.equals("RUN_SETUP") && !context.path("runDraft").isEmpty()) return explicit;
        if (explicit.equals("ATTENDANCE") && !context.path("attendanceDraft").isEmpty()) return explicit;
        if (explicit.equals("PASSENGER_SETUP") && !context.path("passengerDraft").isEmpty()) return explicit;
        if (explicit.equals("DRIVER_SETUP") && !context.path("driverDraft").isEmpty()) return explicit;
        if (!context.path("attendanceDraft").isEmpty()) return "ATTENDANCE";
        if (!context.path("passengerDraft").isEmpty()) return "PASSENGER_SETUP";
        if (!context.path("driverDraft").isEmpty()) return "DRIVER_SETUP";
        if (!context.path("runDraft").isEmpty()) return "RUN_SETUP";
        return "READY";
    }

    static boolean currentDraftReply(JdbcTemplate jdbc, WaInboxAccess.Context event) {
        if (!event.normalizedPayload().hasNonNull("replyTo")) return true;
        String owner = owner(jdbc, event.identityId());
        String draftKey = owner.equals("RUN_SETUP") ? "runDraft" : owner.equals("DRIVER_SETUP") ? "driverDraft" : "passengerDraft";
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.conversation_state conversation
              JOIN wa.command_receipt receipt ON receipt.identity_id=conversation.identity_id
              JOIN wa.message_outbox outbox ON outbox.command_id=receipt.command_id
                AND outbox.recipient_identity_id=receipt.identity_id AND outbox.purpose='RESPONSE'
              JOIN wa.message_attempt attempt ON attempt.outbox_id=outbox.id
                AND attempt.state IN ('SUBMITTED','DELIVERED','READ') AND attempt.credential_id IS NOT NULL
              JOIN wa.integration_inbox current ON current.id=? AND current.identity_id=conversation.identity_id
              JOIN wa.service_credential current_credential ON current_credential.id=current.lease_credential_id
              JOIN wa.service_credential submitted_credential ON submitted_credential.id=attempt.credential_id
                AND submitted_credential.environment=current_credential.environment
              WHERE conversation.identity_id=? AND attempt.instance_id=current.instance_id
                AND (receipt.inbox_event_id::text=COALESCE(conversation.context_json->'ownerPromptEvents'->>?,conversation.last_event_id::text)
                  OR outbox.id::text=conversation.context_json->?->'confirmation'->>'outboxId')
                AND attempt.external_message_id=?)
            """, Boolean.class, event.eventId(), event.identityId(), owner, draftKey, event.normalizedPayload().path("replyTo").asText()));
    }

    static void prompt(ObjectNode context, String owner, UUID sourceEvent) {
        ObjectNode prompts = context.path("ownerPromptEvents") instanceof ObjectNode value ? value : WaJson.MAPPER.createObjectNode();
        prompts.put(owner, sourceEvent.toString());
        context.set("ownerPromptEvents", prompts);
    }
}
