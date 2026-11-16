package com.vanrides.vanrides.whatsapp.privacy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static com.vanrides.vanrides.whatsapp.privacy.WaPrivacyRecoveryFiles.require;

/** Standalone synthetic rehearsal only. No SpringApplication, bean, route, scheduler or live target option. */
public final class WaPrivacyRecoveryCommand {
    private static final String DATABASE="vanrides_wa070",ENVIRONMENT="wa070-local";
    private static final String JDBC="jdbc:postgresql://127.0.0.1:5432/"+DATABASE
        +"?ApplicationName=vanrides-privacy-recovery&connectTimeout=5&socketTimeout=10";
    private final WaPrivacyRecoveryFiles files;
    private final JdbcTemplate jdbc;
    private final DataSourceTransactionManager manager;
    private final WaPrivacyRecoveryFixture fixture;
    private final LabTime time=new LabTime();
    private final WaClock clock=new WaClock(time);
    private final WaPrivacyService.Scope scope;
    private final long deadline=System.nanoTime()+90_000_000_000L;
    private final Consumer<WaPrivacyService.Scope> authority=this::authorize;

    private WaPrivacyRecoveryCommand(WaPrivacyRecoveryFiles files,Connection connection) {
        this.files=files;
        var source=new SingleConnectionDataSource(connection,true); jdbc=new JdbcTemplate(source);
        manager=new DataSourceTransactionManager(source);
        fixture=new WaPrivacyRecoveryFixture(jdbc,files.request.nonce());
        scope=new WaPrivacyService.Scope(ENVIRONMENT,fixture.instance);
        jdbc.execute("SET statement_timeout='3s'"); jdbc.execute("SET lock_timeout='500ms'"); jdbc.execute("SET TIME ZONE 'UTC'");
    }
    public static void main(String[] args) {
        try {
            require(args.length==1); var files=new WaPrivacyRecoveryFiles(Path.of(args[0]));
            Class.forName("org.postgresql.Driver");
            try(var connection=DriverManager.getConnection(JDBC,"postgres",files.request.password())) { execute(files,connection); }
        } catch(Exception failure) { System.exit(2); }
    }
    /** Package seam for real PostgreSQL tests; production main always uses the fixed loopback catalog. */
    static ObjectNode execute(WaPrivacyRecoveryFiles files,Connection connection) throws Exception {
        var command=new WaPrivacyRecoveryCommand(files,connection);
        ObjectNode result=command.run();
        files.write(files.result().getFileName().toString(),result); return result;
    }
    private ObjectNode run() throws Exception {
        guardDatabase(); time.now=jdbc.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant();
        authorize(scope); require(trafficBlocked());
        var journal=new WaPrivacyFileJournal(files.journal());
        if(files.request.operation()==WaPrivacyRecoveryFiles.Operation.PREPARE) {
            files.emptyJournal();
            require(fixture.count("SELECT count(*) FROM wa.privacy_erasure_ledger WHERE environment=? AND instance_id=?",ENVIRONMENT,scope.instanceId())==0);
            journal.initializeEmpty();
            ObjectNode state=fixture.prepare(new TransactionTemplate(manager)); files.write("fixture.json",state);
            fixture.verifyOriginal(state); authorize(scope); require(trafficBlocked());
            return result("PREPARED",false).put("fixtureReady",true);
        }
        ObjectNode state=files.fixture(); Instant base=Instant.parse(state.path("baseAt").asText());
        fixture.verifyOriginal(state); time.now=base;
        WaPrivacyFileJournal.Snapshot original;
        if(files.request.operation()==WaPrivacyRecoveryFiles.Operation.CLOSE_PURGE) {
            require(service(journal).close(scope,fixture.alpha).status().equals("CLOSED"));
            original=journal.snapshot(); validateEntry(original,base);
        } else {
            ObjectNode head=files.retainedHead(); time.now=base.plusSeconds(31L*86400);
            original=journal.snapshot();
            require(original.sequence()==head.path("sequence").longValue() && original.digest().equals(head.path("digest").asText()));
            validateEntry(original,base);
            var restored=restore(journal).reconcile(scope,original.sequence(),original.digest(),0,100);
            require(restored.status().equals("RETENTION_PENDING") && !restored.ready()
                && restored.imported()==1 && restored.acknowledged()==1);
            require(new WaPrivacyLedger(jdbc).entry(fixture.alpha).equals(original.records().get(0).entry()));
        }
        time.now=base.plusSeconds(31L*86400); WaPrincipal actor=actor();
        int terminalErased=0,technicalErased=0,historyDeleted=0,projected=0;
        var policy=WaPrivacyPolicy.syntheticTests();
        var terminal=new WaPrivacyTerminalMaintenance(jdbc,manager,clock,policy,false);
        var technical=new WaPrivacyTechnicalRetention(jdbc,manager,clock,policy,false);
        var history=new WaPrivacyHistoryRetention(jdbc,manager,clock,policy,false);
        var projection=new WaPrivacyHistoryProjection(jdbc,manager,clock,policy,authority,false);
        boolean complete=false;
        for(int round=0;round<20;round++) {
            authorize(scope); require(trafficBlocked());
            var t=terminal.tick(actor,100,null); terminalErased+=t.erased();
            var x=technical.tick(actor,100,null); technicalErased+=x.erased()+x.deleted();
            var p=projection.tick(actor,100,null); projected+=p.erased()+p.deleted();
            var h=history.tick(actor,100,null); historyDeleted+=h.deleted();
            if(t.complete() && x.complete() && p.complete() && h.complete()) { complete=true; break; }
        }
        require(complete && terminalErased>0 && technicalErased>0 && historyDeleted>0 && projected>0);
        require(service(journal).erase(scope,fixture.alpha).status().equals("ERASED"));
        fixture.verifyErased(state); validateEntry(journal.snapshot(),base); require(journal.snapshot().equals(original));
        var readiness=restore(journal).reconcile(scope,original.sequence(),original.digest(),0,100);
        require(readiness.ready() && readiness.pending().isEmpty()); authorize(scope); require(trafficBlocked());
        ObjectNode result=result(files.request.operation()==WaPrivacyRecoveryFiles.Operation.CLOSE_PURGE
            ? "SOURCE_ERASURE_VERIFIED" : "RESTORED_ERASURE_VERIFIED",true);
        result.putObject("head").put("sequence",original.sequence()).put("digest",original.digest());
        result.putObject("maintenance").put("terminalErased",terminalErased).put("technicalChanged",technicalErased)
            .put("historyDeleted",historyDeleted).put("historyProjected",projected);
        result.put("alphaAbsent",true).put("betaPreserved",true).put("corePreserved",true)
            .put("originalClosureAndAckPreserved",true).put("journalUnchangedAfterPurge",true);
        return result;
    }
    private WaPrivacyService service(WaPrivacyFileJournal journal) {
        return new WaPrivacyService(jdbc,manager,clock,WaPrivacyPolicy.syntheticTests(),journal,authority);
    }
    private WaPrivacyRestore restore(WaPrivacyFileJournal journal) {
        return new WaPrivacyRestore(jdbc,manager,clock,WaPrivacyPolicy.syntheticTests(),journal,authority,this::trafficBlocked);
    }
    private void validateEntry(WaPrivacyFileJournal.Snapshot snapshot,Instant base) {
        require(snapshot.sequence()==1 && snapshot.records().size()==1);
        var record=snapshot.records().get(0); var entry=record.entry();
        require(entry.subjectId().equals(fixture.alpha) && entry.environment().equals(ENVIRONMENT) && entry.instanceId().equals(fixture.instance));
        var body=WaJson.object(entry.canonicalPayload());
        require(Instant.parse(body.path("closedAt").asText()).equals(base)
            && Instant.parse(body.path("eraseAfter").asText()).equals(base.plusSeconds(30L*86400)));
        if(fixture.count("SELECT count(*) FROM wa.privacy_erasure_ledger WHERE subject_id=?",fixture.alpha)>0) {
            require(new WaPrivacyLedger(jdbc).entry(fixture.alpha).equals(entry));
            require(fixture.count("SELECT count(*) FROM wa.privacy_erasure_ledger WHERE subject_id=? AND journal_sequence=? AND journal_digest=? AND created_at=?",
                fixture.alpha,record.acknowledgement().journalSequence(),record.acknowledgement().chainDigest(),Timestamp.from(base))==1);
        }
    }
    private ObjectNode result(String status,boolean ready) {
        return WaJson.object("{}").put("schemaVersion",1).put("status",status).put("ready",ready)
            .put("environment",ENVIRONMENT).put("synthetic",true).put("releaseEligible",false);
    }
    private void guardDatabase() {
        require(DATABASE.equals(fixture.string("SELECT current_database()")) && "postgres".equals(fixture.string("SELECT current_user")));
        require(fixture.count("SELECT count(*) FROM wa.test_cohort WHERE NOT synthetic OR environment<>?",ENVIRONMENT)==0);
        require(fixture.count("SELECT count(*) FROM wa.test_cohort")>=1 && fixture.count("SELECT count(*) FROM wa.identity")<=3);
        require(fixture.count("SELECT count(*) FROM public.flyway_schema_history WHERE version='44' AND success")==1);
    }
    private WaPrincipal actor() {
        var ids=jdbc.queryForList("""
            SELECT id FROM wa.service_credential WHERE token_hash=? AND principal='SYSTEM_JOB' AND environment=?
              AND allowed_instance_id=? AND revoked_at IS NULL AND expires_at>? FOR SHARE
            """,UUID.class,WaCrypto.digest(files.request.credential()),ENVIRONMENT,scope.instanceId(),Timestamp.from(clock.now()));
        require(ids.size()==1);
        return new WaPrincipal(WaPrincipal.Kind.SYSTEM_JOB,ids.get(0),null,null,scope.instanceId(),null,0,false);
    }
    private void authorize(WaPrivacyService.Scope requested) {
        require(System.nanoTime()<deadline && requested.equals(scope)); guardDatabase(); actor(); require(trafficBlocked());
    }
    private boolean trafficBlocked() {
        return System.nanoTime()<deadline && fixture.count("""
            SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid()
              AND backend_type NOT IN ('autovacuum worker')
            """)==0;
    }
    private static final class LabTime extends Clock {
        Instant now;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
