package com.vanrides.vanrides.whatsapp.transport;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.privacy.*;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real menu writers and delivery authorization; every actor and coordinate is synthetic. */
@Testcontainers(disabledWithoutDocker=true)
class WaPrivacyRunConversationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_privacy_run");
    private static final String NAME="PESSOA_ALFA_PRIVADA", REASON="MOTIVO_ALFA_PRIVADO";
    private WaRunTestSupport f;
    private WaPrivacyService.Scope scope;
    private final Journal journal=new Journal();
    private UUID reviewBox, reasonInbox;
    @BeforeEach void setup(){
        f=new WaRunTestSupport(POSTGRES);
        scope=new WaPrivacyService.Scope(f.h.settings.environment(),WaTransportTestSupport.INSTANCE);
        f.h.jdbc.update("UPDATE wa.test_cohort SET synthetic=true WHERE id=?",f.attendance.cohort);
        f.h.jdbc.update("UPDATE wa.profile SET display_name=? WHERE identity_id=?",NAME,f.passenger);
        f.h.jdbc.update("UPDATE wa.pickup_point SET latitude=12.3456,longitude=45.6789,reference_text='REFERENCIA_ALFA_PRIVADA' WHERE id=?",f.item.point());
        f.h.jdbc.update("UPDATE wa.routine SET destination_latitude=34.5678,destination_longitude=56.789 WHERE id=?",f.item.routine());
    }

    @Test void realCorrectionAndReasonAreSweptWhileNewThirdPartyResponseAndPostCloseMenusRemainSafe(){
        legacyThenClose();
        assertThat(privacy(f.h.jdbc).erase(scope,f.passenger).status()).isEqualTo("TERMINAL_CONTENT_PENDING");
        assertThat(f.s.string("SELECT display_name FROM wa.profile WHERE identity_id=?",f.passenger)).isEqualTo(NAME);
        UUID fresh=freshThirdPartySummary();
        String before=f.s.string("SELECT row_to_json(o)::text FROM wa.message_outbox o WHERE id=?",fresh);
        var start=f.reviewStart();
        assertThat(start.text()).contains("TERCEIRO_ATUAL").doesNotContain(NAME,REASON);
        f.send("ABANDONAR");
        f.send("CORRIGIR LISTA");
        var members=f.send("1");
        assertThat(members.text()).contains("TERCEIRO_ATUAL").doesNotContain(NAME,REASON);
        sweep();
        assertThat(f.s.string("SELECT payload_json::text FROM wa.message_outbox WHERE id=?",reviewBox)).isEqualTo("{}");
        assertThat(f.s.string("SELECT payload_json->>'text' FROM wa.integration_inbox WHERE id=?",reasonInbox)).isNull();
        assertThat(privacy(f.h.jdbc).erase(scope,f.passenger).status()).isEqualTo("ERASED");
        assertThat(f.s.string("SELECT snapshot_json::text FROM wa.operation_run")).doesNotContain(NAME,REASON,"12.3456","REFERENCIA_ALFA_PRIVADA");
        assertThat(f.s.string("SELECT reason FROM wa.occurrence_roster_override WHERE passenger_id=?",f.passenger)).isNull();
        assertThat(f.s.string("SELECT row_to_json(o)::text FROM wa.message_outbox o WHERE id=?",fresh)).isEqualTo(before);
        assertThat(f.s.number("SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id=?",reasonInbox)).isEqualTo(1);
        assertThat(f.s.number("SELECT count(*) FROM wa.attendance WHERE passenger_id=?",f.passenger)).isEqualTo(1);
        assertThat(privacy(f.h.jdbc).erase(scope,f.passenger).status()).isEqualTo("ALREADY_ERASED");
    }

    @Test void realAuthorizationHoldingOutboxCanWaitForProfileWithoutDeadlockingSubjectErasure() throws Exception {
        legacyThenClose();sweep();
        UUID fresh=freshThirdPartySummary();
        ObjectNode item=null;
        for(var candidate:f.s.outboxClaim().path("items"))if(candidate.path("outboxId").asText().equals(fresh.toString()))item=(ObjectNode)candidate;
        assertThat(item).isNotNull();
        ObjectNode lease=item;
        var profilesLocked=new CountDownLatch(1);var release=new CountDownLatch(1);
        JdbcTemplate probe=new JdbcTemplate(f.h.jdbc.getDataSource()){
            @Override public List<Map<String,Object>> queryForList(String sql,Object...args){
                var result=super.queryForList(sql,args);
                if(sql.contains("ORDER BY identity_id FOR UPDATE SKIP LOCKED")){profilesLocked.countDown();await(release);}
                return result;
            }
        };
        var pool=Executors.newFixedThreadPool(2);
        try{
            var erase=pool.submit(()->privacy(probe).erase(scope,f.passenger));
            assertThat(profilesLocked.await(10,TimeUnit.SECONDS)).isTrue();
            var authorize=pool.submit(()->f.s.authorize(lease));
            boolean waiting=false;
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline){
                waiting=f.h.jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE '%wa.profile%FOR SHARE%')",Boolean.class);
                if(waiting)break;
                Thread.sleep(10);
            }
            assertThat(waiting).as("real authorize owns outbox and waits on the profile fence").isTrue();
            release.countDown();
            assertThat(erase.get(10,TimeUnit.SECONDS).status()).isEqualTo("ERASED");
            assertThat(authorize.get(10,TimeUnit.SECONDS).toString()).contains("TERCEIRO_ATUAL").doesNotContain(NAME,REASON);
            assertThat(f.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?",fresh)).isEqualTo(1);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }

    private void legacyThenClose(){
        f.send("CORRIGIR LISTA");f.send("1");f.send("1");f.send("INCLUIR");
        var review=f.send(REASON);reasonInbox=review.lease().id();
        assertThat(review.text()).contains(NAME,REASON);
        reviewBox=f.h.jdbc.queryForObject("SELECT o.id FROM wa.message_outbox o JOIN wa.command_receipt c ON c.command_id=o.command_id WHERE c.inbox_event_id=?",UUID.class,reasonInbox);
        assertThat(f.s.string("SELECT policy_version FROM wa.message_outbox WHERE id=?",reviewBox)).isEqualTo("run-start-v1");
        assertThat(f.s.string("SELECT run_id::text FROM wa.message_outbox WHERE id=?",reviewBox)).isNull();
        assertThat(f.s.string("SELECT occurrence_id::text FROM wa.message_outbox WHERE id=?",reviewBox)).isNull();
        assertThat(f.s.string("SELECT identity_id::text FROM wa.integration_inbox WHERE id=?",reasonInbox)).isEqualTo(f.driver.toString());
        f.send("CONFIRMAR "+f.marker(review)).decision().requireSuccess();
        UUID run=f.preparation.prepare(f.driver,f.item.occurrence(),f.digest());
        f.h.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED',completed_at=? WHERE id=?",Timestamp.from(f.h.clock.now()),run);
        assertThat(privacy(f.h.jdbc).close(scope,f.passenger).status()).isEqualTo("CLOSED");
        f.h.time.advance(31L*86400);f.renewExpiredTransportCredentials();
        f.h.connection(f.s.channel,"open",f.h.clock.now());
    }
    private UUID freshThirdPartySummary(){
        UUID third=f.attendance.passenger();
        f.h.jdbc.update("UPDATE wa.profile SET display_name='TERCEIRO_ATUAL' WHERE identity_id=?",third);
        var current=f.attendance.question(third,f.h.clock.now().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate(),"OUTBOUND","15:00");
        f.h.jdbc.update("UPDATE wa.routine SET stop_order_pending=false,destination_latitude=34.5678,destination_longitude=56.789 WHERE id=?",current.routine());
        f.h.jdbc.update("UPDATE wa.pickup_point SET latitude=23.4567,longitude=45.6789 WHERE id=?",current.point());
        var response=f.attendance.send(f.driver,"PRESENÇAS");
        assertThat(response.text()).contains("TERCEIRO_ATUAL").doesNotContain(NAME,REASON);
        return UUID.fromString(response.decision().body().path("responseIds").get(0).asText());
    }
    private void sweep(){
        var job=f.h.principal(WaPrincipal.Kind.SYSTEM_JOB);
        var maintenance=new WaPrivacyTerminalMaintenance(f.h.jdbc,f.h.manager,f.h.clock,WaPrivacyPolicy.syntheticTests(),false);
        WaPrivacyTerminalMaintenance.Position cursor=null;
        for(int page=0;page<20;page++){
            var result=maintenance.tick(job,100,cursor);
            if(result.complete())return;
            assertThat(result.deferred()).isZero();cursor=result.next();
        }
        throw new AssertionError("Synthetic terminal content did not finish");
    }
    private WaPrivacyService privacy(JdbcTemplate jdbc){return new WaPrivacyService(jdbc,f.h.manager,f.h.clock,WaPrivacyPolicy.syntheticTests(),journal);}
    private static void await(CountDownLatch latch){
        try{if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("Synthetic profile lock timeout");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
    }
    private static final class Journal implements WaPrivacyJournal{
        private final Map<UUID,Ack> records=new LinkedHashMap<>();
        public Ack append(Entry entry){
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return records.computeIfAbsent(entry.eventId(),id->new Ack(id,entry.payloadHash(),records.size()+1,WaCrypto.digest(entry.payloadHash())));
        }
        public boolean verify(Entry entry,Ack ack){return ack.equals(records.get(entry.eventId()));}
    }
}
