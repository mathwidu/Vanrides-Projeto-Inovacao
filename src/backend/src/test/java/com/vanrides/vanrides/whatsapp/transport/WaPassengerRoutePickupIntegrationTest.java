package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaPassengerTestSupport.json;
import static org.junit.jupiter.api.Assertions.*;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.commands.WaRunPreparation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.Timestamp;
import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class WaPassengerRoutePickupIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_route_pickup");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    WaPassengerTestSupport f;
    UUID driver, passenger, outbound;
    String code;

    @BeforeEach void setup() {
        f = new WaPassengerTestSupport(DATABASE.fixture());
        driver = f.driver(); outbound = f.routine(driver); passenger = f.passenger(); code = f.invite(driver, 10);
    }

    @Test void invitationAuthorizesTheDriverButEachDifferentPickupNeedsItsOwnReview() {
        UUID returning = returning();
        var summary = twoPickups();
        assertTrue(summary.text().contains("→ Ida")); assertTrue(summary.text().contains("← Volta"));
        assertTrue(summary.text().contains("-12.25,34.5")); assertTrue(summary.text().contains("12.5,-34.75"));
        f.confirm(passenger, summary);
        UUID link = link();
        assertEquals("APPROVED", f.conversation.string("SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
        assertEquals(1, f.conversation.number("SELECT count(*) FROM wa.driver_passenger_link WHERE id=? AND approved_by=? AND approved_at IS NOT NULL", link, driver));
        assertEquals(0, f.count("routine_member"));
        assertEquals(2, f.conversation.number("SELECT count(*) FROM wa.pickup_point WHERE link_id=? AND state='PROPOSED' AND approved_by IS NULL", link));
        var review = f.driverReview(driver, "APROVAR");
        UUID selected = UUID.fromString(f.draft(driver).path("pickupId").asText());
        assertFalse(review.text().contains("Aprovar vínculo"));
        f.confirm(driver, review);
        assertEquals(1, f.count("routine_member"));
        assertEquals(1, f.conversation.number("SELECT count(*) FROM wa.routine_member WHERE pickup_point_id=?", selected));
        f.approve(driver);
        assertEquals(2, f.count("routine_member"));
        assertEquals(2, f.conversation.number("SELECT count(DISTINCT pickup_point_id) FROM wa.routine_member WHERE link_id=?", link));
        assertEquals(1, f.conversation.number("SELECT count(*) FROM wa.routine_member m JOIN wa.pickup_point p ON p.id=m.pickup_point_id WHERE m.routine_id=? AND m.weekdays=ARRAY[1,3]::smallint[] AND p.latitude=-12.25", outbound));
        assertEquals(1, f.conversation.number("SELECT count(*) FROM wa.routine_member m JOIN wa.pickup_point p ON p.id=m.pickup_point_id WHERE m.routine_id=? AND m.weekdays=ARRAY[2,4]::smallint[] AND p.latitude=12.5", returning));
    }

    @Test void correctionChangesOnlyTheChosenRoutineAndKeepsTheOldPointUntilApproval() {
        UUID returning = returning(); f.confirm(passenger, twoPickups()); f.approve(driver); f.approve(driver);
        f.transport.jdbc.update("UPDATE wa.routine SET stop_order_pending=false WHERE driver_id=?", driver);
        UUID occurrence = UUID.randomUUID();
        f.transport.jdbc.update("""
                INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at,materialized_routine_version,schedule_snapshot)
                SELECT ?,id,driver_id,DATE '2030-01-01',(DATE '2030-01-01'+local_time) AT TIME ZONE timezone,version,
                  jsonb_build_object('name',name,'direction',direction,'local_time',local_time::text,'timezone',timezone)
                FROM wa.routine WHERE id=?
                """, occurrence, returning);
        var preparation = f.transport.proxy(new WaRunPreparation(f.transport.jdbc, f.transport.clock, f.transport.settings));
        String returnDigest = preparation.rosterDigest(driver, occurrence);
        long linkVersion = f.conversation.number("SELECT version FROM wa.driver_passenger_link WHERE id=?", link());
        String otherMember = member(returning);
        UUID oldPoint = point(outbound), otherPoint = point(returning);
        String other = row("pickup_point", otherPoint), old = row("pickup_point", oldPoint);
        f.send(passenger, "CORRIGIR PONTO");
        int index = 0;
        for (var choice : f.draft(passenger).path("editChoices")) {
            index++;
            if (choice.path("id").asText().equals(outbound.toString())) break;
        }
        f.send(passenger, Integer.toString(index));
        var summary = f.send(passenger, "PONTO -11.5, +33.25");
        f.confirm(passenger, summary);
        assertEquals(oldPoint, point(outbound)); assertEquals(old, row("pickup_point", oldPoint));
        assertEquals(otherMember, member(returning)); assertEquals(other, row("pickup_point", otherPoint));
        assertEquals(returnDigest, preparation.rosterDigest(driver, occurrence));
        assertEquals(1, f.conversation.number("SELECT count(*) FROM wa.pickup_point WHERE link_id=? AND state='PROPOSED'", link()));
        f.approve(driver);
        assertNotEquals(oldPoint, point(outbound));
        assertEquals(otherMember, member(returning)); assertEquals(other, row("pickup_point", otherPoint));
        assertEquals(linkVersion, f.conversation.number("SELECT version FROM wa.driver_passenger_link WHERE id=?", link()));
        assertEquals(returnDigest, preparation.rosterDigest(driver, occurrence));
        UUID run = preparation.prepare(driver, occurrence, returnDigest);
        assertEquals(1, f.conversation.number("SELECT count(*) FROM wa.run_stop WHERE run_id=? AND pickup_point_id=?", run, otherPoint));
        assertEquals(1, f.conversation.number("SELECT count(*) FROM wa.pickup_point WHERE id=? AND state='APPROVED' AND latitude=-11.5", point(outbound)));
    }

    @Test void explicitApprovedPickupReuseAddsOnlyTheSelectedRoutineAndIsIdempotent() {
        f.request(passenger, code); f.approve(driver);
        f.transport.jdbc.update("UPDATE wa.routine SET stop_order_pending=false WHERE id=?", outbound);
        f.transport.jdbc.update("UPDATE wa.profile SET preferences_json=preferences_json||'{\"OTHER_CHOICE\":true}'::jsonb WHERE identity_id=?", passenger);
        String previousMember = member(outbound), previousPoint = row("pickup_point", point(outbound));
        String preferences = f.conversation.string("SELECT preferences_json::text FROM wa.profile WHERE identity_id=?", passenger);
        long previousVersion = f.conversation.number("SELECT version FROM wa.routine WHERE id=?", outbound);
        UUID returning = returning();
        f.open(passenger, code); f.send(passenger, "1"); f.send(passenger, "terça e quinta");
        var summary = f.send(passenger, "MESMO PONTO");
        var lease = f.conversation.text(passenger, f.confirmation(passenger, summary));
        var result = f.execute(lease, false);
        var replay = f.driverFixture.restartedCommands().execute(f.conversation.worker, lease.token(), "synthetic-090-"+lease.id(), result.body(), "synthetic-route-replay").requireSuccess();
        assertEquals(result.response(), replay); f.conversation.finish(lease);
        assertEquals(2, f.count("routine_member")); assertEquals(1, f.count("pickup_point"));
        assertEquals(point(outbound), point(returning));
        assertEquals(previousMember, member(outbound)); assertEquals(previousPoint, row("pickup_point", point(outbound)));
        assertEquals(preferences, f.conversation.string("SELECT preferences_json::text FROM wa.profile WHERE identity_id=?", passenger));
        assertEquals(1, f.conversation.number("SELECT count(*) FROM wa.routine WHERE id=? AND version=? AND NOT stop_order_pending", outbound, previousVersion));
        assertEquals(1, f.conversation.number("SELECT count(*) FROM wa.routine WHERE id=? AND stop_order_pending", returning));
    }

    @Test void expiredOrWrongRecipientInvitationCannotAuthorizeTheLinkAndDriverRoleIsPreserved() {
        returning(); var summary = twoPickups();
        f.transport.jdbc.update("UPDATE wa.driver_invite SET expires_at=? WHERE driver_id=?", Timestamp.from(f.transport.clock.now().plusSeconds(1)), driver);
        f.transport.time.advance(2);
        var lease = f.conversation.text(passenger, f.confirmation(passenger, summary));
        assertEquals(WaProblem.Code.INTENT_EXPIRED, f.decide(lease, "REQUEST_LINK", f.payload(f.context(lease))).error());
        f.conversation.finish(lease);
        assertEquals(0, f.count("driver_passenger_link")); assertEquals(0, f.count("pickup_point"));
        assertEquals(0, f.conversation.number("SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));
        String fresh = f.invite(driver, 1);
        f.transport.jdbc.update("UPDATE wa.driver_invite SET onboarding_identity_id=? WHERE code_hash=?", f.passenger(), f.transport.crypto.secretHash("passenger-invite", fresh));
        var wrong = f.conversation.text(passenger, "PASSAGEIRO "+fresh);
        assertEquals(WaProblem.Code.INTENT_EXPIRED, f.decide(wrong, "ADVANCE_PASSENGER_SETUP", json()).error());
        f.conversation.finish(wrong);
        var driverMessage = f.conversation.text(driver, "PASSAGEIRO "+fresh);
        assertFalse(f.context(driverMessage).path("allowedCommands").toString().contains("REQUEST_LINK"));
        assertEquals("DRIVER", f.conversation.string("SELECT role FROM wa.profile WHERE identity_id=?", driver));
        f.conversation.finish(driverMessage);
        assertEquals(0, f.count("driver_passenger_link"));
    }

    private WaPassengerTestSupport.Step twoPickups() {
        f.open(passenger, code); f.send(passenger, "Pessoa sintética"); f.send(passenger, "ACEITO");
        int index=0, ida=0, volta=0;
        for (var choice : f.draft(passenger).path("routineChoices")) {
            index++;
            if (choice.path("direction").asText().equals("OUTBOUND")) ida=index; else volta=index;
        }
        f.send(passenger, ida+","+volta); f.send(passenger, "segunda e quarta"); f.send(passenger, "terça e quinta");
        var next = f.send(passenger, "PONTO -12.25, +34.5");
        assertEquals("POINT", f.draft(passenger).path("stage").asText());
        assertTrue(next.text().contains("Embarque 2 de 2"), next.text());
        return f.send(passenger, "PONTO +12.5, -34.75");
    }
    private UUID returning() {
        UUID id = UUID.randomUUID();
        f.transport.jdbc.update("""
                INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,destination_latitude,destination_longitude,state,policy_version)
                SELECT ?,driver_id,'Volta sintética','RETURN',weekdays,'18:00',timezone,destination_latitude,destination_longitude,'ACTIVE',policy_version FROM wa.routine WHERE id=?
                """, id, outbound);
        return id;
    }
    private UUID link() { return UUID.fromString(f.conversation.string("SELECT id::text FROM wa.driver_passenger_link WHERE passenger_id=? AND state='APPROVED'", passenger)); }
    private UUID point(UUID routine) { return UUID.fromString(f.conversation.string("SELECT pickup_point_id::text FROM wa.routine_member WHERE routine_id=? AND passenger_id=?", routine, passenger)); }
    private String member(UUID routine) { return f.conversation.string("SELECT to_jsonb(m)::text FROM wa.routine_member m WHERE routine_id=? AND passenger_id=?", routine, passenger); }
    private String row(String table, UUID id) { return f.conversation.string("SELECT to_jsonb(entity)::text FROM wa."+table+" entity WHERE id=?", id); }
}
