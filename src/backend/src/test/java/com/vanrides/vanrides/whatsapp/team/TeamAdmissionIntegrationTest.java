package com.vanrides.vanrides.whatsapp.team;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.admission.WaAdmissionStore;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.transport.WaEventService;
import com.vanrides.vanrides.whatsapp.transport.WaEvolutionNormalizer;
import com.vanrides.vanrides.whatsapp.transport.WaTransportAccess;
import com.vanrides.vanrides.whatsapp.transport.WaTransportTestDatabase;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.vanrides.vanrides.whatsapp.team.WaTeamOperations.Action.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class TeamAdmissionIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_team_admission").withUsername("vanrides_test").withPassword("synthetic-team-db");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String ENV = "synthetic-team-test", INSTANCE = "synthetic-team";
    private static final String PHONE = "+12025550101", PN = "12025550101@s.whatsapp.net", LID = "1001@lid";
    private static final String OTHER_PHONE = "+12025550102", OTHER_PN = "12025550102@s.whatsapp.net";
    private static final WaTeamGitHub.Actor GITHUB = new WaTeamGitHub.Actor(314, "synthetic-collaborator");
    private static final Runnable LIVE = () -> { };
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private WaTeamOperations operations;
    private WaEventService events;
    private Clock time;
    private WaTeamSettings team;
    private WaAdmissionStore admission;
    private WaPrincipal channel;
    private UUID cohort;

    @BeforeEach
    void reset() {
        var source = DATABASE.nextDataSource();
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        time = mock(Clock.class);
        when(time.instant()).thenReturn(NOW);
        var clock = new WaClock(time);
        var settings = new WaSettings(new MockEnvironment().withProperty("vanrides.wa.enabled", "true")
            .withProperty("vanrides.wa.environment", ENV)
            .withProperty("vanrides.wa.public-base-url", "http://127.0.0.1:8080")
            .withProperty("vanrides.wa.hash-key", key((byte) 1))
            .withProperty("vanrides.wa.response-key", key((byte) 2))
            .withProperty("vanrides.wa.operator-credential", key((byte) 3))
            .withProperty("vanrides.wa.operator-expires-at", "2035-01-01T00:00:00Z"));
        var crypto = new WaCrypto(settings);
        team = mock(WaTeamSettings.class);
        cohort = UUID.randomUUID();
        when(team.enabled()).thenReturn(true);
        when(team.instance()).thenReturn(INSTANCE);
        when(team.cohort()).thenReturn(cohort);
        when(team.strictIngress(INSTANCE)).thenReturn(true);
        admission = new WaAdmissionStore(jdbc, crypto);
        operations = new WaTeamOperations(jdbc, admission, team, settings, crypto, clock);
        events = new WaEventService(jdbc, new WaTransportAccess(jdbc, settings, clock), new WaEvolutionNormalizer(crypto),
            settings, clock, team, admission);
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,?,false)", cohort, ENV);
        channel = new WaPrincipal(WaPrincipal.Kind.CHANNEL, UUID.randomUUID(), null, null, INSTANCE, null, 0, false);
        credential(channel.credentialId(), "CHANNEL", INSTANCE);
    }

    @Test
    void driverInviteAndAuditCommitOnceAndReplayIsBoundToActorAndRequest() {
        var request = request(ISSUE_DRIVER_INVITE);
        UUID correlation = UUID.randomUUID();
        var first = execute(request, contact(PN, LID), correlation, LIVE);
        String code = first.path("driverInviteCode").asText();
        assertThat(code).matches("[A-Za-z0-9_-]{43}");
        assertThat(first.path("expiresAt").asText()).isEqualTo(NOW.plusSeconds(86400).toString());
        assertThat(operations.replay(GITHUB, request)).isEqualTo(first);
        assertThat(execute(request, contact(PN, LID), UUID.randomUUID(), LIVE)).isEqualTo(first);
        assertThatThrownBy(() -> operations.replay(new WaTeamGitHub.Actor(200, "another"), request))
            .isInstanceOfSatisfying(WaProblem.class, problem -> assertThat(problem.code()).isEqualTo(WaProblem.Code.IDEMPOTENCY_CONFLICT));
        assertThatThrownBy(() -> operations.replay(GITHUB, new WaTeamOperations.Request(OTHER_PHONE, request.action(), request.id())))
            .isInstanceOfSatisfying(WaProblem.class, problem -> assertThat(problem.code()).isEqualTo(WaProblem.Code.IDEMPOTENCY_CONFLICT));
        assertThat(jdbc.queryForList("SELECT github_actor_id, github_login, correlation_id FROM wa.team_admission_operation"))
            .singleElement().satisfies(row -> assertThat(row).containsEntry("github_actor_id", GITHUB.id())
                .containsEntry("github_login", GITHUB.login()).containsEntry("correlation_id", correlation));
        assertThat(jdbc.queryForObject("SELECT created_by_github_id FROM wa.driver_bootstrap_invite", Long.class)).isEqualTo(GITHUB.id());
        assertThat(jdbc.queryForObject("SELECT code_hash FROM wa.driver_bootstrap_invite", String.class)).doesNotContain(code);
        assertThat(jdbc.queryForObject("SELECT request_fingerprint FROM wa.team_admission_operation", String.class)).doesNotContain(PHONE, code);
        assertThat(count("wa.profile")).isZero();
        assertThat(count("wa.identity")).isOne();
        assertThat(count("wa.identity_alias")).isEqualTo(2);
    }

    @Test
    void canonicalPassengerKeepsItsProfileAndConsentBesideAnEmptyLegacyAdmission() {
        String oldPn = "5511990001234@s.whatsapp.net", canonicalPn = "551190001234@s.whatsapp.net";
        UUID legacy = legacyIdentity(oldPn), identity = legacyIdentity(canonicalPn);
        var contact = new WaTeamEvolution.Contact(oldPn, canonicalPn, LID);
        jdbc.update("""
            INSERT INTO wa.profile(identity_id,role,status,notice_version,notice_accepted_at)
            VALUES (?,'PASSENGER','ACTIVE','synthetic-v1',?)
            """, identity, Timestamp.from(NOW));
        var passenger = new WaTeamOperations.Request("+5511990001234", ADMIT_PASSENGER, UUID.randomUUID());
        var driver = new WaTeamOperations.Request(passenger.phone(), ISSUE_DRIVER_INVITE, UUID.randomUUID());
        assertThat(operations.status(contact).path("identityId").asText()).isEqualTo(identity.toString());
        assertThat(operations.status(new WaTeamEvolution.Contact(oldPn, oldPn, null)).path("admissionStatus").asText())
            .isEqualTo("NOT_ADMITTED");
        assertThat(event(oldPn, null).path("status").asText()).isEqualTo("IGNORED");
        assertThat(event(canonicalPn, null).path("status").asText()).isEqualTo("PERSISTED");
        var result = execute(passenger, contact, UUID.randomUUID(), LIVE);
        assertThat(result.path("identityId").asText()).isEqualTo(identity.toString());
        assertThat(result.path("profileStatus").asText()).isEqualTo("ACTIVE");
        assertThat(result.has("driverInviteCode")).isFalse();
        assertThatThrownBy(() -> execute(driver, contact, UUID.randomUUID(), LIVE))
            .isInstanceOfSatisfying(WaTeamProblem.class, problem -> assertThat(problem.code).isEqualTo(WaTeamProblem.Code.PROFILE_CONFLICT));
        assertThat(count("wa.identity")).isEqualTo(2);
        assertThat(count("wa.driver_bootstrap_invite")).isZero();
        assertThat(jdbc.queryForObject("SELECT notice_version FROM wa.profile WHERE identity_id=?", String.class, identity))
            .isEqualTo("synthetic-v1");
        jdbc.update("INSERT INTO wa.conversation_state(identity_id) VALUES (?)", legacy);
        assertThatThrownBy(() -> operations.status(contact))
            .isInstanceOfSatisfying(WaTeamProblem.class, problem -> assertThat(problem.code).isEqualTo(WaTeamProblem.Code.ADMISSION_CONFLICT));
    }

    @Test
    void renewalRequiresExpiredInviteAndReplacesItWithNewUuidAndCode() {
        var request = request(ISSUE_DRIVER_INVITE);
        var first = execute(request, contact(PN, null), UUID.randomUUID(), LIVE);
        assertThatThrownBy(() -> execute(request(RENEW_DRIVER_INVITE), contact(PN, null), UUID.randomUUID(), LIVE))
            .isInstanceOfSatisfying(WaTeamProblem.class, problem -> assertThat(problem.code).isEqualTo(WaTeamProblem.Code.INVITE_NOT_RENEWABLE));
        when(time.instant()).thenReturn(NOW.plusSeconds(86400));
        assertThat(operations.replay(GITHUB, request).path("inviteStatus").asText()).isEqualTo("EXPIRED");
        assertThat(operations.replay(GITHUB, request).has("driverInviteCode")).isFalse();
        assertThatThrownBy(() -> execute(request(ISSUE_DRIVER_INVITE), contact(PN, null), UUID.randomUUID(), LIVE))
            .isInstanceOfSatisfying(WaTeamProblem.class, problem -> assertThat(problem.code).isEqualTo(WaTeamProblem.Code.INVITE_EXPIRED));
        var renewal = request(RENEW_DRIVER_INVITE);
        var next = execute(renewal, contact(PN, null), UUID.randomUUID(), LIVE);
        assertThat(next.path("inviteId")).isNotEqualTo(first.path("inviteId"));
        assertThat(next.path("driverInviteCode")).isNotEqualTo(first.path("driverInviteCode"));
        assertThat(next.path("expiresAt").asText()).isEqualTo(NOW.plusSeconds(172800).toString());
        assertThat(operations.replay(GITHUB, renewal)).isEqualTo(next);
        assertThat(operations.replay(GITHUB, request).path("inviteStatus").asText()).isEqualTo("REVOKED");
        assertThat(count("wa.team_admission_operation")).isEqualTo(2);
        assertThat(count("wa.driver_bootstrap_invite")).isEqualTo(2);
    }

    @Test
    void aliasConflictAndSessionRevocationLeaveNoPartialAdmissionOrAudit() {
        when(team.strictIngress(INSTANCE)).thenReturn(false);
        assertThatThrownBy(() -> operations.status(contact(PN, null)))
            .isInstanceOfSatisfying(WaProblem.class, problem -> assertThat(problem.code()).isEqualTo(WaProblem.Code.DEPENDENCY_UNAVAILABLE));
        assertThatThrownBy(() -> execute(request(ISSUE_DRIVER_INVITE), contact(PN, null), UUID.randomUUID(), LIVE))
            .isInstanceOfSatisfying(WaProblem.class, problem -> assertThat(problem.code()).isEqualTo(WaProblem.Code.DEPENDENCY_UNAVAILABLE));
        when(team.strictIngress(INSTANCE)).thenReturn(true);
        UUID existing = legacyIdentity(OTHER_PN);
        jdbc.update("""
            INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value)
            VALUES (?,?,'EVOLUTION',?,'LID',?)
            """, UUID.randomUUID(), existing, INSTANCE, LID);
        assertThatThrownBy(() -> execute(request(ISSUE_DRIVER_INVITE), contact(PN, LID), UUID.randomUUID(), LIVE))
            .isInstanceOfSatisfying(WaTeamProblem.class, problem -> assertThat(problem.code).isEqualTo(WaTeamProblem.Code.ADMISSION_CONFLICT));
        assertThatThrownBy(() -> execute(request(ISSUE_DRIVER_INVITE), contact(PN, null), UUID.randomUUID(),
            () -> { throw WaProblem.unauthorized(); })).isInstanceOf(WaProblem.class);
        assertThat(count("wa.identity")).isOne();
        assertThat(count("wa.identity_alias")).isEqualTo(2);
        assertThat(count("wa.driver_bootstrap_invite")).isZero();
        assertThat(count("wa.team_admission_operation")).isZero();
    }

    @Test
    void legacyCliAuthorAndCodeStayCompatibleWithWebRecovery() {
        UUID identity = legacyIdentity(PN);
        UUID credential = UUID.randomUUID(), invite = UUID.randomUUID();
        credential(credential, "OPERATOR", null);
        String code = admission.bootstrapCode(ENV, cohort, identity, invite);
        jdbc.update("""
            INSERT INTO wa.driver_bootstrap_invite(id,identity_id,code_hash,expires_at,created_by,created_at)
            VALUES (?,?,?,?,?,?)
            """, invite, identity, admission.bootstrapHash(code), Timestamp.from(NOW.plusSeconds(7200)), credential, Timestamp.from(NOW));
        var status = operations.status(contact(PN, null));
        assertThat(status.path("inviteStatus").asText()).isEqualTo("ACTIVE");
        assertThat(status.has("driverInviteCode")).isFalse();
        var recovered = execute(request(ISSUE_DRIVER_INVITE), contact(PN, null), UUID.randomUUID(), LIVE);
        assertThat(recovered.path("driverInviteCode").asText()).isEqualTo(code);
        assertThat(recovered.path("inviteId").asText()).isEqualTo(invite.toString());
        assertThat(count("wa.driver_bootstrap_invite")).isOne();
    }

    @Test
    void strictIngressBindsLidOnlyThroughAnAdmittedPnAndNeverOnRetry() {
        assertThat(event(PN, LID).path("status").asText()).isEqualTo("IGNORED");
        UUID legacy = legacyIdentity(PN);
        assertThat(event(PN, null).path("status").asText()).isEqualTo("IGNORED");
        assertThat(operations.status(contact(PN, null)).path("admissionStatus").asText()).isEqualTo("NOT_ADMITTED");
        var admitted = execute(request(ADMIT_PASSENGER), contact(PN, null), UUID.randomUUID(), LIVE);
        assertThat(admitted.path("identityId").asText()).isEqualTo(legacy.toString());
        assertThat(operations.status(contact(PN, null)).path("admissionStatus").asText()).isEqualTo("ADMITTED");
        assertThat(event(PN, "invalid@g.us").path("status").asText()).isEqualTo("IGNORED");
        assertThat(event(LID, null).path("status").asText()).isEqualTo("IGNORED");
        assertThat(count("wa.identity")).isOne();
        assertThat(count("wa.identity_alias")).isOne();
        assertThat(count("wa.integration_inbox")).isZero();
        String original = UUID.randomUUID().toString();
        assertThat(event(PN, null, original).path("status").asText()).isEqualTo("PERSISTED");
        assertThat(event(LID, PN, original).path("status").asText()).isEqualTo("DUPLICATE");
        assertThat(count("wa.identity_alias")).isOne();
        assertThat(event(LID, PN).path("status").asText()).isEqualTo("PERSISTED");
        assertThat(event(LID, null).path("status").asText()).isEqualTo("PERSISTED");
        assertThat(event(OTHER_PN, LID).path("status").asText()).isEqualTo("IGNORED");
        assertThat(event(PN, "1002@lid").path("status").asText()).isEqualTo("IGNORED");
        assertThat(count("wa.integration_inbox")).isEqualTo(3);
        assertThat(count("wa.identity")).isOne();
        assertThat(count("wa.identity_alias")).isEqualTo(2);
        assertThat(count("wa.profile")).isZero();
    }

    private WaTeamOperations.Request request(WaTeamOperations.Action action) {
        return new WaTeamOperations.Request(PHONE, action, UUID.randomUUID());
    }

    private WaTeamEvolution.Contact contact(String pn, String lid) { return new WaTeamEvolution.Contact(pn, pn, lid); }

    private ObjectNode execute(WaTeamOperations.Request request, WaTeamEvolution.Contact contact, UUID correlation, Runnable live) {
        return tx.execute(status -> operations.execute(GITHUB, request, contact, correlation, live));
    }

    private UUID legacyIdentity(String pn) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", id, cohort);
        jdbc.update("""
            INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value)
            VALUES (?,?,'EVOLUTION',?,'PN',?)
            """, UUID.randomUUID(), id, INSTANCE, pn);
        return id;
    }

    private void credential(UUID id, String principal, String instance) {
        jdbc.update("""
            INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,created_at,expires_at)
            VALUES (?,?,?,?,?,?,?)
            """, id, principal, ENV, WaCrypto.digest(id.toString()), instance, Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(604800)));
    }

    private ObjectNode event(String remote, String alternate) {
        return event(remote, alternate, UUID.randomUUID().toString());
    }

    private ObjectNode event(String remote, String alternate, String id) {
        ObjectNode envelope = WaJson.MAPPER.createObjectNode().put("event", "messages.upsert").put("instance", INSTANCE);
        var data = envelope.putObject("data");
        var key = data.putObject("key").put("id", id).put("fromMe", false).put("remoteJid", remote);
        if (alternate != null) key.put("remoteJidAlt", alternate);
        data.putObject("message").put("conversation", "synthetic test");
        return tx.execute(status -> events.accept(channel, envelope));
    }

    private static String key(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return WaCrypto.base64(bytes);
    }

    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
}
