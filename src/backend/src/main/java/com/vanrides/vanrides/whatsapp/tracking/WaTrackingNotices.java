package com.vanrides.vanrides.whatsapp.tracking;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.vanrides.vanrides.whatsapp.tracking.WaTrackingStore.*;

/** Origin is the authenticated start command; messages contain no coordinates or sensor assertions. */
final class WaTrackingNotices {
    private final JdbcTemplate jdbc;
    WaTrackingNotices(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    void save(Session session,String part,String text,Instant now) {
        if(session.intent()==null) return;
        jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,run_id,occurrence_id,command_id,
              response_part,policy_version,payload_kind,payload_json,not_before,expires_at,created_at)
            SELECT ?,i.driver_id,'RESPONSE',i.run_id,i.occurrence_id,i.request_command_id,?,'run-gps-v1','TEXT',?::jsonb,?,?,?
            FROM wa.start_intent i WHERE i.id=? AND i.request_command_id IS NOT NULL
            ON CONFLICT (command_id,recipient_identity_id,response_part) WHERE purpose IN ('RESPONSE','PIN') DO NOTHING
            """,UUID.randomUUID(),part,WaJson.write(Map.of("type","TEXT","text",text,"referenceTime",now.toString())),
            ts(now),ts(now.plusSeconds(300)),ts(now),session.intent());
    }
    void confirmed(Session session,Instant now) { save(session,"run-gps-confirmed","Saída iniciada. O VanRides recebeu uma localização GPS válida do aparelho.",now); }
    void unavailable(Session session,Instant now) { save(session,"run-gps-unavailable","A localização da saída está indisponível. O acompanhamento precisa ser recuperado no aplicativo.",now); }
}
