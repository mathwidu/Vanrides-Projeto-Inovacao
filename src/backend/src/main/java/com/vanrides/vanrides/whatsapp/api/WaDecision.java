package com.vanrides.vanrides.whatsapp.api;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Return expected denials from a transaction when its failed-attempt counter must commit. */
public record WaDecision(ObjectNode body, WaProblem.Code error) {
    public static WaDecision ok(Object value) { return new WaDecision(WaJson.object(WaJson.write(value)), null); }
    public static WaDecision error(WaProblem.Code code) { return new WaDecision(null, code); }
    /** Call at the HTTP boundary, after the transactional service has returned. */
    public ObjectNode requireSuccess() {
        if (error != null) throw new WaProblem(error);
        return body;
    }
}
