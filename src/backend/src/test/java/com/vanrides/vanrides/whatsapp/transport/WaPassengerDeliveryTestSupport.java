package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;

import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

/** Synthetic relational fixtures exercise transport, not passenger-domain authorization. */
final class WaPassengerDeliveryTestSupport {
    record Pair(
            UUID driver,
            UUID passenger,
            UUID link,
            UUID point,
            UUID routine,
            UUID requestCommand) {}

    final WaTransportTestSupport h;
    final WaPrincipal conversation;
    final WaPrincipal delivery;
    final WaOutboxService outbox;
    final UUID cohort;
    private int aliasSequence;

    WaPassengerDeliveryTestSupport(PostgreSQLContainer<?> postgres) {
        h = new WaTransportTestSupport(postgres);
        conversation = h.principal(WaPrincipal.Kind.CONVERSATION_WORKER);
        delivery = h.principal(WaPrincipal.Kind.DELIVERY_WORKER);
        h.connection(h.principal(WaPrincipal.Kind.CHANNEL), "open", h.clock.now());
        outbox =
                h.proxy(
                        new WaOutboxService(
                                h.jdbc,
                                h.access,
                                new WaDeliveryReconciler(h.jdbc, h.clock),
                                h.settings,
                                h.clock));
        cohort = UUID.randomUUID();
        h.jdbc.update(
                "INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES"
                        + " (?,'transport-test',true)",
                cohort);
    }

    UUID actor(boolean driver) {
        return actor(driver, "PN");
    }

    UUID actor(boolean driver, String aliasType) {
        UUID actor = UUID.randomUUID();
        h.jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", actor, cohort);
        h.jdbc.update(
                """
                INSERT INTO wa.profile(identity_id,role,status,driver_enabled,notice_version,notice_accepted_at,display_name)
                VALUES (?,?,'ACTIVE',?,'wa-mvp-v1',?,'Pessoa Sintética')
                """,
                actor,
                driver ? "DRIVER" : "PASSENGER",
                driver,
                Timestamp.from(h.clock.now()));
        if (driver) {
            h.jdbc.update("INSERT INTO wa.driver_authorization(driver_id) VALUES (?)", actor);
        }
        h.alias(
                actor,
                aliasType,
                "0000000000"
                        + String.format("%05d", ++aliasSequence)
                        + (aliasType.equals("LID") ? "@lid" : "@s.whatsapp.net"));
        event(actor);
        return actor;
    }

    UUID event(UUID actor) {
        UUID alias =
                h.jdbc.queryForObject(
                        "SELECT id FROM wa.identity_alias WHERE identity_id=?", UUID.class, actor);
        UUID event =
                h.event(
                        actor,
                        json(
                                WaJson.write(
                                        Map.of(
                                                "text",
                                                "Mensagem sintética",
                                                "senderAliasId",
                                                alias.toString()))));
        h.jdbc.update(
                """
                UPDATE wa.integration_inbox SET state='COMPLETED',lease_credential_id=?,lease_token_hash=?,lease_generation=1,lease_expires_at=?
                WHERE id=?
                """,
                conversation.credentialId(),
                WaCrypto.digest(WaCrypto.randomToken()),
                Timestamp.from(h.clock.now().plusSeconds(60)),
                event);
        return event;
    }

    UUID command(UUID actor, String type) {
        UUID command = UUID.randomUUID(), event = event(actor);
        h.jdbc.update(
                """
                INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,
                  request_hash,result_json,created_at,expires_at) VALUES (?,?,?,?,?,?,'{}',?,?)
                """,
                command,
                actor,
                event,
                command.toString(),
                type,
                WaCrypto.digest("synthetic-passenger-response"),
                Timestamp.from(h.clock.now()),
                Timestamp.from(h.clock.now().plusSeconds(86400)));
        return command;
    }

    Pair pair(String state) {
        UUID driver = actor(true),
                passenger = actor(false),
                command = command(passenger, "REQUEST_LINK");
        UUID source =
                h.jdbc.queryForObject(
                        "SELECT inbox_event_id FROM wa.command_receipt WHERE command_id=?",
                        UUID.class,
                        command);
        UUID link = UUID.randomUUID(), routine = UUID.randomUUID();
        boolean approved = state.equals("APPROVED");
        h.jdbc.update(
                """
                INSERT INTO wa.driver_passenger_link(id,driver_id,passenger_id,state,requested_source_event_id,approved_by,approved_at,valid_until)
                VALUES (?,?,?,?,?,?,?,?)
                """,
                link,
                driver,
                passenger,
                state,
                source,
                approved ? driver : null,
                approved ? Timestamp.from(h.clock.now()) : null,
                state.equals("CLOSED") ? Timestamp.from(h.clock.now()) : null);
        UUID point = point(link, approved ? "APPROVED" : "PROPOSED", 0);
        h.jdbc.update(
                """
                INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,destination_latitude,destination_longitude,state,policy_version)
                VALUES (?,?,'Rotina Sintética','OUTBOUND',ARRAY[1,2,3,4,5]::SMALLINT[],'06:00','America/Sao_Paulo',0,0,'ACTIVE','synthetic')
                """,
                routine,
                driver);
        h.jdbc.update(
                """
                INSERT INTO wa.passenger_link_routine(id,link_id,routine_id,driver_id,passenger_id,weekdays,routine_version)
                VALUES (?,?,?,?,?,ARRAY[1,3]::SMALLINT[],0)
                """,
                UUID.randomUUID(),
                link,
                routine,
                driver,
                passenger);
        Pair pair = new Pair(driver, passenger, link, point, routine, command);
        if (approved) member(pair);
        return pair;
    }

    Pair approveAnotherPassengerOnSameRoutine(Pair first) {
        UUID passenger = actor(false), command = command(passenger, "REQUEST_LINK"), link = UUID.randomUUID();
        UUID source = h.jdbc.queryForObject("SELECT inbox_event_id FROM wa.command_receipt WHERE command_id=?", UUID.class, command);
        h.jdbc.update("""
            INSERT INTO wa.driver_passenger_link(id,driver_id,passenger_id,state,requested_source_event_id,approved_by,approved_at,version)
            VALUES (?,?,?,'APPROVED',?,?,?,1)
            """, link, first.driver(), passenger, source, first.driver(), Timestamp.from(h.clock.now()));
        UUID point = point(link, "APPROVED", 0);
        h.jdbc.update("""
            INSERT INTO wa.passenger_link_routine(id,link_id,routine_id,driver_id,passenger_id,weekdays,routine_version,version)
            SELECT ?,?,?,?,?,ARRAY[1,3]::SMALLINT[],r.version,1 FROM wa.routine r WHERE r.id=?
            """, UUID.randomUUID(), link, first.routine(), first.driver(), passenger, first.routine());
        Pair second = new Pair(first.driver(), passenger, link, point, first.routine(), command);
        member(second);
        h.jdbc.update("UPDATE wa.routine SET version=version+1,stop_order_pending=true WHERE id=?", first.routine());
        return second;
    }

    private void member(Pair pair) {
        h.jdbc.update("""
            INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order,weekdays,version)
            VALUES (?,?,?,?,?,?,(SELECT COALESCE(max(stop_order),0)+1 FROM wa.routine_member WHERE routine_id=?),ARRAY[1,3]::SMALLINT[],1)
            """, UUID.randomUUID(), pair.routine(), pair.driver(), pair.passenger(), pair.link(), pair.point(), pair.routine());
    }

    UUID point(UUID link, String state, double longitude) {
        var owners =
                h.jdbc.queryForMap(
                        "SELECT driver_id,passenger_id FROM wa.driver_passenger_link WHERE id=?",
                        link);
        UUID id = UUID.randomUUID(), pin = event((UUID) owners.get("passenger_id"));
        h.jdbc.update(
                "UPDATE wa.integration_inbox SET"
                        + " normalized_kind='PIN',payload_json=payload_json||?::jsonb WHERE id=?",
                WaJson.write(Map.of("pin", Map.of("latitude", 0, "longitude", longitude))),
                pin);
        h.jdbc.update(
                """
                INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,source_inbox_event_id,state,approved_by,approved_at)
                VALUES (?,?,?,?,0,?,?,?,?,?)
                """,
                id,
                link,
                owners.get("driver_id"),
                owners.get("passenger_id"),
                longitude,
                pin,
                state,
                state.equals("APPROVED") ? owners.get("driver_id") : null,
                state.equals("APPROVED") ? Timestamp.from(h.clock.now()) : null);
        return id;
    }

    UUID peer(Pair pair, String kind) {
        UUID actor =
                kind.equals("REQUESTED") || kind.equals("POINT_CHANGE")
                        ? pair.passenger()
                        : pair.driver();
        UUID command =
                kind.equals("REQUESTED")
                        ? pair.requestCommand()
                        : command(actor, WaPassengerResponses.commandFor(kind));
        return peer(pair, command, actor, kind, pair.point());
    }

    UUID peer(Pair pair, UUID command, UUID actor, String kind, UUID point) {
        UUID box = UUID.randomUUID();
        h.transaction.executeWithoutResult(
                tx ->
                        WaPassengerResponses.savePeer(
                                h.jdbc,
                                box,
                                command,
                                actor,
                                pair.link(),
                                point,
                                kind,
                                "Atualização sintética do cadastro.",
                                h.clock.now()));
        return box;
    }

    ObjectNode claim() {
        return outbox.claim(delivery, json("{\"batchLimit\":10,\"leaseSeconds\":60}"));
    }

    ObjectNode claim(UUID box) {
        for (var item : claim().path("items")) {
            if (item.path("outboxId").asText().equals(box.toString())) {
                return (ObjectNode) item;
            }
        }
        throw new AssertionError("Expected passenger response lease");
    }

    WaDecision decision(ObjectNode lease) {
        return outbox.authorize(
                delivery,
                UUID.fromString(lease.path("outboxId").asText()),
                json("{}").put("leaseToken", lease.path("leaseToken").asText()));
    }

    ObjectNode authorize(ObjectNode lease) {
        return decision(lease).requireSuccess();
    }

    ObjectNode submitted(ObjectNode reservation, String external) {
        return outbox.result(
                delivery,
                UUID.fromString(reservation.path("outboxId").asText()),
                json("{}")
                        .put("outcome", "SUBMITTED")
                        .put("attemptToken", reservation.path("attemptToken").asText())
                        .put("externalMessageId", external));
    }

    UUID callback(String external, String status) {
        UUID id = UUID.randomUUID();
        h.jdbc.update(
                """
                INSERT INTO wa.integration_inbox(id,provider,instance_id,category,event_key,payload_json,created_at)
                VALUES (?,'EVOLUTION',?,'STATUS',?,?::jsonb,?)
                """,
                id,
                INSTANCE,
                id.toString(),
                WaJson.write(Map.of("externalMessageId", external, "status", status)),
                Timestamp.from(h.clock.now()));
        return id;
    }

    String state(UUID box) {
        return h.jdbc.queryForObject(
                "SELECT state FROM wa.message_outbox WHERE id=?", String.class, box);
    }

    String address(UUID actor) {
        return h.jdbc.queryForObject(
                "SELECT alias_value FROM wa.identity_alias WHERE identity_id=?",
                String.class,
                actor);
    }

    int count(String table) {
        return h.jdbc.queryForObject("SELECT count(*) FROM wa." + table, Integer.class);
    }

    void canceled(ObjectNode lease) {
        assertThat(decision(lease).error())
                .isEqualTo(com.vanrides.vanrides.whatsapp.api.WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(state(UUID.fromString(lease.path("outboxId").asText()))).isEqualTo("CANCELED");
        assertThat(count("message_attempt")).isZero();
    }
}
