package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Activation and driver corrections use the same occurrence fence as personal attendance. */
@Service
public class WaRunPreparation {
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final WaRunRoster roster;
    public WaRunPreparation(JdbcTemplate jdbc, WaClock clock, WaSettings settings) {
        this.jdbc = jdbc; this.clock = clock; roster = new WaRunRoster(jdbc, clock, settings);
    }
    @Transactional public String rosterDigest(UUID driver, UUID occurrence) { return digest(startReview(driver, occurrence)); }
    /** Transport already owns a profile SHARE fence; no lock upgrades while it owns the outbox. */
    @Transactional public String rosterDigestForDelivery(UUID driver, UUID occurrence) { return digest(roster.review(driver, occurrence, true, true)); }
    ObjectNode review(UUID driver, UUID occurrence) { return roster.review(driver, occurrence, false); }
    ObjectNode startReview(UUID driver, UUID occurrence) { return roster.review(driver, occurrence, true); }
    static String digest(ObjectNode review) { return WaCrypto.digest(WaJson.canonical(review)); }

    @Transactional public UUID prepare(UUID driver, UUID occurrence, String expectedDigest) {
        ObjectNode review = startReview(driver, occurrence);
        if (!WaCrypto.same(digest(review), expectedDigest)) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        ObjectNode routine = (ObjectNode) review.path("routine"), day = (ObjectNode) review.path("occurrence");
        UUID run = UUID.randomUUID();
        var existing = jdbc.query("SELECT id FROM wa.operation_run WHERE occurrence_id=? AND driver_id=? FOR UPDATE",
            (rs, row) -> rs.getObject(1, UUID.class), occurrence, driver);
        if (!existing.isEmpty()) run = existing.get(0);
        ObjectNode snapshot = WaJson.MAPPER.createObjectNode().put("rosterDigest", expectedDigest);
        snapshot.set("schedule", day.path("schedule_snapshot").deepCopy());
        snapshot.set("routine", routine.deepCopy());
        var participants = snapshot.putArray("participants");
        ObjectNode names = snapshot.putObject("passengerNames");
        for (JsonNode member : review.path("members")) if (member.path("included").asBoolean()) {
            names.put(member.path("passenger").asText(), member.path("name").asText()); participants.add(member.deepCopy());
        }
        Timestamp now = Timestamp.from(clock.now());
        jdbc.update("""
            INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_json,snapshot_at,started_at,created_at)
            VALUES (?,?,?,?,?::date,'STARTING',?,?::jsonb,?,?,?) ON CONFLICT(occurrence_id) DO UPDATE SET
            state='STARTING',policy_version=EXCLUDED.policy_version,snapshot_json=EXCLUDED.snapshot_json,snapshot_at=EXCLUDED.snapshot_at,
            started_at=EXCLUDED.started_at,version=wa.operation_run.version+1
            """, run, occurrence, UUID.fromString(routine.path("id").asText()), driver, day.path("local_date").asText(),
            routine.path("policy_version").asText(), snapshot.toString(), now, now, now);
        for (JsonNode member : review.path("members")) {
            if (!member.path("included").asBoolean()) continue;
            String physical = member.path("commonPoint").isNull()
                ? "pin:" + member.path("latitude").asText() + ":" + member.path("longitude").asText() : member.path("commonPoint").asText();
            jdbc.update("""
                INSERT INTO wa.run_stop(id,run_id,driver_id,passenger_id,physical_stop_key,stop_order,pickup_point_id,pickup_version,
                  latitude_snapshot,longitude_snapshot,reference_snapshot,attendance_snapshot)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), run, driver, UUID.fromString(member.path("passenger").asText()),
                UUID.nameUUIDFromBytes(physical.getBytes(StandardCharsets.UTF_8)), member.path("order").asInt(),
                UUID.fromString(member.path("point").asText()), member.path("pointVersion").asLong(),
                member.path("latitude").asDouble(), member.path("longitude").asDouble(),
                member.path("reference").isNull() ? null : member.path("reference").asText(), member.path("attendance").asText());
        }
        return run;
    }
}
