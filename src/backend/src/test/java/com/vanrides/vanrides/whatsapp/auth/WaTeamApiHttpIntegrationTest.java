package com.vanrides.vanrides.whatsapp.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("homolog")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.profiles.active=homolog","vanrides.wa.team-api-only=true","vanrides.email.provider=disabled",
    "vanrides.wa.enabled=true","vanrides.wa.environment=vanrides-team-hml","vanrides.wa.new-starts-enabled=false",
    "vanrides.wa.approach-enabled=false","vanrides.wa.test-cohort-only=true",
    "vanrides.wa.public-base-url=https://vanrides-inovacao-api.matheusduarte.dev.br",
    "vanrides.auth.jwt-secret=synthetic-team-http-test-secret-at-least-32-characters","vanrides.auth.basic-enabled=false",
    "vanrides.email.verification.public-base-url=","vanrides.wa.operator-expires-at=2035-01-01T00:00:00Z"
})
@Testcontainers(disabledWithoutDocker=true)
class WaTeamApiHttpIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("vanrides_team_api_test").withUsername("vanrides_test").withPassword("synthetic-team-api-database");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",POSTGRES::getJdbcUrl);properties.add("spring.datasource.username",POSTGRES::getUsername);
        properties.add("spring.datasource.password",POSTGRES::getPassword);
        properties.add("vanrides.wa.hash-key",()->key(11));properties.add("vanrides.wa.response-key",()->key(22));
        properties.add("vanrides.wa.operator-credential",()->key(33));
    }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    private static final String BASE="/integrations/whatsapp/v1";
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private record Fixture(UUID actor,UUID credential,String token,UUID event,String instance,String lease) { }
    private static String key(int value) { byte[] b=new byte[32];java.util.Arrays.fill(b,(byte)value);return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private Fixture fixture(String provider,boolean synthetic,String text,boolean claimed) {
        UUID cohort=UUID.randomUUID(),actor=UUID.randomUUID(),credential=UUID.randomUUID(),event=UUID.randomUUID();
        String token=WaCrypto.randomToken(),instance="team-"+UUID.randomUUID(),lease=WaCrypto.randomToken();Instant now=Instant.now();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'vanrides-team-hml',?)",cohort,synthetic);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)",actor,cohort);
        jdbc.update("INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,created_at,expires_at) VALUES (?,'CONVERSATION_WORKER','vanrides-team-hml',?,?,?,?)",
            credential,WaCrypto.digest(token),instance,Timestamp.from(now),Timestamp.from(now.plusSeconds(600)));
        jdbc.update("""
            INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,normalized_kind,payload_json,
              state,lease_credential_id,lease_token_hash,lease_generation,lease_expires_at)
            VALUES (?,?,?,?,?,'MESSAGE','TEXT',?::jsonb,?,?,?,?,?)
            """,event,actor,provider,instance,event.toString(),WaJson.write(Map.of("text",text,"occurredAt",now.toString())),
            claimed?"CLAIMED":"PENDING",claimed?credential:null,claimed?WaCrypto.digest(lease):null,claimed?1:0,claimed?Timestamp.from(now.plusSeconds(60)):null);
        return new Fixture(actor,credential,token,event,instance,lease);
    }
    private HttpResponse<String> send(String method,String path,String body,Map<String,String> headers) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));headers.forEach(builder::header);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    private Map<String,String> headers(Fixture fixture,String lease) {
        var result=new HashMap<String,String>();result.put("Authorization","Bearer "+fixture.token());result.put("Content-Type","application/json");
        if(lease!=null) result.put("X-Inbox-Lease",lease);return result;
    }
    private JsonNode json(HttpResponse<String> response) { return WaJson.object(response.body()); }
    private String claim(Fixture fixture) throws Exception {
        var claimed=send("POST",BASE+"/inbox/claim","{\"batchLimit\":1,\"leaseSeconds\":60}",headers(fixture,null));
        assertThat(claimed.statusCode()).isEqualTo(200);assertThat(json(claimed).path("items")).hasSize(1);
        assertThat(json(claimed).at("/items/0/inboxEventId").asText()).isEqualTo(fixture.event().toString());return json(claimed).at("/items/0/inboxLease").asText();
    }
    private String command(Fixture fixture,String type,Map<String,Object> payload) {
        return WaJson.write(Map.of("commandType",type,"inboxEventId",fixture.event().toString(),"expectedVersion",0,"payload",payload));
    }
    @Test void publicHealthHasTheEnvironmentMarkerWithoutTrustingClientRevision() throws Exception {
        var response=send("GET","/actuator/health",null,Map.of("X-VanRides-Revision","untrusted"));
        assertThat(response.statusCode()).isEqualTo(200);assertThat(json(response).path("status").asText()).isEqualTo("UP");
        assertThat(response.headers().firstValue("X-VanRides-Environment")).contains("vanrides-team-hml");
        assertThat(response.headers().firstValue("X-VanRides-Revision")).isEmpty();assertThat(response.body()).doesNotContain("components","jdbc","password");
    }
    @Test void directHttpBlocksLegacyProvidersGpsAndUnlistedMethodsEvenWithValidWorker() throws Exception {
        var fixture=fixture("SYNTHETIC",true,"iniciar cadastro",false);
        for(String[] route:new String[][]{{"GET","/motoristas"},{"POST","/motoristas"},{"POST","/passageiros"},{"POST","/login"},
            {"POST","/company-admins/bootstrap"},{"GET","/account/me"},{"GET","/actuator/env"},{"POST",BASE+"/events"},
            {"POST",BASE+"/outbox/claim"},{"POST",BASE+"/jobs/tick"},{"POST","/driver-app/v1/pairings/begin"},{"GET","/ws"},
            {"HEAD","/actuator/health"},{"OPTIONS",BASE+"/commands"},{"GET",BASE+"/inbox/claim"}})
            assertThat(send(route[0],route[1],null,headers(fixture,null)).statusCode()).as(route[1]).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.profile WHERE identity_id=?",Integer.class,fixture.actor())).isZero();
    }
    @Test void aSyntheticOnboardingCommitsOnceReplaysAndAcknowledgesWithTheSameLease() throws Exception {
        var fixture=fixture("SYNTHETIC",true,"iniciar cadastro",false);String lease=claim(fixture);var h=headers(fixture,lease);h.put("Idempotency-Key","team-scenario-"+UUID.randomUUID());
        var context=send("GET",BASE+"/context?inboxEventId="+fixture.event(),null,h);assertThat(context.statusCode()).isEqualTo(200);
        assertThat(json(context).path("allowedCommands").toString()).isEqualTo("[\"START_ONBOARDING\"]");assertThat(json(context).path("options")).isEmpty();
        String body=command(fixture,"START_ONBOARDING",Map.of());var first=send("POST",BASE+"/commands",body,h);assertThat(first.statusCode()).isEqualTo(200);
        var replay=send("POST",BASE+"/commands",body,h);assertThat(replay.statusCode()).isEqualTo(200);assertThat(json(replay)).isEqualTo(json(first));
        var ack=send("POST",BASE+"/inbox/"+fixture.event()+"/result","{\"outcome\":\"COMPLETED\"}",h);assertThat(ack.statusCode()).isEqualTo(200);
        assertThat(json(ack).path("state").asText()).isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.profile WHERE identity_id=?",Integer.class,fixture.actor())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id=?",Integer.class,fixture.event())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=?",Integer.class,fixture.actor())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.message_attempt a JOIN wa.message_outbox o ON o.id=a.outbox_id WHERE o.recipient_identity_id=?",Integer.class,fixture.actor())).isZero();
    }
    @Test void instanceBWorkerCannotClaimReadExecuteOrAcknowledgeInstanceA() throws Exception {
        var a=fixture("SYNTHETIC",true,"iniciar cadastro",false);var b=fixture("SYNTHETIC",true,"iniciar cadastro",false);String lease=claim(a);claim(b);
        var h=headers(b,lease);h.put("Idempotency-Key","cross-instance-"+UUID.randomUUID());
        assertThat(send("GET",BASE+"/context?inboxEventId="+a.event(),null,h).statusCode()).isEqualTo(404);
        assertThat(send("POST",BASE+"/commands",command(a,"START_ONBOARDING",Map.of()),h).statusCode()).isEqualTo(404);
        assertThat(send("POST",BASE+"/inbox/"+a.event()+"/result","{\"outcome\":\"COMPLETED\"}",h).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.profile WHERE identity_id=?",Integer.class,a.actor())).isZero();
    }
    @Test void nonSyntheticProviderAndCohortCannotEnterTheScenarioEvenWithAValidLease() throws Exception {
        for(var fixture:new Fixture[]{fixture("EVOLUTION",true,"iniciar cadastro",true),fixture("SYNTHETIC",false,"iniciar cadastro",true)}) {
            var h=headers(fixture,fixture.lease());h.put("Idempotency-Key","wrong-source-"+UUID.randomUUID());
            assertThat(send("GET",BASE+"/context?inboxEventId="+fixture.event(),null,h).statusCode()).isIn(403,404);
            assertThat(send("POST",BASE+"/commands",command(fixture,"START_ONBOARDING",Map.of()),h).statusCode()).isIn(403,404);
            assertThat(send("POST",BASE+"/inbox/"+fixture.event()+"/result","{\"outcome\":\"COMPLETED\"}",h).statusCode()).isIn(403,404);
            assertThat(json(send("POST",BASE+"/inbox/claim","{\"batchLimit\":1,\"leaseSeconds\":60}",h)).path("items")).isEmpty();
        }
    }
    @Test void offeredAndExecutedCommandsAreRestrictedToOnboardingAndExplicitHelp() throws Exception {
        var fixture=fixture("SYNTHETIC",true,"AJUDA",false);String lease=claim(fixture);var h=headers(fixture,lease);h.put("Idempotency-Key","help-scenario-"+UUID.randomUUID());
        var context=json(send("GET",BASE+"/context?inboxEventId="+fixture.event(),null,h));assertThat(context.path("allowedCommands").toString()).isEqualTo("[\"RESPOND_TO_MESSAGE\"]");
        for(String type:new String[]{"SET_PROFILE_NAME","REQUEST_SUPPORT","GET_DRIVER_LOCATION","FINISH_RUN"})
            assertThat(send("POST",BASE+"/commands",command(fixture,type,Map.of()),h).statusCode()).isEqualTo(403);
        assertThat(send("POST",BASE+"/commands",command(fixture,"RESPOND_TO_MESSAGE",Map.of("responseCode","CONTINUE")),h).statusCode()).isEqualTo(403);
        assertThat(send("POST",BASE+"/commands",command(fixture,"RESPOND_TO_MESSAGE",Map.of("responseCode","HELP")),h).statusCode()).isEqualTo(200);
    }
    @Test void missingExpiredRevokedAndWrongPrincipalCredentialsRemainDenied() throws Exception {
        assertThat(send("POST",BASE+"/inbox/claim","{}",Map.of("Content-Type","application/json")).statusCode()).isEqualTo(401);
        for(String state:new String[]{"expired","revoked","principal"}) {
            var fixture=fixture("SYNTHETIC",true,"iniciar cadastro",false);
            if(state.equals("expired")) jdbc.update("UPDATE wa.service_credential SET created_at=?,expires_at=? WHERE id=?",
                Timestamp.from(Instant.now().minusSeconds(120)),Timestamp.from(Instant.now().minusSeconds(1)),fixture.credential());
            if(state.equals("revoked")) jdbc.update("UPDATE wa.service_credential SET revoked_at=? WHERE id=?",Timestamp.from(Instant.now()),fixture.credential());
            if(state.equals("principal")) jdbc.update("UPDATE wa.service_credential SET principal='CHANNEL' WHERE id=?",fixture.credential());
            assertThat(send("POST",BASE+"/inbox/claim","{\"batchLimit\":1,\"leaseSeconds\":60}",headers(fixture,null)).statusCode()).isIn(401,403);
        }
    }
}
