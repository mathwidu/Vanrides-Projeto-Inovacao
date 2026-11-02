package com.vanrides.vanrides.whatsapp.attendance;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.transport.WaTransportAccess;
import com.vanrides.vanrides.whatsapp.transport.WaDeliveryMaintenance;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Jobs have durable pages and service provenance, never a manufactured human inbox. */
@Service
public class WaAttendanceJobs {
    private final WaTransportAccess access;
    private final WaSettings settings;
    private final WaAttendanceJobPages pages;
    private final WaAttendanceCandidates candidates;
    private final WaAttendanceMaterializer materializer;
    private final WaAttendanceReminders reminders;
    private final WaDeliveryMaintenance delivery;

    public WaAttendanceJobs(JdbcTemplate jdbc, WaTransportAccess access, WaSettings settings, WaClock clock) {
        this.access = access;
        this.settings = settings;
        pages = new WaAttendanceJobPages(jdbc, settings, clock);
        candidates = new WaAttendanceCandidates(jdbc, settings);
        materializer = new WaAttendanceMaterializer(jdbc, settings, clock);
        reminders = new WaAttendanceReminders(jdbc, settings, clock);
        delivery = new WaDeliveryMaintenance(jdbc, settings, clock);
    }

    @Transactional
    public ObjectNode tick(WaPrincipal actor, ObjectNode request) {
        if (!settings.enabled() && !request.path("jobType").asText().equals("HEALTH_RECONCILE")) {
            throw WaProblem.denied();
        }
        var grant = access.lock(actor, WaPrincipal.Kind.SYSTEM_JOB);
        if (request.has("cursor")) {
            WaJson.fields(request, "jobType", "windowKey", "batchLimit", "cursor");
        } else {
            WaJson.fields(request, "jobType", "windowKey", "batchLimit");
        }
        String type = WaJson.text(request, "jobType", 1, 40);
        if (!Set.of("MATERIALIZE_OCCURRENCES", "DUE_REMINDERS", "HEALTH_RECONCILE").contains(type)) {
            throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        }
        String window = WaJson.text(request, "windowKey", 1, 100);
        String cursor = request.has("cursor") ? WaJson.text(request, "cursor", 8, 160) : null;
        var requestedLimit = request.get("batchLimit");
        if (requestedLimit == null || !requestedLimit.isIntegralNumber() || !requestedLimit.canConvertToInt()
                || requestedLimit.intValue() < 1 || requestedLimit.intValue() > 100) {
            throw WaProblem.invalid();
        }
        int limit = requestedLimit.intValue();
        if (type.equals("HEALTH_RECONCILE")) delivery.lock(grant.instance());
        var page = pages.begin(type, window, cursor, limit, grant.instance(), grant.credentialId());
        access.revalidate(grant);
        if (page.response() != null) {
            return page.response();
        }
        List<Map<String, Object>> work = type.equals("HEALTH_RECONCILE")
                ? delivery.candidates(grant.instance(), page.after(), limit + 1) : type.equals("MATERIALIZE_OCCURRENCES")
                ? candidates.materialize(grant.instance(), page.window(), page.after(), limit + 1)
                : reminders.candidates(grant.instance(), page.after(), limit + 1);
        int processed = Math.min(limit, work.size());
        String last = page.after();
        for (int index = 0; index < processed; index++) {
            access.revalidate(grant);
            Map<String, Object> item = work.get(index);
            if (type.equals("HEALTH_RECONCILE")) {
                delivery.apply(java.util.UUID.fromString((String) item.get("work_key")), grant.instance());
            } else if (type.equals("MATERIALIZE_OCCURRENCES")) {
                materializer.apply(item, grant.instance(), grant.credentialId());
            } else {
                reminders.apply(item, grant.instance());
            }
            access.revalidate(grant);
            last = (String) item.get("work_key");
        }
        access.revalidate(grant);
        ObjectNode response = pages.finish(page, type, grant.instance(), processed, last, work.size() > limit);
        access.revalidate(grant);
        return response;
    }
}
