package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.util.UUID;

/** Correcting a pickup selects its routine before any domain mutation. */
final class WaPassengerEdit {
    private final WaPassengerData data;
    WaPassengerEdit(WaPassengerData data) { this.data = data; }

    ObjectNode start(WaProfileState actor) {
        var links = data.links(actor.identityId(), false);
        if (links.size()!=1) throw WaProblem.denied();
        ObjectNode draft = WaPassengerWizard.baseLink(links.path(0)).put("action", "PROPOSE_PICKUP")
                .put("routePoints", true).put("editingPickup", true).put("simpleSignup", true).put("stage", "EDIT_ROUTINE");
        var preferences = data.jdbc.queryForObject("SELECT preferences_json::text FROM wa.profile WHERE identity_id=?",
                (rs,row)->WaJson.object(rs.getString(1)), actor.identityId());
        draft.put("approachMessages", preferences.path("APPROACH_MESSAGES").asBoolean());
        var choices = new WaPassengerReviewStore(data).selections(UUID.fromString(draft.path("linkId").asText()), null);
        WaPassengerAnswers.catalogue(draft, "editChoices", choices);
        if (choices.size()==1) choose(draft, "1");
        return draft;
    }

    void revise(ObjectNode draft) {
        draft.set("editChoices", draft.path("selections").deepCopy());
        draft.put("stage", "EDIT_POINT_SELECTION");
        if (draft.path("selections").size()==1) choose(draft, "1");
    }

    void choose(ObjectNode draft, String text) {
        if (!text.matches("[1-8]")) return;
        int index = Integer.parseInt(text)-1;
        if (index>=draft.path("editChoices").size()) return;
        if (draft.path("stage").asText().equals("EDIT_ROUTINE")) {
            ObjectNode selected = draft.path("editChoices").path(index).deepCopy();
            selected.remove("pickup");
            draft.putArray("selections").add(selected);
        } else ((ObjectNode)draft.path("selections").path(index)).remove("pickup");
        new WaPassengerPoints(data).open(draft);
    }
}
