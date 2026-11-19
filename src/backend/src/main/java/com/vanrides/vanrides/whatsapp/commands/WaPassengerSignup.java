package com.vanrides.vanrides.whatsapp.commands;

import static com.vanrides.vanrides.whatsapp.commands.WaPassengerAnswers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;

import java.util.List;
import java.util.UUID;

/** The passenger chooses a schedule and a pickup before asking the driver to approve. */
final class WaPassengerSignup {
    private final WaPassengerData data;
    private final WaPassengerNotice notice;

    WaPassengerSignup(WaPassengerData data, WaPassengerNotice notice) {
        this.data = data;
        this.notice = notice;
    }

    void afterIdentity(WaProfileState actor, ObjectNode draft) {
        if (actor.name() == null) {
            draft.put("stage", "NAME");
        } else {
            draft.put("passengerName", actor.name());
            afterName(actor, draft);
        }
    }

    void name(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft, String text) {
        if (List.of("sim", "nao", "aceito", "confirmar").contains(text)) {
            return;
        }
        String value = clean(event.normalizedPayload().path("text").asText(), 80);
        if (value != null) {
            data.jdbc.update("UPDATE wa.profile SET display_name=? WHERE identity_id=?", value, actor.identityId());
            draft.put("passengerName", value);
            afterName(actor, draft);
        }
    }

    private void afterName(WaProfileState actor, ObjectNode draft) {
        if (WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion())) {
            afterNotice(actor, draft);
        } else {
            notice.open(draft);
        }
    }

    void afterNotice(WaProfileState actor, ObjectNode draft) {
        if (!draft.path("simpleSignup").asBoolean()) {
            draft.put("stage", "PREF_APPROACH");
            return;
        }
        var preferences = data.jdbc.queryForObject(
                "SELECT preferences_json::text FROM wa.profile WHERE identity_id=?",
                (rs, row) -> WaJson.object(rs.getString(1)), actor.identityId());
        draft.put("approachMessages", preferences.path("APPROACH_MESSAGES").asBoolean());
        routes(draft);
        if (draft.path("routineChoices").size() == 1) {
            ObjectNode routine = draft.path("routineChoices").path(0).deepCopy();
            routine.set("selectedDays", routine.path("weekdays").deepCopy());
            draft.set("selections", WaDriverDraftStore.options().add(routine));
            draft.put("dayIndex", 0);
            pointOrReview(draft);
        }
    }

    void routes(ObjectNode draft) {
        draft.put("stage", "ROUTINES");
        catalogue(draft, "routineChoices", data.routines(driver(draft)));
    }

    void legacyPreference(ObjectNode draft, String text) {
        var answer = WaPassengerTexts.APPROACH.answer(text);
        if (answer.isEmpty()) {
            return;
        }
        draft.put("approachMessages", answer.get().id().equals("YES"));
        routes(draft);
    }

    void selectRoutines(ObjectNode draft, String text) {
        ArrayNode chosen = select(text, draft.path("routineChoices"));
        if (chosen != null) {
            draft.set("selections", chosen);
            draft.put("dayIndex", 0).put("stage", "DAYS");
        }
    }

    void selectDays(ObjectNode draft, String text) {
        int index = draft.path("dayIndex").asInt();
        JsonNode routine = draft.path("selections").path(index);
        JsonNode available = routine.path("weekdays");
        JsonNode selected = draft.path("simpleSignup").asBoolean() && text.equals("todos os dias")
                ? available.deepCopy() : days(text);
        if (selected != null && subset(selected, available)) {
            ((ObjectNode) routine).set("selectedDays", selected);
            if (index + 1 < draft.path("selections").size()) {
                draft.put("dayIndex", index + 1);
            } else {
                pointOrReview(draft);
            }
        }
    }

    boolean optionalAnswer(WaInboxAccess.Context event, ObjectNode draft, String text, String stage) {
        if (!draft.path("simpleSignup").asBoolean() || !List.of("POINT", "REVIEW").contains(stage)) {
            return false;
        }
        if (text.equals("alterar dias") && !draft.path("selections").isEmpty()) {
            draft.put("dayIndex", 0).put("stage", "DAYS");
            return true;
        }
        if (List.of("avisos chegada", "avisos nenhum").contains(text)) {
            draft.put("approachMessages", text.equals("avisos chegada"));
            return true;
        }
        if (stage.equals("REVIEW")) {
            if (text.equals("sem referencia")) {
                draft.remove("reference");
                return true;
            }
            if (text.startsWith("referencia ")) {
                String raw = event.normalizedPayload().path("text").asText().strip();
                String value = clean(raw.replaceFirst("(?iu)^refer[eê]ncia[\\p{Z}\\s]+", ""), 160);
                if (value != null && !value.equals(raw)) {
                    draft.put("reference", value);
                    return true;
                }
            }
            return text.equals("pular");
        }
        return false;
    }

    private void pointOrReview(ObjectNode draft) {
        if (draft.path("routePoints").asBoolean()) {
            new WaPassengerPoints(data).open(draft);
            return;
        }
        if (draft.path("simpleSignup").asBoolean() && (draft.has("sourceEvent") || draft.has("commonId"))) {
            draft.put("stage", "REVIEW");
        } else {
            draft.put("stage", "POINT");
            catalogue(draft, "commonChoices", data.commons(driver(draft)));
        }
    }

    private static UUID driver(ObjectNode draft) {
        return UUID.fromString(draft.path("driverId").asText());
    }
}
