package com.vanrides.vanrides.whatsapp.commands;

import static com.vanrides.vanrides.whatsapp.commands.WaPassengerAnswers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** One authenticated human answer at a time. No selected business value comes from the worker. */
final class WaPassengerWizard {
    private final WaPassengerData data;
    private final WaPassengerDriverWizard driver;

    WaPassengerWizard(WaPassengerData data) {
        this.data = data;
        this.driver = new WaPassengerDriverWizard(data);
    }

    static String text(WaInboxAccess.Context event) {
        return WaPassengerAnswers.loose(WaDriverWizard.text(event));
    }

    static boolean starts(String text) {
        return List.of(
                        "passageiros",
                        "menu passageiros",
                        "ponto comum",
                        "continuar cadastro",
                        "corrigir ponto",
                        "encerrar",
                        "encerrar vinculo")
                .contains(text);
    }

    private static final long NOTICE_WINDOW_SECONDS = 600;

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
            return ok(WaPassengerDraftStore.json()
                    .put("stage", "DRIVER_CONFIRM")
                    .put("action", "REQUEST_LINK")
                    .put("inviteHash", invite.path("code_hash").asText())
                    .put("driverId", invite.path("driver_id").asText())
                    .put("driverName", invite.path("driverName").asText()));
        }
        if (text.equals("corrigir ponto")
                && !draft.isEmpty()
                && !draft.has("linkId")
                && draft.path("action").asText().equals("REQUEST_LINK")) {
            draft.remove(
                    List.of(
                            "sourceEvent",
                            "commonId",
                            "commonVersion",
                            "selectionEvent",
                            "reference",
                            "pointLabel",
                            "mapUrl"));
            draft.put("stage", "POINT");
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
        if (text.equals("corrigir")) {
            draft.remove(
                    List.of(
                            "sourceEvent",
                            "commonId",
                            "commonVersion",
                            "selectionEvent",
                            "reference",
                            "pointLabel"));
            draft.put(
                    "stage",
                    draft.path("action").asText().equals("REQUEST_LINK")
                            ? "PREF_APPROACH"
                            : "POINT");
            return ok(draft);
        }
        if (text.equals("voltar")) {
            draft.put(
                    "stage",
                    switch (stage) {
                        case "PREF_REMINDER" -> "PREF_APPROACH";
                        case "ROUTINES" -> "PREF_REMINDER";
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
                afterIdentity(actor, draft);
            }
            if (stage.equals("NOTICE")) {
                notice(draft);
            }
            if (stage.equals("ROUTINES")) {
                catalogue(
                        draft,
                        "routineChoices",
                        data.routines(UUID.fromString(draft.path("driverId").asText())));
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
                    afterIdentity(actor, draft);
                }
            }
            case "NAME" -> {
                String value = clean(event.normalizedPayload().path("text").asText(), 80);
                if (value != null) {
                    data.jdbc.update(
                            "UPDATE wa.profile SET display_name=? WHERE identity_id=?",
                            value,
                            actor.identityId());
                    draft.put("passengerName", value);
                    if (WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion())) {
                        draft.put("stage", "PREF_APPROACH");
                    } else {
                        notice(draft);
                    }
                }
            }
            case "NOTICE" -> {
                if (text.equals("aceito") && noticeAnswer(event, draft)) {
                    data.jdbc.update(
                            "UPDATE wa.profile SET"
                                    + " notice_version=?,notice_accepted_at=?,status='ACTIVE' WHERE"
                                    + " identity_id=?",
                            WaOnboardingCommands.NOTICE_VERSION,
                            Timestamp.from(data.clock.now()),
                            actor.identityId());
                    draft.put("stage", "PREF_APPROACH");
                } else if (noticeExpired(draft)) {
                    notice(draft);
                    lead = WaPassengerTexts.NOTICE_RENEWED;
                }
            }
            case "PREF_APPROACH", "PREF_REMINDER" -> {
                boolean approach = stage.equals("PREF_APPROACH");
                var answer =
                        (approach ? WaPassengerTexts.APPROACH : WaPassengerTexts.REMINDER)
                                .answer(text);
                if (answer.isPresent()) {
                    draft.put(
                            approach ? "approachMessages" : "attendanceReminders",
                            answer.get().id().equals("YES"));
                    if (approach) {
                        draft.put("stage", "PREF_REMINDER");
                    } else {
                        catalogue(
                                draft,
                                "routineChoices",
                                data.routines(UUID.fromString(draft.path("driverId").asText())));
                        draft.put("stage", "ROUTINES");
                    }
                }
            }
            case "ROUTINES" -> {
                ArrayNode chosen = select(text, draft.path("routineChoices"));
                if (chosen != null) {
                    draft.set("selections", chosen);
                    draft.put("dayIndex", 0).put("stage", "DAYS");
                }
            }
            case "DAYS" -> {
                ArrayNode days = days(text);
                int index = draft.path("dayIndex").asInt();
                JsonNode routine = draft.path("selections").path(index);
                if (days != null && subset(days, routine.path("weekdays"))) {
                    ((ObjectNode) routine).set("selectedDays", days);
                    index++;
                    if (index < draft.path("selections").size()) {
                        draft.put("dayIndex", index);
                    } else {
                        draft.put("stage", "POINT");
                        catalogue(
                                draft,
                                "commonChoices",
                                data.commons(UUID.fromString(draft.path("driverId").asText())));
                    }
                }
            }
            case "POINT" -> point(actor, event, draft, text);
            case "REFERENCE" -> {
                if (diverges(text)) {
                    draft.remove(
                            List.of(
                                    "sourceEvent",
                                    "commonId",
                                    "commonVersion",
                                    "selectionEvent",
                                    "reference",
                                    "pointLabel",
                                    "mapUrl"));
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
        return new Step(draft, understood || !lead.isEmpty() ? lead : WaPassengerTexts.NOT_UNDERSTOOD);
    }

    private void afterIdentity(WaProfileState actor, ObjectNode draft) {
        if (actor.name() == null) {
            draft.put("stage", "NAME");
        } else {
            draft.put("passengerName", actor.name());
            if (WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion())) {
                draft.put("stage", "PREF_APPROACH");
            } else {
                notice(draft);
            }
        }
    }

    private void notice(ObjectNode draft) {
        draft.put("stage", "NOTICE").put("noticeEmittedAt", data.clock.now().toString());
    }

    private boolean noticeAnswer(WaInboxAccess.Context event, ObjectNode draft) {
        try {
            Instant issued = Instant.parse(draft.path("noticeEmittedAt").asText());
            Instant received = Instant.parse(event.normalizedPayload().path("occurredAt").asText());
            return !received.isBefore(issued.truncatedTo(ChronoUnit.SECONDS))
                    && issued.plusSeconds(NOTICE_WINDOW_SECONDS).isAfter(data.clock.now());
        } catch (java.time.DateTimeException invalid) {
            return false;
        }
    }

    private boolean noticeExpired(ObjectNode draft) {
        try {
            Instant issued = Instant.parse(draft.path("noticeEmittedAt").asText());
            return !issued.plusSeconds(NOTICE_WINDOW_SECONDS).isAfter(data.clock.now());
        } catch (java.time.DateTimeException invalid) {
            return true;
        }
    }

    private void point(
            WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft, String text) {
        if (event.normalizedKind().equals("PIN")) {
            data.pin(actor.identityId(), event.eventId().toString(), event.eventId());
            draft.remove(List.of("commonId", "commonVersion", "selectionEvent", "pointLabel"));
            draft.put("sourceEvent", event.eventId().toString()).put("stage", "REFERENCE");
        } else {
            if (text.matches("ponto (?:[1-9][0-9]?|100)")) {
                int index = Integer.parseInt(text.substring(6)) - 1;
                if (index >= draft.path("commonChoices").size()) {
                    return;
                }
                JsonNode common = draft.path("commonChoices").get(index);
                draft.remove("sourceEvent");
                draft.put("commonId", common.path("id").asText())
                        .put("commonVersion", common.path("version").asLong())
                        .put("selectionEvent", event.eventId().toString())
                        .put("pointLabel", common.path("name").asText())
                        .put("stage", "REFERENCE");
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
