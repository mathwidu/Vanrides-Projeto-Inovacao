package com.vanrides.vanrides.whatsapp.attendance;

import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** Stable keyset pages select unfinished work, so the next minute can restart safely. */
final class WaAttendanceCandidates {
    static final String HASH = "encode(sha256(convert_to(" + WaAttendanceCalendar.SCHEDULE_JSON + ", 'UTF8')),'hex')";
    private final JdbcTemplate jdbc;
    private final WaSettings settings;

    WaAttendanceCandidates(JdbcTemplate jdbc, WaSettings settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    List<Map<String, Object>> materialize(String instance, Instant window, String after, int limit) {
        return jdbc.queryForList("""
                SELECT r.id routine_id,r.driver_id,d.local_date,m.id member_id,m.passenger_id,
                  r.id::text||d.local_date::text||COALESCE(m.passenger_id::text,'') work_key
                FROM wa.routine r JOIN wa.profile dp ON dp.identity_id=r.driver_id
                JOIN wa.identity di ON di.id=r.driver_id LEFT JOIN wa.test_cohort dc ON dc.id=di.test_cohort_id
                CROSS JOIN generate_series(0,6) day_offset
                CROSS JOIN LATERAL (SELECT (?::timestamptz AT TIME ZONE r.timezone)::date+day_offset local_date) d
                LEFT JOIN LATERAL (
                  SELECT member.*,l.version current_link_version,p.version current_pickup_version FROM wa.routine_member member
                  JOIN wa.profile pp ON pp.identity_id=member.passenger_id
                  JOIN wa.identity pi ON pi.id=member.passenger_id
                  LEFT JOIN wa.test_cohort pc ON pc.id=pi.test_cohort_id
                  JOIN wa.driver_passenger_link l ON l.id=member.link_id AND l.state='APPROVED'
                    AND l.valid_from<=? AND (l.valid_until IS NULL OR l.valid_until>?)
                  JOIN wa.integration_inbox admission ON admission.id=l.requested_source_event_id
                    AND admission.identity_id=member.passenger_id AND admission.instance_id=?
                  JOIN wa.pickup_point p ON p.id=member.pickup_point_id AND p.state='APPROVED'
                    AND p.valid_from<=? AND (p.valid_until IS NULL OR p.valid_until>?)
                  WHERE member.routine_id=r.id AND member.state='ACTIVE'
                    AND extract(isodow FROM d.local_date)=ANY(member.weekdays)
                    AND pp.role='PASSENGER' AND pp.status='ACTIVE' AND pp.notice_version IS NOT NULL
                    AND ((di.test_cohort_id=pi.test_cohort_id AND pc.environment=?)
                      OR (?=false AND di.test_cohort_id IS NULL AND pi.test_cohort_id IS NULL))
                    AND EXISTS(SELECT 1 FROM wa.integration_inbox e JOIN wa.identity_alias alias
                      ON alias.id::text=e.payload_json->>'senderAliasId' AND alias.identity_id=e.identity_id
                      WHERE e.identity_id=member.passenger_id AND e.provider='EVOLUTION' AND e.category='MESSAGE'
                        AND e.instance_id=? AND alias.instance_id=e.instance_id AND alias.provider=e.provider)
                ) m ON true
                LEFT JOIN wa.routine_occurrence o ON o.routine_id=r.id AND o.local_date=d.local_date
                LEFT JOIN wa.attendance a ON a.routine_id=r.id AND a.local_date=d.local_date AND a.passenger_id=m.passenger_id
                LEFT JOIN wa.attendance_question q ON q.attendance_id=a.id AND q.state IN ('OPEN','ANSWERED')
                WHERE r.state='ACTIVE' AND r.timezone IN (SELECT name FROM pg_timezone_names)
                  AND dp.status='ACTIVE' AND dp.role='DRIVER' AND dp.driver_enabled AND dp.notice_version IS NOT NULL
                  AND (?=false OR dc.environment=?)
                  AND (extract(isodow FROM d.local_date)=ANY(r.weekdays) OR (o.id IS NOT NULL AND o.calendar_eligible))
                  AND EXISTS(SELECT 1 FROM wa.integration_inbox e JOIN wa.identity_alias alias
                    ON alias.id::text=e.payload_json->>'senderAliasId' AND alias.identity_id=e.identity_id
                    WHERE e.identity_id=r.driver_id AND e.provider='EVOLUTION' AND e.category='MESSAGE'
                      AND e.instance_id=? AND alias.instance_id=e.instance_id AND alias.provider=e.provider)
                  AND (o.id IS NULL OR o.state='PLANNED')
                  AND NOT EXISTS(SELECT 1 FROM wa.operation_run run WHERE run.occurrence_id=o.id AND run.state<>'PLANNED')
                  AND (o.id IS NULL OR o.materialized_routine_version IS DISTINCT FROM r.version
                    OR o.calendar_eligible IS DISTINCT FROM (extract(isodow FROM d.local_date)=ANY(r.weekdays))
                    OR (m.id IS NOT NULL AND (a.id IS NULL OR q.id IS NULL
                      OR q.member_version<>m.version OR q.link_version<>m.current_link_version
                      OR q.pickup_version<>m.current_pickup_version OR q.schedule_hash<>
                """ + HASH + """
                    )))
                  AND r.id::text||d.local_date::text||COALESCE(m.passenger_id::text,'')>?
                ORDER BY work_key LIMIT ?
                """, Timestamp.from(window), Timestamp.from(window), Timestamp.from(window),
                instance, Timestamp.from(window), Timestamp.from(window), settings.environment(), settings.cohortOnly(), instance,
                settings.cohortOnly(), settings.environment(), instance, after, limit);
    }
}
