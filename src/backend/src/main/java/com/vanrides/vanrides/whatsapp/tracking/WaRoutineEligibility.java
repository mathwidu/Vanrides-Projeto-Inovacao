package com.vanrides.vanrides.whatsapp.tracking;

import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** One eligibility rule for production of approach/arrival and delayed approach delivery. */
public final class WaRoutineEligibility {
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    public WaRoutineEligibility(JdbcTemplate jdbc, WaSettings settings) { this.jdbc=jdbc; this.settings=settings; }

    public boolean reviewed(UUID routine) {
        if (!settings.mvpRehearsalRoutines()) return settings.approachReviewed(routine);
        // The opt-in replaces a manually copied UUID list only inside the validated team rehearsal.
        // It is not a global geographic review or permission to enable a production environment.
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.routine r
              JOIN wa.profile driver ON driver.identity_id=r.driver_id
              JOIN wa.identity person ON person.id=driver.identity_id
              JOIN wa.test_cohort cohort ON cohort.id=person.test_cohort_id
              JOIN wa.driver_authorization auth ON auth.driver_id=driver.identity_id
              JOIN wa.driver_device device ON device.driver_id=driver.identity_id
                AND device.generation=auth.generation AND device.status='ACTIVE'
              WHERE r.id=? AND r.state='ACTIVE' AND driver.role='DRIVER' AND driver.status='ACTIVE'
                AND driver.driver_enabled AND driver.notice_version='wa-mvp-v1'
                AND cohort.id=? AND cohort.environment=? AND NOT cohort.synthetic AND device.environment=?
                AND NOT EXISTS(SELECT 1 FROM wa.conversation_state conversation
                  WHERE conversation.identity_id=driver.identity_id AND conversation.stage='PAUSED'))
            """, Boolean.class, routine, settings.mvpRehearsalCohort(), settings.environment(), settings.environment()));
    }
}
