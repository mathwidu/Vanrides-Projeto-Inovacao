package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaPassengerTestSupport.json;
import static org.junit.jupiter.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class WaPassengerReuseIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_passenger_reuse");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    WaPassengerTestSupport fixture;
    UUID driver;
    UUID passenger;
    UUID originalRoutine;
    String code;

    @BeforeEach
    void setup() {
        fixture = new WaPassengerTestSupport(DATABASE.fixture());
        driver = fixture.driver();
        originalRoutine = fixture.routine(driver);
        passenger = fixture.passenger();
        code = fixture.invite(driver, 10);
    }

    @Test
    void approvedPassengerAddsRoutineOnlyAfterReviewAndKeepsPointMembershipPreferencesAndReplay() {
        UUID link = fixture.request(passenger, code);
        fixture.approve(driver);
        fixture.transport.jdbc.update("UPDATE wa.routine SET stop_order_pending=false WHERE id=?", originalRoutine);
        long originalVersion = fixture.conversation.number("SELECT version FROM wa.routine WHERE id=?", originalRoutine);
        fixture.transport.jdbc.update("UPDATE wa.profile SET preferences_json=?::jsonb WHERE identity_id=?",
                "{\"APPROACH_MESSAGES\":true,\"ATTENDANCE_REMINDERS\":true,\"OTHER_CHOICE\":true}", passenger);
        String preferences = preferences();
        String member = member();
        String point = approvedPoint();
        UUID added = additionalRoutine();
        var review = additionReview();
        assertTrue(review.text().contains("Novos trajetos"), review.text());
        assertTrue(review.text().contains("Seus avisos e trajetos anteriores serão mantidos"), review.text());
        assertEquals(1, fixture.count("passenger_link_routine"));
        var lease = fixture.conversation.text(passenger, fixture.confirmation(passenger, review));
        var request = fixture.execute(lease, false);
        assertEquals("PROPOSE_PICKUP", request.body().path("commandType").asText());
        var replay = fixture.driverFixture.restartedCommands().execute(fixture.conversation.worker,
                lease.token(), "synthetic-090-" + lease.id(), request.body(), "synthetic-reuse-replay").requireSuccess();
        assertEquals(request.response(), replay);
        fixture.conversation.finish(lease);
        assertEquals(1, fixture.count("driver_passenger_link"));
        assertEquals(2, fixture.count("passenger_link_routine"));
        assertEquals(1, fixture.count("routine_member"));
        assertEquals(member, member());
        assertEquals(point, approvedPoint());
        assertEquals(preferences, preferences());
        assertEquals(2, fixture.conversation.number("SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));

        var staleReview = fixture.driverReview(driver, "APROVAR");
        assertTrue(staleReview.text().contains("manter o ponto"), staleReview.text());
        assertTrue(staleReview.text().contains("Volta sintética"), staleReview.text());
        fixture.transport.jdbc.update("UPDATE wa.routine SET version=version+1 WHERE id=?", added);
        var stale = fixture.conversation.text(driver, fixture.confirmation(driver, staleReview));
        assertEquals(WaProblem.Code.VERSION_CONFLICT,
                fixture.decide(stale, "APPROVE_PICKUP", fixture.payload(fixture.context(stale))).error());
        fixture.conversation.finish(stale);
        assertEquals(1, fixture.count("routine_member"));
        fixture.approve(driver);
        assertEquals(2, fixture.count("routine_member"));
        assertEquals(member, member());
        assertEquals(point, approvedPoint());
        assertEquals(preferences, preferences());
        assertEquals(1, fixture.conversation.number("SELECT count(*) FROM wa.routine WHERE id=? AND NOT stop_order_pending AND version=?",
                originalRoutine, originalVersion));
        assertEquals(1, fixture.conversation.number("SELECT count(*) FROM wa.routine WHERE id=? AND stop_order_pending", added));
        assertEquals(1, fixture.conversation.number("""
                SELECT count(*) FROM wa.routine_member m JOIN wa.pickup_point p ON p.id=m.pickup_point_id
                WHERE m.routine_id=? AND m.link_id=? AND m.state='ACTIVE' AND p.state='APPROVED'
                AND m.weekdays=ARRAY[2,4]::smallint[]
                """, added, link));
        fixture.open(passenger, code);
        assertTrue(fixture.draft(passenger).path("routineChoices").isEmpty());
        assertEquals(2, fixture.count("routine_member"));
    }

    @Test
    void requestedPassengerReusesTheRequestAndItsPendingPointWithoutPrematureAdmission() {
        UUID link = fixture.request(passenger, code);
        additionalRoutine();
        var review = additionReview();
        fixture.confirm(passenger, review);
        assertEquals(1, fixture.count("driver_passenger_link"));
        assertEquals("REQUESTED", fixture.conversation.string("SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
        assertEquals(2, fixture.count("passenger_link_routine"));
        assertEquals(0, fixture.count("routine_member"));
        fixture.approve(driver);
        assertEquals(2, fixture.count("routine_member"));
        assertEquals(1, fixture.conversation.number("SELECT count(*) FROM wa.pickup_point WHERE link_id=? AND state='APPROVED'", link));
    }

    @Test
    void invitationFromAnotherDriverExplainsTheBlockAndPreservesExistingAdmission() {
        UUID link = fixture.request(passenger, code);
        fixture.approve(driver);
        String member = member();
        String point = approvedPoint();
        String preferences = preferences();
        UUID other = fixture.driver();
        fixture.routine(other);
        String invitation = fixture.invite(other, 1);
        var lease = fixture.conversation.text(passenger, "PASSAGEIRO " + invitation);
        assertEquals(WaProblem.Code.ACTION_NOT_ALLOWED,
                fixture.decide(lease, "ADVANCE_PASSENGER_SETUP", json()).error());
        assertTrue(fixture.textFor(lease).contains("Este convite não troca seu motorista"));
        assertEquals(1, fixture.count("driver_passenger_link"));
        assertEquals("APPROVED", fixture.conversation.string("SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
        assertEquals(0, fixture.conversation.number("SELECT use_count FROM wa.driver_invite WHERE driver_id=?", other));
        assertEquals(member, member());
        assertEquals(point, approvedPoint());
        assertEquals(preferences, preferences());
    }

    @Test
    void invitationExpiredAfterTheNewSummaryCannotAddSelectionsOrReplaceTheApprovedPoint() {
        fixture.request(passenger, code);
        fixture.approve(driver);
        additionalRoutine();
        String member = member();
        String point = approvedPoint();
        String preferences = preferences();
        var review = additionReview();
        fixture.transport.jdbc.update("UPDATE wa.driver_invite SET expires_at=? WHERE driver_id=?",
                Timestamp.from(fixture.transport.clock.now().minusSeconds(1)), driver);
        var lease = fixture.conversation.text(passenger, fixture.confirmation(passenger, review));
        assertEquals(WaProblem.Code.INTENT_EXPIRED,
                fixture.decide(lease, "PROPOSE_PICKUP", fixture.payload(fixture.context(lease))).error());
        assertTrue(fixture.textFor(lease).contains("Peça outro convite ao mesmo motorista"));
        assertEquals(1, fixture.count("passenger_link_routine"));
        assertEquals(1, fixture.count("pickup_point"));
        assertEquals(1, fixture.conversation.number("SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));
        assertEquals(member, member());
        assertEquals(point, approvedPoint());
        assertEquals(preferences, preferences());
    }

    private WaPassengerTestSupport.Step additionReview() {
        var opened = fixture.open(passenger, code);
        assertTrue(opened.text().contains("Seu cadastro com"), opened.text());
        assertEquals(1, fixture.draft(passenger).path("routineChoices").size());
        fixture.send(passenger, "1");
        var point = fixture.send(passenger, "terça e quinta");
        assertTrue(point.text().contains("MESMO PONTO"), point.text());
        return fixture.send(passenger, "MESMO PONTO");
    }

    private UUID additionalRoutine() {
        UUID id = UUID.randomUUID();
        fixture.transport.jdbc.update("""
                INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,
                    destination_label,destination_latitude,destination_longitude,state,policy_version)
                SELECT ?,driver_id,'Volta sintética','RETURN',weekdays,'18:00',timezone,
                    destination_label,destination_latitude,destination_longitude,'ACTIVE',policy_version
                FROM wa.routine WHERE id=?
                """, id, originalRoutine);
        return id;
    }

    private String preferences() {
        return fixture.conversation.string("SELECT preferences_json::text FROM wa.profile WHERE identity_id=?", passenger);
    }

    private String member() {
        return fixture.conversation.string("SELECT to_jsonb(m)::text FROM wa.routine_member m WHERE routine_id=? AND passenger_id=?",
                originalRoutine, passenger);
    }

    private String approvedPoint() {
        return fixture.conversation.string("SELECT to_jsonb(p)::text FROM wa.pickup_point p WHERE passenger_id=? AND state='APPROVED'", passenger);
    }
}
