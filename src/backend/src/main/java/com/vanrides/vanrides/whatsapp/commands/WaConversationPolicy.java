package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/** Conversation state never owns a lease. Only the authenticated inbox authorizes a human effect. */
final class WaConversationPolicy {
    static final Set<String> RESPONSES = Set.of("HELP", "CLARIFY", "UNSUPPORTED_CONTENT", "STATIC_PIN_REQUIRED",
        "FEATURE_PENDING", "STALE_MESSAGE", "CONTINUE", "DRIVER_INVITE_REQUIRED");
    private static final Set<String> HELP = Set.of("ajuda", "help", "menu", "o que voce faz");
    private static final Set<String> SUPPORT = Set.of("falar com suporte", "quero falar com suporte", "falar com atendente",
        "quero atendimento humano", "atendimento humano");
    // ECMAScript WhiteSpace + LineTerminator: Java's Unicode \\s additionally accepts NEL and excludes BOM.
    private static final Pattern JS_WHITESPACE = Pattern.compile("[\\x{0009}-\\x{000D}\\x{0020}\\x{00A0}\\x{1680}"
        + "\\x{2000}-\\x{200A}\\x{2028}\\x{2029}\\x{202F}\\x{205F}\\x{3000}\\x{FEFF}]+");
    private static final Locale TEXT_LOCALE = Locale.forLanguageTag("pt-BR");
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final String environment;
    WaConversationPolicy(JdbcTemplate jdbc, WaClock clock, String environment) { this.jdbc = jdbc; this.clock = clock; this.environment = environment; }

    String freshness(WaInboxAccess.Context event) {
        Instant occurred = occurred(event);
        if (occurred == null) return "UNKNOWN";
        Instant now = clock.now();
        return occurred.isBefore(now.minusSeconds(300)) || occurred.isAfter(now.plusSeconds(30)) ? "STALE" : "CURRENT";
    }
    void requireCurrent(WaInboxAccess.Context event) {
        if (!freshness(event).equals("CURRENT")) throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
    }
    boolean paused(UUID identity) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM wa.conversation_state WHERE identity_id=? AND stage='PAUSED')",
            Boolean.class, identity));
    }
    String registration(WaProfileState actor, boolean enabled) {
        if (!enabled || actor.blocked() || paused(actor.identityId())) return "PAUSED";
        if (actor.role().equals("UNREGISTERED")) return "WELCOME";
        if (actor.name() == null) return "ASK_NAME";
        if (!WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion())) return "ASK_NOTICE";
        if (actor.role().equals("DRIVER") && !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM wa.driver_device WHERE driver_id=? AND environment=? AND status='ACTIVE')
                """, Boolean.class, actor.identityId(), environment))) return "PAIRING";
        return "READY";
    }
    boolean hasCode(WaInboxAccess.Context event) {
        return event.normalizedPayload().has("driverInviteCodeHash") || event.normalizedPayload().has("pairingCodeHash")
            || event.normalizedPayload().has("passengerInviteCodeHash");
    }
    boolean hasText(WaInboxAccess.Context event) {
        return event.normalizedKind().equals("TEXT") && event.normalizedPayload().path("text").isTextual()
            && !event.normalizedPayload().path("text").asText().isBlank() && !hasCode(event);
    }
    boolean explicitSupport(WaInboxAccess.Context event) {
        return hasText(event) && SUPPORT.contains(text(event));
    }
    boolean isHelp(WaInboxAccess.Context event) { return hasText(event) && HELP.contains(text(event)); }
    boolean replyAllowed(String code, WaProfileState actor, WaInboxAccess.Context event) {
        String reason = event.normalizedPayload().path("reason").asText();
        return switch (code) {
            case "HELP" -> isHelp(event);
            case "CLARIFY" -> hasText(event) && !isHelp(event) && !explicitSupport(event);
            case "UNSUPPORTED_CONTENT" -> event.normalizedKind().equals("UNSUPPORTED") && !hasCode(event);
            case "STATIC_PIN_REQUIRED" -> event.normalizedKind().equals("UNSUPPORTED") && Set.of("LIVE_LOCATION", "INVALID_PIN").contains(reason);
            case "FEATURE_PENDING" -> !hasCode(event) && (hasText(event) || event.normalizedKind().equals("PIN"));
            case "STALE_MESSAGE" -> !freshness(event).equals("CURRENT");
            case "CONTINUE" -> !hasCode(event) && hasText(event);
            case "DRIVER_INVITE_REQUIRED" -> !actor.role().equals("DRIVER") && (hasText(event) || hasCode(event));
            default -> false;
        };
    }
    boolean consentAllowed(WaProfileState actor, WaInboxAccess.Context event) {
        if (actor.name() == null || !hasText(event) || !text(event).equals("aceito")) return false;
        if (WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion())) return true;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT stage,question_ref,question_expires_at,context_json::text FROM wa.conversation_state WHERE identity_id=? FOR UPDATE", actor.identityId());
        if (rows.isEmpty()) return false;
        Map<String, Object> row = rows.get(0);
        if (!"ASK_NOTICE".equals(row.get("stage")) || row.get("question_ref") == null || row.get("question_expires_at") == null
                || !((Timestamp) row.get("question_expires_at")).toInstant().isAfter(clock.now())) return false;
        ObjectNode context = WaJson.object((String) row.get("context_json"));
        Instant issued = parse(context.path("noticeEmittedAt").asText()), received = occurred(event);
        // Baileys messageTimestamp has seconds precision; the emission boundary uses that same precision.
        return WaOnboardingCommands.NOTICE_VERSION.equals(context.path("noticeVersion").asText()) && issued != null && received != null
            && !received.isBefore(issued.truncatedTo(ChronoUnit.SECONDS));
    }
    void emitted(WaProfileState actor, WaInboxAccess.Context event, String stage) {
        var previous = lockState(actor.identityId());
        if (paused(actor.identityId())) return;
        boolean notice = stage.equals("ASK_NOTICE");
        Instant now = clock.now();
        ObjectNode context = WaJson.MAPPER.createObjectNode();
        if (!previous.isEmpty()) {
            ObjectNode old = WaJson.object((String) previous.get(0).get("context_json"));
            if (old.has("driverDraft")) context.set("driverDraft", old.get("driverDraft"));
            if (old.has("passengerDraft")) context.set("passengerDraft", old.get("passengerDraft"));
            if (old.has("attendanceDraft")) context.set("attendanceDraft", old.get("attendanceDraft"));
            if (old.has("conversationOwner")) context.set("conversationOwner", old.get("conversationOwner"));
            if (old.has("ownerPromptEvents")) context.set("ownerPromptEvents", old.get("ownerPromptEvents"));
        }
        String owner = WaConversationOwnership.owner(context);
        if (!owner.equals("READY")) WaConversationOwnership.prompt(context, owner, event.eventId());
        if (notice) context.put("noticeVersion", WaOnboardingCommands.NOTICE_VERSION).put("noticeEmittedAt", now.toString());
        jdbc.update("""
            UPDATE wa.conversation_state SET stage=?,question_ref=?,question_expires_at=?,context_json=?::jsonb,
              last_event_id=?,version=version+1,updated_at=? WHERE identity_id=?
            """, stage, notice ? UUID.randomUUID() : null, notice ? Timestamp.from(now.plusSeconds(300)) : null,
            context.toString(), event.eventId(), Timestamp.from(now), actor.identityId());
    }
    void support(WaProfileState actor, WaInboxAccess.Context event, String reason, String correlation) {
        var previous = lockState(actor.identityId());
        List<UUID> existing = jdbc.query("""
            SELECT id FROM wa.support_case WHERE identity_id=? AND purpose='CONVERSATION' AND state IN ('OPEN','IN_PROGRESS')
            ORDER BY created_at,id LIMIT 1 FOR UPDATE
            """, (rs, i) -> rs.getObject(1, UUID.class), actor.identityId());
        UUID id = existing.isEmpty() ? UUID.randomUUID() : existing.get(0);
        if (existing.isEmpty()) jdbc.update("""
            INSERT INTO wa.support_case(id,identity_id,deduplication_key,reason_code,correlation_id,created_at)
            VALUES (?,?,?,?,?,?)
            """, id, actor.identityId(), "conversation-" + id, reason, correlation, Timestamp.from(clock.now()));
        ObjectNode context = WaJson.object(WaJson.write(Map.of("supportCaseId", id.toString(), "sourceInboxEventId", event.eventId().toString())));
        if (!previous.isEmpty()) {
            ObjectNode old = WaJson.object((String) previous.get(0).get("context_json"));
            if (old.has("ownerPromptEvents")) context.set("ownerPromptEvents", old.get("ownerPromptEvents"));
            if (old.path("passengerDraft") instanceof ObjectNode passengerDraft) {
                passengerDraft.remove("confirmation");
                context.set("passengerDraft", passengerDraft);
                jdbc.update("UPDATE wa.profile SET version=version+1,updated_at=? WHERE identity_id=?", Timestamp.from(clock.now()), actor.identityId());
            }
            if (old.path("driverDraft") instanceof ObjectNode draft) {
                draft.remove("confirmation"); draft.put("revision", draft.path("revision").asLong() + 1);
                context.set("driverDraft", draft);
                jdbc.update("UPDATE wa.profile SET version=version+1,updated_at=? WHERE identity_id=?", Timestamp.from(clock.now()), actor.identityId());
            }
        }
        jdbc.update("""
            UPDATE wa.conversation_state SET stage='PAUSED',question_ref=NULL,question_expires_at=NULL,
              context_json=?::jsonb,last_event_id=?,paused_until=NULL,version=version+1,updated_at=? WHERE identity_id=?
            """, context.toString(),
            event.eventId(), Timestamp.from(clock.now()), actor.identityId());
    }
    private List<Map<String, Object>> lockState(UUID identity) {
        jdbc.update("INSERT INTO wa.conversation_state(identity_id,updated_at) VALUES (?,?) ON CONFLICT (identity_id) DO NOTHING",
            identity, Timestamp.from(clock.now()));
        return jdbc.queryForList("SELECT stage,question_ref,question_expires_at,context_json::text FROM wa.conversation_state WHERE identity_id=? FOR UPDATE", identity);
    }
    private static String text(WaInboxAccess.Context event) { return normalize(event.normalizedPayload().path("text").asText()); }
    static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(TEXT_LOCALE);
        normalized = JS_WHITESPACE.matcher(normalized).replaceAll(" ").replaceFirst("^ +", "").replaceFirst(" +$", "");
        // Match normalizeText(...).replace(/[.!?]$/u, ''): no second trim after punctuation removal.
        return normalized.replaceFirst("[.!?]$", "");
    }
    private static Instant occurred(WaInboxAccess.Context event) {
        return parse(event.normalizedPayload().path("occurredAt").asText());
    }
    private static Instant parse(String value) {
        if (value == null || value.length() > 40) return null;
        try { return Instant.parse(value); } catch (DateTimeException invalid) { return null; }
    }
}
