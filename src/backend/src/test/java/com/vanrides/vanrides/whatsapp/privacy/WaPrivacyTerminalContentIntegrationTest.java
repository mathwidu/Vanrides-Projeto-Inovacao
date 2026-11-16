package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.privacy.WaPrivacyTestSupport.*;

import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class WaPrivacyTerminalContentIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_privacy_terminal").withUsername("synthetic_privacy").withPassword("synthetic-privacy-test-only");
    private WaPrivacyTestSupport f;
    @BeforeEach void setup(){f=new WaPrivacyTestSupport(POSTGRES,true);}

    @Test void boundedPagesEraseOnlyOldContentAndPreserveNewThirdPartyMessageExactly() {
        f.time.days(31);
        UUID fresh=f.freshDriverMessage("TERCEIRO_RECENTE_INTACTO");
        String before=f.string("SELECT row_to_json(o)::text FROM wa.message_outbox o WHERE id=?",fresh);
        var job=f.job(ENV,INSTANCE);
        WaPrivacyTerminalMaintenance.Position cursor=null;
        int examined=0,erased=0;
        for(int page=0;page<20;page++){
            var result=f.maintenance(false).tick(job,2,cursor);
            assertThat(result.examined()).isLessThanOrEqualTo(2);
            assertThat(result.erased()).isLessThanOrEqualTo(result.examined());
            assertThat(result.deleted()).isZero();
            examined+=result.examined();erased+=result.erased();
            if(result.complete())break;
            assertThat(result.next()).isNotNull();cursor=result.next();
        }
        assertThat(examined).isEqualTo(erased).isGreaterThan(2);
        assertThat(new WaPrivacyTerminalContent(f.jdbc).complete(SCOPE,f.time.now,true)).isTrue();
        assertThat(f.string("SELECT row_to_json(o)::text FROM wa.message_outbox o WHERE id=?",fresh)).isEqualTo(before);
        assertThat(f.count("integration_inbox")).isEqualTo(3);
        assertThat(f.count("message_outbox")).isEqualTo(4);
    }

    @Test void dryRunAndDisabledAreNonDestructive() {
        f.time.days(8);String before=f.string("SELECT payload_json::text FROM wa.integration_inbox WHERE id=?",f.inboxes.get(f.first));
        var job=f.job(ENV,INSTANCE);
        var dry=new WaPrivacyTerminalMaintenance(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.syntheticTests());
        var result=dry.tick(job,100,null);
        assertThat(result.status()).isEqualTo("DRY_RUN");assertThat(result.erased()).isZero();
        assertThat(result.complete()).isFalse();assertThat(result.next()).isNull();
        assertThat(f.string("SELECT payload_json::text FROM wa.integration_inbox WHERE id=?",f.inboxes.get(f.first))).isEqualTo(before);
        var disabled=new WaPrivacyTerminalMaintenance(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.disabled(),false);
        assertThat(disabled.tick(job,100,null).status()).isEqualTo("DISABLED");
    }

    @Test void skippedLockedHeadNeverAdvancesPrefixOrAuthorizesSubjectErasure() throws Exception {
        f.service().close(SCOPE,f.first);f.time.days(31);
        UUID head=f.jdbc.queryForObject("SELECT id FROM wa.integration_inbox ORDER BY created_at,id LIMIT 1",UUID.class);
        var pool=Executors.newSingleThreadExecutor();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try{
            var blocker=pool.submit(()->new TransactionTemplate(f.transactions).execute(tx->{
                f.jdbc.queryForList("SELECT id FROM wa.integration_inbox WHERE id=? FOR UPDATE",head);locked.countDown();
                try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Synthetic lock timeout");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return true;
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var job=f.job(ENV,INSTANCE);var page=f.maintenance(false).tick(job,100,null);
            assertThat(page.deferred()).isEqualTo(1);assertThat(page.erased()).isZero();assertThat(page.complete()).isFalse();
            assertThat(page.next().after()).isEmpty();
            assertThat(f.service().erase(SCOPE,f.first).status()).isEqualTo("TERMINAL_CONTENT_PENDING");
            assertThat(f.string("SELECT state FROM wa.privacy_erasure_ledger")).isEqualTo("JOURNALED");
            release.countDown();blocker.get(10,TimeUnit.SECONDS);
            assertThat(f.maintenance(false).tick(job,100,page.next()).complete()).isTrue();
            assertThat(f.service().erase(SCOPE,f.first).status()).isEqualTo("ERASED");
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }

    @Test void scopesComeFromCredentialAndAnAliasInAnotherInstanceCannotMoveCommandOrigin() {
        f.time.days(8);
        UUID source=UUID.randomUUID(),command=UUID.randomUUID(),box=UUID.randomUUID();
        f.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION','other-instance','PN','000000000000004@s.whatsapp.net')",UUID.randomUUID(),f.second);
        f.jdbc.update("INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,state,payload_json,created_at) VALUES (?,?,'EVOLUTION','other-instance',?,'MESSAGE','COMPLETED','{\"text\":\"OUTRA_INSTANCIA_PRIVADA\"}',?)",
            source,f.second,"synthetic-other-"+source,Timestamp.from(f.time.now.minusSeconds(8*86400L)));
        f.jdbc.update("INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,request_hash,result_json,created_at,expires_at) VALUES (?,?,?,?,'RESPOND_TO_MESSAGE',?,'{}',?,?)",
            command,f.second,source,"synthetic-"+command,"f".repeat(64),Timestamp.from(f.time.now.minusSeconds(8*86400L)),f.timestamp());
        f.jdbc.update("INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,command_id,response_part,policy_version,payload_json,created_at,not_before,expires_at) VALUES (?,?,'RESPONSE',?,'synthetic','attendance-v1','{\"text\":\"OUTRA_INSTANCIA_PRIVADA\"}',?,?,?)",
            box,f.second,command,Timestamp.from(f.time.now.minusSeconds(8*86400L)),Timestamp.from(f.time.now.minusSeconds(8*86400L)),Timestamp.from(f.time.now.minusSeconds(7*86400L)));
        var scoped=f.maintenance(false).tick(f.job(ENV,INSTANCE),100,null);
        assertThat(scoped.complete()).isFalse();
        assertThat(scoped.deferred()).as("legacy outbox without origin is ambiguous with two aliases").isEqualTo(1);
        assertThat(f.string("SELECT payload_json->>'text' FROM wa.message_outbox WHERE id=?",box)).isEqualTo("OUTRA_INSTANCIA_PRIVADA");
        assertThat(f.string("SELECT payload_json->>'text' FROM wa.integration_inbox WHERE id=?",source)).isEqualTo("OUTRA_INSTANCIA_PRIVADA");
    }

    @Test void partialPageIsNotACompleteGateAndReplayKeysRemain() {
        f.service().close(SCOPE,f.first);f.time.days(31);
        var job=f.job(ENV,INSTANCE);var result=f.maintenance(false).tick(job,1,null);
        assertThat(result.examined()).isEqualTo(1);assertThat(result.complete()).isFalse();
        assertThat(f.service().erase(SCOPE,f.first).status()).isEqualTo("TERMINAL_CONTENT_PENDING");
        f.sweepTerminal();assertThat(f.service().erase(SCOPE,f.first).status()).isEqualTo("ERASED");
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.integration_inbox WHERE message_id IS NOT NULL",Long.class)).isEqualTo(3);
    }

    @Test void expiredInviteAndStartCiphertextsArePhysicallyRemovedWithTheirReplayKeysIntact() {
        UUID command=UUID.randomUUID(),invite=UUID.randomUUID(),intent=UUID.randomUUID();
        var deadline=Timestamp.from(f.time.now.plusSeconds(300));
        f.jdbc.update("INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,request_hash,result_json,created_at,expires_at) VALUES (?,?,?,?,'REQUEST_START',?,'{}',?,?)",
            command,f.driver,f.inboxes.get(f.driver),"synthetic-cipher-"+command,"f".repeat(64),f.timestamp(),deadline);
        f.jdbc.update("INSERT INTO wa.driver_invite(id,driver_id,code_hash,expires_at,max_uses,created_at) VALUES (?,?,?,?,1,?)",
            invite,f.driver,"b".repeat(64),deadline,f.timestamp());
        f.jdbc.update("INSERT INTO wa.start_intent(id,driver_id,occurrence_id,token_hash,environment,expires_at,created_at) VALUES (?,?,?,?,?,?,?)",
            intent,f.driver,f.occurrence,"c".repeat(64),ENV,deadline,f.timestamp());
        var settings=new com.vanrides.vanrides.whatsapp.config.WaSettings(new org.springframework.mock.env.MockEnvironment()
            .withProperty("vanrides.wa.environment",ENV).withProperty("vanrides.wa.public-base-url","http://127.0.0.1:8080")
            .withProperty("vanrides.wa.response-key",key((byte)1)).withProperty("vanrides.wa.hash-key",key((byte)2))
            .withProperty("vanrides.wa.operator-credential",key((byte)3)).withProperty("vanrides.wa.operator-expires-at","2035-01-01T00:00:00Z"));
        var crypto=new com.vanrides.vanrides.whatsapp.auth.WaCrypto(settings);
        byte[] ciphertext=crypto.encrypt("synthetic-retention","SEGREDO_SINTETICO_DESCARTAVEL");
        assertThat(crypto.decrypt("synthetic-retention",ciphertext)).isEqualTo("SEGREDO_SINTETICO_DESCARTAVEL");
        for(boolean start:java.util.List.of(false,true)){
            String column=start?"start_intent_id,start_payload_ciphertext":"private_invite_id,private_payload_ciphertext";
            f.jdbc.update("INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,command_id,response_part,policy_version,payload_json,created_at,not_before,expires_at,"+column+") VALUES (?,?,'RESPONSE',?,?,?,'{}',?,?,?,?,?)",
                UUID.randomUUID(),f.driver,command,start?"start":"invite",start?"run-start-v1":"driver-setup-v1",
                f.timestamp(),f.timestamp(),deadline,start?intent:invite,ciphertext);
        }
        f.time.days(8);f.sweepTerminal();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.message_outbox WHERE command_id=? AND private_payload_ciphertext IS NULL AND start_payload_ciphertext IS NULL AND state='EXPIRED'",Long.class,command)).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.message_outbox WHERE command_id=?",Long.class,command)).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.command_receipt WHERE command_id=?",Long.class,command)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.start_intent WHERE id=? AND token_hash=?",Long.class,intent,"c".repeat(64))).isEqualTo(1);
        assertThat(f.maintenance(false).tick(f.job(ENV,INSTANCE),100,null).erased()).isZero();
        assertThat(f.count("message_attempt")).isZero();
    }
    private static String key(byte value){
        byte[] key=new byte[32];java.util.Arrays.fill(key,value);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(key);
    }
}
