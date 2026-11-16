package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.tracking.WaRunFinishes;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit contingency, reviewed once, bound to the authenticated driver and exact run version. */
final class WaRunFinishConversation {
    record Offer(ArrayNode options) { }
    record Result(WaDecision decision, String text) { }
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final WaCrypto crypto;
    private final WaRunDraftStore drafts;
    private final WaRunFinishes finishes;
    WaRunFinishConversation(JdbcTemplate jdbc, WaClock clock, WaSettings settings, WaCrypto crypto) {
        this.jdbc = jdbc; this.clock = clock; this.crypto = crypto;
        drafts = new WaRunDraftStore(jdbc, clock, crypto); finishes = new WaRunFinishes(jdbc, settings);
    }
    Offer offer(WaProfileState actor, WaInboxAccess.Context event) {
        if (!eligible(actor) || !event.normalizedKind().equals("TEXT")) return null;
        String text = text(event);
        if (entry(text)) return offered(actor, event, "review");
        ObjectNode draft = drafts.load(actor.identityId());
        if (!"FINISH".equals(draft.path("action").asText()) || !WaConversationOwnership.owner(jdbc, actor.identityId()).equals("RUN_SETUP")) return null;
        if (text.equals("abandonar") || text.matches("confirmar [bcdfghjkmnpqrstvwxyz]{8}"))
            return offered(actor, event, draft.path("run").asText());
        return null;
    }
    private Offer offered(WaProfileState actor, WaInboxAccess.Context event, String target) {
        String ref = "finish_" + crypto.secretHash("finish-option", actor.identityId() + ":" + event.eventId() + ":" + actor.version() + ":" + target);
        return new Offer(WaDriverDraftStore.options().add(WaDriverDraftStore.option("RUN", ref)));
    }
    Result execute(WaProfileState actor, WaInboxAccess.Context event, ObjectNode payload, UUID response) {
        Offer offer = offer(actor, event);
        if (offer == null) return unavailable();
        ObjectNode expected = WaJson.object("{}").put("runRef", offer.options().get(0).path("ref").asText()).put("reason", "MANUAL_COMPLETION");
        if (!WaJson.canonical(expected).equals(WaJson.canonical(payload))) throw WaProblem.denied();
        String text = text(event);
        if (text.equals("abandonar")) {
            drafts.save(actor, event, WaJson.object("{}"));
            return ok(true, "Finalização abandonada. A viagem não foi encerrada.");
        }
        if (entry(text)) {
            var runs = jdbc.queryForList("""
                SELECT id,version,local_date,snapshot_json->'routine'->>'name' name,
                  snapshot_json->'routine'->>'direction' direction FROM wa.operation_run
                WHERE driver_id=? AND state IN ('STARTING','START_FAILED','TRACKING','DEGRADED') FOR UPDATE
                """, actor.identityId());
            if (runs.size() != 1) return ok(false, "Não há viagem em andamento para finalizar. Parar GPS no aplicativo interrompe apenas a coleta; não encerra uma viagem.");
            var run = runs.get(0);
            ObjectNode draft = WaJson.object("{}").put("action", "FINISH").put("stage", "REVIEW")
                .put("run", run.get("id").toString()).put("runVersion", ((Number) run.get("version")).longValue())
                .put("expiresAt", clock.now().plusSeconds(300).toString());
            String marker = drafts.marker(actor, event, draft, response);
            drafts.save(actor, event, draft);
            String direction = "RETURN".equals(run.get("direction")) ? "volta" : "ida";
            return ok(true, "Revisão de finalização: " + run.get("local_date") + ", " + direction + " (" + run.get("name") + ").\n"
                + "Esta é a contingência manual: encerra a viagem e cancela novos avisos. Para confirmar, envie CONFIRMAR " + marker
                + ". Para manter a viagem, envie ABANDONAR.\nParar GPS interrompe só a coleta. O encerramento aguarda a confirmação de parada pelo aplicativo.");
        }
        ObjectNode draft = drafts.load(actor.identityId());
        if (!"FINISH".equals(draft.path("action").asText()) || !WaConversationOwnership.currentDraftReply(jdbc, event) || !drafts.confirms(actor, event, draft))
            return unavailable();
        try {
            boolean changed = finishes.manual(actor.identityId(), UUID.fromString(draft.path("run").asText()), draft.path("runVersion").longValue(), clock.now());
            drafts.save(actor, event, WaJson.object("{}"));
            UUID run = UUID.fromString(draft.path("run").asText());
            String sensor = jdbc.queryForObject("""
                SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM wa.tracking_session WHERE run_id=?) THEN 'NONE'
                  WHEN EXISTS(SELECT 1 FROM wa.tracking_session WHERE run_id=? AND stopped_at IS NULL) THEN 'PENDING'
                  ELSE 'STOPPED' END
                """, String.class, run, run);
            return ok(changed, changed
                ? "Viagem encerrada manualmente. Novos avisos foram cancelados e a autorização do GPS foi revogada. "
                    + ("STOPPED".equals(sensor) ? "O aplicativo já confirmou a parada da coleta." : "NONE".equals(sensor)
                        ? "Não há sessão de coleta vinculada a esta viagem." : "A parada da coleta ainda aguarda confirmação do aplicativo; confira o estado no app.")
                : "Essa viagem já foi encerrada. Nenhuma nova viagem foi alterada. Confira no aplicativo se a parada da coleta já foi confirmada.");
        } catch (WaProblem problem) {
            if (problem.code() != WaProblem.Code.VERSION_CONFLICT) throw problem;
            drafts.save(actor, event, WaJson.object("{}"));
            return new Result(WaDecision.error(problem.code()), "A viagem mudou após a revisão. Envie FINALIZAR para conferir e confirmar o estado atual. Nenhuma outra viagem foi encerrada.");
        }
    }
    String help(WaProfileState actor) {
        ObjectNode draft = drafts.load(actor.identityId());
        return eligible(actor) && draft.path("action").asText().equals("FINISH")
            && WaConversationOwnership.owner(jdbc,actor.identityId()).equals("RUN_SETUP")
            ? "Você está revisando o encerramento manual da viagem. Responda com CONFIRMAR e o código da última revisão, ABANDONAR para manter a viagem ou FINALIZAR para emitir uma nova revisão. Parar GPS no aplicativo interrompe apenas a coleta." : null;
    }
    private static boolean eligible(WaProfileState actor) {
        return !actor.blocked() && actor.role().equals("DRIVER") && actor.status().equals("ACTIVE") && actor.driverEnabled()
            && WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion());
    }
    private static String text(WaInboxAccess.Context event) { return WaAttendanceAnswers.normalize(event.normalizedPayload().path("text").asText()); }
    private static boolean entry(String text) { return Set.of("finalizar", "finalizar viagem").contains(text); }
    private static Result ok(boolean changed, String text) { return new Result(WaDecision.ok(Map.of("changed", changed)), text); }
    static Result unavailable() { return new Result(WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED), "Esta revisão da finalização não é válida. Envie FINALIZAR para revisar a viagem atual."); }
}
