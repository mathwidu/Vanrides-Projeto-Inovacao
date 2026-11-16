package com.vanrides.vanrides.whatsapp.attendance;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class WaAttendanceRoster {
    public enum Group { CONFIRMED, PENDING, ABSENT }

    public record Member(String name, String state, Boolean operationalIncluded, String operationalReason) {
        public Group group() {
            if (state.equals("NOT_GOING") || operationalIncluded != null && !operationalIncluded) return Group.ABSENT;
            return state.equals("GOING") ? Group.CONFIRMED : Group.PENDING;
        }
    }

    private WaAttendanceRoster() {}

    public static List<Member> beforeStart(JdbcTemplate jdbc, UUID driver, UUID occurrence, UUID routine, LocalDate localDate) {
        return jdbc.query("""
            SELECT COALESCE(p.display_name,'Passageiro'),a.state,ov.included,ov.reason
            FROM wa.attendance a JOIN wa.routine_member m ON m.routine_id=a.routine_id AND m.passenger_id=a.passenger_id
            JOIN wa.driver_passenger_link l ON l.id=m.link_id AND l.driver_id=m.driver_id AND l.passenger_id=m.passenger_id
            JOIN wa.profile p ON p.identity_id=a.passenger_id
            LEFT JOIN LATERAL (SELECT v.included,v.reason FROM wa.occurrence_roster_override v
              WHERE v.occurrence_id=? AND v.passenger_id=a.passenger_id ORDER BY v.revision DESC LIMIT 1) ov ON true
            WHERE a.routine_id=? AND a.local_date=?::date AND m.driver_id=? AND m.state='ACTIVE' AND l.state='APPROVED'
            ORDER BY p.display_name,a.passenger_id LIMIT 1001
            """, (rs, row) -> new Member(rs.getString(1), rs.getString(2), rs.getObject(3, Boolean.class), rs.getString(4)),
            occurrence, routine, Date.valueOf(localDate), driver);
    }
}
