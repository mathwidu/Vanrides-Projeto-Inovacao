package com.vanrides.vanrides.whatsapp.privacy;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

public final class WaPrivacyHistoryProjectionFixture {
    public static final String ENV="history-projection", INSTANCE="synthetic-history-projection", DIGEST="f".repeat(64);
    public static final WaPrivacyService.Scope SCOPE=new WaPrivacyService.Scope(ENV,INSTANCE);
    public final JdbcTemplate jdbc; public final DataSourceTransactionManager manager; final Time time=new Time(); public final WaClock clock=new WaClock(time);
    public final UUID cohort=UUID.randomUUID(),driver=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();
    public final UUID routine=UUID.randomUUID(),occurrence=UUID.randomUUID(),run=UUID.randomUUID(),intent=UUID.randomUUID(),session=UUID.randomUUID();
    public final UUID credential=UUID.randomUUID(); public final WaPrincipal actor;
    final Map<UUID,UUID> inbox=new LinkedHashMap<>(),alias=new LinkedHashMap<>(),point=new LinkedHashMap<>(),stop=new LinkedHashMap<>();
    final Instant observed,past;
    public WaPrivacyHistoryProjectionFixture(PostgreSQLContainer<?> postgres) { this(postgres,true); }
    WaPrivacyHistoryProjectionFixture(PostgreSQLContainer<?> postgres,boolean sources) {
        var ds=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);
        jdbc.execute("DROP SCHEMA IF EXISTS wa CASCADE");var flyway=Flyway.configure().dataSource(ds).cleanDisabled(false).load();flyway.clean();flyway.migrate();
        observed=jdbc.queryForObject("SELECT transaction_timestamp()",Timestamp.class).toInstant();time.now=observed;past=observed.minusSeconds(91L*86400);
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,?,true)",cohort,ENV);
        jdbc.update("INSERT INTO wa.service_credential(id,principal,environment,allowed_instance_id,token_hash,created_at,expires_at) VALUES (?,'SYSTEM_JOB',?,?,?,?,?)",
            credential,ENV,INSTANCE,WaCrypto.digest(credential.toString()),ts(observed.minusSeconds(3600)),ts(observed.plusSeconds(3600)));
        actor=new WaPrincipal(WaPrincipal.Kind.SYSTEM_JOB,credential,null,null,INSTANCE,null,0,false);
        person(driver,"DRIVER_PRIVATE","DRIVER");person(first,"ALPHA_PRIVATE","PASSENGER");person(second,"BETA_ACTIVE","PASSENGER");
        jdbc.update("INSERT INTO wa.driver_authorization(driver_id) VALUES (?)",driver);
        jdbc.update("""
            INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,state,policy_version,
              destination_latitude,destination_longitude,destination_source_event_id,stop_order_pending)
            VALUES (?,?,'SCHEDULE_PRIVATE','OUTBOUND',ARRAY[1,2,3,4,5,6,7]::smallint[],'09:00','UTC','ACTIVE','history-test',10,11,?,false)
            """,routine,driver,inbox.get(driver));
        for(UUID passenger:java.util.List.of(first,second)) member(passenger);
        var date=past.atZone(ZoneOffset.UTC).toLocalDate();
        jdbc.update("""
            INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at,created_at,schedule_snapshot)
            VALUES (?,?,?,?,?,?,?::jsonb)
            """,occurrence,routine,driver,date,ts(past),ts(past),"{\"name\":\"SCHEDULE_PRIVATE\",\"direction\":\"OUTBOUND\"}");
        var snapshot=WaJson.object("{}").put("rosterDigest",DIGEST);
        snapshot.set("routine",WaJson.object("{}").put("name","SCHEDULE_PRIVATE"));
        if(sources) snapshot.withObject("routine").put("destination_source_event_id",inbox.get(driver).toString());
        var participants=snapshot.putArray("participants");var names=snapshot.putObject("passengerNames");
        for(UUID passenger:java.util.List.of(first,second)){
            String name=passenger.equals(first)?"ALPHA_PRIVATE":"BETA_ACTIVE";names.put(passenger.toString(),name);
            participants.addObject().put("passenger",passenger.toString()).put("name",name).put("latitude",12.3456).put("longitude",45.6789).put("reference","REFERENCE_PRIVATE");
            jdbc.update("INSERT INTO wa.attendance(id,routine_id,passenger_id,local_date,state,actor_identity_id,answered_at,created_at) VALUES (?,?,?,?,'GOING',?,?,?)",
                UUID.randomUUID(),routine,passenger,date,passenger,ts(past),ts(past));
        }
        jdbc.update("""
            INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_json,
              snapshot_at,started_at,completed_at,completion_reason,created_at,version)
            VALUES (?,?,?,?,?,'COMPLETED','history-test',?::jsonb,?,?,?,'REASON_PRIVATE',?,7)
            """,run,occurrence,routine,driver,date,snapshot.toString(),ts(past),ts(past),ts(past),ts(past));
        for(UUID passenger:java.util.List.of(first,second)){
            UUID id=UUID.randomUUID();stop.put(passenger,id);
            jdbc.update("""
                INSERT INTO wa.run_stop(id,run_id,driver_id,passenger_id,physical_stop_key,stop_order,pickup_point_id,pickup_version,
                  latitude_snapshot,longitude_snapshot,reference_snapshot,attendance_snapshot,state,created_at,version)
                VALUES (?,?,?,?,?,?,?,4,12.3456,45.6789,'REFERENCE_PRIVATE','GOING','PASSED',?,3)
                """,id,run,driver,passenger,UUID.randomUUID(),passenger.equals(first)?1:2,point.get(passenger),ts(past));
            if(sources) question(passenger,date);
        }
        UUID command=UUID.randomUUID();
        if(sources) {
        jdbc.update("""
            INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,request_hash,result_json,created_at,expires_at)
            VALUES (?,?,?,?,'REQUEST_START',?,'{}',?,?)
            """,command,driver,inbox.get(driver),command.toString(),"a".repeat(64),ts(past),ts(past.plusSeconds(3600)));
        jdbc.update("""
            INSERT INTO wa.start_intent(id,driver_id,occurrence_id,run_id,token_hash,environment,state,created_at,expires_at,request_command_id)
            VALUES (?,?,?,?,?,?,'REVOKED',?,?,?)
            """,intent,driver,occurrence,run,"b".repeat(64),ENV,ts(past),ts(past.plusSeconds(3600)),command);
        }
        UUID device=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO wa.driver_device(id,driver_id,jkt,public_jwk,generation,environment,platform,app_version,status,created_at,revoked_at)
            VALUES (?,?,'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA','{}',1,?,'ANDROID','history-test','REVOKED',?,?)
            """,device,driver,ENV,ts(past),ts(past));
        jdbc.update("""
            INSERT INTO wa.tracking_session(id,run_id,driver_id,device_id,generation,state,lease_expires_at,last_sequence,stopped_at,created_at,origin_intent_id)
            VALUES (?,?,?,?,1,'STOPPED',?,5,?,?,?)
            """,session,run,driver,device,ts(past.plusSeconds(60)),ts(past),ts(past),sources?intent:null);
        jdbc.update("""
            INSERT INTO wa.approach_progress(run_id,physical_stop_key,nearest_m,nearest_at,last_session_id,last_sequence,policy_version,last_reason,suppressed_count)
            SELECT run_id,physical_stop_key,3.5,?,?,5,'history-test','REASON_PRIVATE',1 FROM wa.run_stop WHERE id=?
            """,ts(past),session,stop.get(first));
        jdbc.update("INSERT INTO wa.run_finish_progress(run_id,session_id,last_sequence,last_captured_at,policy_version,last_reason) VALUES (?,?,5,?,'history-test','REASON_PRIVATE')",run,session,ts(past));
        if(sources) jdbc.update("""
            INSERT INTO wa.occurrence_roster_override(id,occurrence_id,routine_id,driver_id,passenger_id,local_date,included,revision,reason,actor_identity_id,source_inbox_event_id,created_at)
            VALUES (?,?,?,?,?,?,true,1,'REASON_PRIVATE',?,?,?)
            """,UUID.randomUUID(),occurrence,routine,driver,first,date,driver,inbox.get(driver),ts(past));
    }
    public WaPrivacyHistoryProjection projection(boolean dry){return new WaPrivacyHistoryProjection(jdbc,manager,clock,WaPrivacyPolicy.syntheticTests(),this::authority,dry);}
    public void authority(WaPrivacyService.Scope scope){
        if(!scope.equals(SCOPE)||jdbc.queryForList("SELECT id FROM wa.service_credential WHERE id=? AND revoked_at IS NULL AND expires_at>clock_timestamp() FOR SHARE",credential).size()!=1)throw new IllegalStateException("TEST_AUTHORITY");
    }
    void pin(){jdbc.update("""
        UPDATE wa.routine_occurrence SET history_environment=?,history_instance_id=?,history_origin_kind='START_INTENT',
          history_origin_id=?,history_origin_bound_at=transaction_timestamp() WHERE id=?
        """,ENV,INSTANCE,intent,occurrence);}
    void leaves(){new WaPrivacyHistoryRetention(jdbc,manager,clock,WaPrivacyPolicy.syntheticTests(),false).tick(actor,100,null);}
    public void finish(){for(int i=0;i<12;i++){var r=projection(false).tick(actor,100,null);leaves();if(r.complete())return;}throw new AssertionError("History projection pending");}
    String string(String sql,Object... args){return jdbc.queryForObject(sql,String.class,args);}
    long count(String table){return jdbc.queryForObject("SELECT count(*) FROM wa."+table,Long.class);}
    static Timestamp ts(Instant value){return Timestamp.from(value);}
    private void person(UUID id,String name,String role){
        UUID a=UUID.randomUUID(),event=UUID.randomUUID();alias.put(id,a);inbox.put(id,event);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)",id,cohort);
        jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION',?,'PN',?)",a,id,INSTANCE,id+"@synthetic.invalid");
        jdbc.update("INSERT INTO wa.profile(identity_id,role,status,display_name,driver_enabled) VALUES (?,?,'ACTIVE',?,?)",id,role,name,role.equals("DRIVER"));
        jdbc.update("INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,state,payload_json,created_at) VALUES (?,?,'EVOLUTION',?,?,'MESSAGE','COMPLETED',?::jsonb,?)",
            event,id,INSTANCE,event.toString(),WaJson.object("{}").put("senderAliasId",a.toString()).put("text",name).toString(),ts(past));
    }
    private void member(UUID passenger){
        UUID link=UUID.randomUUID(),p=UUID.randomUUID();point.put(passenger,p);
        jdbc.update("INSERT INTO wa.driver_passenger_link(id,driver_id,passenger_id,state,approved_by,approved_at,requested_source_event_id) VALUES (?,?,?,'APPROVED',?,?,?)",link,driver,passenger,driver,ts(past),inbox.get(passenger));
        jdbc.update("INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,reference_text,source_inbox_event_id,state,approved_by,approved_at) VALUES (?,?,?,?,12.3456,45.6789,'REFERENCE_PRIVATE',?,'APPROVED',?,?)",
            p,link,driver,passenger,inbox.get(passenger),driver,ts(past));
        jdbc.update("INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),routine,driver,passenger,link,p,passenger.equals(first)?1:2);
    }
    private void question(UUID passenger,java.time.LocalDate date){
        jdbc.update("""
            INSERT INTO wa.attendance_question(id,attendance_id,occurrence_id,routine_id,driver_id,passenger_id,local_date,routine_name,
              direction,local_time,timezone,member_id,link_id,pickup_point_id,instance_id,environment,state,job_credential_id,
              link_version,member_version,pickup_version,schedule_hash,intent_hash,opens_at,reminder_at,send_until,answer_until,created_at)
            SELECT ?,a.id,?,a.routine_id,m.driver_id,a.passenger_id,a.local_date,'SCHEDULE_PRIVATE','OUTBOUND','09:00','UTC',
              m.id,m.link_id,m.pickup_point_id,?,?,'CLOSED',?,0,0,0,?,?,?, ?,?,?,?
            FROM wa.attendance a JOIN wa.routine_member m ON m.routine_id=a.routine_id AND m.passenger_id=a.passenger_id
            WHERE a.passenger_id=? AND a.local_date=?
            """,UUID.randomUUID(),occurrence,INSTANCE,ENV,credential,DIGEST,DIGEST,ts(past.minusSeconds(7200)),ts(past.minusSeconds(3600)),ts(past.plusSeconds(60)),ts(past.plusSeconds(3600)),ts(past),passenger,date);
    }
    static final class Time extends Clock {Instant now;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}}
}
