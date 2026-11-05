package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;

import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Real ingress, worker context, commands, receipt and routing; only the initial driver is a
 * fixture.
 */
final class WaPassengerTestSupport {
    static final Set<String> TYPES =
            Set.of(
                    "ADVANCE_PASSENGER_SETUP",
                    "REQUEST_LINK",
                    "APPROVE_LINK",
                    "CLOSE_LINK",
                    "PROPOSE_PICKUP",
                    "APPROVE_PICKUP");

    record Step(
            WaConversationTestSupport.Lease lease,
            ObjectNode context,
            ObjectNode body,
            ObjectNode response,
            String text) {}

    final WaDriverTestSupport driverFixture;
    final WaConversationTestSupport conversation;
    final WaTransportTestSupport transport;
    UUID cohort;

    WaPassengerTestSupport(PostgreSQLContainer<?> postgres) {
        driverFixture = new WaDriverTestSupport(postgres);
        conversation = driverFixture.s;
        transport = driverFixture.h;
    }

    UUID driver() {
        UUID driver = driverFixture.driver();
        assignCohort(driver);
        return driver;
    }

    UUID passenger() {
        UUID passenger = conversation.actor();
        assignCohort(passenger);
        return passenger;
    }

    private void assignCohort(UUID actor) {
        if (cohort == null) {
            cohort =
                    transport.jdbc.queryForObject(
                            "SELECT test_cohort_id FROM wa.identity WHERE id=?", UUID.class, actor);
        } else {
            transport.jdbc.update(
                    "UPDATE wa.identity SET test_cohort_id=? WHERE id=?", cohort, actor);
        }
    }

    UUID routine(UUID driver) {
        return driverFixture.routine(driver);
    }

    String invite(UUID driver, int uses) {
        String code = WaCrypto.randomToken();
        transport.jdbc.update(
                "INSERT INTO"
                        + " wa.driver_invite(id,driver_id,code_hash,expires_at,max_uses,created_at)"
                        + " VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),
                driver,
                transport.crypto.secretHash("passenger-invite", code),
                Timestamp.from(transport.clock.now().plusSeconds(3600)),
                uses,
                Timestamp.from(transport.clock.now()));
        return code;
    }

    ObjectNode context(WaConversationTestSupport.Lease lease) {
        return conversation.commands.context(conversation.worker, lease.id(), lease.token());
    }

    ObjectNode payload(ObjectNode context) {
        ObjectNode payload = json();
        for (var option : context.path("options")) {
            String field =
                    switch (option.path("kind").asText()) {
                        case "LINK" -> "linkRef";
                        case "PICKUP" -> "pickupRef";
                        case "CONFIRMATION" -> "confirmationRef";
                        case "QUESTION" -> "sourceMessageId";
                        default -> throw new AssertionError("Unexpected passenger option");
                    };
            assertFalse(payload.has(field));
            payload.put(field, option.path("ref").asText());
        }
        return payload;
    }

    String command(ObjectNode context) {
        var commands = new ArrayList<String>();
        context.path("allowedCommands")
                .forEach(
                        value -> {
                            if (TYPES.contains(value.asText())) {
                                commands.add(value.asText());
                            }
                        });
        assertEquals(1, commands.size(), context.toString());
        return commands.get(0);
    }

    Step execute(WaConversationTestSupport.Lease lease, boolean finish) {
        ObjectNode context = context(lease);
        ObjectNode body = conversation.body(lease, command(context), payload(context));
        body.put("expectedVersion", context.path("entityVersion").asLong());
        ObjectNode response =
                conversation.execute(lease, body, "synthetic-090-" + lease.id()).requireSuccess();
        String text = textFor(lease);
        if (finish) {
            conversation.finish(lease);
        }
        return new Step(lease, context, body, response, text);
    }

    String textFor(WaConversationTestSupport.Lease lease) {
        return conversation.string(
                "SELECT o.payload_json->>'text' FROM wa.message_outbox o JOIN wa.command_receipt r"
                        + " ON r.command_id=o.command_id WHERE r.inbox_event_id=? AND"
                        + " o.recipient_identity_id=r.identity_id",
                lease.id());
    }

    Step send(UUID actor, String text) {
        return execute(conversation.text(actor, text), true);
    }

    Step pin(UUID actor) {
        UUID event =
                conversation.pending(
                        actor,
                        json().set(
                                        "locationMessage",
                                        json().put("degreesLatitude", 0)
                                                .put("degreesLongitude", 0)),
                        transport.clock.now());
        return execute(conversation.claim(event), true);
    }

    Step open(UUID passenger, String code) {
        return send(passenger, "PASSAGEIRO " + code);
    }

    Step review(UUID passenger, String code) {
        open(passenger, code);
        send(passenger, "CONTINUAR");
        send(passenger, "Passageiro Sintético");
        send(passenger, "ACEITO");
        send(passenger, "SIM");
        send(passenger, "NÃO");
        send(passenger, "1");
        send(passenger, "1,3,5");
        pin(passenger);
        return send(passenger, "PULAR");
    }

    String marker(Step review) {
        var match = Pattern.compile("CONFIRMAR ([BCDFGHJKMNPQRSTVWXYZ]{8})").matcher(review.text());
        assertTrue(match.find(), review.text());
        return match.group(1);
    }

    String confirmationText(Step review) {
        var match = Pattern.compile("CONFIRMAR [BCDFGHJKMNPQRSTVWXYZ]{8}").matcher(review.text());
        if (match.find()) {
            return match.group();
        }
        assertTrue(review.text().contains("responda CONFIRMAR."), review.text());
        return "CONFIRMAR";
    }

    void markSummarySubmitted(UUID actor) {
        transport.jdbc.update(
                "UPDATE wa.message_outbox SET state='SUBMITTED' WHERE id=?::uuid",
                draft(actor).path("confirmation").path("outboxId").asText());
    }

    void deliverSummary(UUID actor) {
        markSummarySubmitted(actor);
        transport.time.advance(1);
    }

    String confirmation(UUID actor, Step review) {
        deliverSummary(actor);
        return confirmationText(review);
    }

    Step confirm(UUID actor, Step review) {
        return send(actor, confirmation(actor, review));
    }

    UUID request(UUID passenger, String code) {
        confirm(passenger, review(passenger, code));
        return UUID.fromString(
                conversation.string(
                        "SELECT id::text FROM wa.driver_passenger_link WHERE passenger_id=? AND"
                                + " state='REQUESTED'",
                        passenger));
    }

    Step driverReview(UUID driver, String action) {
        send(driver, "PASSAGEIROS");
        return send(driver, action + " 1");
    }

    Step approve(UUID driver) {
        return confirm(driver, driverReview(driver, "APROVAR"));
    }

    ObjectNode draft(UUID actor) {
        return WaJson.object(
                        conversation.string(
                                "SELECT context_json::text FROM wa.conversation_state WHERE"
                                        + " identity_id=?",
                                actor))
                .with("passengerDraft");
    }

    long count(String table) {
        return conversation.number("SELECT count(*) FROM wa." + table);
    }

    WaDecision decide(WaConversationTestSupport.Lease lease, String type, ObjectNode payload) {
        return conversation.execute(lease, type, payload);
    }

    static ObjectNode json() {
        return WaConversationTestSupport.json();
    }
}
