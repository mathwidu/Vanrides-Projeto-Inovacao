package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import java.util.Set;

/** Closed payload shapes; hashes are read from the authenticated inbox, never accepted over HTTP. */
final class WaCommandPayloads {
    private WaCommandPayloads() { }
    static void validate(String type, ObjectNode payload) {
        switch (type) {
            case "ADVANCE_DRIVER_SETUP", "ADVANCE_PASSENGER_SETUP", "ADVANCE_ATTENDANCE", "ADVANCE_RUN_SETUP", "GET_DRIVER_LOCATION" -> WaJson.fields(payload);
            case "REQUEST_START", "REQUEST_RESUME" -> refs(payload, "occurrenceRef", "confirmationRef");
            case "SET_OPERATIONAL_ATTENDANCE" -> refs(payload, "occurrenceRef", "passengerRef", "confirmationRef");
            case "SET_ATTENDANCE" -> {
                WaJson.fields(payload, "questionRef", "choice");
                WaAttendanceQuestions.question(WaJson.text(payload, "questionRef", 8, 160));
                if (!Set.of("GOING", "NOT_GOING").contains(WaJson.text(payload, "choice", 1, 16))) throw WaProblem.invalid();
            }
            case "CONFIGURE_ROUTINE", "SET_STOP_ORDER", "SET_ROUTINE_STATE" -> refs(payload, "routineRef", "confirmationRef");
            case "CREATE_INVITE", "REQUEST_LINK" -> refs(payload, "confirmationRef");
            case "APPROVE_LINK" -> refs(payload, "linkRef", "pickupRef", "confirmationRef");
            case "CLOSE_LINK" -> refs(payload, "linkRef", "confirmationRef");
            case "PROPOSE_PICKUP" -> { refs(payload, "sourceMessageId", "confirmationRef"); WaJson.uuid(payload, "sourceMessageId"); }
            case "APPROVE_PICKUP" -> refs(payload, "pickupRef", "confirmationRef");
            case "REVOKE_INVITE" -> refs(payload, "inviteRef", "confirmationRef");
            case "CANCEL_OCCURRENCE" -> refs(payload, "occurrenceRef", "confirmationRef");
            case "GET_SUMMARY" -> refs(payload, "summaryRef");
            case "START_ONBOARDING" -> WaJson.fields(payload);
            case "SET_PROFILE_NAME" -> { WaJson.fields(payload, "name"); WaJson.text(payload, "name", 1, 80); }
            case "ACCEPT_NOTICE" -> {
                WaJson.fields(payload, "noticeVersion");
                if (!WaJson.text(payload, "noticeVersion", 1, 40).matches("[A-Za-z0-9._-]+")) throw WaProblem.invalid();
            }
            case "CLAIM_DRIVER_INVITE" -> {
                if (payload.isEmpty()) WaJson.fields(payload);
                else { WaJson.fields(payload, "inviteCode"); WaDpopVerifier.canonical32(WaJson.text(payload, "inviteCode", 43, 43)); }
            }
            case "CONFIRM_PAIRING" -> {
                if (payload.isEmpty()) WaJson.fields(payload);
                else {
                    WaJson.fields(payload, "userCode");
                    if (!WaJson.text(payload, "userCode", 8, 8).matches("[BCDFGHJKMNPQRSTVWXYZ]{8}")) throw WaProblem.invalid();
                }
            }
            case "RESPOND_TO_MESSAGE" -> {
                WaJson.fields(payload, "responseCode");
                if (!WaConversationPolicy.RESPONSES.contains(WaJson.text(payload, "responseCode", 1, 32))) throw WaProblem.invalid();
            }
            case "REQUEST_SUPPORT" -> {
                WaJson.fields(payload, "reason", "detail");
                if (!Set.of("HELP", "PAIRING", "LOCATION", "OTHER").contains(WaJson.text(payload, "reason", 1, 16))) throw WaProblem.invalid();
                WaJson.text(payload, "detail", 1, 240);
            }
            default -> throw WaProblem.invalid();
        }
    }
    private static void refs(ObjectNode payload, String... fields) {
        WaJson.fields(payload, fields);
        for (String field : fields) if (!WaJson.text(payload, field, 8, 160).matches("[A-Za-z0-9_-]+")) throw WaProblem.invalid();
    }
    static String codeHash(ObjectNode payload, WaInboxAccess.Context event, String rawField, String hashField,
                           String purpose, WaCrypto crypto) {
        String hash = event.normalizedPayload().path(hashField).asText();
        if (!hash.matches("[0-9a-f]{64}")) return null;
        if (!payload.isEmpty() && !WaCrypto.same(hash, crypto.secretHash(purpose, payload.path(rawField).asText()))) return null;
        return hash;
    }
}
