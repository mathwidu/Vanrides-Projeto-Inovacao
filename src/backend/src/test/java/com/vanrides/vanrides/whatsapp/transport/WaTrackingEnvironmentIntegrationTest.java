package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaTrackingEnvironmentIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_tracking_environment");
    @Test void watchdogNeverDegradesExpiresOrPurgesAnotherEnvironment() {
        var t=new WaTrackingTestSupport(POSTGRES); t.activate();
        t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5)));
        Foreign other=foreign(t);
        var before=t.h.jdbc.queryForMap("SELECT state,gps_state,last_captured_at,lease_expires_at,version FROM wa.tracking_session WHERE id=?",other.session());
        byte[] ciphertext=t.h.jdbc.queryForObject("SELECT start_payload_ciphertext FROM wa.message_outbox WHERE id=?",byte[].class,other.outbox());
        t.h.time.advance(31); t.watchdog.tick();
        assertEquals("DEGRADED",t.runState());
        assertEquals(before,t.h.jdbc.queryForMap("SELECT state,gps_state,last_captured_at,lease_expires_at,version FROM wa.tracking_session WHERE id=?",other.session()));
        assertEquals("TRACKING",t.f.s.string("SELECT state FROM wa.operation_run WHERE id=?",other.run()));
        assertEquals("PENDING",t.f.s.string("SELECT state FROM wa.start_intent WHERE id=?",other.intent()));
        assertEquals("PENDING",t.f.s.string("SELECT state FROM wa.message_outbox WHERE id=?",other.outbox()));
        assertArrayEquals(ciphertext,t.h.jdbc.queryForObject("SELECT start_payload_ciphertext FROM wa.message_outbox WHERE id=?",byte[].class,other.outbox()));
        t.h.transaction.executeWithoutResult(tx->new WaDeliveryReconciler(t.h.jdbc,t.h.clock).refresh(WaTransportTestSupport.INSTANCE,t.h.settings.environment()));
        assertArrayEquals(ciphertext,t.h.jdbc.queryForObject("SELECT start_payload_ciphertext FROM wa.message_outbox WHERE id=?",byte[].class,other.outbox()));
    }
    private Foreign foreign(WaTrackingTestSupport t) {
        UUID cohort=UUID.randomUUID(),driver=UUID.randomUUID(),device=UUID.randomUUID(),routine=UUID.randomUUID(),occurrence=UUID.randomUUID();
        UUID run=UUID.randomUUID(),session=UUID.randomUUID(),event=UUID.randomUUID(),command=UUID.randomUUID(),intent=UUID.randomUUID(),box=UUID.randomUUID();
        Timestamp now=Timestamp.from(t.h.clock.now()),until=Timestamp.from(t.h.clock.now().plusSeconds(10));
        t.h.jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'foreign-test',true)",cohort);
        t.h.jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)",driver,cohort);
        t.h.jdbc.update("INSERT INTO wa.profile(identity_id,role,status,driver_enabled) VALUES (?,'DRIVER','ACTIVE',true)",driver);
        t.h.jdbc.update("INSERT INTO wa.driver_authorization(driver_id,generation) VALUES (?,1)",driver);
        t.h.jdbc.update("""
            INSERT INTO wa.driver_device(id,driver_id,jkt,public_jwk,generation,environment,platform,app_version)
            VALUES (?,?,?,'{}',1,'foreign-test','ANDROID','synthetic-env-110')
            """,device,driver,WaCrypto.randomToken());
        t.h.jdbc.update("""
            INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,destination_latitude,destination_longitude,state,policy_version)
            VALUES (?,?,'Rotina sintética isolada','OUTBOUND',ARRAY[2]::SMALLINT[],'12:00','UTC',0,0,'ACTIVE','synthetic-env-v1')
            """,routine,driver);
        t.h.jdbc.update("INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at) VALUES (?,?,?,'2030-01-01',?)",occurrence,routine,driver,now);
        t.h.jdbc.update("""
            INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_at)
            VALUES (?,?,?,?,'2030-01-01','TRACKING','synthetic-env-v1',?)
            """,run,occurrence,routine,driver,now);
        t.h.jdbc.update("""
            INSERT INTO wa.tracking_session(id,run_id,driver_id,device_id,generation,state,lease_expires_at,last_captured_at,last_latitude,last_longitude,last_accuracy_m,gps_state)
            VALUES (?,?,?,?,1,'ACTIVE',?,?,0,0,5,'FRESH')
            """,session,run,driver,device,Timestamp.from(t.h.clock.now().plusSeconds(300)),now);
        t.h.jdbc.update("INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category) VALUES (?,?,'SYNTHETIC','foreign-instance',?,'MESSAGE')",event,driver,event.toString());
        t.h.jdbc.update("""
            INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,request_hash,result_json,created_at,expires_at)
            VALUES (?,?,?,?,'REQUEST_START',?,'{}',?,?)
            """,command,driver,event,command.toString(),WaCrypto.digest("synthetic-foreign"),now,Timestamp.from(t.h.clock.now().plusSeconds(300)));
        t.h.jdbc.update("""
            INSERT INTO wa.start_intent(id,driver_id,occurrence_id,device_id,run_id,token_hash,environment,expires_at,created_at,request_command_id)
            VALUES (?,?,?,?,?,?,'foreign-test',?,?,?)
            """,intent,driver,occurrence,device,run,WaCrypto.digest(WaCrypto.randomToken()),until,now,command);
        t.h.jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,command_id,response_part,policy_version,start_intent_id,start_payload_ciphertext,not_before,expires_at,created_at)
            VALUES (?,?,'RESPONSE',?,'run-start','run-start-v1',?,?,?,?,?)
            """,box,driver,command,intent,t.h.crypto.encrypt("synthetic-foreign-outbox","synthetic-fragment"),now,until,now);
        return new Foreign(run,session,intent,box);
    }
    private record Foreign(UUID run,UUID session,UUID intent,UUID outbox) { }
}
