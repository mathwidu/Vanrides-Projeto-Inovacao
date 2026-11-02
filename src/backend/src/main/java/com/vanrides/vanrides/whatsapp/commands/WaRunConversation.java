package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.tracking.WaTrackingService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Server-owned menus and a reviewed digest bind start and F06 to the authenticated conversation. */
final class WaRunConversation {
    static final Set<String> COMMANDS = Set.of("ADVANCE_RUN_SETUP", "REQUEST_START", "REQUEST_RESUME", "SET_OPERATIONAL_ATTENDANCE");
    record Offer(String command, ArrayNode options) { }
    record Result(WaDecision decision, String text, ObjectNode intent) { }
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final WaCrypto crypto;
    private final WaRunDraftStore store;
    private final WaRunPreparation preparation;
    private final WaAttendanceSummary summaries;
    private final WaAttendanceQuestions questions;
    private final WaTrackingService tracking;

    WaRunConversation(JdbcTemplate jdbc, WaClock clock, WaSettings settings, WaCrypto crypto) {
        this.jdbc = jdbc; this.clock = clock; this.crypto = crypto;
        store = new WaRunDraftStore(jdbc, clock, crypto); preparation = new WaRunPreparation(jdbc, clock, settings);
        questions = new WaAttendanceQuestions(jdbc, clock, settings); summaries = new WaAttendanceSummary(questions);
        tracking = new WaTrackingService(jdbc, settings, clock, crypto);
    }
    Offer offer(WaProfileState actor, WaInboxAccess.Context event) {
        if (!eligible(actor) || !event.normalizedKind().equals("TEXT")) return null;
        String text = text(event);
        ObjectNode draft = store.load(actor.identityId());
        if (entry(text) != null) return advance();
        if (!WaConversationOwnership.owner(jdbc, actor.identityId()).equals("RUN_SETUP")
                || WaAttendanceAnswers.entry(text) || WaAttendanceAnswers.interrupt(text)) return null;
        if (draft.isEmpty()) return advance();
        if (!WaConversationOwnership.currentDraftReply(jdbc, event)) return advance();
        if (!store.confirms(actor, event, draft)) return advance();
        ArrayNode options = WaDriverDraftStore.options().add(WaDriverDraftStore.option("OCCURRENCE", ref(actor, event, "occurrence", draft.path("occurrence").asText())));
        if (draft.path("action").asText().equals("CORRECT")) options.add(WaDriverDraftStore.option("PASSENGER", ref(actor, event, "passenger", draft.path("passenger").asText())));
        options.add(WaDriverDraftStore.option("CONFIRMATION", draft.path("confirmation").path("ref").asText()));
        return new Offer(switch (draft.path("action").asText()) {
            case "START" -> "REQUEST_START"; case "RESUME" -> "REQUEST_RESUME"; default -> "SET_OPERATIONAL_ATTENDANCE";
        }, options);
    }
    Result execute(WaProfileState actor, WaInboxAccess.Context event, String type, ObjectNode payload, UUID box) {
        Offer offer = offer(actor, event);
        if (offer == null || !offer.command().equals(type)) return unavailable();
        ObjectNode expected = WaDriverDraftStore.json();
        for (JsonNode option : offer.options()) expected.put(switch (option.path("kind").asText()) {
            case "OCCURRENCE" -> "occurrenceRef"; case "PASSENGER" -> "passengerRef"; default -> "confirmationRef";
        }, option.path("ref").asText());
        if (!WaJson.canonical(payload).equals(WaJson.canonical(expected))) throw WaProblem.denied();
        ObjectNode draft = store.load(actor.identityId());
        String text = text(event);
        if (entry(text) == null && !draft.isEmpty() && !WaConversationOwnership.currentDraftReply(jdbc, event))
            return ok(false, "A mensagem citada não corresponde à revisão atual da saída. Responda à última pergunta ou envie INICIAR SAÍDA para revisar.", null);
        if (text.equals("abandonar")) {
            store.save(actor, event, WaDriverDraftStore.json());
            return ok(true, "Seleção encerrada. Nenhuma saída ou presença foi alterada.", null);
        }
        try {
            if (type.equals("ADVANCE_RUN_SETUP")) return advance(actor, event, draft, box);
            UUID occurrence = UUID.fromString(draft.path("occurrence").asText());
            if (!store.confirms(actor, event, draft)) throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
            ObjectNode current = draft.path("action").asText().equals("RESUME") ? resume(actor.identityId(), occurrence)
                : draft.path("action").asText().equals("START") ? preparation.startReview(actor.identityId(), occurrence) : preparation.review(actor.identityId(), occurrence);
            String digest = draft.path("action").asText().equals("RESUME") ? current.path("digest").asText() : WaRunPreparation.digest(current);
            if (!WaCrypto.same(digest, draft.path("rosterDigest").asText())) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            ObjectNode intent = null;
            if (type.equals("SET_OPERATIONAL_ATTENDANCE")) correct(actor, event, draft, current);
            else intent = tracking.requestStart(actor.identityId(), occurrence, type.equals("REQUEST_RESUME"), digest);
            store.save(actor, event, WaDriverDraftStore.json());
            return ok(true, intent == null ? "Correção operacional registrada. A declaração pessoal do passageiro foi preservada. Envie PRESENÇAS para conferir a lista."
                : "A lista foi revisada. Abra o link no aplicativo VanRides Motorista para autorizar o acompanhamento. A partida ainda aguarda GPS válido.", intent);
        } catch (WaProblem conflict) {
            store.save(actor, event, WaDriverDraftStore.json());
            return new Result(WaDecision.error(conflict.code()), "A lista ou a saída mudou e a revisão perdeu a validade. Nenhuma outra data foi alterada. "
                + "Envie INICIAR SAÍDA, RETOMAR SAÍDA ou CORRIGIR LISTA para revisar as opções atuais.", null);
        }
    }
    private Result advance(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft, UUID box) {
        String text = text(event), action = entry(text), response;
        if (action != null) draft = menu(actor.identityId(), event, action);
        else if (draft.path("stage").asText().equals("OCCURRENCE")) {
            JsonNode choice = numbered(draft.path("choices"), text);
            if (choice != null) {
                UUID id = UUID.fromString(choice.path("id").asText());
                ObjectNode row = draft.path("action").asText().equals("RESUME") ? resumedOccurrence(actor.identityId(), id)
                    : summaries.own(actor.identityId(), questions.instance(event), id);
                if (row == null) throw WaProblem.missing();
                draft.put("occurrence", id.toString()).put("label", WaAttendanceQuestions.label(row));
                if (draft.path("action").asText().equals("RESUME")) {
                    ObjectNode run = resume(actor.identityId(), id);
                    draft.put("rosterDigest", run.path("digest").asText()).put("rosterText", summaries.render(actor.identityId(), row, 0)).put("stage", "REVIEW");
                } else {
                    ObjectNode review = draft.path("action").asText().equals("START") ? preparation.startReview(actor.identityId(), id) : preparation.review(actor.identityId(), id);
                    draft.put("rosterDigest", WaRunPreparation.digest(review)).put("rosterText", WaRunTexts.roster(review));
                    if (draft.path("action").asText().equals("CORRECT")) {
                        draft.set("members", review.path("members").deepCopy()); draft.put("stage", "PASSENGER");
                    } else draft.put("stage", "REVIEW");
                }
            }
        } else if (draft.path("stage").asText().equals("PASSENGER")) {
            JsonNode member = numbered(draft.path("members"), text);
            if (member != null) draft.put("passenger", member.path("passenger").asText()).put("passengerName", member.path("name").asText())
                .put("beforeIncluded", member.path("included").asBoolean()).put("passengerEligible", member.path("eligible").asBoolean())
                .put("personalAttendance", member.path("attendance").asText()).put("stage", "ACTION");
        } else if (draft.path("stage").asText().equals("ACTION") && Set.of("incluir", "excluir").contains(text)) {
            if (text.equals("incluir") && draft.path("personalAttendance").asText().equals("NOT_GOING"))
                return ok(false, "O passageiro declarou que não vai. A inclusão operacional foi recusada. Peça que ele próprio atualize a presença e depois revise novamente.", null);
            if (text.equals("incluir") && !draft.path("passengerEligible").asBoolean())
                return ok(false, "O vínculo ou o ponto ainda precisa de aprovação. A inclusão operacional foi recusada. Revise o cadastro antes de tentar novamente.", null);
            draft.put("included", text.equals("incluir")).put("stage", "REASON");
        } else if (draft.path("stage").asText().equals("REASON")) {
            String reason = java.text.Normalizer.normalize(event.normalizedPayload().path("text").asText(), java.text.Normalizer.Form.NFC).strip();
            if (reason.length() >= 3 && reason.length() <= 160 && reason.codePoints().noneMatch(c -> Character.isISOControl(c)
                    || Character.getType(c) == Character.FORMAT)) draft.put("reason", reason).put("stage", "REVIEW");
        }
        if (draft.path("stage").asText().equals("REVIEW") && !draft.has("confirmation")) {
            if (WaRunTexts.review(draft, "XXXXXXXX").length() > 2000) return oversized(actor, event);
            response = WaRunTexts.review(draft, store.marker(actor, event, draft, box));
        }
        else if (draft.path("stage").asText().equals("REVIEW")) return ok(false, "A revisão aguarda o código de confirmação da última mensagem. Para escolher novamente, envie INICIAR SAÍDA, RETOMAR SAÍDA ou CORRIGIR LISTA.", null);
        else response = WaRunTexts.prompt(draft);
        if (response.length() > 2000) return oversized(actor, event);
        store.save(actor, event, draft);
        return ok(true, response, null);
    }
    private Result oversized(WaProfileState actor, WaInboxAccess.Context event) {
        store.save(actor, event, WaDriverDraftStore.json());
        return ok(false, "A lista excede o tamanho permitido para uma revisão completa nesta conversa. Nenhuma saída ou presença foi alterada. Envie FALAR COM SUPORTE para revisar esta ocorrência.", null);
    }
    private void correct(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft, ObjectNode review) {
        UUID passenger = UUID.fromString(draft.path("passenger").asText());
        JsonNode selected = null;
        for (JsonNode member : review.path("members")) if (member.path("passenger").asText().equals(passenger.toString())) selected = member;
        if (selected == null) throw WaProblem.missing();
        if (draft.path("included").asBoolean() && selected.path("attendance").asText().equals("NOT_GOING")) throw WaProblem.denied();
        if (draft.path("included").asBoolean() && !selected.path("eligible").asBoolean()) throw new WaProblem(WaProblem.Code.POINT_NOT_APPROVED);
        jdbc.update("""
            INSERT INTO wa.occurrence_roster_override(id,occurrence_id,routine_id,driver_id,passenger_id,local_date,
              included,reason,actor_identity_id,source_inbox_event_id,created_at,revision)
            VALUES (?,?,?,?,?,?::date,?,?,?,?,?,?)
            """, UUID.randomUUID(), UUID.fromString(draft.path("occurrence").asText()), UUID.fromString(review.path("routine").path("id").asText()),
            actor.identityId(), passenger, review.path("occurrence").path("local_date").asText(), draft.path("included").asBoolean(),
            draft.path("reason").asText(), actor.identityId(), event.eventId(), Timestamp.from(clock.now()), review.path("occurrence").path("version").asLong() + 1);
        jdbc.update("UPDATE wa.routine_occurrence SET version=version+1 WHERE id=?", UUID.fromString(draft.path("occurrence").asText()));
    }
    private ObjectNode menu(UUID driver, WaInboxAccess.Context event, String action) {
        ObjectNode draft = WaDriverDraftStore.json().put("action", action).put("stage", "OCCURRENCE").put("expiresAt", clock.now().plusSeconds(300).toString());
        ArrayNode choices = draft.putArray("choices");
        ArrayNode catalogue = summaries.occurrences(driver, questions.instance(event));
        if (action.equals("RESUME")) {
            catalogue.removeAll();
            jdbc.query("SELECT occurrence_id FROM wa.operation_run WHERE driver_id=? AND state IN ('STARTING','START_FAILED','TRACKING','DEGRADED')",
                (rs, row) -> rs.getObject(1, UUID.class), driver).forEach(id -> catalogue.add(resumedOccurrence(driver, id)));
        }
        for (JsonNode occurrence : catalogue) {
            UUID id = WaAttendanceQuestions.id(occurrence, "id");
            var runs = jdbc.queryForList("SELECT state FROM wa.operation_run WHERE occurrence_id=? AND driver_id=?", id, driver);
            boolean active = !runs.isEmpty() && List.of("STARTING", "START_FAILED", "TRACKING", "DEGRADED").contains(runs.get(0).get("state"));
            boolean resumable = !runs.isEmpty() && List.of("START_FAILED", "DEGRADED").contains(runs.get(0).get("state"));
            if (action.equals("RESUME") ? !resumable : active || !occurrence.path("state").asText().equals("PLANNED")) continue;
            if (action.equals("START") && !LocalDate.parse(occurrence.path("local_date").asText()).equals(
                    clock.now().atZone(ZoneId.of(occurrence.path("schedule_snapshot").path("timezone").asText(occurrence.path("timezone").asText()))).toLocalDate())) continue;
            choices.add(WaDriverDraftStore.json().put("id", id.toString()).put("label", WaAttendanceQuestions.label(occurrence)));
        }
        if (choices.size() > 30) { choices.removeAll(); draft.put("tooMany", true); }
        return draft;
    }
    private ObjectNode resumedOccurrence(UUID driver, UUID occurrence) {
        var rows = jdbc.query("""
            SELECT (to_jsonb(o)||jsonb_build_object('routineName',COALESCE(o.schedule_snapshot->>'name',r.name),
              'direction',COALESCE(o.schedule_snapshot->>'direction',r.direction),
              'time',COALESCE(o.schedule_snapshot->>'local_time',r.local_time::text),
              'timezone',COALESCE(o.schedule_snapshot->>'timezone',r.timezone)))::text
            FROM wa.routine_occurrence o JOIN wa.routine r ON r.id=o.routine_id
            JOIN wa.operation_run run ON run.occurrence_id=o.id AND run.driver_id=o.driver_id
            WHERE o.id=? AND o.driver_id=? AND run.state IN ('STARTING','START_FAILED','TRACKING','DEGRADED')
            """, (rs, row) -> WaJson.object(rs.getString(1)), occurrence, driver);
        if (rows.size() != 1) throw WaProblem.missing();
        return rows.get(0);
    }
    private ObjectNode resume(UUID driver, UUID occurrence) {
        var rows = jdbc.query("SELECT id,snapshot_json::text FROM wa.operation_run WHERE occurrence_id=? AND driver_id=? "
            + "AND state IN ('STARTING','START_FAILED','TRACKING','DEGRADED') FOR UPDATE", (rs, row) -> {
                ObjectNode result = WaDriverDraftStore.json().put("runId", rs.getObject(1).toString());
                result.put("digest", WaJson.object(rs.getString(2)).path("rosterDigest").asText()); return result;
            }, occurrence, driver);
        if (rows.size() != 1) throw WaProblem.missing();
        return rows.get(0);
    }
    void attach(UUID box, UUID command, UUID actor, Result result) {
        if (result.intent() != null) tracking.attachStartLink(box, command, actor, result.intent());
    }
    String help(WaProfileState actor) {
        ObjectNode draft = store.load(actor.identityId());
        return !eligible(actor) || draft.isEmpty() || !WaConversationOwnership.owner(jdbc, actor.identityId()).equals("RUN_SETUP") ? null : WaRunTexts.prompt(draft);
    }
    private boolean eligible(WaProfileState actor) {
        return actor.role().equals("DRIVER") && actor.status().equals("ACTIVE") && actor.driverEnabled()
            && WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion());
    }
    private String ref(WaProfileState actor, WaInboxAccess.Context event, String kind, String id) {
        return "run_" + kind + "_" + crypto.secretHash("run-option", actor.identityId() + ":" + event.eventId() + ":" + kind + ":" + id);
    }
    private static JsonNode numbered(JsonNode options, String text) {
        if (!text.matches("[1-9][0-9]?")) return null;
        int index = Integer.parseInt(text) - 1;
        return index < options.size() ? options.get(index) : null;
    }
    private static String text(WaInboxAccess.Context event) { return WaAttendanceAnswers.normalize(event.normalizedPayload().path("text").asText()); }
    private static String entry(String text) {
        return switch (text) { case "iniciar", "iniciar saida" -> "START"; case "retomar", "retomar saida" -> "RESUME"; case "corrigir lista" -> "CORRECT"; default -> null; };
    }
    private static Offer advance() { return new Offer("ADVANCE_RUN_SETUP", WaDriverDraftStore.options()); }
    private static Result ok(boolean changed, String text, ObjectNode intent) { return new Result(WaDecision.ok(Map.of("changed", changed)), text, intent); }
    static Result unavailable() { return new Result(WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED), "Essa revisão da saída não está mais disponível. Envie INICIAR SAÍDA ou CORRIGIR LISTA para revisar.", null); }
}
