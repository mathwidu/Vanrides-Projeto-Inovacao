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
        Step step = answer(actor, event, old);
        ObjectNode draft = step.draft();
        if (draft.path("addingRoutines").asBoolean()
                && List.of("NAME", "NOTICE").contains(old.path("stage").asText())
                && !List.of("NAME", "NOTICE").contains(draft.path("stage").asText())) {
            draft.remove("selections");
            draft.put("stage", "ROUTINES");
        }
        if (draft.path("addingRoutines").asBoolean() && draft.path("stage").asText().equals("ROUTINES")) {
            catalogue(draft, "routineChoices", data.additionalRoutines(
                    UUID.fromString(draft.path("driverId").asText()), UUID.fromString(draft.path("linkId").asText())));
        }
        return step;
    }

    private Step answer(WaProfileState actor, WaInboxAccess.Context event, ObjectNode old) {
        String text = text(event);
        ObjectNode draft = old.deepCopy();
        draft.remove("confirmation");
        if (text.equals("abandonar") || text.equals("sair")) {
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
            ArrayNode links = data.links(actor.identityId(), false);
            if (!links.isEmpty() && (links.size() != 1
                    || !links.path(0).path("driver_id").equals(invite.path("driver_id")))) {
                throw new WaProblem(WaProblem.Code.ACTION_NOT_ALLOWED);
            }
            ObjectNode request = WaPassengerDraftStore.json()
                    .put("simpleSignup", true)
                    .put("routePoints", true)
                    .put("action", "REQUEST_LINK")
                    .put("inviteHash", invite.path("code_hash").asText())
                    .put("driverId", invite.path("driver_id").asText())
                    .put("driverName", invite.path("driverName").asText());
            if (!links.isEmpty()) {
                request.put("linkId", links.path(0).path("id").asText())
                        .put("action", "PROPOSE_PICKUP").put("addingRoutines", true);
            }
            signup.afterIdentity(actor, request);
            if (!links.isEmpty() && List.of("POINT", "ROUTINES").contains(request.path("stage").asText())) {
                request.remove("selections");
                request.put("stage", "ROUTINES");
            }
            return new Step(request, WaPassengerTexts.inviteLead(request));
        }
        if (text.equals("corrigir ponto")
                && draft.path("routePoints").asBoolean() && !draft.path("selections").isEmpty()) {
            if (!draft.path("stage").asText().equals("POINT")) new WaPassengerEdit(data).revise(draft);
            return ok(draft);
        }
        if (text.equals("corrigir ponto")
                && !draft.isEmpty()
                && (draft.path("addingRoutines").asBoolean()
                        || !draft.has("linkId") && draft.path("action").asText().equals("REQUEST_LINK"))) {
            if (List.of("POINT", "REFERENCE", "REVIEW").contains(draft.path("stage").asText())) {
                clearPoint(draft);
                draft.put("stage", "POINT");
            }
            return ok(draft);
        }
        if (text.equals("corrigir ponto") || draft.isEmpty() && text.equals("continuar cadastro")) {
            return ok(new WaPassengerEdit(data).start(actor));
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
        if (new WaPassengerPoints(data).editReference(event, draft, text)) return ok(draft);
        if ((draft.path("addingRoutines").asBoolean() || draft.path("editingPickup").asBoolean()) && text.startsWith("avisos ")) {
            return new Step(draft, "Seus avisos foram mantidos. Você pode ajustá-los depois deste pedido.\n\n");
        }
        if (stage.equals("REVIEW") && text.equals("ajustar") && WaPassengerTexts.signupReview(draft)) {
            return ok(restart(draft));
        }
        if (signup.optionalAnswer(event, draft, text, stage)) {
            return ok(draft);
        }
        if (stage.equals("REVIEW") && draft.path("simpleSignup").asBoolean() && diverges(text)) {
            if (draft.path("routePoints").asBoolean()) {
                new WaPassengerEdit(data).revise(draft);
                return ok(draft);
            }
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
                        case "ROUTINES" -> draft.path("simpleSignup").asBoolean() ? "ROUTINES" : "PREF_APPROACH";
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
        JsonNode pointBefore = draft.path("pointIndex");
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
            case "PREF_APPROACH" -> signup.legacyPreference(draft, text);
            case "ROUTINES" -> signup.selectRoutines(draft, text);
            case "DAYS" -> signup.selectDays(draft, text);
            case "EDIT_ROUTINE", "EDIT_POINT_SELECTION" -> new WaPassengerEdit(data).choose(draft, text);
            case "POINT" -> {
                if (event.normalizedPayload().path("pin").path("typed").asBoolean()) {
                    lead = WaPassengerTexts.TYPED_POINT;
                } else {
                    point(actor, event, draft, text);
                }
            }
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
                        || !draft.path("pointIndex").equals(pointBefore)
                        || stage.equals("NOTICE") && text.equals("aceito");
        if (!understood && lead.isEmpty()) {
            lead = WaPassengerTexts.NOT_UNDERSTOOD;
        }
        return new Step(draft, lead);
    }

    private static ObjectNode restart(ObjectNode draft) {
        ObjectNode fresh = WaPassengerDraftStore.json();
        for (String field : List.of("simpleSignup", "routePoints", "action", "inviteHash", "driverId", "driverName",
                "linkId", "addingRoutines")) {
            if (draft.has(field)) fresh.set(field, draft.get(field));
        }
        return fresh.put("stage", "NAME");
    }

    private static void clearPoint(ObjectNode draft) {
        draft.remove(List.of("sourceEvent", "commonId", "commonVersion", "selectionEvent", "reference", "pointLabel", "mapUrl", "reusePickupId", "reusePickupVersion"));
    }

    private void point(
            WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft, String text) {
        if (draft.path("routePoints").asBoolean()) {
            new WaPassengerPoints(data).answer(actor, event, draft, text);
            return;
        }
        String next = draft.path("simpleSignup").asBoolean() ? "REVIEW" : "REFERENCE";
        if (text.equals("mesmo ponto") && draft.has("reusablePoint")) {
            JsonNode point = draft.path("reusablePoint");
            clearPoint(draft);
            draft.put("reusePickupId", point.path("id").asText())
                    .put("reusePickupVersion", point.path("version").asLong())
                    .put("selectionEvent", event.eventId().toString()).put("stage", next);
            if (point.hasNonNull("label")) draft.put("pointLabel", point.path("label").asText());
            if (point.hasNonNull("reference_text")) draft.put("reference", point.path("reference_text").asText());
            if (point.hasNonNull("common_pickup_point_id")) {
                draft.put("commonId", point.path("common_pickup_point_id").asText())
                        .put("commonVersion", point.path("common_pickup_version").asLong());
            }
        } else if (event.normalizedKind().equals("PIN")) {
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
