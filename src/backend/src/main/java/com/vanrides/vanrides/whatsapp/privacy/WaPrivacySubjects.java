package com.vanrides.vanrides.whatsapp.privacy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Locks the subject before closure, credential revocation or erasure. */
final class WaPrivacySubjects {
    private final JdbcTemplate jdbc;
    private final WaPrivacyPolicy policy;
    WaPrivacySubjects(JdbcTemplate jdbc, WaPrivacyPolicy policy) { this.jdbc = jdbc; this.policy = policy; }

    boolean lock(WaPrivacyService.Scope scope, UUID subject) {
        return !jdbc.queryForList("""
            SELECT p.identity_id FROM wa.profile p JOIN wa.identity i ON i.id=p.identity_id
            JOIN wa.test_cohort c ON c.id=i.test_cohort_id
            WHERE p.identity_id=? AND c.environment=? AND (?=false OR c.synthetic)
              AND EXISTS(SELECT 1 FROM wa.identity_alias a WHERE a.identity_id=i.id AND a.instance_id=?)
              AND NOT EXISTS(SELECT 1 FROM wa.identity_alias a WHERE a.identity_id=i.id AND a.instance_id IS DISTINCT FROM ?)
            FOR UPDATE OF p
            """, subject, scope.environment(), policy.syntheticOnly(), scope.instanceId(), scope.instanceId()).isEmpty();
    }

    boolean active(UUID subject) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT wa.privacy_has_active_reference(?)", Boolean.class, subject));
    }

    boolean lockCopyOwners(UUID subject) {
        String owners = """
            SELECT driver_id FROM wa.driver_passenger_link WHERE passenger_id=?
            UNION SELECT driver_id FROM wa.run_stop WHERE passenger_id=? UNION SELECT ?::uuid
            """;
        int expected = jdbc.queryForObject("SELECT count(*) FROM wa.profile WHERE identity_id IN (" + owners + ")",
            Integer.class, subject, subject, subject);
        return jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id IN (" + owners
            + ") ORDER BY identity_id FOR UPDATE SKIP LOCKED", subject, subject, subject).size() == expected;
    }

    void close(WaPrivacyService.Scope scope, UUID subject, Instant now) {
        Timestamp time = Timestamp.from(now);
        jdbc.update("""
            INSERT INTO wa.privacy_subject(subject_id,environment,instance_id,closed_at,erase_after)
            VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING
            """, subject, scope.environment(), scope.instanceId(), time, Timestamp.from(now.plusSeconds(30L * 86400)));
        jdbc.update("""
            UPDATE wa.profile SET status='CLOSED',driver_enabled=false,closed_at=COALESCE(closed_at,?),
              updated_at=?,version=version+1 WHERE identity_id=? AND closed_at IS NULL
            """, time, time, subject);
        jdbc.update("UPDATE wa.routine_member SET state='REMOVED',version=version+1 WHERE (passenger_id=? OR driver_id=?) AND state='ACTIVE'", subject, subject);
        jdbc.update("""
            UPDATE wa.driver_passenger_link SET state='CLOSED',valid_until=GREATEST(valid_from,?),version=version+1
            WHERE (passenger_id=? OR driver_id=?) AND state IN ('REQUESTED','APPROVED')
            """, time, subject, subject);
        jdbc.update("""
            UPDATE wa.pickup_point SET state='RETIRED',valid_until=COALESCE(valid_until,GREATEST(valid_from,?)),version=version+1
            WHERE (passenger_id=? OR driver_id=?) AND state IN ('PROPOSED','APPROVED')
            """, time, subject, subject);
        jdbc.update("UPDATE wa.routine SET state='CLOSED',updated_at=?,version=version+1 WHERE driver_id=? AND state<>'CLOSED'", time, subject);
        jdbc.update("UPDATE wa.common_pickup_point SET state='RETIRED',version=version+1 WHERE driver_id=? AND state<>'RETIRED'", subject);
        jdbc.update("UPDATE wa.driver_device SET status='REVOKED',revoked_at=? WHERE driver_id=? AND status='ACTIVE'", time, subject);
        jdbc.update("UPDATE wa.device_token SET revoked_at=COALESCE(revoked_at,?) WHERE device_id IN (SELECT id FROM wa.driver_device WHERE driver_id=?)", time, subject);
        jdbc.update("UPDATE wa.start_intent SET state='REVOKED' WHERE driver_id=? AND state='PENDING'", subject);
        jdbc.update("UPDATE wa.pairing_challenge SET state='REVOKED' WHERE driver_id=? AND state IN ('PENDING','APPROVED')", subject);
        jdbc.update("UPDATE wa.driver_bootstrap_invite SET revoked_at=COALESCE(revoked_at,?) WHERE identity_id=?", time, subject);
        jdbc.update("UPDATE wa.driver_invite SET revoked_at=COALESCE(revoked_at,?) WHERE driver_id=? OR onboarding_identity_id=?", time, subject, subject);
        jdbc.update("UPDATE wa.attendance_question SET state='CLOSED',version=version+1 WHERE (passenger_id=? OR driver_id=?) AND state<>'CLOSED'", subject, subject);
    }
}
