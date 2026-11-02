package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static org.assertj.core.api.Assertions.*;

/** Starts UNKNOWN. Every connection transition uses the real authenticated ingress transaction. */
final class WaDeliveryConnectionTestSupport {
    record Box(UUID id, UUID actor) { }
    final WaTransportTestSupport h;
    final WaPrincipal channel, worker;
    final WaEventService events;
    final WaOutboxService outbox;
    private int actors;

    WaDeliveryConnectionTestSupport(PostgreSQLContainer<?> postgres) {
        this(new WaTransportTestSupport(postgres));
    }
    WaDeliveryConnectionTestSupport(WaTransportTestSupport transport) {
        h = transport;
        channel = h.principal(WaPrincipal.Kind.CHANNEL);
        worker = h.principal(WaPrincipal.Kind.DELIVERY_WORKER);
        events = h.proxy(new WaEventService(h.jdbc, h.access, new WaEvolutionNormalizer(h.crypto), h.settings, h.clock));
        outbox = service();
    }
    WaOutboxService service() {
        return h.proxy(new WaOutboxService(h.jdbc, h.access, new WaDeliveryReconciler(h.jdbc, h.clock), h.settings, h.clock));
    }
    Box enqueue() {
        UUID actor = h.identity();
        h.alias(actor, "PN", String.format("%013d", ++actors) + "@s.whatsapp.net");
        return enqueue(actor);
    }
    Box enqueue(UUID actor) {
        UUID alias = h.jdbc.queryForObject("SELECT id FROM wa.identity_alias WHERE identity_id=?", UUID.class, actor);
        UUID event = h.event(actor, json(WaJson.write(Map.of("text", "Olá", "senderAliasId", alias.toString()))));
        UUID command = UUID.randomUUID(), box = UUID.randomUUID();
        h.jdbc.update("""
            INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,
              request_hash,result_json,created_at,expires_at) VALUES (?,?,?,?,'START_ONBOARDING',?,'{}',?,?)
            """, command, actor, event, command.toString(), WaCrypto.digest("synthetic"),
            Timestamp.from(h.clock.now()), Timestamp.from(h.clock.now().plusSeconds(86400)));
        WaFoundationResponses.save(h.jdbc, box, command, actor, "START_ONBOARDING", h.clock.now());
        return new Box(box, actor);
    }
    void connection(String state) { h.connection(channel, state, h.clock.now()); }
    ObjectNode claim() { return outbox.claim(worker, json("{\"batchLimit\":10,\"leaseSeconds\":60}")); }
    ObjectNode lease() {
        var response = claim(); assertThat(response.path("items")).hasSize(1);
        return (ObjectNode) response.path("items").get(0);
    }
    ObjectNode authorize(ObjectNode lease) {
        return outbox.authorize(worker, WaJson.uuid(lease, "outboxId"),
            json("{}").put("leaseToken", lease.path("leaseToken").asText())).requireSuccess();
    }
    ObjectNode submitted(ObjectNode reservation, String external) {
        return outbox.result(worker, WaJson.uuid(reservation, "outboxId"),
            json("{}").put("attemptToken", reservation.path("attemptToken").asText())
                .put("outcome", "SUBMITTED").put("externalMessageId", external));
    }
    void callback(String external, String status) {
        ObjectNode envelope = json("{}").put("event", "messages.update").put("instance", INSTANCE)
            .put("date_time", h.clock.now().toString());
        envelope.putObject("data").put("keyId", external).put("fromMe", true).put("status", status);
        events.accept(channel, envelope);
    }
    String state(UUID box) { return h.jdbc.queryForObject("SELECT state FROM wa.message_outbox WHERE id=?", String.class, box); }
    long count(String table) { return h.jdbc.queryForObject("SELECT count(*) FROM wa." + table, Long.class); }
    String connectionState() {
        return h.jdbc.queryForObject("SELECT state FROM wa.delivery_connection WHERE environment=? AND instance_id=?",
            String.class, h.settings.environment(), INSTANCE);
    }
}
