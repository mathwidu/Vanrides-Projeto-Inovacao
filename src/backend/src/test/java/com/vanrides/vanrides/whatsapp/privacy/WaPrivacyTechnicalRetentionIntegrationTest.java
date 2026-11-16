package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.privacy.WaPrivacyTestSupport.*;
import com.vanrides.vanrides.whatsapp.api.*;
import com.vanrides.vanrides.whatsapp.auth.*;
import com.vanrides.vanrides.whatsapp.pairing.WaPrivacyPairingReplayProbe;
import com.vanrides.vanrides.whatsapp.tracking.WaPrivacyTrackingReplayProbe;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class WaPrivacyTechnicalRetentionIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_privacy_technical").withUsername("synthetic_privacy").withPassword("synthetic-privacy-test-only");
    private WaPrivacyTechnicalFixture t;
    private WaPrivacyTestSupport f;
    @BeforeEach void setup(){t=new WaPrivacyTechnicalFixture(POSTGRES);f=t.f;}

    @Test void pagesDeleteChildrenBeforeParentsAndPreserveSessionGenerationSequenceAndStop(){
        t.gps();String before=f.string("SELECT row_to_json(s)::text FROM wa.tracking_session s WHERE id=?",t.session);f.time.days(8);
        var actor=f.job(ENV,INSTANCE);WaPrivacyTechnicalRetention.Position cursor=null;int deleted=0;
        for(int page=0;page<20;page++){
            var result=t.port(false).tick(actor,1,cursor);assertThat(result.examined()).isLessThanOrEqualTo(1);
            assertThat(result.erased()+result.deleted()).isLessThanOrEqualTo(1);deleted+=result.deleted();
            if(result.complete())break;assertThat(result.deferred()).isZero();cursor=result.next();
        }
        assertThat(deleted).isEqualTo(3);assertThat(f.count("tracking_sample_receipt")).isZero();assertThat(f.count("tracking_batch_receipt")).isZero();
        assertThat(f.string("SELECT row_to_json(s)::text FROM wa.tracking_session s WHERE id=?",t.session)).isEqualTo(before);
        assertThat(t.sweep().complete()).isTrue();
    }
    @Test void receiptsInsideSevenDaysAndActiveRunRemain(){
        t.gps();f.time.days(6);t.sweep();assertThat(f.count("tracking_sample_receipt")).isEqualTo(2);
        f.time.days(2);f.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED' WHERE id=?",f.run);
        f.jdbc.update("UPDATE wa.tracking_session SET state='STOP_REQUESTED',stopped_at=NULL WHERE id=?",t.session);
        t.sweep();assertThat(f.count("tracking_sample_receipt")).isEqualTo(2);
    }
    @Test void humanAgeIgnoresMaintenanceUpdatedAtAndPreservesPauseSupportAndFreshness(){
        String id=UUID.randomUUID().toString();
        f.jdbc.update("UPDATE wa.conversation_state SET context_json=context_json||?::jsonb WHERE identity_id=?",
            "{\"supportCaseId\":\""+id+"\",\"sourceInboxEventId\":\""+f.inboxes.get(f.first)+"\",\"noticeVersion\":\"wa-mvp-v1\",\"noticeEmittedAt\":\"2030-01-01T12:00:00Z\",\"ownerPromptEvents\":{\"PASSENGER_SETUP\":\""+f.inboxes.get(f.first)+"\"}}",f.first);
        f.time.days(8);f.jdbc.update("UPDATE wa.conversation_state SET updated_at=?",f.timestamp());t.sweep();
        var context=WaJson.object(f.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",f.first));
        assertThat(context.has("passengerDraft")).isFalse();assertThat(context.path("supportCaseId").asText()).isEqualTo(id);
        assertThat(context.path("noticeVersion").asText()).isEqualTo("wa-mvp-v1");assertThat(context.has("ownerPromptEvents")).isTrue();
        assertThat(f.string("SELECT stage FROM wa.conversation_state WHERE identity_id=?",f.first)).isEqualTo("PAUSED");
        assertThat(f.jdbc.queryForObject("SELECT last_event_id FROM wa.conversation_state WHERE identity_id=?",UUID.class,f.first)).isEqualTo(f.inboxes.get(f.first));
    }
    @Test void freshHumanMessageProtectsDraftEvenWhenConversationUpdatedAtIsOld(){
        f.time.days(8);UUID event=UUID.randomUUID();
        f.jdbc.update("INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,state,payload_json,created_at) VALUES (?,?,'EVOLUTION',?,?,'MESSAGE','COMPLETED','{}',?)",event,f.driver,INSTANCE,event.toString(),f.timestamp());
        String before=f.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",f.driver);t.sweep();
        assertThat(f.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",f.driver)).isEqualTo(before);
    }
    @Test void bothRealReplayReadersRejectExpiredTombstoneAndKeepKeysAndHash(){
        UUID pairing=t.replay("SESSION_REVOKE","synthetic-pairing",f.time.now.plusSeconds(60));
        UUID tracking=t.replay("TRACK_STATE","synthetic-tracking",f.time.now.plusSeconds(60));
        String hash=f.string("SELECT request_hash FROM wa.operation_replay WHERE id=?",pairing);f.time.days(8);t.sweep();
        assertThat(f.jdbc.queryForObject("SELECT octet_length(response_ciphertext) FROM wa.operation_replay WHERE id=?",Integer.class,pairing)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT octet_length(response_ciphertext) FROM wa.operation_replay WHERE id=?",Integer.class,tracking)).isEqualTo(1);
        assertThat(f.string("SELECT request_hash FROM wa.operation_replay WHERE id=?",pairing)).isEqualTo(hash);
        assertThatThrownBy(()->WaPrivacyPairingReplayProbe.read(f.jdbc,t.crypto,f.clock,"SESSION_REVOKE",t.device,"synthetic-pairing",t.request)).isInstanceOf(WaProblem.class).extracting(e->((WaProblem)e).code()).isEqualTo(WaProblem.Code.INTENT_EXPIRED);
        assertThatThrownBy(()->WaPrivacyTrackingReplayProbe.read(f.jdbc,t.crypto,f.time.now,"TRACK_STATE",t.device,"synthetic-tracking",t.request)).isInstanceOf(WaProblem.class).extracting(e->((WaProblem)e).code()).isEqualTo(WaProblem.Code.INTENT_EXPIRED);
        assertThatThrownBy(()->WaPrivacyPairingReplayProbe.read(f.jdbc,t.crypto,f.clock,"SESSION_REVOKE",t.device,"synthetic-pairing",WaJson.object("{\"changed\":true}"))).isInstanceOf(WaProblem.class).extracting(e->((WaProblem)e).code()).isEqualTo(WaProblem.Code.IDEMPOTENCY_CONFLICT);
    }
    @Test void normalUnexpiredReplayStillDecryptsAndIsNotChanged(){
        t.replay("SESSION_REVOKE","fresh-pairing",f.time.now.plusSeconds(60));t.replay("TRACK_STATE","fresh-tracking",f.time.now.plusSeconds(60));t.sweep();
        assertThat(WaPrivacyPairingReplayProbe.read(f.jdbc,t.crypto,f.clock,"SESSION_REVOKE",t.device,"fresh-pairing",t.request).path("secret").asText()).isEqualTo("SEGREDO_SINTETICO");
        assertThat(WaPrivacyTrackingReplayProbe.read(f.jdbc,t.crypto,f.time.now,"TRACK_STATE",t.device,"fresh-tracking",t.request).path("secret").asText()).isEqualTo("SEGREDO_SINTETICO");
    }
    @Test void terminalOwnedPairingCanBeDeletedAndRevokedDeviceLosesJwkWithoutDeletingIdentity(){
        UUID pairing=UUID.randomUUID();
        f.jdbc.update("INSERT INTO wa.pairing_challenge(id,public_jwk,jkt,pkce_challenge,platform,app_version,environment,code_hash,state,expires_at,driver_id,approval_generation,created_at) VALUES (?,?::jsonb,?,?,'ANDROID','synthetic',?,?,'EXPIRED',?,?,1,?)",
            pairing,t.material.json(),t.material.jkt(),WaCrypto.randomToken(),ENV,"e".repeat(64),Timestamp.from(f.time.now.plusSeconds(60)),f.driver,f.timestamp());
        f.jdbc.update("UPDATE wa.driver_device SET status='REVOKED',revoked_at=? WHERE id=?",f.timestamp(),t.device);f.time.days(8);
        assertThat(t.sweep().complete()).isTrue();assertThat(f.count("pairing_challenge")).isZero();
        assertThat(f.string("SELECT public_jwk::text FROM wa.driver_device WHERE id=?",t.device)).isEqualTo("{}");assertThat(f.count("identity")).isEqualTo(3);
    }
    @Test void preauthWithoutOwnerIsNotPrunedAndNonceHasIntrinsicExpiry(){
        var material=WaPrivacyTechnicalFixture.material();var ledger=new WaProofLedger(f.jdbc,f.clock,t.settings);String nonce=ledger.issue(material.jkt());
        UUID id=UUID.randomUUID();
        f.jdbc.update("INSERT INTO wa.operation_replay(id,operation_kind,scope_id,request_key,request_hash,response_ciphertext,expires_at) VALUES (?,'PAIR_BEGIN',?,'unowned',?,?,?)",
            id,UUID.randomUUID(),"f".repeat(64),new byte[]{1,2,3},Timestamp.from(f.time.now.plusSeconds(1)));
        f.time.days(8);t.sweep();
        assertThat(ledger.consume(new WaDpopVerifier.Proof(material,"synthetic-old-proof",nonce,f.time.now))).isFalse();
        assertThat(f.count("proof_nonce")).isEqualTo(1);assertThat(f.jdbc.queryForObject("SELECT octet_length(response_ciphertext) FROM wa.operation_replay WHERE id=?",Integer.class,id)).isEqualTo(3);
    }
    @Test void dryRunDisabledAmbientAndCursorScopesAreEnforced(){
        t.gps();f.time.days(8);var actor=f.job(ENV,INSTANCE);
        var dry=new WaPrivacyTechnicalRetention(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.syntheticTests());
        var preview=dry.tick(actor,1,null);assertThat(preview.erased()+preview.deleted()).isZero();assertThat(preview.wouldChange()).isEqualTo(1);
        assertThatThrownBy(()->t.port(false).tick(actor,1,preview.next())).isInstanceOf(WaProblem.class);
        assertThat(new WaPrivacyTechnicalRetention(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.disabled(),false).tick(actor,100,null).status()).isEqualTo("DISABLED");
        assertThatThrownBy(()->new TransactionTemplate(f.transactions).execute(tx->t.port(false).tick(actor,100,null))).isInstanceOf(IllegalStateException.class);
        assertThat(f.count("tracking_sample_receipt")).isEqualTo(2);
    }
    @Test void multiInstanceOrMissingCohortCannotBePurged(){
        t.gps();f.time.days(8);f.jdbc.update("UPDATE wa.identity SET test_cohort_id=NULL WHERE id=?",f.first);
        f.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION','other-instance','PN','000000000000008@s.whatsapp.net')",UUID.randomUUID(),f.driver);
        var preview=t.port(true).tick(f.job(ENV,INSTANCE),100,null);
        assertThat(preview.deferred()).isEqualTo(1);assertThat(preview.wouldChange()).isZero();assertThat(preview.next().after()).isEmpty();
        var result=t.sweep();assertThat(result.deferred()).isEqualTo(1);assertThat(result.pending().get(0).reason()).isEqualTo("MULTI_INSTANCE");assertThat(result.next().after()).isEmpty();
        assertThat(f.count("tracking_sample_receipt")).isEqualTo(2);assertThat(f.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",f.first)).contains(NAME);
    }
}
