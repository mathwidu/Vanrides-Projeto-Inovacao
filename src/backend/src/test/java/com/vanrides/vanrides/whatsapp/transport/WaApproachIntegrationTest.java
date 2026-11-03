package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.tracking.WaTrackingService;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaApproachIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_approach_130");
    private WaTrackingTestSupport t; private WaTrackingService tracking; private WaOutboxService outbox;
    @BeforeEach void setup() { t=new WaTrackingTestSupport(POSTGRES); }
    private void enable(boolean enabled,UUID reviewed) {
        var env=new MockEnvironment().withProperty("vanrides.wa.enabled","true").withProperty("vanrides.wa.environment",t.h.settings.environment())
            .withProperty("vanrides.wa.public-base-url",t.h.settings.baseUrl()).withProperty("vanrides.wa.start-link-base-url",t.h.settings.startLinkBaseUrl())
            .withProperty("vanrides.wa.hash-key",java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(t.h.settings.hashKey()))
            .withProperty("vanrides.wa.response-key",java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(t.h.settings.responseKey()))
            .withProperty("vanrides.wa.operator-credential",t.h.settings.operatorCredential()).withProperty("vanrides.wa.operator-expires-at",t.h.settings.operatorExpiresAt().toString())
            .withProperty("vanrides.wa.approach-enabled",Boolean.toString(enabled)).withProperty("vanrides.wa.approach-reviewed-routine-ids",reviewed.toString());
        var settings=new WaSettings(env); tracking=t.h.proxy(new WaTrackingService(t.h.jdbc,settings,t.h.clock,t.h.crypto));
        outbox=t.h.proxy(new WaOutboxService(t.h.jdbc,new WaTransportAccess(t.h.jdbc,settings,t.h.clock),new WaDeliveryReconciler(t.h.jdbc,t.h.clock),settings,t.h.clock));
    }
    private void start() { t.activate(); enable(true,t.f.item.routine()); }
    private ObjectNode fix(long seq,long offset,double lon,double heading) { return t.sample(seq,offset,5).put("longitude",lon).put("headingDeg",heading); }
    private void approaching() { tracking.samples(t.actor,t.session(),t.batch(fix(1,-10,-.007,90),fix(2,-5,-.0055,90),fix(3,0,-.004,90))); }
    private long count() { return t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE purpose='APPROACH'"); }
    private UUID box() { return t.h.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE purpose='APPROACH' LIMIT 1",UUID.class); }
    private ObjectNode lease() {
        for(var item:outbox.claim(t.f.s.delivery,WaJson.object("{}").put("batchLimit",10).put("leaseSeconds",60)).path("items"))
            if(item.path("outboxId").asText().equals(box().toString())) return (ObjectNode)item;
        throw new AssertionError("Expected approach notice");
    }
    @Test void flagIsOffByDefaultAndNoEphemeralHistoryIsCollected() {
        t.activate(); t.tracking.samples(t.actor,t.session(),t.batch(fix(1,-10,-.007,90),fix(2,-5,-.0055,90),fix(3,0,-.004,90)));
        assertEquals(0,count()); assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.approach_fix")); assertFalse(t.h.settings.approachEnabled());
    }
    @Test void threeCoherentFixesEnqueueAtomicallyAndDeliveryRevalidatesCurrentTrajectory() {
        start(); approaching(); assertEquals(1,count());
        assertEquals("APPROACHING",t.f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?",t.run()));
        var item=lease(); assertEquals("APPROACH",item.path("purpose").asText());
        var authorized=outbox.authorize(t.f.s.delivery,box(),WaJson.object("{}").put("leaseToken",item.path("leaseToken").asText())).requireSuccess();
        assertEquals("TEXT",authorized.path("content").path("type").asText());
        assertFalse(authorized.path("content").path("text").asText().contains("minutos"));
        assertFalse(t.f.s.string("SELECT payload_json::text FROM wa.message_outbox WHERE id=?",box()).contains("latitude"));
    }
    @Test void unreviewedRoutineSuppressesAndCountsOpportunityEvenWithGlobalFlagEnabled() {
        var reviewed=t.f.attendance.question(t.f.passenger,WaRunTestSupport.DAY,"RETURN","16:00");
        t.activate(); enable(true,reviewed.routine()); approaching();
        assertEquals(0,count()); assertEquals("UNREVIEWED_GEOGRAPHY",t.f.s.string("SELECT last_reason FROM wa.approach_progress WHERE run_id=?",t.run()));
        assertTrue(t.f.s.number("SELECT suppressed_count FROM wa.approach_progress WHERE run_id=?",t.run())>0);
    }
    @Test void replayAndPolicyVersionChangeNeverDuplicateTheBusinessKey() {
        start(); approaching(); t.h.jdbc.update("UPDATE wa.message_outbox SET policy_version='older-policy' WHERE purpose='APPROACH'");
        t.h.time.advance(3); tracking.samples(t.actor,t.session(),t.batch(fix(4,0,-.003,90)));
        assertEquals(1,count()); assertEquals(3,t.f.s.number("SELECT count(*) FROM wa.approach_fix"));
    }
    @Test void optOutAndLateCancellationSuppressTheRecipient() {
        start(); t.h.jdbc.update("UPDATE wa.profile SET preferences_json=preferences_json||'{\"APPROACH_MESSAGES\":false}'::jsonb WHERE identity_id=?",t.f.passenger);
        approaching(); assertEquals(0,count());
        t.h.jdbc.update("UPDATE wa.profile SET preferences_json=preferences_json||'{\"APPROACH_MESSAGES\":true}'::jsonb WHERE identity_id=?",t.f.passenger);
        t.h.jdbc.update("UPDATE wa.run_stop SET state='CANCELED',canceled_at=? WHERE run_id=?",Timestamp.from(t.h.clock.now()),t.run());
        t.h.time.advance(3);tracking.samples(t.actor,t.session(),t.batch(fix(4,0,-.003,90)));assertEquals(0,count());
    }
    @Test void optOutBetweenClaimAndAuthorizePreventsAnyAttempt() {
        start();approaching();var item=lease();
        t.h.jdbc.update("UPDATE wa.profile SET preferences_json=preferences_json||'{\"APPROACH_MESSAGES\":false}'::jsonb WHERE identity_id=?",t.f.passenger);
        assertNotNull(outbox.authorize(t.f.s.delivery,box(),WaJson.object("{}").put("leaseToken",item.path("leaseToken").asText())).error());
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?",box()));
    }
    @Test void staleFixesAndWatchdogDegradationPreventDelayedSubmission() {
        start();approaching();var item=lease();t.h.time.advance(21);t.watchdog.tick();
        assertNotNull(outbox.authorize(t.f.s.delivery,box(),WaJson.object("{}").put("leaseToken",item.path("leaseToken").asText())).error());
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?",box()));
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.approach_fix"));
    }
    @Test void aLaterNearbyStopNeverBypassesTheFirstUnresolvedPhysicalStop() {
        UUID peer=t.f.attendance.passenger();var extra=t.f.attendance.question(peer,WaRunTestSupport.DAY,"RETURN","16:00");
        t.h.jdbc.update("INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order) VALUES (?,?,?,?,?,?,2)",
            UUID.randomUUID(),t.f.item.routine(),t.f.driver,peer,extra.link(),extra.point());
        t.h.jdbc.update("UPDATE wa.pickup_point SET longitude=.02 WHERE id=?",t.f.item.point());start();approaching();
        assertEquals(0,count());assertEquals(2,t.f.s.number("SELECT count(*) FROM wa.run_stop WHERE run_id=? AND state='WAITING'",t.run()));
    }
    @Test void oppositeHeadingAndGpsJumpSuspendAnOtherwiseNearbyDecision() {
        start(); tracking.samples(t.actor,t.session(),t.batch(fix(1,-10,-.007,270),fix(2,-5,-.0055,270),fix(3,0,-.004,270)));
        assertEquals(0,count());assertEquals("DIRECTION_UNCERTAIN",t.f.s.string("SELECT last_reason FROM wa.approach_progress WHERE run_id=?",t.run()));
        t.h.time.advance(1);tracking.samples(t.actor,t.session(),t.batch(fix(4,0,.02,90)));assertEquals(0,count());
        assertEquals("GPS_JUMP",t.f.s.string("SELECT last_reason FROM wa.approach_progress WHERE run_id=?",t.run()));
    }
    @Test void passageRequiresNearFixThenConsistentDepartureAndProgressNeverRegresses() {
        start();approaching(); t.h.time.advance(10);tracking.samples(t.actor,t.session(),t.batch(fix(4,-5,-.002,90),fix(5,0,-.0003,90)));
        t.h.time.advance(15); tracking.samples(t.actor,t.session(),t.batch(fix(6,-10,.001,90),fix(7,-5,.0025,90),fix(8,0,.004,90)));
        assertEquals("PASSED",t.f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?",t.run()));
        t.h.time.advance(10);tracking.samples(t.actor,t.session(),t.batch(fix(9,-5,-.002,270),fix(10,0,-.003,270)));
        assertEquals("PASSED",t.f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?",t.run()));assertEquals(1,count());
    }
    @Test void aSharedPhysicalPointAdvancesTogetherButEachRecipientGetsOneMessage() {
        UUID peer=t.f.attendance.passenger();var extra=t.f.attendance.question(peer,WaRunTestSupport.DAY,"RETURN","16:00");
        t.h.jdbc.update("INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order) VALUES (?,?,?,?,?,?,2)",
            UUID.randomUUID(),t.f.item.routine(),t.f.driver,peer,extra.link(),extra.point());
        start();approaching();assertEquals(2,count());
        assertEquals(1,t.f.s.number("SELECT count(DISTINCT physical_stop_key) FROM wa.run_stop WHERE run_id=?",t.run()));
        assertEquals(2,t.f.s.number("SELECT count(DISTINCT recipient_identity_id) FROM wa.message_outbox WHERE purpose='APPROACH'"));
        assertEquals(2,t.f.s.number("SELECT count(*) FROM wa.run_stop WHERE run_id=? AND state='APPROACHING'",t.run()));
    }
    @Test void missedStopWithConsistentNearPassageIsSkippedWithoutReorderingItsSnapshot() {
        start();approaching();t.h.time.advance(10);tracking.samples(t.actor,t.session(),t.batch(fix(4,-5,-.002,90),fix(5,0,-.001,90)));
        t.h.time.advance(20);tracking.samples(t.actor,t.session(),t.batch(fix(6,-10,.0015,90),fix(7,-5,.003,90),fix(8,0,.0045,90)));
        assertEquals("SKIPPED",t.f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?",t.run()));
        assertEquals(1,t.f.s.number("SELECT stop_order FROM wa.run_stop WHERE run_id=?",t.run()));
        assertEquals("PENDING",t.f.s.string("SELECT attendance_snapshot FROM wa.run_stop WHERE run_id=?",t.run()));
    }
    @Test void staleAndLowAccuracySamplesDoNotEnterTheDecisionWindow() {
        start();var ack=tracking.samples(t.actor,t.session(),t.batch(fix(1,-21,-.007,90),fix(2,-5,-.0055,90).put("accuracyM",51),fix(3,0,-.004,90)));
        assertEquals(2,ack.path("rejected").size());assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.approach_fix"));assertEquals(0,count());
    }
    @Test void databaseRejectsRegressionAndCancellationAfterClaimPreventsSubmission() {
        start();approaching();var item=lease();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->t.h.jdbc.update("UPDATE wa.run_stop SET state='WAITING' WHERE run_id=?",t.run()));
        t.h.jdbc.update("UPDATE wa.run_stop SET state='CANCELED',canceled_at=? WHERE run_id=?",Timestamp.from(t.h.clock.now()),t.run());
        assertNotNull(outbox.authorize(t.f.s.delivery,box(),WaJson.object("{}").put("leaseToken",item.path("leaseToken").asText())).error());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->t.h.jdbc.update("UPDATE wa.run_stop SET state='APPROACHING' WHERE run_id=?",t.run()));
    }
    @Test void sharedPointSeparatedByAnotherPointUsesTheMinimumGroupOrderForEveryRecipient() {
        UUID middle=t.f.attendance.passenger(),peer=t.f.attendance.passenger();
        var b=t.f.attendance.question(middle,WaRunTestSupport.DAY,"RETURN","16:00");var a=t.f.attendance.question(peer,WaRunTestSupport.DAY,"RETURN","17:00");
        t.h.jdbc.update("UPDATE wa.pickup_point SET longitude=.02 WHERE id=?",b.point());
        t.h.jdbc.update("INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order) VALUES (?,?,?,?,?,?,2)",UUID.randomUUID(),t.f.item.routine(),t.f.driver,middle,b.link(),b.point());
        t.h.jdbc.update("INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order) VALUES (?,?,?,?,?,?,3)",UUID.randomUUID(),t.f.item.routine(),t.f.driver,peer,a.link(),a.point());
        start();approaching();assertEquals(2,count());
        UUID peerBox=t.h.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE purpose='APPROACH' AND recipient_identity_id=?",UUID.class,peer);
        ObjectNode selected=null;for(var item:outbox.claim(t.f.s.delivery,WaJson.object("{}").put("batchLimit",10).put("leaseSeconds",60)).path("items")) if(item.path("outboxId").asText().equals(peerBox.toString())) selected=(ObjectNode)item;
        assertNotNull(selected);assertNull(outbox.authorize(t.f.s.delivery,peerBox,WaJson.object("{}").put("leaseToken",selected.path("leaseToken").asText())).error());
        assertEquals("WAITING",t.f.s.string("SELECT state FROM wa.run_stop WHERE run_id=? AND passenger_id=?",t.run(),middle));
    }
    @Test void aNinetySecondStopWithFreshGpsPreservesArrivalAndAllowsTheNextPointAfterDeparture() {
        UUID peer=t.f.attendance.passenger();var extra=t.f.attendance.question(peer,WaRunTestSupport.DAY,"RETURN","16:00");
        t.h.jdbc.update("UPDATE wa.pickup_point SET longitude=.01 WHERE id=?",extra.point());
        t.h.jdbc.update("INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order) VALUES (?,?,?,?,?,?,2)",UUID.randomUUID(),t.f.item.routine(),t.f.driver,peer,extra.link(),extra.point());
        start();approaching();t.h.time.advance(10);tracking.samples(t.actor,t.session(),t.batch(fix(4,-5,-.002,90),fix(5,0,-.0003,90)));
        long sequence=5;for(int i=0;i<9;i++) { t.h.time.advance(10);tracking.samples(t.actor,t.session(),t.batch(fix(++sequence,0,-.0003,90))); }
        assertTrue(t.h.jdbc.queryForObject("SELECT nearest_m<80 FROM wa.approach_progress WHERE run_id=?",Boolean.class,t.run()));
        t.h.time.advance(15);tracking.samples(t.actor,t.session(),t.batch(fix(++sequence,-10,.001,90),fix(++sequence,-5,.0025,90),fix(++sequence,0,.004,90)));
        assertEquals("PASSED",t.f.s.string("SELECT state FROM wa.run_stop WHERE run_id=? AND passenger_id=?",t.run(),t.f.passenger));
        assertEquals("APPROACHING",t.f.s.string("SELECT state FROM wa.run_stop WHERE run_id=? AND passenger_id=?",t.run(),peer));assertEquals(2,count());
    }
    @Test void anAcquisitionGapInvalidatesArrivalEvidenceInsteadOfInventingPassage() {
        start();approaching();t.h.time.advance(10);tracking.samples(t.actor,t.session(),t.batch(fix(4,-5,-.002,90),fix(5,0,-.0003,90)));
        t.h.time.advance(45);tracking.samples(t.actor,t.session(),t.batch(fix(6,-10,.001,90),fix(7,-5,.0025,90),fix(8,0,.004,90)));
        assertEquals("APPROACHING",t.f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?",t.run()));
        assertEquals(80000,t.h.jdbc.queryForObject("SELECT nearest_m FROM wa.approach_progress WHERE run_id=?",Double.class,t.run()));
    }
    @Test void trackingNeverWaitsForTheLatestPassengerInboxWhileHoldingTheDriverRun() throws Exception {
        start();UUID latest=t.f.s.pending(t.f.passenger,WaJson.object("{}").put("conversation","onde está?"),t.h.clock.now());
        var locked=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var holder=executor.submit(()->t.h.transaction.executeWithoutResult(status->{
                t.h.jdbc.queryForList("SELECT id FROM wa.integration_inbox WHERE id=? FOR UPDATE",latest);
                t.h.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE",t.f.passenger);
                locked.countDown();try { assertTrue(release.await(10,java.util.concurrent.TimeUnit.SECONDS)); } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new AssertionError(e); }
            }));
            assertTrue(locked.await(3,java.util.concurrent.TimeUnit.SECONDS));
            var ingestion=executor.submit(this::approaching);ingestion.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1,count());assertNotEquals(latest,t.h.jdbc.queryForObject("SELECT recipient_source_event_id FROM wa.approach_delivery_route WHERE outbox_id=?",UUID.class,box()));
            release.countDown();holder.get(5,java.util.concurrent.TimeUnit.SECONDS);
        } finally { release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS)); }
    }
}
