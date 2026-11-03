package com.vanrides.vanrides.whatsapp.pairing;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Persistence primitives are called only inside the service transaction. */
final class WaPairingRepository {
    record Pairing(UUID id, String publicJwk, String jkt, String pkce, String platform, String appVersion,
                   String environment, String state, Instant expiresAt, UUID driverId,
                   Long approvalGeneration, int attempts, UUID deviceId) { }
    record Driver(UUID id, long generation, String role, String status, String name, boolean enabled,
                  boolean noticeAccepted, String cohortEnvironment) { }
    record Device(UUID id, UUID driverId, String jkt, String environment, long generation, String state) { }
    record Token(UUID id, UUID deviceId, String kind, UUID familyId, Instant expiresAt,
                 Instant consumedAt, Instant revokedAt) { }
    record TokenOwner(Token token, UUID driverId, String jkt, String environment) { }

    private final JdbcTemplate jdbc;
    WaPairingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void serialize(String namespace, String scope) {
        byte[] digest = WaCrypto.sha256((namespace + "\0" + scope).getBytes(StandardCharsets.UTF_8));
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)", Object.class, ByteBuffer.wrap(digest).getLong());
    }

    Pairing pairing(UUID id, boolean lock) {
        return one(jdbc.query("SELECT * FROM wa.pairing_challenge WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> pairing(rs), id));
    }

    Pairing pairingByCode(String hash, String environment) {
        return one(jdbc.query("SELECT * FROM wa.pairing_challenge WHERE code_hash=? AND environment=? FOR UPDATE",
                (rs, row) -> pairing(rs), hash, environment));
    }

    Driver lockDriver(UUID id) {
        // Human commands already hold profile before entering confirm().
        // Keep the same order for app requests and re-read after a blocker commits.
        if (jdbc.query("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), id).isEmpty()) return null;
        return one(jdbc.query("""
                SELECT a.driver_id,a.generation,p.role,p.status,p.display_name,p.driver_enabled,
                    p.notice_version IS NOT NULL AND p.notice_accepted_at IS NOT NULL notice_accepted,
                    c.environment cohort_environment
                FROM wa.driver_authorization a JOIN wa.profile p ON p.identity_id=a.driver_id
                JOIN wa.identity i ON i.id=a.driver_id
                LEFT JOIN wa.test_cohort c ON c.id=i.test_cohort_id
                WHERE a.driver_id=? FOR UPDATE OF a
                """, (rs, row) -> new Driver(rs.getObject("driver_id", UUID.class), rs.getLong("generation"),
                rs.getString("role"), rs.getString("status"), rs.getString("display_name"),
                rs.getBoolean("driver_enabled"), rs.getBoolean("notice_accepted"), rs.getString("cohort_environment")), id));
    }

    Device lockDevice(UUID id) {
        return one(jdbc.query("SELECT * FROM wa.driver_device WHERE id=? FOR UPDATE",
                (rs, row) -> new Device(rs.getObject("id", UUID.class), rs.getObject("driver_id", UUID.class),
                rs.getString("jkt"), rs.getString("environment"), rs.getLong("generation"), rs.getString("status")), id));
    }

    TokenOwner tokenOwner(String hash, String kind) {
        return one(jdbc.query("""
                SELECT t.*,d.driver_id,d.jkt,d.environment FROM wa.device_token t
                JOIN wa.driver_device d ON d.id=t.device_id WHERE t.token_hash=? AND t.kind=?
                """, (rs, row) -> new TokenOwner(token(rs), rs.getObject("driver_id", UUID.class),
                rs.getString("jkt"), rs.getString("environment")), hash, kind));
    }

    Token lockToken(UUID id) {
        return one(jdbc.query("SELECT * FROM wa.device_token WHERE id=? FOR UPDATE", (rs, row) -> token(rs), id));
    }

    boolean createPairing(UUID id, String publicJwk, String jkt, String challenge, String platform, String version,
                       String environment, String codeHash, Instant now, Instant expiry) {
        return jdbc.update("""
                INSERT INTO wa.pairing_challenge(id,public_jwk,jkt,pkce_challenge,platform,app_version,
                    environment,code_hash,created_at,expires_at)
                VALUES (?,?::jsonb,?,?,?,?,?,?,?,?) ON CONFLICT (code_hash) DO NOTHING
                """, id, publicJwk, jkt, challenge, platform, version, environment, codeHash, time(now), time(expiry)) == 1;
    }

    void approve(Pairing pairing, Driver driver) {
        jdbc.update("UPDATE wa.pairing_challenge SET state='APPROVED',driver_id=?,approval_generation=? WHERE id=?",
                driver.id(), driver.generation(), pairing.id());
    }

    void expire(Pairing pairing) {
        if (List.of("PENDING", "APPROVED").contains(pairing.state()))
            jdbc.update("UPDATE wa.pairing_challenge SET state='EXPIRED' WHERE id=?", pairing.id());
    }

    int failedPkce(Pairing pairing) {
        int attempts = Math.min(5, pairing.attempts() + 1);
        jdbc.update("UPDATE wa.pairing_challenge SET failed_attempts=?,state=? WHERE id=?",
                attempts, attempts == 5 ? "REVOKED" : pairing.state(), pairing.id());
        return attempts;
    }

    boolean hasForeignActiveKey(String jkt, UUID driver) {
        return count("SELECT count(*) FROM wa.driver_device WHERE jkt=? AND driver_id<>? AND status='ACTIVE'", jkt, driver) > 0;
    }

    boolean hasForeignEnvironment(UUID driver, String environment) {
        return count("SELECT count(*) FROM wa.driver_device WHERE driver_id=? AND environment<>? AND status='ACTIVE'",
                driver, environment) > 0;
    }

    long advanceGeneration(UUID driver) {
        Long result = jdbc.queryForObject("UPDATE wa.driver_authorization SET generation=generation+1 WHERE driver_id=? RETURNING generation",
                Long.class, driver);
        if (result == null) throw WaProblem.unauthorized();
        return result;
    }

    void replaceDevices(UUID driver, Instant now) {
        List<UUID> active = jdbc.query("SELECT id FROM wa.driver_device WHERE driver_id=? AND status='ACTIVE' FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class), driver);
        for (UUID id : active) revokeDevice(id, now);
    }

    void revokeDevice(UUID device, Instant now) {
        jdbc.update("UPDATE wa.driver_device SET status='REVOKED',revoked_at=COALESCE(revoked_at,?) WHERE id=?", time(now), device);
        jdbc.update("UPDATE wa.device_token SET revoked_at=COALESCE(revoked_at,?) WHERE device_id=?", time(now), device);
        jdbc.update("""
                UPDATE wa.tracking_session SET state='STOP_REQUESTED',stop_requested_at=COALESCE(stop_requested_at,?),
                    lease_expires_at=LEAST(lease_expires_at,?),lease_token_hash=NULL,version=version+1
                WHERE device_id=? AND state IN ('STARTING','ACTIVE','DEGRADED','STOP_REQUESTED')
                """, time(now), time(now), device);
    }

    void invalidateApprovals(UUID driver, long generation, UUID keepPairing) {
        jdbc.update("""
                UPDATE wa.pairing_challenge SET state='REVOKED'
                WHERE driver_id=? AND state='APPROVED' AND approval_generation<? AND id<>?
                """, driver, generation, keepPairing);
    }

    UUID createDevice(Pairing pairing, long generation, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO wa.driver_device(id,driver_id,jkt,public_jwk,generation,environment,platform,app_version,created_at)
                VALUES (?,?,?,?::jsonb,?,?,?,?,?)
                """, id, pairing.driverId(), pairing.jkt(), pairing.publicJwk(), generation, pairing.environment(),
                pairing.platform(), pairing.appVersion(), time(now));
        jdbc.update("UPDATE wa.pairing_challenge SET state='REDEEMED',device_id=? WHERE id=?", id, pairing.id());
        return id;
    }

    void createToken(UUID id, UUID device, String raw, String kind, UUID family, Instant now, Instant expiry) {
        jdbc.update("""
                INSERT INTO wa.device_token(id,device_id,token_hash,kind,family_id,created_at,expires_at)
                VALUES (?,?,?,?,?,?,?)
                """, id, device, WaCrypto.digest(raw), kind, family, time(now), time(expiry));
    }

    void revokeAccess(UUID device, Instant now) {
        jdbc.update("UPDATE wa.device_token SET revoked_at=COALESCE(revoked_at,?) WHERE device_id=? AND kind='ACCESS'",
                time(now), device);
    }

    void rotate(Token old, UUID next, Instant now) {
        jdbc.update("UPDATE wa.device_token SET consumed_at=?,rotated_to=? WHERE id=?", time(now), next, old.id());
    }

    boolean responseTokensUsable(UUID device, String access, String refresh, Instant now) {
        return count("""
                SELECT count(*) FROM wa.device_token WHERE device_id=? AND expires_at>? AND revoked_at IS NULL
                    AND consumed_at IS NULL AND ((kind='ACCESS' AND token_hash=?) OR (kind='REFRESH' AND token_hash=?))
                """, device, time(now), WaCrypto.digest(access), WaCrypto.digest(refresh)) == 2;
    }

    UUID activeRun(UUID driver) {
        return one(jdbc.query("""
                SELECT id FROM wa.operation_run WHERE driver_id=?
                    AND state IN ('STARTING','START_FAILED','TRACKING','DEGRADED')
                """, (rs, row) -> rs.getObject("id", UUID.class), driver));
    }

    void audit(UUID driver, UUID resource, String action, Instant now) {
        jdbc.update("""
                INSERT INTO wa.audit_event(id,actor_identity_id,actor_kind,action,resource_id,correlation_id,created_at)
                VALUES (?,?,'DEVICE',?,?,?,?)
                """, UUID.randomUUID(), driver, action, resource, UUID.randomUUID().toString(), time(now));
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
    private static Timestamp time(Instant instant) { return Timestamp.from(instant); }
    private static <T> T one(List<T> values) { return values.size() == 1 ? values.get(0) : null; }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name); return value == null ? null : value.toInstant();
    }
    private static Token token(ResultSet rs) throws SQLException {
        return new Token(rs.getObject("id", UUID.class), rs.getObject("device_id", UUID.class), rs.getString("kind"),
                rs.getObject("family_id", UUID.class), instant(rs, "expires_at"), instant(rs, "consumed_at"), instant(rs, "revoked_at"));
    }
    private static Pairing pairing(ResultSet rs) throws SQLException {
        return new Pairing(rs.getObject("id", UUID.class), rs.getString("public_jwk"), rs.getString("jkt"),
                rs.getString("pkce_challenge"), rs.getString("platform"), rs.getString("app_version"),
                rs.getString("environment"), rs.getString("state"), instant(rs, "expires_at"),
                rs.getObject("driver_id", UUID.class), rs.getObject("approval_generation", Long.class),
                rs.getInt("failed_attempts"), rs.getObject("device_id", UUID.class));
    }
}
