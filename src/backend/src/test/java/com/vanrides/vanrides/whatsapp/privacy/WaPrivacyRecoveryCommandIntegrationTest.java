package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@EnabledOnOs({OS.LINUX,OS.MAC})
@Testcontainers(disabledWithoutDocker=true)
class WaPrivacyRecoveryCommandIntegrationTest {
    static final String PASSWORD=WaCrypto.randomToken(),TOKEN=WaCrypto.randomToken();
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
        .withTmpFs(Map.of("/var/lib/postgresql/data","rw,size=256m"))
        .withDatabaseName("vanrides_wa070").withUsername("postgres").withPassword(PASSWORD);
    @TempDir Path temporary;
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private Path directory;
    private String nonce,instance;

    @BeforeEach void setup() throws Exception {
        source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),PASSWORD); jdbc=new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA IF EXISTS wa CASCADE");
        var flyway=Flyway.configure().dataSource(source).cleanDisabled(false).load(); flyway.clean(); flyway.migrate();
        nonce=UUID.randomUUID().toString().replace("-",""); instance="wa_privacy_recovery_"+nonce;
        UUID core=UUID.randomUUID(),identity=UUID.randomUUID();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'wa070-local',true)",core);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)",identity,core);
        jdbc.update("INSERT INTO wa.profile(identity_id,role,status,display_name) VALUES (?,'PASSENGER','ONBOARDING','CORE_UNCHANGED')",identity);
        jdbc.update("""
            INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,expires_at)
            VALUES (?,'SYSTEM_JOB','wa070-local',?,?,clock_timestamp()+interval '120 days')
            """,UUID.randomUUID(),WaCrypto.digest(TOKEN),instance);
        directory=Files.createDirectory(temporary.resolve("private-recovery"),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toRealPath();
        Files.createDirectory(directory.resolve("journal"),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }
    @Test void realOldDumpCurrentFileJournalReapplyErasesAlphaAndPreservesBetaAndCore() throws Exception {
        invoke("PREPARE"); backup(); ObjectNode closed=invoke("CLOSE_PURGE");
        var snapshot=new WaPrivacyFileJournal(directory.resolve("journal")).snapshot();
        byte[] journal=Files.readAllBytes(directory.resolve("journal/erasures.jsonl"));
        ObjectNode state=WaJson.object(Files.readAllBytes(directory.resolve("fixture.json")));
        var fixture=new WaPrivacyRecoveryFixture(jdbc,nonce);
        assertThat(closed.path("ready").asBoolean()).isTrue(); fixture.verifyErased(state);
        restoreOldDump();
        assertThat(jdbc.queryForObject("SELECT display_name FROM wa.profile WHERE identity_id=?",String.class,fixture.alpha)).isEqualTo(fixture.alphaMarker);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.privacy_erasure_ledger",Long.class)).isZero();
        write("retained-head.json",closed.with("head")); ObjectNode restored=invoke("REAPPLY_PURGE");
        assertThat(restored.path("status").asText()).isEqualTo("RESTORED_ERASURE_VERIFIED");
        fixture.verifyErased(state); assertThat(new WaPrivacyFileJournal(directory.resolve("journal")).snapshot()).isEqualTo(snapshot);
        assertThat(Files.readAllBytes(directory.resolve("journal/erasures.jsonl"))).isEqualTo(journal);
        assertThat(new WaPrivacyLedger(jdbc).entry(fixture.alpha)).isEqualTo(snapshot.records().get(0).entry());
        Instant base=Instant.parse(state.path("baseAt").asText());
        assertThat(jdbc.queryForObject("SELECT closed_at FROM wa.profile WHERE identity_id=?",Timestamp.class,fixture.alpha).toInstant()).isEqualTo(base);
        assertThat(jdbc.queryForObject("SELECT erase_after FROM wa.privacy_subject WHERE subject_id=?",Timestamp.class,fixture.alpha).toInstant()).isEqualTo(base.plusSeconds(30L*86400));
        assertThat(jdbc.queryForObject("SELECT display_name FROM wa.profile WHERE display_name='CORE_UNCHANGED'",String.class)).isEqualTo("CORE_UNCHANGED");
        assertThat(Files.getPosixFilePermissions(directory.resolve("reapply_purge.result.json"))).isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }
    @Test void revokedWrongScopeOrRealCohortBlocksBeforeJournalInitialization() throws Exception {
        jdbc.update("UPDATE wa.service_credential SET revoked_at=clock_timestamp()");
        assertThatThrownBy(()->invoke("PREPARE")).isInstanceOf(IllegalStateException.class);
        jdbc.update("UPDATE wa.service_credential SET revoked_at=NULL,allowed_instance_id='foreign'");
        assertThatThrownBy(()->executeExisting("PREPARE")).isInstanceOf(IllegalStateException.class);
        jdbc.update("UPDATE wa.service_credential SET allowed_instance_id=?",instance);
        jdbc.update("UPDATE wa.test_cohort SET synthetic=false");
        assertThatThrownBy(()->executeExisting("PREPARE")).isInstanceOf(IllegalStateException.class);
        try(var files=Files.list(directory.resolve("journal"))) { assertThat(files.count()).isZero(); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.profile",Long.class)).isEqualTo(1);
    }
    @Test void actualOtherClientConnectionBlocksEvenWithAnAuthorizedRequest() throws Exception {
        try(var competing=source.getConnection()) {
            assertThatThrownBy(()->invoke("PREPARE")).isInstanceOf(IllegalStateException.class);
        }
        assertThat(Files.exists(directory.resolve("journal/head.json"))).isFalse();
    }
    @Test void retainedHeadMissingOrJournalTamperedCannotMutateRestoredDatabase() throws Exception {
        invoke("PREPARE"); backup(); ObjectNode closed=invoke("CLOSE_PURGE"); restoreOldDump();
        write("retained-head.json",closed.with("head")); request("REAPPLY_PURGE");
        Files.delete(directory.resolve("retained-head.json"));
        assertThatThrownBy(()->executeExisting("REAPPLY_PURGE")).isInstanceOf(Exception.class);
        write("retained-head.json",closed.with("head"));
        Files.writeString(directory.resolve("journal/erasures.jsonl"),"tampered\n",StandardCharsets.UTF_8);
        assertThatThrownBy(()->executeExisting("REAPPLY_PURGE")).isInstanceOf(Exception.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.privacy_erasure_ledger",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.profile WHERE status='CLOSED'",Long.class)).isZero();
    }
    private void backup() throws Exception { command("pg_dump","-U","postgres","-d","vanrides_wa070","-Fc","-f","/tmp/recovery-before-close.dump"); }
    private void restoreOldDump() throws Exception { command("pg_restore","-U","postgres","-d","vanrides_wa070","--clean","--if-exists","--no-owner","--exit-on-error","/tmp/recovery-before-close.dump"); }
    private void command(String... args) throws Exception { assertThat(POSTGRES.execInContainer(args).getExitCode()).isZero(); }
    private ObjectNode invoke(String operation) throws Exception { request(operation); return executeExisting(operation); }
    private ObjectNode executeExisting(String operation) throws Exception {
        try(var connection=source.getConnection()) {
            return WaPrivacyRecoveryCommand.execute(new WaPrivacyRecoveryFiles(directory.resolve(operation.toLowerCase(java.util.Locale.ROOT)+".request.json")),connection);
        }
    }
    private void request(String operation) throws Exception {
        ObjectNode body=WaJson.object("{}").put("schemaVersion",1).put("operation",operation).put("environment","wa070-local")
            .put("nonce",nonce).put("password",PASSWORD).put("credential",TOKEN);
        if(!operation.equals("PREPARE")) body.put("fixtureSha256",WaCrypto.digest(Files.readString(directory.resolve("fixture.json"))));
        if(operation.equals("REAPPLY_PURGE")) body.put("retainedHeadSha256",WaCrypto.digest(Files.readString(directory.resolve("retained-head.json"))));
        write(operation.toLowerCase(java.util.Locale.ROOT)+".request.json",body);
    }
    private void write(String name,ObjectNode value) throws Exception {
        Files.writeString(directory.resolve(name),WaJson.canonical(value)+"\n",StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE);
        Files.setPosixFilePermissions(directory.resolve(name),PosixFilePermissions.fromString("rw-------"));
    }
}
