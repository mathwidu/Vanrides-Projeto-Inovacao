package com.vanrides.vanrides.whatsapp.attendance;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Calendar decisions use a routine's zone, including gaps and overlaps. */
public final class WaAttendanceCalendar {
    public static final String SCHEDULE_JSON = "jsonb_build_array(r.name,r.direction,r.local_time::text,"
            + "r.timezone,r.weekdays,r.state,r.attendance_policy,r.policy_version)::text";

    private WaAttendanceCalendar() {}

    public static Instant scheduledAt(LocalDate date, LocalTime time, String timezone) {
        var local = date.atTime(time);
        var offsets = ZoneId.of(timezone).getRules().getValidOffsets(local);
        if (offsets.size() != 1) {
            throw new WaProblem(WaProblem.Code.AMBIGUOUS_OCCURRENCE);
        }
        return local.toInstant(offsets.get(0));
    }

    public static Instant reminderAt(LocalDate date, String timezone) {
        return scheduledAt(date.minusDays(1), LocalTime.of(20, 0), timezone);
    }

    public static Instant answerUntil(LocalDate date, String timezone) {
        return date.plusDays(1).atStartOfDay(ZoneId.of(timezone)).toInstant();
    }

    public static Instant sendUntil(Instant reminderAt, Instant scheduledAt) {
        Instant ordinary = reminderAt.plusSeconds(1800);
        return ordinary.isBefore(scheduledAt) ? ordinary : scheduledAt;
    }

    public static String scheduleHash(JdbcTemplate jdbc, UUID routine) {
        String snapshot = jdbc.queryForObject(
                "SELECT " + SCHEDULE_JSON + " FROM wa.routine r WHERE r.id=?", String.class, routine);
        return WaCrypto.digest(snapshot);
    }

    public static String intentHash(JdbcTemplate jdbc, UUID routine) {
        return WaCrypto.digest(jdbc.queryForObject("""
                SELECT jsonb_build_array(direction,local_time::text,timezone,weekdays)::text
                FROM wa.routine WHERE id=?
                """, String.class, routine));
    }
}
