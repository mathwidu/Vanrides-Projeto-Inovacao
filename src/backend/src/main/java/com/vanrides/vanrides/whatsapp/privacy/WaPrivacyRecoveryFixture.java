package com.vanrides.vanrides.whatsapp.privacy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static com.vanrides.vanrides.whatsapp.privacy.WaPrivacyRecoveryFiles.require;

/** Two known lab subjects. Never imports a contact, human input, fixture SQL or arbitrary subject ID. */
final class WaPrivacyRecoveryFixture {
    final JdbcTemplate jdbc;
    final String nonce, instance, alphaMarker, betaMarker;
    final UUID cohort, alpha, beta, routine, occurrence, run, point;
    WaPrivacyRecoveryFixture(JdbcTemplate jdbc,String nonce) {
        this.jdbc=jdbc; this.nonce=nonce; instance="wa_privacy_recovery_"+nonce;
        alphaMarker="RECOVERY_ALPHA_PRIVATE_"+nonce; betaMarker="RECOVERY_BETA_KEEP_"+nonce;
        cohort=id("cohort"); alpha=id("alpha"); beta=id("beta"); routine=id("routine");
        occurrence=id("occurrence"); run=id("run"); point=id("point");
    }
    UUID id(String name) { return UUID.nameUUIDFromBytes(("wa-recovery-v44:"+nonce+":"+name).getBytes(StandardCharsets.UTF_8)); }
    Timestamp at(Instant time) { return Timestamp.from(time); }
    String string(String sql,Object... args) { return jdbc.queryForObject(sql,String.class,args); }
    long count(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }

    ObjectNode prepare(TransactionTemplate transaction) {
        require(count("SELECT count(*) FROM wa.privacy_erasure_ledger WHERE environment='wa070-local' AND instance_id=?",instance)==0);
        require(count("SELECT count(*) FROM wa.test_cohort WHERE id=?",cohort)==0);
        String core=coreDigest(); Instant base=jdbc.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant();
        transaction.execute(tx->{ create(base); return true; });
        return WaJson.object("{}").put("schemaVersion",1).put("nonce",nonce).put("baseAt",base.toString())
            .put("coreDigest",core).put("betaDigest",betaDigest());
    }
    private void create(Instant base) {
        Instant origin=base.minusSeconds(100L*86400),recent=base.minusSeconds(8L*86400),future=base.plusSeconds(30L*86400);
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic,created_at) VALUES (?,'wa070-local',true,?)",cohort,at(origin));
        person(alpha,"PASSENGER",alphaMarker,"000000000000001@s.whatsapp.net",origin);
        person(beta,"DRIVER",betaMarker,"000000000000002@s.whatsapp.net",origin);
        jdbc.update("INSERT INTO wa.driver_authorization(driver_id) VALUES (?)",beta);
        jdbc.update("""
            INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,destination_label,
              destination_latitude,destination_longitude,state,policy_version,stop_order_pending,created_at,updated_at)
            VALUES (?, ?,?,'OUTBOUND',ARRAY[1,2,3,4,5,6,7]::smallint[],'09:00','UTC',?,10,11,'ACTIVE','recovery-v44',false,?,?)
            """,routine,beta,betaMarker,betaMarker,at(origin),at(origin));
        UUID link=id("link");
        jdbc.update("""
            INSERT INTO wa.driver_passenger_link(id,driver_id,passenger_id,state,approved_by,approved_at,requested_source_event_id,valid_from,created_at)
            VALUES (?,?,?,'APPROVED',?,?,?,?,?)
            """,link,beta,alpha,beta,at(origin),id("alpha-inbox"),at(origin),at(origin));
        jdbc.update("""
            INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,label,reference_text,
              source_inbox_event_id,state,approved_by,approved_at,valid_from,created_at)
            VALUES (?,?,?,?,12.3456,45.6789,?,?,?,'APPROVED',?,?,?,?)
            """,point,link,beta,alpha,alphaMarker,alphaMarker,id("alpha-inbox"),beta,at(origin),at(origin),at(origin));
        jdbc.update("INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order) VALUES (?,?,?,?,?,?,1)",
            id("member"),routine,beta,alpha,link,point);
        var day=recent.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        jdbc.update("INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at,created_at) VALUES (?,?,?,?,?,?)",
            occurrence,routine,beta,day,at(recent),at(recent));
        ObjectNode snapshot=WaJson.object("{}").put("rosterDigest","f".repeat(64));
        snapshot.set("routine",WaJson.object(string("SELECT to_jsonb(r)::text FROM wa.routine r WHERE id=?",routine)));
        snapshot.set("schedule",WaJson.object("{}").put("name",betaMarker).put("direction","OUTBOUND"));
        snapshot.putObject("passengerNames").put(alpha.toString(),alphaMarker);
        snapshot.putArray("participants").addObject().put("passenger",alpha.toString()).put("point",point.toString())
            .put("name",alphaMarker).put("latitude",12.3456).put("longitude",45.6789).put("reference",alphaMarker).put("order",1).put("attendance","GOING");
        jdbc.update("""
            INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,
              snapshot_json,snapshot_at,started_at,completed_at,created_at)
            VALUES (?,?,?,?,?,'COMPLETED','recovery-v44',?::jsonb,?,?,?,?)
            """,run,occurrence,routine,beta,day,snapshot.toString(),at(recent),at(recent),at(recent),at(recent));
        jdbc.update("""
            INSERT INTO wa.run_stop(id,run_id,driver_id,passenger_id,physical_stop_key,stop_order,pickup_point_id,pickup_version,
              latitude_snapshot,longitude_snapshot,reference_snapshot,attendance_snapshot,state)
            VALUES (?,?,?,?,?,1,?,0,12.3456,45.6789,?,'GOING','PASSED')
            """,id("stop"),run,beta,alpha,id("physical-stop"),point,alphaMarker);
        box(alpha,"alpha-box",alphaMarker,"DELIVERED",recent,recent.plusSeconds(300));
        box(beta,"beta-control-box",betaMarker,"PENDING",future,future.plusSeconds(30L*86400));
        jdbc.update("INSERT INTO wa.conversation_state(identity_id,stage,context_json,last_event_id,updated_at) VALUES (?,'PAUSED',?::jsonb,?,?)",
            alpha,WaJson.object("{}").set("passengerDraft",WaJson.object("{}").put("name",alphaMarker)).toString(),id("alpha-inbox"),at(recent));
        jdbc.update("""
            INSERT INTO wa.support_case(id,identity_id,deduplication_key,reason_code,state,correlation_id,created_at,closed_at)
            VALUES (?,?,?,'SYNTHETIC_HISTORY','CLOSED',?,?,?)
            """,id("old-support"),alpha,"recovery-"+nonce,id("alpha-inbox").toString(),at(origin),at(base.minusSeconds(91L*86400)));
        historical(snapshot,origin);
    }
    private void historical(ObjectNode snapshot,Instant origin) {
        UUID dayId=id("old-occurrence"),runId=id("old-run"),command=id("old-command");
        var day=origin.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        jdbc.update("""
            INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,request_hash,result_json,created_at,expires_at)
            VALUES (?,?,?,?,'SYNTHETIC_HISTORY',?,'{}',?,?)
            """,command,beta,id("beta-inbox"),"recovery-"+nonce,WaCrypto.digest("recovery-history-"+nonce),at(origin),at(origin.plusSeconds(3600)));
        jdbc.update("INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at,created_at,schedule_snapshot) VALUES (?,?,?,?,?,?,?::jsonb)",
            dayId,routine,beta,day,at(origin),at(origin),WaJson.object("{}").put("name",alphaMarker).toString());
        jdbc.update("""
            INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_json,snapshot_at,started_at,completed_at,created_at)
            VALUES (?,?,?,?,?,'COMPLETED','recovery-v44',?::jsonb,?,?,?,?)
            """,runId,dayId,routine,beta,day,snapshot.toString(),at(origin),at(origin),at(origin),at(origin));
        jdbc.update("""
            INSERT INTO wa.run_stop(id,run_id,driver_id,passenger_id,physical_stop_key,stop_order,pickup_point_id,pickup_version,
              latitude_snapshot,longitude_snapshot,reference_snapshot,attendance_snapshot,state)
            VALUES (?,?,?,?,?,1,?,0,12.3456,45.6789,?,'GOING','PASSED')
            """,id("old-stop"),runId,beta,alpha,id("old-physical-stop"),point,alphaMarker);
        jdbc.update("""
            INSERT INTO wa.start_intent(id,driver_id,occurrence_id,run_id,token_hash,environment,state,expires_at,created_at,request_command_id)
            VALUES (?,?,?,?,?,'wa070-local','REVOKED',?,?,?)
            """,id("old-intent"),beta,dayId,runId,WaCrypto.digest("recovery-intent-"+nonce),at(origin.plusSeconds(3600)),at(origin),command);
    }
    private void person(UUID person,String role,String name,String address,Instant origin) {
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id,created_at) VALUES (?,?,?)",person,cohort,at(origin));
        jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value,created_at) VALUES (?,?,'EVOLUTION',?,'PN',?,?)",
            id(person.equals(alpha)?"alpha-alias":"beta-alias"),person,instance,address,at(origin));
        jdbc.update("""
            INSERT INTO wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at,driver_enabled,created_at,updated_at)
            VALUES (?,?,'ACTIVE',?,'wa-mvp-v1',?,?,?,?)
            """,person,role,name,at(origin),role.equals("DRIVER"),at(origin),at(origin));
        jdbc.update("""
            INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,normalized_kind,state,payload_json,created_at)
            VALUES (?,?,'EVOLUTION',?,?,'MESSAGE','TEXT','COMPLETED',?::jsonb,?)
            """,id(person.equals(alpha)?"alpha-inbox":"beta-inbox"),person,instance,"recovery-"+person,
            WaJson.object("{}").put("text",name).toString(),at(origin));
    }
    private void box(UUID person,String name,String text,String state,Instant created,Instant expires) {
        jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,business_key,policy_version,payload_json,state,created_at,not_before,expires_at)
            VALUES (?,?,'OPERATIONAL',?,'recovery-v44',?::jsonb,?,?,?,?)
            """,id(name),person,"recovery-"+nonce+"-"+name,WaJson.object("{}").put("type","TEXT").put("text",text).toString(),state,at(created),at(created),at(expires));
    }
    void verifyOriginal(ObjectNode state) {
        verifyControls(state);
        require(alphaMarker.equals(string("SELECT display_name FROM wa.profile WHERE identity_id=?",alpha)));
        require(count("SELECT count(*) FROM wa.privacy_erasure_ledger WHERE subject_id=?",alpha)==0);
        require(string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?",run).contains(alphaMarker));
    }
    void verifyControls(ObjectNode state) {
        require(state.path("coreDigest").asText().equals(coreDigest()) && state.path("betaDigest").asText().equals(betaDigest()));
        require(count("SELECT count(*) FROM wa.identity WHERE test_cohort_id=?",cohort)==2);
    }
    void verifyErased(ObjectNode state) {
        verifyControls(state);
        require(count("SELECT count(*) FROM wa.profile WHERE identity_id=? AND status='CLOSED' AND display_name IS NULL AND privacy_erased_at IS NOT NULL",alpha)==1);
        require(count("SELECT count(*) FROM wa.identity_alias WHERE identity_id=? AND alias_value IS NOT NULL",alpha)==0);
        require(count("SELECT count(*) FROM wa.pickup_point WHERE id=? AND (latitude IS NOT NULL OR longitude IS NOT NULL OR reference_text IS NOT NULL)",point)==0);
        require(count("SELECT count(*) FROM wa.run_stop WHERE run_id=? AND passenger_id=? AND (latitude_snapshot IS NOT NULL OR longitude_snapshot IS NOT NULL OR reference_snapshot IS NOT NULL)",run,alpha)==0);
        require(count("SELECT count(*) FROM wa.support_case WHERE id=?",id("old-support"))==0);
        // Known marker is absent from every physical WA table, not merely hidden by a DTO.
        for(String table:jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='wa' ORDER BY tablename",String.class)) {
            require(table.matches("[a-z][a-z0-9_]*"));
            require(count("SELECT count(*) FROM wa."+table+" t WHERE strpos(row_to_json(t)::text,?)>0",alphaMarker)==0);
        }
    }
    String coreDigest() {
        String value=string("""
            WITH settings AS MATERIALIZED (SELECT set_config('TimeZone','UTC',true)) SELECT jsonb_build_object(
             'profiles',(SELECT jsonb_agg(to_jsonb(p) ORDER BY identity_id) FROM wa.profile p WHERE identity_id NOT IN (?,?)),
             'inbox',(SELECT jsonb_agg(to_jsonb(i) ORDER BY id) FROM wa.integration_inbox i WHERE instance_id<>?),
             'outbox',(SELECT jsonb_agg(to_jsonb(o) ORDER BY id) FROM wa.message_outbox o WHERE recipient_identity_id NOT IN (?,?)),
             'commands',(SELECT jsonb_agg(to_jsonb(c) ORDER BY command_id) FROM wa.command_receipt c WHERE identity_id NOT IN (?,?)),
             'context',(SELECT jsonb_agg(to_jsonb(c) ORDER BY identity_id) FROM wa.conversation_state c WHERE identity_id NOT IN (?,?)))::text FROM settings
            """,alpha,beta,instance,alpha,beta,alpha,beta,alpha,beta);
        return WaCrypto.digest(value);
    }
    String betaDigest() {
        return WaCrypto.digest(string("""
            WITH settings AS MATERIALIZED (SELECT set_config('TimeZone','UTC',true)) SELECT jsonb_build_object('profile',(SELECT to_jsonb(p) FROM wa.profile p WHERE identity_id=?),
             'alias',(SELECT jsonb_agg(to_jsonb(a) ORDER BY id) FROM wa.identity_alias a WHERE identity_id=?),
             'routine',(SELECT to_jsonb(r) FROM wa.routine r WHERE id=?),
             'queue',(SELECT to_jsonb(o) FROM wa.message_outbox o WHERE id=?),
             'snapshotRoutine',(SELECT snapshot_json->'routine' FROM wa.operation_run WHERE id=?))::text FROM settings
            """,beta,beta,routine,id("beta-control-box"),run));
    }
}
