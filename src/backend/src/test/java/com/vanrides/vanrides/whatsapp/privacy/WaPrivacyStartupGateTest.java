package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class WaPrivacyStartupGateTest {
    @TempDir Path temporary;
    private List<Map<String,Object>> rows=List.of();private boolean orphan;
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class,call->switch(call.getMethod().getName()){
        case "queryForList"->rows;case "queryForObject"->orphan;default->RETURNS_DEFAULTS.answer(call);
    });
    private final WaSettings settings=new WaSettings(new MockEnvironment().withProperty("vanrides.wa.environment","privacy-test"));
    private WaPrivacySettings config(boolean apply){return new WaPrivacySettings(new MockEnvironment().withProperty("WA_PRIVACY_ENABLED","true")
        .withProperty("WA_PRIVACY_APPLY",Boolean.toString(apply)).withProperty("WA_PRIVACY_JOURNAL_DIR",temporary.toString()));}
    private WaPrivacyStartupGate gate(){return new WaPrivacyStartupGate(jdbc,config(true),settings);}
    private static WaPrivacyFileJournal.Snapshot empty(){return new WaPrivacyFileJournal.Snapshot(0,"0".repeat(64),List.of());}
    private Map<String,Object> row(String state){
        UUID subject=UUID.randomUUID(),event=UUID.randomUUID();Instant closed=Instant.parse("2020-01-01T00:00:00Z"),after=closed.plusSeconds(30L*86400);
        String payload=WaJson.write(WaJson.object("{}").put("policy","wa-privacy-v1").put("eventId",event.toString()).put("subjectId",subject.toString())
            .put("environment","privacy-test").put("instanceId","synthetic").put("closedAt",closed.toString()).put("eraseAfter",after.toString()));
        var row=new HashMap<String,Object>();row.put("sequence",1L);row.put("event_id",event);row.put("subject_id",subject);
        row.put("environment","privacy-test");row.put("instance_id","synthetic");row.put("payload_json",payload);row.put("payload_hash",WaCrypto.digest(payload));
        row.put("subject_environment","privacy-test");row.put("subject_instance","synthetic");row.put("subject_closed",Timestamp.from(closed));row.put("erase_after",Timestamp.from(after));
        row.put("profile_status","CLOSED");row.put("profile_closed",Timestamp.from(closed));row.put("cohort_environment","privacy-test");row.put("synthetic",true);row.put("state",state);
        if(!state.equals("INTENT")){row.put("journal_sequence",1L);row.put("journal_digest","a".repeat(64));}return row;
    }
    private WaPrivacyFileJournal.Snapshot snapshot(Map<String,Object> row){
        var entry=new WaPrivacyJournal.Entry(1,(UUID)row.get("event_id"),(UUID)row.get("subject_id"),"privacy-test","synthetic",(String)row.get("payload_json"),(String)row.get("payload_hash"));
        var ack=new WaPrivacyJournal.Ack(entry.eventId(),entry.payloadHash(),1,"a".repeat(64));
        return new WaPrivacyFileJournal.Snapshot(1,"a".repeat(64),List.of(new WaPrivacyFileJournal.VerifiedEntry(entry,ack)));
    }
    @Test void disabledAndPreviewNeverTouchFilesystemOrPrivacyTables(){
        new WaPrivacyStartupGate(jdbc,new WaPrivacySettings(new MockEnvironment()),settings).verify();
        var preview=new WaPrivacyStartupGate(jdbc,config(false),settings);preview.verify();assertThat(preview.status()).isEqualTo("DRY_RUN");verifyNoInteractions(jdbc);
    }
    @Test @EnabledOnOs({OS.LINUX,OS.MAC}) void missingOrAdulteratedFilesystemBlocksWithoutProvisioning() throws Exception {
        temporary=temporary.toRealPath();
        Files.setPosixFilePermissions(temporary,PosixFilePermissions.fromString("rwx------"));
        assertThatThrownBy(()->gate().verify()).isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_STARTUP_BLOCKED");
        assertThat(temporary.resolve("head.json")).doesNotExist();
        var journal=new WaPrivacyFileJournal(temporary);journal.initializeEmpty();Files.writeString(temporary.resolve("head.json"),"{\"sequence\":1,\"digest\":\""+"0".repeat(64)+"\"}");
        assertThatThrownBy(()->gate().verify()).isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_STARTUP_BLOCKED");verifyNoInteractions(jdbc);
    }
    @Test void journalRecordAbsentFromDatabaseRequiresRestore(){
        var row=row("JOURNALED");assertThatThrownBy(()->gate().verify(snapshot(row))).isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_RESTORE_REQUIRED");
    }
    @Test void databaseAcknowledgementAbsentFromJournalBlocks(){
        rows=List.of(row("JOURNALED"));assertThatThrownBy(()->gate().verify(empty())).isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_STARTUP_BLOCKED");
    }
    @Test void intentWithoutExternalRecordRemainsExplicitlyPending(){
        rows=List.of(row("INTENT"));var gate=gate();gate.verify(empty());assertThat(gate.status()).isEqualTo("INTENT_PENDING");
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void dueUnpurgedSubjectDoesNotBlockButMetadataOrAckDivergenceDoes(){
        var row=row("JOURNALED");rows=List.of(row);var snapshot=snapshot(row);var gate=gate();gate.verify(snapshot);assertThat(gate.status()).isEqualTo("READY");
        row.put("synthetic",false);gate().verify(snapshot); // Eligibility policy is enforced by ports, not confused with restore consistency.
        row.put("profile_closed",Timestamp.from(Instant.EPOCH));assertThatThrownBy(()->gate().verify(snapshot)).isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_RESTORE_REQUIRED");
        row.put("profile_closed",row.get("subject_closed"));row.put("journal_digest","b".repeat(64));assertThatThrownBy(()->gate().verify(snapshot)).isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_STARTUP_BLOCKED");
        row.put("journal_digest","a".repeat(64));row.put("state","PURGED");Instant after=((Timestamp)row.get("erase_after")).toInstant();
        row.put("purge_started_at",Timestamp.from(after));row.put("profile_erased",Timestamp.from(after.plusMillis(1)));
        row.put("erased_at",Timestamp.from(after.plusMillis(2)));row.put("purged_at",Timestamp.from(after.plusMillis(3)));
        gate().verify(snapshot); // Real writers call the clock separately for these three timestamps.
        row.put("profile_erased",Timestamp.from(after.minusMillis(1)));
        assertThatThrownBy(()->gate().verify(snapshot)).isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_RESTORE_REQUIRED");
    }
    @Test void orphanExplicitClosureRequiresRestore(){
        orphan=true;assertThatThrownBy(()->gate().verify(empty())).isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_RESTORE_REQUIRED");
    }
    @Test void failedGatePreventsDependentRuntimeBeanConstruction(){
        assertThat(WaPrivacyStartupGate.class.isAnnotationPresent(DependsOnDatabaseInitialization.class)).isTrue();
        var constructed=new java.util.concurrent.atomic.AtomicBoolean();
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()){
            context.registerBean(JdbcTemplate.class,()->jdbc);context.registerBean(WaPrivacySettings.class,()->config(true));context.registerBean(WaSettings.class,()->settings);
            context.registerBean(WaPrivacyStartupGate.class);
            context.registerBean("runtimeDependent",Object.class,()->{context.getBean(WaPrivacyStartupGate.class);constructed.set(true);return new Object();});
            assertThatThrownBy(context::refresh).hasRootCauseMessage("PRIVACY_STARTUP_BLOCKED");assertThat(constructed.get()).isFalse();
        }
    }
}
