package com.vanrides.vanrides.whatsapp.team;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.admission.WaAdmissionStore;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.commands.WaTeamTestDriver;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;

import static com.vanrides.vanrides.whatsapp.team.WaTeamTestDriverOperations.Action.ISSUE_TEST_PASSENGER_INVITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TeamTestDriverTestSupport {
    static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    static final String ENV = "synthetic-team-test", INSTANCE = "synthetic-team";
    static final WaTeamGitHub.Actor GITHUB = new WaTeamGitHub.Actor(314, "synthetic-collaborator");
    static final WaTeamGitHub.Actor ANOTHER = new WaTeamGitHub.Actor(200, "another");
    static final Runnable LIVE = () -> { };
    final JdbcTemplate jdbc;
    final WaTeamTestDriverOperations operations;
    final WaTeamTestDriver drivers;
    final WaTeamTestDriverStore store;
    final WaCrypto crypto;
    final UUID cohort = UUID.randomUUID();
    private final TransactionTemplate tx;
    private int passengers;

    TeamTestDriverTestSupport(DataSource source) {
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        var time = mock(Clock.class);
        when(time.instant()).thenReturn(NOW);
        var clock = new WaClock(time);
        var settings = new WaSettings(new MockEnvironment().withProperty("vanrides.wa.enabled", "true")
            .withProperty("vanrides.wa.environment", ENV)
            .withProperty("vanrides.wa.public-base-url", "http://127.0.0.1:8080")
            .withProperty("vanrides.wa.hash-key", key((byte) 1))
            .withProperty("vanrides.wa.response-key", key((byte) 2))
            .withProperty("vanrides.wa.operator-credential", key((byte) 3))
            .withProperty("vanrides.wa.operator-expires-at", "2035-01-01T00:00:00Z"));
        crypto = new WaCrypto(settings);
        var team = mock(WaTeamSettings.class);
        when(team.enabled()).thenReturn(true);
        when(team.instance()).thenReturn(INSTANCE);
        when(team.cohort()).thenReturn(cohort);
        when(team.strictIngress(INSTANCE)).thenReturn(true);
        var admission = new WaAdmissionStore(jdbc, crypto);
        drivers = new WaTeamTestDriver(jdbc, clock, settings);
        store = new WaTeamTestDriverStore(jdbc);
        operations = new WaTeamTestDriverOperations(jdbc, admission,
            new WaTeamOperations(jdbc, admission, team, settings, crypto, clock), drivers, store, team, settings, crypto, clock);
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,?,false)", cohort, ENV);
    }

    WaTeamTestDriverOperations.Request issue() {
        return new WaTeamTestDriverOperations.Request(ISSUE_TEST_PASSENGER_INVITE, UUID.randomUUID(), null);
    }

    WaTeamTestDriverOperations.Request decision(WaTeamTestDriverOperations.Action action, UUID link) {
        return new WaTeamTestDriverOperations.Request(action, UUID.randomUUID(), link);
    }

    ObjectNode execute(WaTeamTestDriverOperations.Request request) { return execute(GITHUB, request); }

    ObjectNode execute(WaTeamGitHub.Actor actor, WaTeamTestDriverOperations.Request request) {
        return tx.execute(status -> operations.execute(actor, request, UUID.randomUUID(), LIVE));
    }

    UUID testDriver() {
        execute(issue());
        return store.find(cohort);
    }

    UUID normalDriver() {
        UUID driver = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", driver, cohort);
        jdbc.update("""
            INSERT INTO wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at,driver_enabled,version)
            VALUES (?,'DRIVER','ACTIVE','Motorista sintético','wa-mvp-v1',?,true,1)
            """, driver, Timestamp.from(NOW));
        jdbc.update("INSERT INTO wa.driver_authorization(driver_id) VALUES (?)", driver);
        return driver;
    }

    UUID requestedLink(UUID driver) {
        return requestedLink(driver, jdbc.queryForObject("SELECT routine_id FROM wa.team_test_driver WHERE driver_id=?", UUID.class, driver));
    }

    UUID requestedLink(UUID driver, UUID routine) {
        UUID passenger = UUID.randomUUID(), inbox = UUID.randomUUID(), invite = UUID.randomUUID(), link = UUID.randomUUID();
        UUID alias = UUID.randomUUID();
        Timestamp at = Timestamp.from(NOW);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", passenger, cohort);
        jdbc.update("""
            INSERT INTO wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at)
            VALUES (?,'PASSENGER','ACTIVE','Passageiro Sintético','wa-mvp-v1',?)
            """, passenger, at);
        jdbc.update("""
            INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value)
            VALUES (?,?,'EVOLUTION',?,'PN',?)
            """, alias, passenger, INSTANCE, (2025550000L + ++passengers) + "@s.whatsapp.net");
        jdbc.update("""
            INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,normalized_kind,payload_json,created_at,not_before)
            VALUES (?,?,'EVOLUTION',?,?,'MESSAGE','TEXT',?::jsonb,?,?)
            """, inbox, passenger, INSTANCE, inbox.toString(),
            "{\"occurredAt\":\"" + NOW + "\",\"senderAliasId\":\"" + alias + "\"}", at, at);
        store.insertInvite(driver, invite, crypto.secretHash("passenger-invite", invite.toString()), NOW.plusSeconds(3600), GITHUB.id(), NOW);
        jdbc.update("""
            INSERT INTO wa.driver_passenger_link(id,driver_id,passenger_id,invite_id,requested_source_event_id,created_at,valid_from)
            VALUES (?,?,?,?,?,?,?)
            """, link, driver, passenger, invite, inbox, at, at);
        jdbc.update("""
            INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,source_inbox_event_id,created_at,valid_from)
            VALUES (?,?,?,?,0,0,?,?,?)
            """, UUID.randomUUID(), link, driver, passenger, inbox, at, at);
        if (routine != null) jdbc.update("""
            INSERT INTO wa.passenger_link_routine(id,link_id,routine_id,driver_id,passenger_id,weekdays,routine_version,created_at)
            VALUES (?,?,?,?,?,'{1,3,5}'::smallint[],1,?)
            """, UUID.randomUUID(), link, routine, driver, passenger, at);
        return link;
    }

    UUID proposePoint(UUID link) {
        UUID point = UUID.randomUUID();
        Timestamp at = Timestamp.from(NOW);
        jdbc.update("""
            INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,source_inbox_event_id,created_at,valid_from)
            SELECT ?,l.id,l.driver_id,l.passenger_id,0,0,l.requested_source_event_id,?,? FROM wa.driver_passenger_link l WHERE l.id=?
            """, point, at, at, link);
        jdbc.update("UPDATE wa.driver_passenger_link SET version=version+1 WHERE id=?", link);
        return point;
    }

    void assertPassengerNotice(UUID operation, UUID link, String textStart) {
        UUID passenger = jdbc.queryForObject("SELECT passenger_id FROM wa.driver_passenger_link WHERE id=?", UUID.class, link);
        assertThat(jdbc.queryForList("""
            SELECT recipient_identity_id,policy_version,purpose,payload_json->>'text' AS text FROM wa.message_outbox WHERE business_key=?
            """, "team-test-driver:" + operation)).singleElement().satisfies(row -> {
                assertThat(row).containsEntry("recipient_identity_id", passenger).containsEntry("policy_version", "team-test-driver-v1")
                    .containsEntry("purpose", "OPERATIONAL");
                assertThat((String) row.get("text")).startsWith(textStart);
            });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.team_passenger_notice WHERE operation_id=? AND link_id=?",
            Long.class, operation, link)).isOne();
    }

    List<Object> snapshot() {
        var rows = new ArrayList<Object>();
        for (String table : List.of("wa.driver_passenger_link", "wa.pickup_point", "wa.routine_member", "wa.team_admission_operation",
                "wa.passenger_link_routine", "wa.routine", "wa.message_outbox", "wa.team_passenger_notice"))
            rows.add(jdbc.queryForList("SELECT to_jsonb(t)::text FROM " + table + " t ORDER BY 1", String.class));
        return rows;
    }

    long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }

    private static String key(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return WaCrypto.base64(bytes);
    }
}
