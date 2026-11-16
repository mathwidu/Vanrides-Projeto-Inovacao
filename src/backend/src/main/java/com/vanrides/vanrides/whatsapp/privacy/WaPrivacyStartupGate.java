package com.vanrides.vanrides.whatsapp.privacy;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only consistency check. Missing reconciliation blocks boot; overdue erasure does not. */
@Component
@DependsOnDatabaseInitialization
public class WaPrivacyStartupGate {
    private final JdbcTemplate jdbc;
    private final WaPrivacySettings privacy;
    private final WaSettings settings;
    private WaPrivacyFileJournal journal;
    private String status="DISABLED";
    public WaPrivacyStartupGate(JdbcTemplate jdbc,WaPrivacySettings privacy,WaSettings settings){
        this.jdbc=jdbc;this.privacy=privacy;this.settings=settings;
    }
    @PostConstruct public void verify(){
        if(!privacy.apply()){status=privacy.enabled()?"DRY_RUN":"DISABLED";return;}
        WaPrivacyFileJournal candidate;
        try{candidate=new WaPrivacyFileJournal(privacy.journalDirectory());}
        catch(RuntimeException unavailable){throw blocked();}
        final WaPrivacyFileJournal.Snapshot snapshot;
        try{snapshot=candidate.snapshot();}catch(RuntimeException unavailable){throw blocked();}
        verify(snapshot);
        journal=candidate;
    }
    /** Package seam for metadata-only tests; production always obtains a verified filesystem snapshot. */
    void verify(WaPrivacyFileJournal.Snapshot snapshot){
        var expected=new HashMap<UUID,WaPrivacyFileJournal.VerifiedEntry>();
        for(var record:snapshot.records()){
            var entry=record.entry();
            if(!settings.environment().equals(entry.environment()) || expected.put(entry.subjectId(),record)!=null)throw restore();
        }
        var rows=jdbc.queryForList("""
            SELECT l.*,s.environment subject_environment,s.instance_id subject_instance,s.closed_at subject_closed,s.erase_after,
              s.erased_at,p.status profile_status,p.closed_at profile_closed,p.privacy_erased_at profile_erased,
              c.environment cohort_environment,c.synthetic
            FROM wa.privacy_erasure_ledger l LEFT JOIN wa.privacy_subject s ON s.subject_id=l.subject_id
            LEFT JOIN wa.profile p ON p.identity_id=l.subject_id LEFT JOIN wa.identity i ON i.id=l.subject_id
            LEFT JOIN wa.test_cohort c ON c.id=i.test_cohort_id WHERE l.environment=? ORDER BY l.sequence LIMIT 10001
            """,settings.environment());
        if(rows.size()>10000)throw blocked();int pending=0;
        for(var row:rows){
            UUID subject=(UUID)row.get("subject_id");var record=expected.remove(subject);
            if(!closure(row))throw restore();
            String state=(String)row.get("state");
            if(record==null){if(!"INTENT".equals(state))throw blocked();pending++;continue;}
            var entry=record.entry();var ack=record.acknowledgement();
            if(!entry.equals(entry(row)) || !"JOURNALED".equals(state) && !"PURGED".equals(state))throw restore();
            if(!(row.get("journal_sequence") instanceof Number sequence) || sequence.longValue()!=ack.journalSequence()
                || !ack.chainDigest().equals(row.get("journal_digest")))throw blocked();
            boolean purged="PURGED".equals(state);
            if(purged && !erased(row))throw restore();
            if(!purged && (row.get("erased_at")!=null || row.get("profile_erased")!=null))throw restore();
        }
        if(!expected.isEmpty())throw restore();
        Boolean orphan=jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.privacy_subject s LEFT JOIN wa.privacy_erasure_ledger l ON l.subject_id=s.subject_id
              WHERE s.environment=? AND (l.subject_id IS NULL OR l.environment<>s.environment OR l.instance_id<>s.instance_id))
              OR EXISTS(SELECT 1 FROM wa.profile p JOIN wa.identity i ON i.id=p.identity_id
                JOIN wa.test_cohort c ON c.id=i.test_cohort_id LEFT JOIN wa.privacy_subject s ON s.subject_id=p.identity_id
                WHERE c.environment=? AND p.closed_at IS NOT NULL AND (s.subject_id IS NULL OR s.environment<>c.environment))
            """,Boolean.class,settings.environment(),settings.environment());
        if(Boolean.TRUE.equals(orphan))throw restore();
        status=pending==0?"READY":"INTENT_PENDING";
    }
    private boolean closure(Map<String,Object> row){
        var payload=WaJson.object(String.valueOf(row.get("payload_json")));
        Instant closed,date;
        try{closed=OffsetDateTime.parse(payload.path("closedAt").asText()).toInstant();date=OffsetDateTime.parse(payload.path("eraseAfter").asText()).toInstant();}
        catch(RuntimeException invalid){return false;}
        return "wa-privacy-v1".equals(payload.path("policy").asText())
            && String.valueOf(row.get("subject_id")).equals(payload.path("subjectId").asText())
            && String.valueOf(row.get("event_id")).equals(payload.path("eventId").asText())
            && row.get("environment").equals(payload.path("environment").asText())
            && row.get("instance_id").equals(payload.path("instanceId").asText())
            && "CLOSED".equals(row.get("profile_status")) && closed.equals(instant(row.get("profile_closed")))
            && closed.equals(instant(row.get("subject_closed"))) && date.equals(instant(row.get("erase_after")))
            && settings.environment().equals(row.get("cohort_environment"))
            && row.get("environment").equals(row.get("subject_environment")) && row.get("instance_id").equals(row.get("subject_instance"))
            && WaCrypto.digest(String.valueOf(row.get("payload_json"))).equals(row.get("payload_hash"));
    }
    private static WaPrivacyJournal.Entry entry(Map<String,Object> row){
        return new WaPrivacyJournal.Entry(((Number)row.get("sequence")).longValue(),(UUID)row.get("event_id"),(UUID)row.get("subject_id"),
            (String)row.get("environment"),(String)row.get("instance_id"),String.valueOf(row.get("payload_json")),(String)row.get("payload_hash"));
    }
    private static boolean erased(Map<String,Object> row){
        Instant after=instant(row.get("erase_after")),start=instant(row.get("purge_started_at"));
        if(after==null || start==null)return false;
        for(String field:java.util.List.of("profile_erased","erased_at","purged_at")){
            Instant at=instant(row.get(field));if(at==null || at.isBefore(after))return false;
        }
        return !instant(row.get("purged_at")).isBefore(start);
    }
    public WaPrivacyJournal journal(){
        if(!privacy.apply())return WaPrivacyJournal.unavailable();
        if(journal==null)throw blocked();return journal;
    }
    public String status(){return status;}
    private static Instant instant(Object value){return value instanceof Timestamp timestamp?timestamp.toInstant():null;}
    private static IllegalStateException blocked(){return new IllegalStateException("PRIVACY_STARTUP_BLOCKED");}
    private static IllegalStateException restore(){return new IllegalStateException("PRIVACY_RESTORE_REQUIRED");}
}
