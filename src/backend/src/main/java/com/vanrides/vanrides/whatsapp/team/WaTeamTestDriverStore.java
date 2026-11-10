package com.vanrides.vanrides.whatsapp.team;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.commands.WaOnboardingCommands;
import com.vanrides.vanrides.whatsapp.transport.WaEventService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class WaTeamTestDriverStore {
    public static final String NAME = "Motorista de teste da equipe";
    static final String ROUTINE = "Rotina de teste da equipe";

    public record Invite(UUID id, String hash, Instant expires, boolean usable) { }

    private final JdbcTemplate jdbc;

    public WaTeamTestDriverStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID find(UUID cohort) {
        var rows = jdbc.query("SELECT driver_id FROM wa.team_test_driver WHERE test_cohort_id=?",
            (rs, i) -> rs.getObject(1, UUID.class), cohort);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public UUID ensure(UUID cohort, String instance, long githubActor, Instant now) {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", (rs, i) -> 0,
            WaEventService.lockKey("TEAM_TEST_DRIVER", instance, cohort.toString()));
        UUID existing = find(cohort);
        if (existing != null) return existing;
        UUID driver = UUID.randomUUID(), routine = UUID.randomUUID();
        Timestamp at = Timestamp.from(now);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id,created_at) VALUES (?,?,?)", driver, cohort, at);
        jdbc.update("""
            INSERT INTO wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at,driver_enabled,version,created_at,updated_at)
            VALUES (?,'DRIVER','ACTIVE',?,?,?,true,1,?,?)
            """, driver, NAME, WaOnboardingCommands.NOTICE_VERSION, at, at, at);
        jdbc.update("INSERT INTO wa.driver_authorization(driver_id,generation) VALUES (?,0)", driver);
        jdbc.update("""
            INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,destination_label,
              destination_latitude,destination_longitude,state,policy_version,version,created_at,updated_at)
            VALUES (?,?,?,'OUTBOUND','{1,2,3,4,5,6,7}','07:30','America/Sao_Paulo','Destino fictício de teste',
              -29.6783,-51.1309,'ACTIVE','driver-setup-v1',1,?,?)
            """, routine, driver, ROUTINE, at, at);
        jdbc.update("""
            INSERT INTO wa.team_test_driver(driver_id,test_cohort_id,instance_id,routine_id,created_by_github_id,created_at)
            VALUES (?,?,?,?,?,?)
            """, driver, cohort, instance, routine, githubActor, at);
        return driver;
    }

    public void insertInvite(UUID driver, UUID invite, String hash, Instant expires, long githubActor, Instant now) {
        jdbc.update("""
            INSERT INTO wa.driver_invite(id,driver_id,code_hash,expires_at,max_uses,created_at,created_by_github_id)
            VALUES (?,?,?,?,1,?,?)
            """, invite, driver, hash, Timestamp.from(expires), Timestamp.from(now), githubActor);
    }

    public Invite invite(UUID driver, UUID invite, Instant now) {
        var rows = jdbc.query("""
            SELECT code_hash,expires_at,revoked_at IS NULL AND use_count<max_uses AND expires_at>?
            FROM wa.driver_invite WHERE id=? AND driver_id=?
            """, (rs, i) -> new Invite(invite, rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getBoolean(3)),
            Timestamp.from(now), invite, driver);
        if (rows.size() != 1) throw WaProblem.missing();
        return rows.get(0);
    }
}
