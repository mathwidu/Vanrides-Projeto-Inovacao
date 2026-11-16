package com.vanrides.vanrides.whatsapp.privacy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;

/** Two active passengers, one old terminal day, a recent day and a future day created long ago. */
final class WaPrivacyHistoryFixture {
    final WaPrivacyTestSupport f;
    final UUID oldClosed=UUID.randomUUID(), open=UUID.randomUUID(), recentClosed=UUID.randomUUID();
    final UUID deliveryOpen=UUID.randomUUID(), deliveryRecent=UUID.randomUUID(), attributableAudit=UUID.randomUUID();
    final UUID recentOccurrence=UUID.randomUUID(), futureOccurrence=UUID.randomUUID();
    final Instant old;
    WaPrivacyHistoryFixture(PostgreSQLContainer<?> postgres) {
        f=new WaPrivacyTestSupport(postgres,true); old=f.time.now; historicalOrigin();
        f.time.days(91);
        support(oldClosed,"CLOSED",old,old,f.inboxes.get(f.first).toString());
        support(open,"OPEN",old,null,"open-old");
        support(recentClosed,"CLOSED",old,f.time.now,"recently-closed");
        deliverySupport(deliveryOpen,"OPEN",null,f.first);
        deliverySupport(deliveryRecent,"CLOSED",f.time.now,f.second);
        var job=f.job(WaPrivacyTestSupport.ENV,WaPrivacyTestSupport.INSTANCE);
        f.jdbc.update("""
            INSERT INTO wa.audit_event(id,actor_identity_id,credential_id,actor_kind,action,correlation_id,created_at)
            VALUES (?, ?,?,'SYSTEM_JOB','SYNTHETIC_HISTORY','synthetic-history',?)
            """,attributableAudit,f.first,job.credentialId(),Timestamp.from(old));
        day(recentOccurrence,f.time.now.minusSeconds(3600),f.time.now.minusSeconds(1800),f.time.now.minusSeconds(3600));
        day(futureOccurrence,f.time.now.plusSeconds(10*86400),old,old);
    }
    WaPrivacyHistoryRetention retention(boolean dryRun) {
        return new WaPrivacyHistoryRetention(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.syntheticTests(),dryRun);
    }
    void support(UUID id,String state,Instant created,Instant closed,String correlation) {
        f.jdbc.update("""
            INSERT INTO wa.support_case(id,identity_id,deduplication_key,reason_code,state,correlation_id,created_at,closed_at)
            VALUES (?, ?,?,'SYNTHETIC_HISTORY',?,?,?,?)
            """,id,f.first,"synthetic-"+id,state,correlation,Timestamp.from(created),closed==null ? null : Timestamp.from(closed));
    }
    private void deliverySupport(UUID id,String state,Instant closed,UUID recipient) {
        UUID outbox=f.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE recipient_identity_id=?",UUID.class,recipient);
        f.jdbc.update("""
            INSERT INTO wa.support_case(id,identity_id,purpose,delivery_outbox_id,environment,instance_id,
              deduplication_key,reason_code,state,correlation_id,created_at,closed_at)
            VALUES (?,?,'DELIVERY',?,?,?,?,'DELIVERY_UNKNOWN_EXPIRED',?,?,?,?)
            """,id,recipient,outbox,WaPrivacyTestSupport.ENV,WaPrivacyTestSupport.INSTANCE,"delivery-unknown:"+outbox,
            state,outbox.toString(),Timestamp.from(old),closed==null ? null : Timestamp.from(closed));
    }
    private void historicalOrigin() {
        UUID command=UUID.randomUUID();
        f.jdbc.update("""
            INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,
              request_hash,result_json,created_at,expires_at) VALUES (?,?,?,?,'SYNTHETIC_HISTORY',?,'{}',?,?)
            """,command,f.driver,f.inboxes.get(f.driver),"synthetic-history-"+command,"f".repeat(64),
            Timestamp.from(old),Timestamp.from(old.plusSeconds(3600)));
        f.jdbc.update("""
            INSERT INTO wa.start_intent(id,driver_id,occurrence_id,run_id,token_hash,environment,state,expires_at,created_at,request_command_id)
            VALUES (?,?,?,?,?,?,'REVOKED',?,?,?)
            """,UUID.randomUUID(),f.driver,f.occurrence,f.run,"a".repeat(64),WaPrivacyTestSupport.ENV,
            Timestamp.from(old.plusSeconds(3600)),Timestamp.from(old),command);
    }
    private void day(UUID occurrence,Instant scheduled,Instant completed,Instant created) {
        var localDate=scheduled.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        f.jdbc.update("""
            INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at,created_at)
            VALUES (?,?,?,?,?,?)
            """,occurrence,f.routine,f.driver,localDate,Timestamp.from(scheduled),Timestamp.from(created));
        f.jdbc.update("""
            INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,
              snapshot_json,snapshot_at,started_at,completed_at,created_at)
            VALUES (?,?,?,?,?,'COMPLETED','synthetic-history','{"privateCopy":"KEEP_UNTIL_ELIGIBLE"}',?,?,?,?)
            """,UUID.randomUUID(),occurrence,f.routine,f.driver,localDate,
            Timestamp.from(created),Timestamp.from(created),Timestamp.from(completed),Timestamp.from(created));
        for (UUID person : java.util.List.of(f.first,f.second)) f.jdbc.update("""
            INSERT INTO wa.attendance(id,routine_id,passenger_id,local_date,created_at) VALUES (?,?,?,?,?)
            """,UUID.randomUUID(),f.routine,person,localDate,Timestamp.from(created));
    }
}
