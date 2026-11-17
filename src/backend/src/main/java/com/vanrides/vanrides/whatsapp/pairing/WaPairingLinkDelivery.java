package com.vanrides.vanrides.whatsapp.pairing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Closed origin and private link material for both synchronous and app-triggered notices. */
public final class WaPairingLinkDelivery {
    public static final String POLICY="pairing-connect-v1";
    public static final String ELIGIBLE="""
        (o.purpose='OPERATIONAL' AND o.policy_version='pairing-connect-v1' AND o.command_id IS NULL
          AND EXISTS(SELECT 1 FROM wa.pairing_link_notice n JOIN wa.pairing_handoff h ON h.id=n.link_id
            JOIN wa.command_receipt r ON r.command_id=h.source_command_id AND r.identity_id=h.driver_id
            JOIN wa.integration_inbox e ON e.id=h.source_inbox_id AND e.id=r.inbox_event_id AND e.identity_id=h.driver_id
            WHERE n.outbox_id=o.id AND h.driver_id=o.recipient_identity_id AND e.provider='EVOLUTION'
              AND h.instance_id=delivery_scope.instance_id AND e.instance_id=h.instance_id
              AND h.environment=delivery_scope.environment))
        """;
    private final JdbcTemplate jdbc;private final WaClock clock;private final WaSettings settings;private final WaCrypto crypto;
    public WaPairingLinkDelivery(JdbcTemplate jdbc,WaClock clock,WaSettings settings) {
        this.jdbc=jdbc;this.clock=clock;this.settings=settings;crypto=new WaCrypto(settings);
    }
    void save(UUID link,UUID box,String kind,String text,String token) {
        var h=jdbc.queryForMap("SELECT driver_id,expires_at,created_at FROM wa.pairing_handoff WHERE id=?",link);
        Instant now=clock.now();
        if(!((Timestamp)h.get("expires_at")).toInstant().isAfter(now))
            throw new com.vanrides.vanrides.whatsapp.api.WaProblem(com.vanrides.vanrides.whatsapp.api.WaProblem.Code.INTENT_EXPIRED);
        jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,business_key,response_part,policy_version,
              payload_kind,payload_json,not_before,expires_at,created_at)
            VALUES (?,?,'OPERATIONAL',?,?,'pairing-connect-v1','TEXT',?::jsonb,?,?,?)
            """,box,h.get("driver_id"),"pairing:"+link+":"+kind+":"+box,"pairing-"+kind.toLowerCase(java.util.Locale.ROOT),
            WaJson.write(Map.of("type","TEXT","text",text,"referenceTime",now.toString())),
            Timestamp.from(now),h.get("expires_at"),Timestamp.from(now));
        byte[] secret=token==null?null:crypto.encrypt(aad(box,link),token);
        jdbc.update("INSERT INTO wa.pairing_link_notice(outbox_id,link_id,kind,secret_ciphertext) VALUES (?,?,?,?)",box,link,kind,secret);
    }
    public boolean current(UUID box,String instance,boolean lock) {
        var row=find(box);if(row==null || !settings.enabled()) return false;
        UUID driver=(UUID)row.get("driver_id");
        if(lock) jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR SHARE",driver);
        return instance.equals(row.get("instance_id")) && settings.environment().equals(row.get("environment"))
            && !"REVOKED".equals(row.get("state")) && ((Timestamp)row.get("expires_at")).toInstant().isAfter(clock.now())
            && Boolean.TRUE.equals(jdbc.queryForObject("""
              SELECT EXISTS(SELECT 1 FROM wa.profile p JOIN wa.driver_authorization a ON a.driver_id=p.identity_id
                JOIN wa.identity i ON i.id=p.identity_id LEFT JOIN wa.test_cohort c ON c.id=i.test_cohort_id
                WHERE p.identity_id=? AND p.role='DRIVER' AND p.status='ACTIVE' AND p.driver_enabled
                  AND p.privacy_erased_at IS NULL AND p.notice_version IS NOT NULL AND p.notice_accepted_at IS NOT NULL
                  AND a.generation=? AND (?=false OR c.environment=?))
              """,Boolean.class,driver,row.get("generation"),settings.cohortOnly(),settings.environment()))
            && switch((String)row.get("kind")) {
                case "ISSUE" -> "OPEN".equals(row.get("state"));
                case "REVIEW" -> "CLAIMED".equals(row.get("state"));
                case "RETURN" -> "APPROVED".equals(row.get("state"));
                default -> false;
            };
    }
    public List<Map<String,Object>> aliases(UUID box,String instance) {
        return jdbc.queryForList("""
            SELECT a.alias_type,a.alias_value FROM wa.pairing_link_notice n JOIN wa.pairing_handoff h ON h.id=n.link_id
            JOIN wa.integration_inbox e ON e.id=h.source_inbox_id AND e.identity_id=h.driver_id
            JOIN wa.identity_alias a ON a.id::text=e.payload_json->>'senderAliasId' AND a.identity_id=h.driver_id
            WHERE n.outbox_id=? AND h.instance_id=? AND a.instance_id=? AND a.provider='EVOLUTION'
              AND a.alias_type IN ('PN','LID') FOR SHARE OF a
            """,box,instance,instance);
    }
    public ObjectNode reveal(UUID box,ObjectNode content) {
        var row=find(box);if(row==null || content==null) return content;
        if(!current(box,(String)row.get("instance_id"),false) || settings.startLinkBaseUrl().isEmpty()) return null;
        String suffix="";
        if("ISSUE".equals(row.get("kind"))) {
            if(!(row.get("secret_ciphertext") instanceof byte[] secret)) return null;
            String token=crypto.decrypt(aad(box,(UUID)row.get("id")),secret);
            try { com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier.canonical32(token); }
            catch(RuntimeException invalid) { return null; }
            suffix="\n\nConectar meu celular: "+settings.startLinkBaseUrl()+"/motorista/conectar#v1."+row.get("id")+"."+token;
        } else if("RETURN".equals(row.get("kind"))) suffix="\n\nConcluir no app: "+settings.startLinkBaseUrl()+"/motorista/vinculo/retorno";
        String text=content.path("text").asText()+suffix;
        return text.length()<=2000?content.deepCopy().put("text",text):null;
    }
    public Instant deadline(UUID box) {
        var row=find(box);return row==null?Instant.MIN:((Timestamp)row.get("expires_at")).toInstant();
    }
    public void purge(String instance) {
        jdbc.update("""
            UPDATE wa.pairing_link_notice n SET secret_ciphertext=NULL FROM wa.pairing_handoff h,wa.message_outbox o
            WHERE n.link_id=h.id AND o.id=n.outbox_id AND n.secret_ciphertext IS NOT NULL AND h.environment=? AND h.instance_id=?
              AND (h.state<>'OPEN' OR h.expires_at<=? OR o.state IN ('SUBMITTED','DELIVERED','READ','FAILED','CANCELED','EXPIRED','UNKNOWN'))
            """,settings.environment(),instance,Timestamp.from(clock.now()));
    }
    private Map<String,Object> find(UUID box) {
        var rows=jdbc.queryForList("""
            SELECT h.*,n.kind,n.secret_ciphertext FROM wa.pairing_link_notice n JOIN wa.pairing_handoff h ON h.id=n.link_id
            WHERE n.outbox_id=?
            """,box);return rows.size()==1?rows.get(0):null;
    }
    private static String aad(UUID box,UUID link) { return "pairing-handoff:"+box+":"+link; }
}
