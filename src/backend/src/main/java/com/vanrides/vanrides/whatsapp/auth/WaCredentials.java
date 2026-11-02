package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public final class WaCredentials {
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaClock clock;
    public WaCredentials(JdbcTemplate jdbc, WaSettings settings, WaClock clock) {
        this.jdbc = jdbc; this.settings = settings; this.clock = clock;
    }
    public WaPrincipal service(String token) {
        WaDpopVerifier.canonical32(token);
        List<WaPrincipal> found = jdbc.query("SELECT id,principal,allowed_instance_id FROM wa.service_credential WHERE token_hash=? AND environment=? AND expires_at>? AND revoked_at IS NULL",
            (rs, i) -> new WaPrincipal(WaPrincipal.Kind.valueOf(rs.getString("principal")), rs.getObject("id", UUID.class),
                null, null, rs.getString("allowed_instance_id"), null, 0, false),
            WaCrypto.digest(token), settings.environment(), Timestamp.from(clock.now()));
        if (found.size() != 1) throw WaProblem.unauthorized();
        return found.get(0);
    }
    public WaPrincipal device(String token, boolean allowRevokedForRevokeOnly) {
        WaDpopVerifier.canonical32(token);
        List<WaPrincipal> found = jdbc.query("""
            SELECT t.id,t.revoked_at token_revoked,d.id device_id,d.driver_id,d.jkt,d.generation,d.status,
                a.generation current_generation,p.status profile_status,p.driver_enabled
            FROM wa.device_token t JOIN wa.driver_device d ON d.id=t.device_id
            JOIN wa.driver_authorization a ON a.driver_id=d.driver_id
            JOIN wa.profile p ON p.identity_id=d.driver_id
            WHERE t.token_hash=? AND t.kind='ACCESS' AND t.expires_at>? AND d.environment=?
            """, (rs, i) -> {
                boolean revoked = rs.getTimestamp("token_revoked") != null || !"ACTIVE".equals(rs.getString("status"))
                    || rs.getLong("generation") != rs.getLong("current_generation")
                    || !"ACTIVE".equals(rs.getString("profile_status")) || !rs.getBoolean("driver_enabled");
                return new WaPrincipal(WaPrincipal.Kind.DEVICE, rs.getObject("id", UUID.class), rs.getObject("device_id", UUID.class),
                    rs.getObject("driver_id", UUID.class), null, rs.getString("jkt"), rs.getLong("generation"), revoked);
            }, WaCrypto.digest(token), Timestamp.from(clock.now()), settings.environment());
        if (found.size() != 1 || found.get(0).revoked() && !allowRevokedForRevokeOnly) throw WaProblem.unauthorized();
        return found.get(0);
    }
}
