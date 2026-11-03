package com.vanrides.vanrides.whatsapp.attendance;

import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class WaAttendanceScope {
    private final JdbcTemplate jdbc;
    private final WaSettings settings;

    WaAttendanceScope(JdbcTemplate jdbc, WaSettings settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    boolean driver(UUID driver, String instance) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM wa.profile p JOIN wa.identity i ON i.id=p.identity_id
                  LEFT JOIN wa.test_cohort c ON c.id=i.test_cohort_id
                  WHERE p.identity_id=? AND p.role='DRIVER' AND p.status='ACTIVE' AND p.driver_enabled
                    AND p.notice_version IS NOT NULL AND (?=false OR c.environment=?)
                    AND EXISTS(SELECT 1 FROM wa.integration_inbox e JOIN wa.identity_alias a
                      ON a.id::text=e.payload_json->>'senderAliasId' AND a.identity_id=e.identity_id
                      WHERE e.identity_id=p.identity_id AND e.category='MESSAGE' AND e.provider='EVOLUTION'
                        AND e.instance_id=? AND a.instance_id=e.instance_id AND a.provider=e.provider))
                """, Boolean.class, driver, settings.cohortOnly(), settings.environment(), instance));
    }

    boolean pair(UUID driver, UUID passenger) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM wa.identity d JOIN wa.identity p ON p.id=?
                  JOIN wa.profile profile ON profile.identity_id=p.id
                  LEFT JOIN wa.test_cohort c ON c.id=p.test_cohort_id
                  WHERE d.id=? AND profile.status='ACTIVE' AND profile.role='PASSENGER'
                    AND profile.notice_version IS NOT NULL
                    AND ((d.test_cohort_id IS NULL AND p.test_cohort_id IS NULL AND ?=false)
                      OR (d.test_cohort_id=p.test_cohort_id AND c.environment=?)))
                """, Boolean.class, passenger, driver, settings.cohortOnly(), settings.environment()));
    }
}
