package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.transport.WaGuidedPairingTestSupport.*;

@Testcontainers(disabledWithoutDocker=true)
class WaGuidedPairingIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_guided_pairing");
    private WaGuidedPairingTestSupport g;
    @BeforeEach void setup() { g=new WaGuidedPairingTestSupport(new WaRunTestSupport(POSTGRES)); }

    @Test void linkAndClaimNeverAuthorizeBeforeTheFreshHumanReviewAndPkceRedeem() throws Exception {
        var link=g.issue();var client=client();
        var preview=g.links.preview(client.proof(),link.id(),WaJson.object("{}").put("linkToken",link.token()));
        assertEquals("READY",preview.path("state").asText());assertFalse(preview.path("sameCurrentDevice").asBoolean());
        assertEquals(0,g.f.s.number("SELECT count(*) FROM wa.pairing_challenge"));
        var claimed=g.claim(link,client,"guided-claim-once");
        assertFalse(claimed.has("userCode"));assertEquals("PENDING",claimed.path("state").asText());
        assertEquals(claimed.path("pairingId"),g.claim(link,client,"guided-claim-once").path("pairingId"));
        assertEquals(0,g.f.s.number("SELECT count(*) FROM wa.driver_device"));
        assertTrue(g.f.s.string("SELECT payload_json::text FROM wa.message_outbox WHERE id=?",link.box()).indexOf(link.token())<0);
        assertEquals("NO_CHANGE",g.send("Conectar este celular").decision().body().path("outcome").asText());
        assertEquals("PENDING",g.f.s.string("SELECT state FROM wa.pairing_challenge WHERE id=?",WaJson.uuid(claimed,"pairingId")));
        g.deliver(g.review(link));g.f.h.time.advance(2);
        var approved=g.send("Conectar este celular");approved.decision().requireSuccess();
        assertTrue(g.deliver(approved.box()).contains("/motorista/vinculo/retorno"));
        var session=g.redeem(client,claimed,"guided-redeem-once");
        assertEquals(session,g.redeem(client,claimed,"guided-redeem-once"));
        assertEquals(1,g.f.s.number("SELECT count(*) FROM wa.driver_device WHERE status='ACTIVE'"));
        assertEquals(0,g.f.s.number("SELECT count(*) FROM wa.tracking_session"));
        assertEquals(0,g.f.s.number("SELECT count(*) FROM wa.operation_run"));
    }
    @Test void aDifferentKeyCannotTakeTheClaimAndWrongVerifierCannotRedeem() throws Exception {
        var link=g.issue();var owner=client();var claimed=g.claim(link,owner,"guided-owner-key");
        assertThrows(WaProblem.class,()->g.claim(link,clientUnchecked(),"guided-foreign-key"));
        assertEquals(1,g.f.s.number("SELECT count(*) FROM wa.pairing_challenge"));
        g.deliver(g.review(link));g.f.h.time.advance(2);g.send("Conectar este celular");
        var result=g.f.s.pairing.redeem(owner.proof(),WaJson.object("{}").put("pairingId",claimed.path("pairingId").asText())
            .put("pkceVerifier",client().verifier()),"guided-bad-verifier");
        assertNotNull(result.error());assertEquals(0,g.f.s.number("SELECT count(*) FROM wa.driver_device"));
    }
    @Test void delayedConfirmationAndWrongQuoteNeverApproveTheNewClaim() throws Exception {
        var first=g.issue();g.claim(first,client(),"guided-a");g.deliver(g.review(first));
        g.f.h.time.advance(2);Instant oldConfirmation=g.f.h.clock.now();String oldQuote=g.external(g.review(first));
        g.f.h.time.advance(2);var next=g.issue();var claimed=g.claim(next,client(),"guided-b");
        g.deliver(g.review(next));g.f.h.time.advance(2);
        assertEquals("NO_CHANGE",g.send("Conectar este celular",oldConfirmation,null,true).decision().body().path("outcome").asText());
        assertEquals("NO_CHANGE",g.send("Conectar este celular",g.f.h.clock.now(),oldQuote,true).decision().body().path("outcome").asText());
        assertEquals("PENDING",g.f.s.string("SELECT state FROM wa.pairing_challenge WHERE id=?",WaJson.uuid(claimed,"pairingId")));
        var exact=g.send("Conectar este celular",g.f.h.clock.now(),g.external(g.review(next)),false);
        assertEquals("APPLIED",exact.decision().body().path("outcome").asText());
    }
    @Test void linkDeadlineNeverExtendsAndAdministrativeGenerationInvalidatesItsApproval() throws Exception {
        var link=g.issue();var owner=client();g.f.h.time.advance(100);
        var claimed=g.claim(link,owner,"guided-original-deadline");
        assertEquals(g.f.s.string("SELECT expires_at::text FROM wa.pairing_handoff WHERE id=?",link.id()),
            g.f.s.string("SELECT expires_at::text FROM wa.pairing_challenge WHERE id=?",WaJson.uuid(claimed,"pairingId")));
        g.deliver(g.review(link));g.f.h.time.advance(2);g.send("Conectar este celular");
        g.f.h.jdbc.update("UPDATE wa.driver_authorization SET generation=generation+1 WHERE driver_id=?",g.f.driver);
        assertThrows(WaProblem.class,()->g.redeem(owner,claimed,"guided-revoked-generation"));
        assertEquals(0,g.f.s.number("SELECT count(*) FROM wa.driver_device"));
        g.f.h.time.advance(201);
        assertThrows(WaProblem.class,()->g.links.preview(owner.proof(),link.id(),WaJson.object("{}").put("linkToken",link.token())));
    }
    @Test void pastedLinkNeverReachesTheStoredTextOrAgentProjectionAndErasureRevokesPendingKey() throws Exception {
        var link=g.issue();var owner=client();var claimed=g.claim(link,owner,"guided-private-key");
        String raw="Esse link não abriu: https://lab.invalid/motorista/conectar#v1."+link.id()+"."+link.token();
        var lease=g.message(raw,g.f.h.clock.now(),null,true);
        assertFalse(g.f.s.stored(lease.id()).toString().contains(link.token()));
        assertFalse(lease.message().toString().contains(link.token()));
        g.f.s.finish(lease);
        // Set up the legal V44 journal boundary; never bypass its profile guards.
        var now=java.sql.Timestamp.from(g.f.h.clock.now());
        var closed=java.sql.Timestamp.from(g.f.h.clock.now().minusSeconds(31L*86400));
        var due=java.sql.Timestamp.from(g.f.h.clock.now().minusSeconds(86400));
        g.f.h.jdbc.update("INSERT INTO wa.privacy_subject(subject_id,environment,instance_id,closed_at,erase_after) VALUES (?,?,?,?,?)",
            g.f.driver,g.f.h.settings.environment(),WaTransportTestSupport.INSTANCE,closed,due);
        g.f.h.jdbc.update("""
            INSERT INTO wa.privacy_erasure_ledger(event_id,subject_id,environment,instance_id,payload_json,payload_hash,created_at)
            VALUES (?,?,?,?,'{}',encode(sha256(convert_to('{}','UTF8')),'hex'),?)
            """,java.util.UUID.randomUUID(),g.f.driver,g.f.h.settings.environment(),WaTransportTestSupport.INSTANCE,closed);
        g.f.h.jdbc.update("UPDATE wa.privacy_erasure_ledger SET state='JOURNALED',journal_sequence=1,journal_digest=?,journaled_at=?,purge_started_at=? WHERE subject_id=?",
            "a".repeat(64),closed,now,g.f.driver);
        g.f.h.jdbc.update("UPDATE wa.profile SET status='CLOSED',closed_at=?,driver_enabled=false WHERE identity_id=?",closed,g.f.driver);
        g.f.h.jdbc.update("UPDATE wa.profile SET display_name=NULL,preferences_json='{}',privacy_erased_at=? WHERE identity_id=?",now,g.f.driver);
        assertEquals("REVOKED",g.f.s.string("SELECT state FROM wa.pairing_handoff WHERE id=?",link.id()));
        assertEquals("REVOKED",g.f.s.string("SELECT state FROM wa.pairing_challenge WHERE id=?",WaJson.uuid(claimed,"pairingId")));
        assertEquals(0,g.f.s.number("SELECT count(*) FROM wa.pairing_link_notice WHERE secret_ciphertext IS NOT NULL"));
    }
    @Test void resumedCollectionAfterClaimBlocksRedeemWithoutChangingDevices() throws Exception {
        var t=new WaTrackingTestSupport(POSTGRES);g=new WaGuidedPairingTestSupport(t.f);
        var link=g.issue();var owner=client();var claimed=g.claim(link,owner,"guided-before-start");
        g.deliver(g.review(link));g.f.h.time.advance(2);g.send("Conectar este celular");
        t.activate();
        assertEquals(WaProblem.Code.ACTIVE_RUN_EXISTS,assertThrows(WaProblem.class,()->g.redeem(owner,claimed,"guided-during-start")).code());
        assertEquals(1,g.f.s.number("SELECT generation FROM wa.driver_authorization WHERE driver_id=?",g.f.driver));
        assertEquals("ACTIVE",g.f.s.string("SELECT status FROM wa.driver_device WHERE id=?",t.device));
        t.tracking.state(t.actor,t.session(),t.state("STOPPED"),"guided-old-stop");
        String ack=g.f.s.string("SELECT stopped_at::text FROM wa.tracking_session WHERE id=?",t.session());
        g.redeem(owner,claimed,"guided-during-start");
        var review=g.send("Quero finalizar a viagem");review.decision().requireSuccess();
        assertTrue(g.deliver(review.box()).contains("Finalizar esta viagem"));
        g.f.h.time.advance(2);g.send("Finalizar esta viagem").decision().requireSuccess();
        assertEquals("COMPLETED",t.runState());assertEquals("STOPPED",t.sessionState());
        assertEquals(ack,g.f.s.string("SELECT stopped_at::text FROM wa.tracking_session WHERE id=?",t.session()));
        assertEquals(1,g.f.s.number("SELECT count(*) FROM wa.tracking_session"));
    }
    @Test void removingAnApprovedHandoffCannotLeaveARedeemableLegacyChallenge() throws Exception {
        var link=g.issue();var owner=client();var claimed=g.claim(link,owner,"guided-source-cleanup");
        g.deliver(g.review(link));g.f.h.time.advance(2);g.send("Conectar este celular");
        g.f.h.jdbc.update("DELETE FROM wa.pairing_handoff WHERE id=?",link.id());
        assertEquals("REVOKED",g.f.s.string("SELECT state FROM wa.pairing_challenge WHERE id=?",WaJson.uuid(claimed,"pairingId")));
        assertThrows(WaProblem.class,()->g.redeem(owner,claimed,"guided-after-source-cleanup"));
        assertEquals(0,g.f.s.number("SELECT count(*) FROM wa.driver_device"));
    }
    @Test void anOldStopReviewCannotFinishALaterStoppedSession() throws Exception {
        var t=new WaTrackingTestSupport(POSTGRES);g=new WaGuidedPairingTestSupport(t.f);t.activate();
        t.tracking.state(t.actor,t.session(),t.state("STOPPED"),"guided-stop-a");
        // A disconnected old device makes the acknowledged-stop chat contingency applicable.
        g.f.h.jdbc.update("UPDATE wa.driver_device SET status='REVOKED',revoked_at=? WHERE id=?",java.sql.Timestamp.from(g.f.h.clock.now()),t.device);
        var review=g.send("Quero finalizar a viagem");assertTrue(g.deliver(review.box()).contains("Finalizar esta viagem"));
        g.f.h.time.advance(2);
        // Model the persisted state after RESUME and a second acknowledged STOP, with the same run version.
        g.f.h.jdbc.update("""
            INSERT INTO wa.tracking_session(id,run_id,driver_id,device_id,generation,state,created_at,lease_expires_at,stopped_at)
            SELECT ?,run_id,driver_id,device_id,generation+1,'STOPPED',?,lease_expires_at,? FROM wa.tracking_session WHERE id=?
            """,java.util.UUID.randomUUID(),java.sql.Timestamp.from(g.f.h.clock.now()),java.sql.Timestamp.from(g.f.h.clock.now()),t.session());
        var stale=g.send("Finalizar esta viagem");
        assertEquals(WaProblem.Code.VERSION_CONFLICT,stale.decision().error());
        assertNotEquals("COMPLETED",t.runState());assertEquals(2,g.f.s.number("SELECT count(*) FROM wa.tracking_session"));
    }
    private Client clientUnchecked() { try { return client(); } catch(Exception error) { throw new AssertionError(error); } }
}
