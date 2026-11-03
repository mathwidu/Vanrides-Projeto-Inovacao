package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static com.vanrides.vanrides.whatsapp.transport.WaDeliveryQuotaTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaDeliveryQuotaIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=postgres("wa_delivery_quota");
    private WaDeliveryConnectionTestSupport f;
    @BeforeEach void setup() { f=new WaDeliveryConnectionTestSupport(POSTGRES); f.connection("open"); }
    @Test void recipientQuotaDefersWithoutAttemptAndOldLeaseCannotBypassTheExactWindowBoundary() {
        var box=f.enqueue(); var lease=f.lease(); var legacy=legacy(f,"transport-test",INSTANCE);
        history(f,box.actor(),legacy,12,NOW);
        assertThat(decision(lease).error()).isEqualTo(WaProblem.Code.RATE_LIMITED);
        assertDeferred(box.id(),NOW.plusSeconds(60)); assertThat(f.count("message_attempt")).isEqualTo(12);
        assertThatThrownBy(()->f.authorize(lease)).isInstanceOf(WaProblem.class);
        f.h.time.advance(59); assertThat(claim(f,1).path("items")).isEmpty();
        f.h.time.advance(1); var next=f.lease(); assertThat(next.path("leaseToken")).isNotEqualTo(lease.path("leaseToken"));
        assertThat(f.h.jdbc.queryForObject("SELECT lease_generation FROM wa.message_outbox WHERE id=?",Long.class,box.id())).isEqualTo(2L);
        assertThat(f.authorize(next).path("attemptToken").asText()).hasSize(43); assertThat(f.count("message_attempt")).isEqualTo(13);
    }
    @Test void globalQuotaSurvivesRestartRotationRevocationAndAllResultStates() {
        var box=f.enqueue(); var lease=f.lease(); var old=legacy(f,"transport-test",INSTANCE);
        history(f,null,old,30,NOW); f.h.jdbc.update("UPDATE wa.service_credential SET revoked_at=? WHERE id=?",Timestamp.from(NOW),old.credentialId());
        var restarted=f.service();
        var result=restarted.authorize(f.worker,box.id(),json("{}").put("leaseToken",lease.path("leaseToken").asText()));
        assertThat(result.error()).isEqualTo(WaProblem.Code.RATE_LIMITED); assertDeferred(box.id(),NOW.plusSeconds(60));
        assertThat(f.count("message_attempt")).isEqualTo(30);
        assertThat(f.h.jdbc.queryForObject("SELECT count(DISTINCT state) FROM wa.message_attempt",Integer.class)).isEqualTo(6);
    }
    @Test void overfullGlobalHistoryUsesTheThirtiethMostRecentRatherThanOldestAuthorization() {
        var box=f.enqueue(); var lease=f.lease(); var old=legacy(f,"transport-test",INSTANCE);
        history(f,null,old,NOW.minusSeconds(59),"SUBMITTED"); history(f,null,old,NOW.minusSeconds(50),"SUBMITTED");
        history(f,null,old,NOW.minusSeconds(40),"SUBMITTED"); history(f,null,old,29,NOW.minusSeconds(10));
        assertThat(decision(lease).error()).isEqualTo(WaProblem.Code.RATE_LIMITED); assertDeferred(box.id(),NOW.plusSeconds(20));
        assertThat(f.count("message_attempt")).isEqualTo(32);
    }
    @Test void overfullRecipientHistoryUsesTheTwelfthMostRecentAuthorization() {
        var box=f.enqueue(); var lease=f.lease(); var old=legacy(f,"transport-test",INSTANCE);
        history(f,box.actor(),old,NOW.minusSeconds(59),"SUBMITTED"); history(f,box.actor(),old,NOW.minusSeconds(40),"FAILED");
        history(f,box.actor(),old,11,NOW.minusSeconds(10));
        assertThat(decision(lease).error()).isEqualTo(WaProblem.Code.RATE_LIMITED); assertDeferred(box.id(),NOW.plusSeconds(20));
    }
    @Test void saturatedRecipientsAreFilteredBeforeTheClaimLimit() {
        var box=f.enqueue(); var lease=f.lease(); var old=legacy(f,"transport-test",INSTANCE); history(f,box.actor(),old,12,NOW);
        f.enqueue(box.actor()); f.h.time.advance(1); var other=f.enqueue();
        var response=claim(f,1); assertThat(response.path("items")).hasSize(1);
        assertThat(response.path("items").get(0).path("outboxId").asText()).isEqualTo(other.id().toString());
        assertThat(decision(lease).error()).isEqualTo(WaProblem.Code.RATE_LIMITED);
    }
    @Test void anotherInstanceOrEnvironmentDoesNotSpendThisScopeQuota() {
        var box=f.enqueue(); var lease=f.lease();
        history(f,null,legacy(f,"other-test",INSTANCE),30,NOW);
        history(f,null,legacy(f,"transport-test","synthetic-other"),30,NOW);
        assertThat(f.authorize(lease).path("attemptToken").asText()).hasSize(43);
        assertThat(f.count("message_attempt")).isEqualTo(61);
    }
    @Test void nextSlotAtTheBusinessDeadlineExpiresAndPurgesInsteadOfViolatingNotBefore() {
        var driver=new WaDriverTestSupport(POSTGRES); UUID actor=driver.driver(); driver.invite(actor);
        var item=driver.privateItem(actor); UUID box=WaJson.uuid(item,"outboxId");
        var fixture=new WaDeliveryConnectionTestSupport(driver.h); var old=legacy(fixture,"transport-test",INSTANCE);
        history(fixture,actor,old,12,driver.h.clock.now());
        driver.h.jdbc.update("UPDATE wa.message_outbox SET expires_at=? WHERE id=?",Timestamp.from(driver.h.clock.now().plusSeconds(60)),box);
        var result=driver.s.outbox.authorize(driver.s.delivery,box,json("{}").put("leaseToken",item.path("leaseToken").asText()));
        assertThat(result.error()).isEqualTo(WaProblem.Code.RATE_LIMITED);
        assertThat(driver.s.string("SELECT state FROM wa.message_outbox WHERE id=?",box)).isEqualTo("EXPIRED");
        assertThat(driver.h.jdbc.queryForObject("SELECT private_payload_ciphertext FROM wa.message_outbox WHERE id=?",byte[].class,box)).isNull();
        assertThat(driver.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?",box)).isZero();
    }
    @Test void deferredPrivatePayloadIsPreservedWithoutRenewingItsOriginalTtl() {
        var driver=new WaDriverTestSupport(POSTGRES); UUID actor=driver.driver(); driver.invite(actor);
        var item=driver.privateItem(actor); UUID box=WaJson.uuid(item,"outboxId");
        byte[] cipher=driver.h.jdbc.queryForObject("SELECT private_payload_ciphertext FROM wa.message_outbox WHERE id=?",byte[].class,box);
        Timestamp ttl=driver.h.jdbc.queryForObject("SELECT expires_at FROM wa.message_outbox WHERE id=?",Timestamp.class,box);
        var fixture=new WaDeliveryConnectionTestSupport(driver.h); history(fixture,actor,legacy(fixture,"transport-test",INSTANCE),12,driver.h.clock.now());
        assertThat(driver.s.outbox.authorize(driver.s.delivery,box,json("{}").put("leaseToken",item.path("leaseToken").asText())).error()).isEqualTo(WaProblem.Code.RATE_LIMITED);
        assertThat(driver.h.jdbc.queryForObject("SELECT private_payload_ciphertext FROM wa.message_outbox WHERE id=?",byte[].class,box)).isEqualTo(cipher);
        assertThat(driver.h.jdbc.queryForObject("SELECT expires_at FROM wa.message_outbox WHERE id=?",Timestamp.class,box)).isEqualTo(ttl);
    }
    @Test void quotaSlotAfterGpsFreshnessExpiresTheNoticeWithoutCreatingAnAttempt() {
        var t=new WaTrackingTestSupport(POSTGRES); t.activate(); t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5)));
        UUID box=WaRunGpsDeliveryTestSupport.notice(t,"run-gps-confirmed"); var item=WaRunGpsDeliveryTestSupport.lease(t,box);
        var fixture=new WaDeliveryConnectionTestSupport(t.h); history(fixture,t.f.driver,legacy(fixture,"transport-test",INSTANCE),12,t.h.clock.now());
        assertThat(t.f.s.outbox.authorize(t.f.s.delivery,box,json("{}").put("leaseToken",item.path("leaseToken").asText())).error()).isEqualTo(WaProblem.Code.RATE_LIMITED);
        assertThat(t.f.s.string("SELECT state FROM wa.message_outbox WHERE id=?",box)).isEqualTo("EXPIRED");
        assertThat(WaRunGpsDeliveryTestSupport.attempts(t,box)).isZero();
    }
    @Test void responseOutranksAnOlderReminder() {
        var attendance=new WaAttendanceDeliveryTestSupport(POSTGRES); var reminder=attendance.reminder();
        var fixture=new WaDeliveryConnectionTestSupport(attendance.h); var response=fixture.enqueue();
        var items=fixture.claim().path("items"); assertThat(items).hasSize(2);
        assertThat(items.get(0).path("outboxId").asText()).isEqualTo(response.id().toString());
        assertThat(items.get(1).path("outboxId").asText()).isEqualTo(reminder.outbox().toString());
        assertThat(items.get(0).path("purpose").asText()).isEqualTo("COMMAND_RESPONSE");
    }
    private com.vanrides.vanrides.whatsapp.api.WaDecision decision(ObjectNode lease) {
        return f.outbox.authorize(f.worker,WaJson.uuid(lease,"outboxId"),json("{}").put("leaseToken",lease.path("leaseToken").asText()));
    }
    private void assertDeferred(UUID box,Instant slot) {
        var row=f.h.jdbc.queryForMap("SELECT state,not_before,lease_generation,lease_credential_id,lease_token_hash,lease_expires_at FROM wa.message_outbox WHERE id=?",box);
        assertThat(row.get("state")).isEqualTo("PENDING"); assertThat(row.get("not_before")).isEqualTo(Timestamp.from(slot));
        assertThat(row.get("lease_generation")).isEqualTo(1L); assertThat(row.get("lease_credential_id")).isNull();
        assertThat(row.get("lease_token_hash")).isNull(); assertThat(row.get("lease_expires_at")).isNull();
    }
}
