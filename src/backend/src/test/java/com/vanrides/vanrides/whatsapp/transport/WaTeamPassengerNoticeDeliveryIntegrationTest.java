package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaPassengerTestSupport.json;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.commands.WaTeamTestDriver;
import com.vanrides.vanrides.whatsapp.commands.WaTeamTestDriver.Decision;
import com.vanrides.vanrides.whatsapp.team.WaTeamTestDriverStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class WaTeamPassengerNoticeDeliveryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            WaTransportTestSupport.postgres("wa_team_notice_delivery");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    private static final long GITHUB = 314;

    WaPassengerTestSupport fixture;
    WaTeamTestDriver drivers;
    WaTeamTestDriverStore store;
    UUID passenger;
    UUID driver;
    UUID link;

    @BeforeEach
    void setup() {
        fixture = new WaPassengerTestSupport(DATABASE.fixture());
        drivers =
                new WaTeamTestDriver(
                        fixture.transport.jdbc, fixture.transport.clock, fixture.transport.settings);
        store = new WaTeamTestDriverStore(fixture.transport.jdbc);
        passenger = fixture.passenger();
        driver =
                fixture.transport.transaction.execute(
                        status ->
                                store.ensure(
                                        fixture.cohort,
                                        WaTransportTestSupport.INSTANCE,
                                        GITHUB,
                                        fixture.transport.clock.now()));
        String code = WaCrypto.randomToken();
        store.insertInvite(
                driver,
                UUID.randomUUID(),
                fixture.transport.crypto.secretHash("passenger-invite", code),
                fixture.transport.clock.now().plusSeconds(86400),
                GITHUB,
                fixture.transport.clock.now());
        link = fixture.request(passenger, code);
        fixture.transport.jdbc.update(
                "UPDATE wa.message_outbox SET state='CANCELED' WHERE recipient_identity_id=?",
                passenger);
    }

    @Test
    void approvalNoticeIsAuthorizedForThePassengerAliasWithTheExactText() {
        UUID box = notice(Decision.APPROVE_LINK, "APPROVE_TEST_LINK");
        String text = storedText(box);
        assertTrue(text.startsWith("Cadastro confirmado."), text);
        var reservation = fixture.conversation.authorize(lease(box));
        assertEquals(address(), reservation.path("recipient").path("address").asText());
        assertEquals(text, reservation.path("content").path("text").asText());
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.message_attempt a JOIN wa.message_outbox o ON"
                                + " o.id=a.outbox_id WHERE o.id=? AND o.recipient_identity_id=?",
                        box,
                        driver));
    }

    @Test
    void anObsoleteNoticeIsCanceledAtClaimWhileTheCurrentOneIsDelivered() {
        UUID first = notice(Decision.APPROVE_LINK, "APPROVE_TEST_LINK");
        UUID second = notice(Decision.CLOSE_LINK, "CLOSE_TEST_LINK");
        var claimed = fixture.conversation.outboxClaim().path("items");
        assertEquals(1, claimed.size());
        assertEquals(second.toString(), claimed.get(0).path("outboxId").asText());
        assertEquals("CANCELED", state(first));
        var reservation = fixture.conversation.authorize((ObjectNode) claimed.get(0));
        assertTrue(
                reservation.path("content").path("text").asText().startsWith("O vínculo de transporte foi encerrado"),
                reservation.toString());
    }

    @Test
    void aLeasedNoticeIsCanceledAtAuthorizationWhenTheLinkChangesAfterTheClaim() {
        UUID first = notice(Decision.APPROVE_LINK, "APPROVE_TEST_LINK");
        var lease = lease(first);
        notice(Decision.CLOSE_LINK, "CLOSE_TEST_LINK");
        var decision =
                fixture.conversation.outbox.authorize(
                        fixture.conversation.delivery,
                        first,
                        json().put("leaseToken", lease.path("leaseToken").asText()));
        assertEquals(WaProblem.Code.ACTION_NOT_ALLOWED, decision.error());
        assertEquals("CANCELED", state(first));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?", first));
    }

    private UUID notice(Decision decision, String action) {
        UUID operation = UUID.randomUUID();
        var now = fixture.transport.clock.now();
        fixture.transport.transaction.executeWithoutResult(
                status -> {
                    var outcome = drivers.decide(driver, link, decision);
                    fixture.transport.jdbc.update(
                            """
                            INSERT INTO wa.team_admission_operation(id,github_actor_id,github_login,action,request_fingerprint,
                              identity_id,link_id,correlation_id,created_at) VALUES (?,?,'synthetic-collaborator',?,?,?,?,?,?)
                            """,
                            operation,
                            GITHUB,
                            action,
                            "0".repeat(64),
                            driver,
                            link,
                            UUID.randomUUID(),
                            Timestamp.from(now));
                    WaTeamPassengerNotices.save(
                            fixture.transport.jdbc,
                            operation,
                            link,
                            outcome.pickup(),
                            outcome.kind(),
                            outcome.passengerText(),
                            fixture.transport.settings.environment(),
                            WaTransportTestSupport.INSTANCE,
                            now);
                });
        return fixture.transport.jdbc.queryForObject(
                "SELECT id FROM wa.message_outbox WHERE business_key=?",
                UUID.class,
                "team-test-driver:" + operation);
    }

    private ObjectNode lease(UUID box) {
        for (var item : fixture.conversation.outboxClaim().path("items")) {
            if (item.path("outboxId").asText().equals(box.toString())) {
                return (ObjectNode) item;
            }
        }
        throw new AssertionError("Expected team notice lease");
    }

    private String storedText(UUID box) {
        return fixture.conversation.string(
                "SELECT payload_json->>'text' FROM wa.message_outbox WHERE id=?", box);
    }

    private String state(UUID box) {
        return fixture.conversation.string("SELECT state FROM wa.message_outbox WHERE id=?", box);
    }

    private String address() {
        return fixture.conversation.string(
                "SELECT alias_value FROM wa.identity_alias WHERE identity_id=?", passenger);
    }
}
