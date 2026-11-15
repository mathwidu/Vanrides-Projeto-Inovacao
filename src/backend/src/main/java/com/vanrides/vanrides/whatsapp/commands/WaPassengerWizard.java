package com.vanrides.vanrides.whatsapp.commands;

import static com.vanrides.vanrides.whatsapp.commands.WaPassengerAnswers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/** One authenticated human answer at a time. No selected business value comes from the worker. */
final class WaPassengerWizard {
    private final WaPassengerData data;
    private final WaPassengerDriverWizard driver;
    private final WaPassengerNotice notice;
    private final WaPassengerSignup signup;

    WaPassengerWizard(WaPassengerData data) {
        this.data = data;
        this.driver = new WaPassengerDriverWizard(data);
        this.notice = new WaPassengerNotice(data.clock);
        this.signup = new WaPassengerSignup(data, notice);
    }

    static String text(WaInboxAccess.Context event) {
        return WaPassengerAnswers.loose(WaDriverWizard.text(event));
    }

    static boolean starts(String text) {
        return List.of(
                        "passageiros",
                        "menu passageiros",
                        "revisar pedido",
                        "ponto comum",
                        "continuar cadastro",
                        "corrigir ponto",
                        "encerrar",
                        "encerrar vinculo")
                .contains(text);
    }

    record Step(ObjectNode draft, String lead) {}

    private static Step ok(ObjectNode draft) {
        return new Step(draft, "");
    }

    Step advance(WaProfileState actor, WaInboxAccess.Context event, ObjectNode old) {
        String text = text(event);
        ObjectNode draft = old.deepCopy();
        draft.remove("confirmation");
        if (text.equals("abandonar")) {
            return ok(WaPassengerDraftStore.json());
        }
        if (actor.role().equals("DRIVER")) {
            return ok(driver.advance(actor, event, draft, text));
        }
        if (event.normalizedPayload().hasNonNull("passengerInviteCodeHash")) {
            ObjectNode invite =
                    data.invite(
                            actor.identityId(),
                            event,
                            event.normalizedPayload().path("passengerInviteCodeHash").asText(),
                            false);
            if (!data.links(actor.identityId(), false).isEmpty()) {
                throw new WaProblem(WaProblem.Code.ACTION_NOT_ALLOWED);
            }
            ObjectNode request = WaPassengerDraftStore.json()
                    .put("simpleSignup", true)
                    .put("action", "REQUEST_LINK")
                    .put("inviteHash", invite.path("code_hash").asText())
                    .put("driverId", invite.path("driver_id").asText())
                    .put("driverName", invite.path("driverName").asText());
            signup.afterIdentity(actor, request);
            return new Step(request, WaPassengerTexts.inviteLead(request));
        }
        if (text.equals("corrigir ponto")
                && !draft.isEmpty()
                && !draft.has("linkId")
                && draft.path("action").asText().equals("REQUEST_LINK")) {
            if (List.of("POINT", "REFERENCE", "REVIEW").contains(draft.path("stage").asText())) {
                clearPoint(draft);
                draft.put("stage", "POINT");
            }
            return ok(draft);
        }
        if (text.equals("corrigir ponto") || draft.isEmpty() && text.equals("continuar cadastro")) {
            ArrayNode links = data.links(actor.identityId(), false);
            if (links.size() != 1) {
                throw new WaProblem(WaProblem.Code.ACTION_NOT_ALLOWED);
            }
            JsonNode link = links.get(0);
            draft = baseLink(link).put("stage", "POINT").put("action", "PROPOSE_PICKUP");
            catalogue(
                    draft,
                    "commonChoices",
                    data.commons(UUID.fromString(draft.path("driverId").asText())));
            return ok(draft);
        }
        if (text.equals("encerrar") || text.equals("encerrar vinculo")) {
            ArrayNode links = data.links(actor.identityId(), false);
            if (links.size() != 1) {
                throw new WaProblem(WaProblem.Code.ACTION_NOT_ALLOWED);
            }
            return ok(baseLink(links.get(0))
                    .put("stage", "REVIEW")
                    .put("action", "CLOSE_LINK")
                    .put("operation", "CLOSED"));
        }
        if (draft.isEmpty()) {
            return ok(draft);
        }
        String stage = draft.path("stage").asText();
        if (signup.optionalAnswer(event, draft, text, stage)) {
            return ok(draft);
        }
        if (stage.equals("REVIEW") && draft.path("simpleSignup").asBoolean() && diverges(text)) {
            clearPoint(draft);
            return ok(draft.put("stage", "POINT"));
        }
        if (text.equals("corrigir")) {
            if (draft.path("simpleSignup").asBoolean() && List.of("NAME", "NOTICE").contains(stage)) {
                return ok(draft);
            }
            clearPoint(draft);
            if (draft.path("simpleSignup").asBoolean()) {
                signup.routes(draft);
            } else {
                draft.put("stage", draft.path("action").asText().equals("REQUEST_LINK") ? "PREF_APPROACH" : "POINT");
            }
            return ok(draft);
        }
        if (text.equals("voltar")) {
            draft.put(
                    "stage",
                    switch (stage) {
                        case "PREF_REMINDER" -> "PREF_APPROACH";
                        case "ROUTINES" -> draft.path("simpleSignup").asBoolean() ? "ROUTINES" : "PREF_REMINDER";
                        case "DAYS" -> "ROUTINES";
                        case "REFERENCE", "REVIEW" -> "POINT";
                        case "POINT" ->
                                draft.path("action").asText().equals("REQUEST_LINK")
                                        ? "ROUTINES"
                                        : "POINT";
                        default -> stage;
                    });
            return ok(draft);
        }
        if (text.equals("continuar") || text.equals("continuar cadastro")) {
            if (stage.equals("DRIVER_CONFIRM")) {
                signup.afterIdentity(actor, draft);
            }
            if (stage.equals("NOTICE")) {
                notice.open(draft);
            }
            if (stage.equals("ROUTINES")) {
                signup.routes(draft);
            }
            return ok(draft);
        }
        JsonNode dayBefore = draft.path("dayIndex");
        String lead = "";
        switch (stage) {
            case "DRIVER_CONFIRM" -> {
                var answer =
                        WaPassengerTexts.driverConfirm(draft.path("driverName").asText())
                                .answer(text);
                if (answer.isPresent()) {
                    if (answer.get().id().equals("NO")) {
                        return ok(WaPassengerDraftStore.json());
                    }
                    signup.afterIdentity(actor, draft);
                }
            }
            case "NAME" -> signup.name(actor, event, draft, text);
            case "NOTICE" -> {
                if (text.equals("aceito") && notice.accepts(event, draft)) {
                    data.jdbc.update(
                            "UPDATE wa.profile SET"
                                    + " notice_version=?,notice_accepted_at=?,status='ACTIVE' WHERE"
                                    + " identity_id=?",
                            WaOnboardingCommands.NOTICE_VERSION,
                            Timestamp.from(data.clock.now()),
                            actor.identityId());
                    signup.afterNotice(actor, draft);
                } else if (notice.expired(draft)) {
                    notice.open(draft);
                    lead = WaPassengerTexts.NOTICE_RENEWED;
                }
            }
            case "PREF_APPROACH", "PREF_REMINDER" -> signup.legacyPreference(draft, text, stage.equals("PREF_APPROACH"));
            case "ROUTINES" -> signup.selectRoutines(draft, text);
            case "DAYS" -> signup.selectDays(draft, text);
            case "POINT" -> point(actor, event, draft, text);
            case "REFERENCE" -> {
                if (diverges(text)) {
                    clearPoint(draft);
                    draft.put("stage", "POINT");
                    break;
                }
                String value = clean(event.normalizedPayload().path("text").asText(), 160);
                if (text.equals("pular") || text.equals("sem referencia")) {
                    draft.remove("reference");
                    draft.put("stage", "REVIEW");
                } else {
                    if (value != null) {
                        draft.put("reference", value).put("stage", "REVIEW");
                    }
                }
            }
            default -> {
                /* Invalid or stale marker re-emits the current question and a fresh review. */
            }
        }
        boolean understood =
                !draft.path("stage").asText().equals(stage)
                        || !draft.path("dayIndex").equals(dayBefore)
                        || stage.equals("NOTICE") && text.equals("aceito");
        if (!understood && lead.isEmpty()) {
            lead = WaPassengerTexts.NOT_UNDERSTOOD;
        }
        return new Step(draft, lead);
    }

    private static void clearPoint(ObjectNode draft) {
        draft.remove(List.of("sourceEvent", "commonId", "commonVersion", "selectionEvent", "reference", "pointLabel", "mapUrl"));
    }

    private void point(
            WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft, String text) {
        String next = draft.path("simpleSignup").asBoolean() ? "REVIEW" : "REFERENCE";
        if (event.normalizedKind().equals("PIN")) {
            data.pin(actor.identityId(), event.eventId().toString(), event.eventId());
            clearPoint(draft);
            draft.put("sourceEvent", event.eventId().toString()).put("stage", next);
        } else {
            if (text.matches("ponto (?:[1-9][0-9]?|100)")) {
                int index = Integer.parseInt(text.substring(6)) - 1;
                if (index >= draft.path("commonChoices").size()) {
                    return;
                }
                JsonNode common = draft.path("commonChoices").get(index);
                clearPoint(draft);
                draft.put("commonId", common.path("id").asText())
                        .put("commonVersion", common.path("version").asLong())
                        .put("selectionEvent", event.eventId().toString())
                        .put("pointLabel", common.path("name").asText())
                        .put("stage", next);
            }
        }
    }

    static ObjectNode baseLink(JsonNode link) {
        return WaPassengerDraftStore.json()
                .put("driverId", link.path("driver_id").asText())
                .put("driverName", link.path("driverName").asText())
                .put("linkId", link.path("id").asText())
                .put("passengerName", link.path("passengerName").asText());
    }
}
