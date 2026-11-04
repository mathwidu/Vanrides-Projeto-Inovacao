package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.config.PublicRuntimeConfigurationGuard;
import com.vanrides.vanrides.config.TeamApiTestEnvironment;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaOperatorAdmissionPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_admission_test").withUsername("wa_admission_test").withPassword("synthetic-admission-password");
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement static class Transactions { }
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private WaOperatorService service;
    private WaCrypto crypto;
    private final Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS);
    private UUID batch,technical,identity;
    private String operator,instance;
    @BeforeAll static void migrate() { Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).load().migrate(); }
    @BeforeEach void prepare() {
        batch=UUID.randomUUID();technical=UUID.randomUUID();identity=UUID.randomUUID();operator=WaCrypto.randomToken();instance="admission-"+UUID.randomUUID();
        context=new AnnotationConfigApplicationContext();context.getEnvironment().setActiveProfiles("homolog");
        TeamApiTestEnvironment.validOnline().getPropertySources().forEach(context.getEnvironment().getPropertySources()::addLast);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("private-test",Map.of(
            "spring.main.web-application-type","none","vanrides.wa.operator-credential",operator,
            "vanrides.wa.operator-expires-at",now.plusSeconds(3600).toString())));
        context.register(Transactions.class,PublicRuntimeConfigurationGuard.class,WaSettings.class,WaCrypto.class,
            com.vanrides.vanrides.whatsapp.config.WaTrackingScheduling.class,
            WaOperatorMode.class,WaOperatorFixtures.class,WaOperatorReset.class,WaOperatorRevocation.class,WaOperatorService.class);
        context.registerBean(DataSource.class,()->new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()));
        context.registerBean(JdbcTemplate.class,()->new JdbcTemplate(context.getBean(DataSource.class)));
        context.registerBean(PlatformTransactionManager.class,()->new DataSourceTransactionManager(context.getBean(DataSource.class)));
        context.registerBean(WaClock.class,()->new WaClock(Clock.fixed(now,ZoneOffset.UTC)));context.refresh();
        jdbc=context.getBean(JdbcTemplate.class);service=context.getBean(WaOperatorService.class);crypto=context.getBean(WaCrypto.class);
    }
    @AfterEach void close() { if(context!=null) context.close(); }
    private ObjectNode request(String operation,UUID cohort) {
        return WaJson.MAPPER.createObjectNode().put("environment","vanrides-mvp-hml").put("operation",operation)
            .put("batchId",cohort.toString()).put("operatorCredential",operator);
    }
    private WaOperatorRequest parse(ObjectNode request) { return WaOperatorRequest.parse(WaJson.write(request).getBytes(StandardCharsets.UTF_8)); }
    private ObjectNode execute(ObjectNode request) {
        var result=new AtomicReference<ObjectNode>();service.execute(parse(request),v->result.set(v.deepCopy()));return result.get();
    }
    private void real(UUID cohort) { execute(request("create-authorized-cohort",cohort).put("reasonCode","AUTHORIZED_HOMOLOGATION_GROUP")); }
    private UUID credential(String principal,String scope) {
        execute(request("create-test-cohort",technical).put("synthetic",true));
        return UUID.fromString(execute(request("provision-service-credential",technical).put("principal",principal).put("allowedInstanceId",scope)
            .put("expiresAt",now.plusSeconds(600).toString())).path("credentialId").asText());
    }
    private ObjectNode admission(UUID person,UUID cohort,String phone,String scope) {
        return request("admit-participant",cohort).put("identityId",person.toString()).put("allowedInstanceId",scope)
            .put("phoneNumber",phone).put("phoneVerification","PARTICIPANT_CONFIRMED");
    }
    private ObjectNode admit() { return admission(identity,batch,"+15550001111",instance); }
    private ObjectNode bootstrap(UUID person,UUID invite) {
        return request("driver-bootstrap",batch).put("identityId",person.toString()).put("inviteId",invite.toString())
            .put("expiresAt",now.plusSeconds(600).toString());
    }
    private long count(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
    @Test void admissionIsIdempotentPrivateAndDoesNotGrantARole() {
        assertThat(context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
        credential("CHANNEL",instance);real(batch);real(batch);var first=execute(admit());var second=execute(admit());
        assertThat(second.path("identityId")).isEqualTo(first.path("identityId"));assertThat(first.path("state").asText()).isEqualTo("ADMITTED");
        assertThat(first.toString()).doesNotContain("15550001111","@s.whatsapp.net",operator,"phoneNumber");
        assertThat(count("SELECT count(*) FROM wa.identity WHERE test_cohort_id=?",batch)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.identity_alias WHERE identity_id=? AND provider='EVOLUTION' AND instance_id=? AND alias_type='PN' AND alias_value='15550001111@s.whatsapp.net'",identity,instance)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM wa.profile WHERE identity_id=?",identity)).isZero();
        assertThat(count("SELECT count(*) FROM wa.integration_inbox WHERE identity_id=?",identity)).isZero();
        assertThat(jdbc.queryForObject("SELECT string_agg(action||reason_code,',') FROM wa.audit_event WHERE resource_id=?",String.class,identity))
            .doesNotContain("15550001111",operator);
    }
    @Test void realAdmissionRequiresOnlineModeAndCannotConvertAnExistingCohort() {
        credential("CHANNEL",instance);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("disabled-online",Map.of("vanrides.wa.mvp-online","false")));
        assertThatThrownBy(()->real(batch)).isInstanceOf(WaOperatorFailure.class);assertThat(count("SELECT count(*) FROM wa.test_cohort WHERE id=?",batch)).isZero();
        context.getEnvironment().getPropertySources().remove("disabled-online");
        assertThatThrownBy(()->real(technical)).isInstanceOf(WaOperatorFailure.class);
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'other_environment',false)",batch);
        assertThatThrownBy(()->real(batch)).isInstanceOf(WaOperatorFailure.class);assertThatThrownBy(()->execute(admit())).isInstanceOf(WaOperatorFailure.class);
        assertThat(jdbc.queryForObject("SELECT environment FROM wa.test_cohort WHERE id=?",String.class,batch)).isEqualTo("other_environment");
    }
    @Test void phoneOrIdentityCannotBeReboundAcrossGroupsOrInstances() {
        credential("CHANNEL",instance);real(batch);execute(admit());UUID otherBatch=UUID.randomUUID(),otherIdentity=UUID.randomUUID();real(otherBatch);
        for(var input:new ObjectNode[]{admission(otherIdentity,otherBatch,"+15550001111",instance),admission(identity,otherBatch,"+15550001111",instance),
                admission(identity,batch,"+15550002222",instance)}) assertThatThrownBy(()->execute(input)).isInstanceOf(WaOperatorFailure.class);
        String otherInstance="other-"+UUID.randomUUID();credential("CHANNEL",otherInstance);
        assertThatThrownBy(()->execute(admission(identity,batch,"+15550001111",otherInstance))).isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.identity WHERE id=?",otherIdentity)).isZero();
        assertThat(count("SELECT count(*) FROM wa.identity_alias WHERE identity_id=?",identity)).isEqualTo(1);
    }
    @Test void aValidChannelInTheSameEnvironmentAndInstanceMustAlreadyExist() {
        real(batch);assertThatThrownBy(()->execute(admit())).isInstanceOf(WaOperatorFailure.class);UUID channel=credential("CHANNEL",instance);
        jdbc.update("UPDATE wa.service_credential SET revoked_at=? WHERE id=?",Timestamp.from(now),channel);
        assertThatThrownBy(()->execute(admit())).isInstanceOf(WaOperatorFailure.class);
        jdbc.update("UPDATE wa.service_credential SET revoked_at=NULL,environment='other_environment' WHERE id=?",channel);
        assertThatThrownBy(()->execute(admit())).isInstanceOf(WaOperatorFailure.class);
        jdbc.update("UPDATE wa.service_credential SET environment='vanrides-mvp-hml',created_at=?,expires_at=? WHERE id=?",
            Timestamp.from(now.minusSeconds(600)),Timestamp.from(now.minusSeconds(1)),channel);
        assertThatThrownBy(()->execute(admit())).isInstanceOf(WaOperatorFailure.class);assertThat(count("SELECT count(*) FROM wa.identity WHERE id=?",identity)).isZero();
    }
    @Test void realBootstrapReplaysOneServerSecretWithoutRawDatabaseStorageOrRoleGrant() {
        credential("CHANNEL",instance);real(batch);execute(admit());UUID invite=UUID.randomUUID();var first=execute(bootstrap(identity,invite));var second=execute(bootstrap(identity,invite));
        String code=first.path("driverInviteCode").asText();assertThat(second.path("driverInviteCode").asText()).isEqualTo(code);
        assertThat(count("SELECT count(*) FROM wa.driver_bootstrap_invite WHERE id=? AND identity_id=? AND code_hash=?",invite,identity,crypto.secretHash("driver-bootstrap",code))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT code_hash FROM wa.driver_bootstrap_invite WHERE id=?",String.class,invite)).doesNotContain(code);
        assertThat(count("SELECT count(*) FROM wa.profile WHERE identity_id=?",identity)).isZero();
        assertThat(first.toString()).doesNotContain("15550001111");
    }
    @Test void reusedInviteCannotChangeIdentityExpiryOrResurrectConsumedOrRevokedState() {
        credential("CHANNEL",instance);real(batch);execute(admit());UUID invite=UUID.randomUUID();execute(bootstrap(identity,invite));
        assertThatThrownBy(()->execute(bootstrap(identity,invite).put("expiresAt",now.plusSeconds(601).toString()))).isInstanceOf(WaOperatorFailure.class);
        UUID other=UUID.randomUUID();execute(admission(other,batch,"+15550002222",instance));
        assertThatThrownBy(()->execute(bootstrap(other,invite))).isInstanceOf(WaOperatorFailure.class);
        jdbc.update("UPDATE wa.driver_bootstrap_invite SET revoked_at=? WHERE id=?",Timestamp.from(now),invite);
        assertThatThrownBy(()->execute(bootstrap(identity,invite))).isInstanceOf(WaOperatorFailure.class);
        jdbc.update("UPDATE wa.driver_bootstrap_invite SET revoked_at=NULL,consumed_at=? WHERE id=?",Timestamp.from(now),invite);
        assertThatThrownBy(()->execute(bootstrap(identity,invite))).isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.driver_bootstrap_invite WHERE identity_id=?",identity)).isEqualTo(1);
    }
    @Test void realCohortCannotBeSeededCompletedResetPromotedOrProvisionTechnicalCredentials() {
        credential("CHANNEL",instance);real(batch);execute(admit());UUID worker=credential("CONVERSATION_WORKER",instance);
        for(var input:new ObjectNode[]{request("reset-test-data",batch),request("create-test-cohort",batch).put("synthetic",true),
            request("complete-test-inbox",batch).put("inboxEventId",UUID.randomUUID().toString()),
            request("provision-service-credential",batch).put("principal","CHANNEL").put("allowedInstanceId",instance).put("expiresAt",now.plusSeconds(600).toString()),
            request("seed-test-data",batch).put("identityId",identity.toString()).put("inboxEventId",UUID.randomUUID().toString()).put("workerCredentialId",worker.toString()).put("leaseExpiresAt",now.plusSeconds(60).toString()),
            request("driver-bootstrap",batch).put("identityId",identity.toString()).put("expiresAt",now.plusSeconds(600).toString())})
            assertThatThrownBy(()->execute(input)).isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.identity_alias WHERE identity_id=?",identity)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT synthetic FROM wa.test_cohort WHERE id=?",Boolean.class,batch)).isFalse();
    }
    @Test void privateOutputFailureRollsBackAdmissionAndInvitation() {
        credential("CHANNEL",instance);real(batch);
        assertThatThrownBy(()->service.execute(parse(admit()),v->{throw new WaOperatorFailure();})).isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.identity WHERE id=?",identity)).isZero();execute(admit());UUID invite=UUID.randomUUID();
        assertThatThrownBy(()->service.execute(parse(bootstrap(identity,invite)),v->{throw new WaOperatorFailure();})).isInstanceOf(WaOperatorFailure.class);
        assertThat(count("SELECT count(*) FROM wa.driver_bootstrap_invite WHERE id=?",invite)).isZero();
    }
}
