package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.vanrides.vanrides.whatsapp.operator.WaOperatorService.*;

@Component
public final class WaOperatorRevocation {
    private record Device(UUID owner, long generation, String state) { }
    private final JdbcTemplate jdbc;
    public WaOperatorRevocation(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    ObjectNode revoke(WaOperatorRequest request, Actor actor) {
        requireCohort(jdbc, actor);
        UUID deviceId = request.uuid("deviceId");
        List<UUID> owners = jdbc.query("""
            SELECT d.driver_id FROM wa.driver_device d JOIN wa.identity i ON i.id=d.driver_id
            WHERE d.id=? AND d.environment=? AND i.test_cohort_id=?
            """, (rs, row) -> rs.getObject(1, UUID.class), deviceId, actor.environment(), actor.batchId());
        if (owners.size() != 1) throw new WaOperatorFailure();
        UUID driver = owners.get(0);
        // Same order as pairing/refresh: profile -> authorization -> device -> tokens.
        List<UUID> profiles = jdbc.query("SELECT identity_id FROM wa.profile WHERE identity_id=? AND role='DRIVER' FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), driver);
        List<Long> generations = jdbc.query("SELECT generation FROM wa.driver_authorization WHERE driver_id=? FOR UPDATE",
                (rs, row) -> rs.getLong(1), driver);
        if (profiles.size() != 1 || generations.size() != 1) throw new WaOperatorFailure();
        List<Device> devices = jdbc.query("""
            SELECT driver_id,generation,status FROM wa.driver_device
            WHERE id=? AND environment=? FOR UPDATE
            """, (rs, row) -> new Device(rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3)), deviceId, actor.environment());
        if (devices.size() != 1 || !driver.equals(devices.get(0).owner())) throw new WaOperatorFailure();
        Device device = devices.get(0);
        jdbc.update("UPDATE wa.driver_device SET status='REVOKED',revoked_at=COALESCE(revoked_at,?) WHERE id=?", time(actor.now()), deviceId);
        jdbc.update("UPDATE wa.device_token SET revoked_at=COALESCE(revoked_at,?) WHERE device_id=?", time(actor.now()), deviceId);
        jdbc.update("""
            UPDATE wa.tracking_session SET state='STOP_REQUESTED',stop_requested_at=COALESCE(stop_requested_at,?),
                lease_expires_at=LEAST(lease_expires_at,?),lease_token_hash=NULL,version=version+1
            WHERE device_id=? AND state IN ('STARTING','ACTIVE','DEGRADED','STOP_REQUESTED')
            """, time(actor.now()), time(actor.now()), deviceId);
        if ("ACTIVE".equals(device.state()) && device.generation() == generations.get(0)) {
            jdbc.update("UPDATE wa.driver_authorization SET generation=generation+1 WHERE driver_id=?", driver);
            jdbc.update("UPDATE wa.pairing_challenge SET state='REVOKED' WHERE driver_id=? AND state='APPROVED'", driver);
        }
        return object().put("resourceId", deviceId.toString()).put("deviceId", deviceId.toString()).put("state", "REVOKED");
    }
}
