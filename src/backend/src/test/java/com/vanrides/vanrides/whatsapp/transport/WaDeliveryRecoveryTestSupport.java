package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceJobs;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.time.temporal.ChronoUnit;

final class WaDeliveryRecoveryTestSupport {
    final WaTransportTestSupport h;
    final WaAttendanceJobs jobs;
    final WaPrincipal job;
    WaDeliveryRecoveryTestSupport(WaTransportTestSupport h) { this(h, h.settings); }
    WaDeliveryRecoveryTestSupport(WaTransportTestSupport h, WaSettings configuration) {
        this.h = h;
        job = h.principal(WaPrincipal.Kind.SYSTEM_JOB, WaTransportTestSupport.INSTANCE, configuration.environment());
        jobs = h.proxy(new WaAttendanceJobs(h.jdbc, new WaTransportAccess(h.jdbc, configuration, h.clock), configuration, h.clock));
    }
    ObjectNode request(int limit, String cursor) {
        ObjectNode request = WaJson.object("{}").put("jobType", "HEALTH_RECONCILE")
            .put("windowKey", h.clock.now().truncatedTo(ChronoUnit.MINUTES).toString()).put("batchLimit", limit);
        if (cursor != null) request.put("cursor", cursor);
        return request;
    }
    ObjectNode tick(int limit, String cursor) { return jobs.tick(job, request(limit, cursor)); }
    static ObjectNode unknown(WaDeliveryConnectionTestSupport f, ObjectNode reservation) {
        return f.outbox.result(f.worker, WaJson.uuid(reservation, "outboxId"), WaJson.object("{}")
            .put("attemptToken", reservation.path("attemptToken").asText()).put("outcome", "UNKNOWN").put("failureCode", "TIMEOUT"));
    }
}
