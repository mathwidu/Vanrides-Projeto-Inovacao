package com.vanrides.vanrides.whatsapp.attendance;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class WaAttendanceJobPages {
    record Page(UUID id, String after, Instant window, ObjectNode response) {}
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaClock clock;

    WaAttendanceJobPages(JdbcTemplate jdbc, WaSettings settings, WaClock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.clock = clock;
    }

    Page begin(String type, String windowKey, String cursor, int limit, String instance, UUID credential) {
        String pageKey = cursor == null ? "ROOT" : cursor;
        Instant window = window(windowKey);
        var stored = jdbc.queryForList("""
                SELECT * FROM wa.attendance_job_page
                WHERE environment=? AND instance_id=? AND job_type=? AND window_key=? AND page_key=? FOR UPDATE
                """, settings.environment(), instance, type, windowKey, pageKey);
        if (!stored.isEmpty()) {
            return cached(stored.get(0), limit, window);
        }
        String after = "";
        if (cursor == null) {
            if (window.isAfter(clock.now()) || !window.plusSeconds(120).isAfter(clock.now())) {
                throw WaProblem.invalid();
            }
        } else {
            UUID id = cursorId(cursor);
            var cursors = jdbc.queryForList("""
                    SELECT last_key,expires_at FROM wa.attendance_job_cursor
                    WHERE id=? AND environment=? AND instance_id=? AND job_type=? AND window_key=?
                    """, id, settings.environment(), instance, type, windowKey);
            if (cursors.size() != 1 || !((Timestamp) cursors.get(0).get("expires_at")).toInstant().isAfter(clock.now())) {
                throw WaProblem.invalid();
            }
            after = (String) cursors.get(0).get("last_key");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO wa.attendance_job_page(id,environment,instance_id,job_type,window_key,page_key,
                  batch_limit,credential_id,created_at) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
                """, id, settings.environment(), instance, type, windowKey, pageKey, limit, credential,
                Timestamp.from(clock.now()));
        var row = jdbc.queryForMap("""
                SELECT * FROM wa.attendance_job_page
                WHERE environment=? AND instance_id=? AND job_type=? AND window_key=? AND page_key=? FOR UPDATE
                """, settings.environment(), instance, type, windowKey, pageKey);
        if (row.get("response_json") != null) {
            return cached(row, limit, window);
        }
        if (!row.get("id").equals(id)) {
            throw new IllegalStateException("WA_JOB_PAGE_INCOMPLETE");
        }
        return new Page(id, after, window, null);
    }

    ObjectNode finish(Page page, String type, String instance, int processed, String lastKey, boolean more) {
        if (!page.window().plusSeconds(300).isAfter(clock.now())) {
            throw WaProblem.invalid();
        }
        ObjectNode response = WaJson.object("{}").put("jobType", type).put("processed", processed)
                .put("serverTime", clock.now().toString());
        if (more) {
            UUID cursor = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO wa.attendance_job_cursor(id,environment,instance_id,job_type,window_key,
                      last_key,created_at,expires_at) VALUES (?,?,?,?,?,?,?,?)
                    """, cursor, settings.environment(), instance, type, page.window().toString(), lastKey,
                    Timestamp.from(clock.now()), Timestamp.from(page.window().plusSeconds(300)));
            response.put("nextCursor", "jobcursor_" + cursor.toString().replace("-", ""));
        } else {
            response.putNull("nextCursor");
        }
        jdbc.update("UPDATE wa.attendance_job_page SET response_json=?::jsonb WHERE id=?",
                response.toString(), page.id());
        return response;
    }

    private Page cached(java.util.Map<String, Object> row, int limit, Instant window) {
        if (((Number) row.get("batch_limit")).intValue() != limit) {
            throw WaProblem.invalid();
        }
        if (row.get("response_json") == null) {
            throw new IllegalStateException("WA_JOB_PAGE_INCOMPLETE");
        }
        return new Page((UUID) row.get("id"), "", window, WaJson.object(row.get("response_json").toString()));
    }

    private Instant window(String value) {
        try {
            Instant instant = Instant.parse(value);
            if (!instant.toString().equals(value) || !instant.truncatedTo(ChronoUnit.MINUTES).equals(instant)) {
                throw WaProblem.invalid();
            }
            return instant;
        } catch (java.time.DateTimeException invalid) {
            throw WaProblem.invalid();
        }
    }

    private UUID cursorId(String value) {
        if (!value.matches("jobcursor_[a-f0-9]{32}")) {
            throw WaProblem.invalid();
        }
        String raw = value.substring(10);
        return WaJson.uuid(raw.substring(0,8) + "-" + raw.substring(8,12) + "-" + raw.substring(12,16)
                + "-" + raw.substring(16,20) + "-" + raw.substring(20));
    }
}
