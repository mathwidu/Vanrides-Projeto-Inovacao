package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.transport.WaPrivateInviteDelivery;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Final effects only, after the confirmation and captured aggregate snapshot were revalidated. */
final class WaDriverMutations {
    record Result(String text, UUID invite, String privateText, java.time.Instant expires) { }
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final WaCrypto crypto;
    private final WaDriverTargets targets;
    WaDriverMutations(JdbcTemplate jdbc, WaClock clock, WaCrypto crypto, WaDriverTargets targets) {
        this.jdbc = jdbc; this.clock = clock; this.crypto = crypto; this.targets = targets;
    }
    Result apply(UUID actor, ObjectNode d) {
        switch (d.path("action").asText()) {
            case "CONFIGURE_ROUTINE" -> configure(actor, d);
            case "SET_STOP_ORDER" -> {
                int order = 0;
                for (var member : d.path("memberOrder")) jdbc.update("UPDATE wa.routine_member SET stop_order=?,version=version+1 WHERE id=? AND driver_id=? AND routine_id=? AND state='ACTIVE'",
                    ++order, UUID.fromString(member.asText()), actor, UUID.fromString(d.path("routineId").asText()));
                jdbc.update("UPDATE wa.routine SET stop_order_pending=?,version=version+1,updated_at=? WHERE id=? AND driver_id=?",
                    order == 0, Timestamp.from(clock.now()), UUID.fromString(d.path("routineId").asText()), actor);
            }
            case "SET_ROUTINE_STATE" -> jdbc.update("UPDATE wa.routine SET state=?,version=version+1,updated_at=? WHERE id=? AND driver_id=?",
                d.path("routineState").asText(), Timestamp.from(clock.now()), UUID.fromString(d.path("routineId").asText()), actor);
            case "CANCEL_OCCURRENCE" -> cancel(actor, d);
            case "REVOKE_INVITE" -> {
                jdbc.update("UPDATE wa.driver_invite SET revoked_at=COALESCE(revoked_at,?),version=version+1 WHERE id=? AND driver_id=?",
                    Timestamp.from(clock.now()), UUID.fromString(d.path("inviteId").asText()), actor);
                new WaPrivateInviteDelivery(jdbc, clock, crypto).cancelInvalid(actor);
            }
            case "CREATE_INVITE" -> {
                UUID id = UUID.randomUUID(); String secret = WaCrypto.randomToken();
                var expires = clock.now().plusSeconds(d.path("ttlMinutes").asLong() * 60);
                jdbc.update("INSERT INTO wa.driver_invite(id,driver_id,code_hash,expires_at,max_uses,created_at) VALUES (?,?,?,?,?,?)",
                    id, actor, crypto.secretHash("passenger-invite", secret), Timestamp.from(expires), d.path("maxUses").asInt(), Timestamp.from(clock.now()));
                String until = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", java.util.Locale.forLanguageTag("pt-BR"))
                    .withZone(ZoneId.of("America/Sao_Paulo")).format(expires);
                String text = "Convite VanRides: PASSAGEIRO " + secret + ". Válido até " + until + " (horário de Brasília)"
                    + "; máximo " + d.path("maxUses").asInt() + " uso(s). Não enviamos este convite a nenhum contato.";
                return new Result("Convite confirmado. O código será entregue somente nesta resposta privada.", id, text, expires);
            }
            default -> throw WaProblem.invalid();
        }
        String text = switch (d.path("action").asText()) {
            case "CONFIGURE_ROUTINE" -> "Rotina salva.";
            case "SET_STOP_ORDER" -> "Ordem dos pontos atualizada.";
            case "SET_ROUTINE_STATE" -> d.path("routineState").asText().equals("PAUSED") ? "Rotina pausada." : "Rotina retomada.";
            case "CANCEL_OCCURRENCE" -> "Ocorrência da data selecionada cancelada.";
            case "REVOKE_INVITE" -> "Convite revogado.";
            default -> throw WaProblem.invalid();
        };
        return new Result(text, null, null, null);
    }
    private void configure(UUID actor, ObjectNode d) {
        UUID id = UUID.fromString(d.path("routineId").asText());
        ObjectNode pin;
        UUID event = d.has("destinationEvent") ? UUID.fromString(d.path("destinationEvent").asText()) : null;
        if (event != null) pin = targets.pin(actor, event.toString());
        else {
            ObjectNode previous = targets.routine(actor, id);
            if (previous.path("destination_latitude").isNull()) throw WaProblem.invalid();
            pin = WaDriverDraftStore.json().put("latitude", previous.path("destination_latitude").asDouble())
                .put("longitude", previous.path("destination_longitude").asDouble());
            if (!previous.path("destination_source_event_id").isNull()) event = UUID.fromString(previous.path("destination_source_event_id").asText());
        }
        String days = java.util.stream.StreamSupport.stream(d.path("weekdays").spliterator(), false)
            .map(node -> Integer.toString(node.asInt())).collect(java.util.stream.Collectors.joining(",", "{", "}"));
        if (d.path("creating").asBoolean()) jdbc.update("""
            INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,destination_latitude,destination_longitude,
              destination_source_event_id,state,policy_version,version,created_at,updated_at)
            VALUES (?,?,?,?,?::smallint[],?::time,?,?,?,?,'ACTIVE','driver-setup-v1',1,?,?)
            """, id, actor, d.path("name").asText(), d.path("direction").asText(), days, d.path("time").asText(),
            d.path("timezone").asText(), pin.path("latitude").asDouble(), pin.path("longitude").asDouble(), event,
            Timestamp.from(clock.now()), Timestamp.from(clock.now()));
        else jdbc.update("""
            UPDATE wa.routine SET name=?,direction=?,weekdays=?::smallint[],local_time=?::time,timezone=?,destination_latitude=?,
              destination_longitude=?,destination_source_event_id=?,version=version+1,updated_at=? WHERE id=? AND driver_id=?
            """, d.path("name").asText(), d.path("direction").asText(), days, d.path("time").asText(), d.path("timezone").asText(),
            pin.path("latitude").asDouble(), pin.path("longitude").asDouble(), event, Timestamp.from(clock.now()), id, actor);
    }
    private void cancel(UUID actor, ObjectNode d) {
        ObjectNode routine = targets.routine(actor, UUID.fromString(d.path("routineId").asText()));
        LocalDate date = LocalDate.parse(d.path("date").asText());
        var scheduled = WaDriverTargets.scheduledAt(routine, date.toString());
        jdbc.update("""
            INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at,state,version,created_at)
            VALUES (?,?,?,?::date,?,'CANCELED',1,?) ON CONFLICT(routine_id,local_date)
            DO UPDATE SET state='CANCELED',version=wa.routine_occurrence.version+1
            """, UUID.fromString(d.path("occurrenceId").asText()), UUID.fromString(d.path("routineId").asText()), actor,
            date.toString(), Timestamp.from(scheduled), Timestamp.from(clock.now()));
    }
}
