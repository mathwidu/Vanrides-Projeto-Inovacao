package com.vanrides.vanrides.whatsapp.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.postgresql.util.PSQLException;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

final class WhatsappPostgresTestSupport {

    static final String INSTANCE = "synthetic-instance";
    static final String PUBLIC_JWK = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"synthetic\",\"y\":\"synthetic\"}";
    static final LocalDate DATE = LocalDate.of(2030, 1, 1);
    private static final String TEST_PASSWORD = "synthetic-database-password";

    private WhatsappPostgresTestSupport() {
    }

    static PostgreSQLContainer<?> postgres(String database) {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName(database).withUsername("vanrides_test").withPassword(TEST_PASSWORD);
    }

    static FluentConfiguration configuration(PostgreSQLContainer<?> postgres) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false);
    }

    static void resetTo(PostgreSQLContainer<?> postgres, String version) throws SQLException {
        // Only this disposable container is ever used. Flyway owns public;
        // wa is created transactionally by V26, so must not be precreated by schemas().
        try (Connection connection = open(postgres)) {
            update(connection, "DROP SCHEMA IF EXISTS wa CASCADE");
        }
        configuration(postgres).load().clean();
        configuration(postgres).target(version).load().migrate();
    }

    static void migrateLatest(PostgreSQLContainer<?> postgres) {
        configuration(postgres).load().migrate();
    }

    static Connection open(PostgreSQLContainer<?> postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    static int update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            return statement.executeUpdate();
        }
    }

    static long number(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getLong(1);
            }
        }
    }

    static String string(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    static UUID identity(Connection connection, String role) throws SQLException {
        UUID id = UUID.randomUUID();
        update(connection, "INSERT INTO wa.identity(id) VALUES (?)", id);
        if (role != null) {
            update(connection, """
                    INSERT INTO wa.profile(identity_id,role,status,driver_enabled)
                    VALUES (?,?,'ACTIVE',?)
                    """, id, role, "DRIVER".equals(role));
            if ("DRIVER".equals(role)) {
                update(connection, "INSERT INTO wa.driver_authorization(driver_id,generation) VALUES (?,1)", id);
            }
        }
        return id;
    }

    static UUID credential(Connection connection, String principal) throws SQLException {
        UUID id = UUID.randomUUID();
        update(connection, """
                INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,expires_at)
                VALUES (?,?,'test',?,?,CURRENT_TIMESTAMP + INTERVAL '1 day')
                """, id, principal, hash(id.toString()), INSTANCE);
        return id;
    }

    static UUID inbox(Connection connection, UUID identity) throws SQLException {
        UUID id = UUID.randomUUID();
        update(connection, """
                INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,normalized_kind)
                VALUES (?,?,'SYNTHETIC',?,?,'MESSAGE','TEXT')
                """, id, identity, INSTANCE, id.toString());
        return id;
    }

    static UUID device(Connection connection, UUID driver, String jkt) throws SQLException {
        UUID id = UUID.randomUUID();
        update(connection, """
                INSERT INTO wa.driver_device(id,driver_id,jkt,public_jwk,generation,environment,platform,app_version)
                VALUES (?,?,?,?::jsonb,1,'test','ANDROID','synthetic-1')
                """, id, driver, jkt, PUBLIC_JWK);
        return id;
    }

    static UUID link(Connection connection, UUID driver, UUID passenger, String state) throws SQLException {
        UUID id = UUID.randomUUID();
        update(connection, """
                INSERT INTO wa.driver_passenger_link(id,driver_id,passenger_id,state,approved_by,approved_at)
                VALUES (?,?,?,?,?,CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, id, driver, passenger, state, "APPROVED".equals(state) ? driver : null,
                "APPROVED".equals(state));
        return id;
    }

    static UUID routine(Connection connection, UUID driver) throws SQLException {
        UUID id = UUID.randomUUID();
        update(connection, """
                INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,
                    destination_latitude,destination_longitude,state,policy_version)
                VALUES (?,?,'Synthetic routine','OUTBOUND',ARRAY[1,2,3,4,5]::SMALLINT[],
                    TIME '07:30','UTC',0,1,'ACTIVE','synthetic-v1')
                """, id, driver);
        return id;
    }

    static UUID occurrence(Connection connection, UUID routine, UUID driver, LocalDate date) throws SQLException {
        UUID id = UUID.randomUUID();
        update(connection, """
                INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at)
                VALUES (?,?,?,?,CURRENT_TIMESTAMP + INTERVAL '1 day')
                """, id, routine, driver, date);
        return id;
    }

    static UUID run(Connection connection, UUID occurrence, UUID routine, UUID driver,
                    LocalDate date, String state) throws SQLException {
        UUID id = UUID.randomUUID();
        update(connection, """
                INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,
                    policy_version,snapshot_at)
                VALUES (?,?,?,?,?,?,'synthetic-v1',CURRENT_TIMESTAMP)
                """, id, occurrence, routine, driver, date, state);
        return id;
    }

    static Fixture fixture(Connection connection, String runState) throws SQLException {
        UUID driver = identity(connection, "DRIVER");
        UUID passenger = identity(connection, "PASSENGER");
        UUID link = link(connection, driver, passenger, "APPROVED");
        UUID event = inbox(connection, passenger);
        UUID pickup = UUID.randomUUID();
        update(connection, """
                INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,
                    source_inbox_event_id,state,approved_by,approved_at)
                VALUES (?,?,?,?,0,0,?,'APPROVED',?,CURRENT_TIMESTAMP)
                """, pickup, link, driver, passenger, event, driver);
        UUID routine = routine(connection, driver);
        update(connection, """
                INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order)
                VALUES (?,?,?,?,?,?,1)
                """, UUID.randomUUID(), routine, driver, passenger, link, pickup);
        UUID occurrence = occurrence(connection, routine, driver, DATE);
        UUID run = run(connection, occurrence, routine, driver, DATE, runState);
        UUID stop = UUID.randomUUID();
        update(connection, """
                INSERT INTO wa.run_stop(id,run_id,driver_id,passenger_id,physical_stop_key,stop_order,
                    pickup_point_id,pickup_version,latitude_snapshot,longitude_snapshot,attendance_snapshot)
                VALUES (?,?,?,?,?,1,?,0,0,0,'GOING')
                """, stop, run, driver, passenger, UUID.randomUUID(), pickup);
        return new Fixture(driver, passenger, link, pickup, routine, occurrence, run, stop, event);
    }

    static void command(Connection connection, UUID command, UUID identity, UUID event, String key) throws SQLException {
        update(connection, """
                INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,
                    command_type,request_hash,result_json,expires_at)
                VALUES (?,?,?,?,'START_ONBOARDING',?,'{}'::jsonb,CURRENT_TIMESTAMP + INTERVAL '1 day')
                """, command, identity, event, key, hash("synthetic-command"));
    }

    static UUID outbox(Connection connection, UUID recipient, String purpose, UUID run, UUID stop,
                       UUID occurrence, UUID command, String part, String window, String policy) throws SQLException {
        UUID id = UUID.randomUUID();
        update(connection, """
                INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,run_id,stop_id,occurrence_id,
                    command_id,response_part,reminder_window,policy_version,expires_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP + INTERVAL '5 minutes')
                """, id, recipient, purpose, run, stop, occurrence, command, part, window, policy);
        return id;
    }

    static void assertConstraint(String sqlState, String constraint, SqlAction action) {
        Throwable failure = catchThrowable(action::run);
        assertThat(failure).isInstanceOf(PSQLException.class);
        PSQLException sql = (PSQLException) failure;
        assertThat(sql.getSQLState()).isEqualTo(sqlState);
        assertThat(sql.getServerErrorMessage()).isNotNull();
        assertThat(sql.getServerErrorMessage().getConstraint()).isEqualTo(constraint);
    }

    static String constraint(SQLException exception) {
        return exception instanceof PSQLException postgres && postgres.getServerErrorMessage() != null
                ? postgres.getServerErrorMessage().getConstraint() : null;
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void bind(PreparedStatement statement, Object[] parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    @FunctionalInterface
    interface SqlAction {
        void run() throws SQLException;
    }

    record Fixture(UUID driver, UUID passenger, UUID link, UUID pickup, UUID routine,
                   UUID occurrence, UUID run, UUID stop, UUID event) {
    }
}
