package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.Timestamp;
import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class WaPassengerRoutePickupMigrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_route_pickup_upgrade");

    @Test
    void upgradeScopesSharedPendingCorrectionAndPreservesAdmittedPointsMembershipsAndRunSnapshots() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var old = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("44").cleanDisabled(false).load();
        old.clean(); old.migrate();
        var fixture = new WaPassengerTestSupport(new WaTransportTestSupport(source, true));
        UUID driver = fixture.driver(), outbound = fixture.routine(driver);
        UUID member = fixture.driverFixture.members(driver, outbound, 1).get(0);
        var jdbc = fixture.transport.jdbc;
        UUID passenger = jdbc.queryForObject("SELECT passenger_id FROM wa.routine_member WHERE id=?", UUID.class, member);
        UUID link = jdbc.queryForObject("SELECT link_id FROM wa.routine_member WHERE id=?", UUID.class, member);
        UUID admitted = jdbc.queryForObject("SELECT pickup_point_id FROM wa.routine_member WHERE id=?", UUID.class, member);
        UUID returning = UUID.randomUUID(), proposed = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,destination_latitude,destination_longitude,state,policy_version)
                SELECT ?,driver_id,'Volta legada','RETURN',weekdays,'18:00',timezone,destination_latitude,destination_longitude,state,policy_version
                FROM wa.routine WHERE id=?
                """, returning, outbound);
        jdbc.update("""
                INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order,weekdays)
                SELECT ?,?,driver_id,passenger_id,link_id,pickup_point_id,1,ARRAY[1,3]::smallint[] FROM wa.routine_member WHERE id=?
                """, UUID.randomUUID(), returning, member);
        jdbc.update("""
                INSERT INTO wa.passenger_link_routine(id,link_id,routine_id,driver_id,passenger_id,weekdays,routine_version)
                SELECT gen_random_uuid(),link_id,routine_id,driver_id,passenger_id,ARRAY[1,3]::smallint[],0 FROM wa.routine_member WHERE link_id=?
                """, link);
        jdbc.update("""
                INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,reference_text,source_inbox_event_id,version)
                SELECT ?,link_id,driver_id,passenger_id,12,34,'Portão corrigido',source_inbox_event_id,7 FROM wa.pickup_point WHERE id=?
                """, proposed, admitted);
        Timestamp now = Timestamp.from(fixture.transport.clock.now());
        UUID occurrence = UUID.randomUUID(), run = UUID.randomUUID(), stop = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at) VALUES (?,?,?,'2030-01-01',?)", occurrence, outbound, driver, now);
        jdbc.update("""
                INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_json,snapshot_at,started_at)
                VALUES (?,?,?,?,'2030-01-01','TRACKING','synthetic-upgrade','{"frozen":true}'::jsonb,?,?)
                """, run, occurrence, outbound, driver, now, now);
        jdbc.update("""
                INSERT INTO wa.run_stop(id,run_id,driver_id,passenger_id,physical_stop_key,stop_order,pickup_point_id,pickup_version,
                    latitude_snapshot,longitude_snapshot,reference_snapshot,attendance_snapshot)
                VALUES (?,?,?,?,?,1,?,0,0,0,'Portaria antiga','PENDING')
                """, stop, run, driver, passenger, UUID.randomUUID(), admitted);
        String pointBefore = row(fixture, "pickup_point", admitted), proposalBefore = row(fixture, "pickup_point", proposed);
        String runBefore = row(fixture, "operation_run", run), stopBefore = row(fixture, "run_stop", stop);
        String membersBefore = fixture.conversation.string("SELECT jsonb_agg(to_jsonb(m) ORDER BY m.id)::text FROM wa.routine_member m WHERE link_id=?", link);

        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("46").load().migrate();

        assertEquals("wa.finish_intent_binding", fixture.conversation.string("SELECT to_regclass('wa.finish_intent_binding')::text"));
        assertEquals(2, fixture.conversation.number("""
                SELECT count(DISTINCT s.pickup_point_id) FROM wa.passenger_link_routine s JOIN wa.pickup_point p ON p.id=s.pickup_point_id
                WHERE s.link_id=? AND p.state='PROPOSED' AND p.latitude=12 AND p.longitude=34 AND p.reference_text='Portão corrigido'
                AND p.id<>? AND p.id<>? AND p.approved_by IS NULL
                """, link, admitted, proposed));
        assertEquals(pointBefore, row(fixture, "pickup_point", admitted));
        assertEquals(proposalBefore, row(fixture, "pickup_point", proposed));
        assertEquals(membersBefore, fixture.conversation.string("SELECT jsonb_agg(to_jsonb(m) ORDER BY m.id)::text FROM wa.routine_member m WHERE link_id=?", link));
        assertEquals(runBefore, row(fixture, "operation_run", run));
        assertEquals(stopBefore, row(fixture, "run_stop", stop));
    }

    private String row(WaPassengerTestSupport fixture, String table, UUID id) {
        return fixture.conversation.string("SELECT to_jsonb(entity)::text FROM wa."+table+" entity WHERE id=?", id);
    }
}
