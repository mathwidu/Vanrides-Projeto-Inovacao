package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class WaPassengerSnapshotsIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            WaTransportTestSupport.postgres("wa_passenger_snapshots");

    WaPassengerTestSupport fixture;
    UUID driver;
    UUID routine;
    UUID passenger;
    String code;

    @BeforeEach
    void setup() {
        fixture = new WaPassengerTestSupport(POSTGRES);
        driver = fixture.driver();
        routine = fixture.routine(driver);
        passenger = fixture.passenger();
        code = fixture.invite(driver, 10);
    }

    @Test
    void pointChangeKeepsOldApprovedPointUntilDriverApprovalAndPreservesHistory() {
        long routineVersion =
                fixture.conversation.number("SELECT version FROM wa.routine WHERE id=?", routine);
        UUID link = fixture.request(passenger, code);
        fixture.approve(driver);
        UUID previous =
                fixture.transport.jdbc.queryForObject(
                        "SELECT id FROM wa.pickup_point WHERE link_id=? AND state='APPROVED'",
                        UUID.class,
                        link);
        fixture.send(passenger, "CORRIGIR PONTO");
        fixture.pin(passenger);
        fixture.confirm(passenger, fixture.send(passenger, "PULAR"));
        assertEquals(
                "APPROVED",
                fixture.conversation.string(
                        "SELECT state FROM wa.pickup_point WHERE id=?", previous));
        assertEquals(
                previous.toString(),
                fixture.conversation.string(
                        "SELECT pickup_point_id::text FROM wa.routine_member WHERE link_id=?",
                        link));
        var approved = fixture.approve(driver);
        assertEquals("APPROVE_PICKUP", approved.body().path("commandType").asText());
        assertEquals(
                "RETIRED",
                fixture.conversation.string(
                        "SELECT state FROM wa.pickup_point WHERE id=?", previous));
        assertEquals(
                2,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.pickup_point WHERE link_id=?", link));
        assertNotEquals(
                previous.toString(),
                fixture.conversation.string(
                        "SELECT pickup_point_id::text FROM wa.routine_member WHERE link_id=?",
                        link));
        assertEquals(
                routineVersion + 2,
                fixture.conversation.number("SELECT version FROM wa.routine WHERE id=?", routine));
    }

    @Test
    void runningStopAndRunSnapshotRemainUnchangedAfterPointCorrectionAndClosure() {
        UUID link = fixture.request(passenger, code);
        fixture.approve(driver);
        UUID pickup =
                fixture.transport.jdbc.queryForObject(
                        "SELECT id FROM wa.pickup_point WHERE link_id=? AND state='APPROVED'",
                        UUID.class,
                        link);
        UUID occurrence = UUID.randomUUID();
        UUID run = UUID.randomUUID();
        UUID stop = UUID.randomUUID();
        Timestamp now = Timestamp.from(fixture.transport.clock.now());
        fixture.transport.jdbc.update(
                "INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at)"
                        + " VALUES (?,?,?,'2030-01-01',?)",
                occurrence,
                routine,
                driver,
                now);
        fixture.transport.jdbc.update(
                """
                INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_json,snapshot_at,started_at)
                VALUES (?,?,?,?,'2030-01-01','TRACKING','synthetic-090','{"frozen":true}'::jsonb,?,?)
                """,
                run,
                occurrence,
                routine,
                driver,
                now,
                now);
        fixture.transport.jdbc.update(
                """
                INSERT INTO wa.run_stop(id,run_id,driver_id,passenger_id,physical_stop_key,stop_order,pickup_point_id,pickup_version,
                  latitude_snapshot,longitude_snapshot,reference_snapshot,attendance_snapshot)
                VALUES (?,?,?,?,?,1,?,1,0,0,'Entrada original','PENDING')
                """,
                stop,
                run,
                driver,
                passenger,
                UUID.randomUUID(),
                pickup);
        String frozenRun =
                fixture.conversation.string(
                        "SELECT to_jsonb(r)::text FROM wa.operation_run r WHERE id=?", run);
        String frozenStop =
                fixture.conversation.string(
                        "SELECT to_jsonb(s)::text FROM wa.run_stop s WHERE id=?", stop);
        fixture.send(passenger, "CORRIGIR PONTO");
        fixture.pin(passenger);
        fixture.confirm(passenger, fixture.send(passenger, "Novo portão"));
        fixture.approve(driver);
        fixture.confirm(driver, fixture.driverReview(driver, "ENCERRAR"));
        assertEquals(
                frozenRun,
                fixture.conversation.string(
                        "SELECT to_jsonb(r)::text FROM wa.operation_run r WHERE id=?", run));
        assertEquals(
                frozenStop,
                fixture.conversation.string(
                        "SELECT to_jsonb(s)::text FROM wa.run_stop s WHERE id=?", stop));
        assertEquals(
                "CLOSED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
    }
}
