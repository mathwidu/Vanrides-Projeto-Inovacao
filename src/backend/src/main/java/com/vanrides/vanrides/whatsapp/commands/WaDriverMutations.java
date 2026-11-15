package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.transport.WaPrivateInviteDelivery;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;

/** Final effects after the conversation has authorized a reviewed action or a request for a shareable link. */
final class WaDriverMutations {
    record Result(String text, UUID invite, String privateText, Instant expires, UUID recipient) { }
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final WaCrypto crypto;
    private final WaDriverTargets targets;
    private final Function<String, String> botPhone;
    WaDriverMutations(JdbcTemplate jdbc, WaClock clock, WaCrypto crypto, WaDriverTargets targets, Function<String, String> botPhone) {
        this.jdbc = jdbc; this.clock = clock; this.crypto = crypto; this.targets = targets;
        this.botPhone = botPhone;
    }
    Result apply(UUID actor, ObjectNode d, String instance) {
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
            case "CREATE_INVITE" -> { return createInvite(actor, d, instance); }
            default -> throw WaProblem.invalid();
        }
        String text = switch (d.path("action").asText()) {
            case "CONFIGURE_ROUTINE" -> "Rotina salva. Envie CONVIDAR para adicionar um passageiro ou RESUMO para ver suas rotinas.";
            case "SET_STOP_ORDER" -> "Ordem dos pontos atualizada.";
            case "SET_ROUTINE_STATE" -> d.path("routineState").asText().equals("PAUSED") ? "Rotina pausada." : "Rotina retomada.";
            case "CANCEL_OCCURRENCE" -> "Ocorrência da data selecionada cancelada.";
            case "REVOKE_INVITE" -> "Convite revogado.";
            default -> throw WaProblem.invalid();
        };
        return new Result(text, null, null, null, null);
    }
    private Result createInvite(UUID actor, ObjectNode draft, String instance) {
        UUID id = UUID.randomUUID();
        UUID recipient = draft.hasNonNull("inviteRecipient") ? UUID.fromString(draft.path("inviteRecipient").asText()) : null;
        String secret = WaCrypto.randomToken();
        String phone = botPhone.apply(instance);
        String link = phone != null && phone.matches("[1-9][0-9]{7,14}")
            ? "https://wa.me/" + phone + "?text=PASSAGEIRO%20" + secret : null;
        Instant now = clock.now();
        Instant expires = now.plusSeconds(draft.path("ttlMinutes").asLong() * 60);
        int uses = draft.path("maxUses").asInt();
        jdbc.update("""
            INSERT INTO wa.driver_invite(id,driver_id,code_hash,expires_at,max_uses,created_at,onboarding_identity_id,allow_first_contact)
            VALUES (?,?,?,?,?,?,?,?)
            """, id, actor, crypto.secretHash("passenger-invite", secret), Timestamp.from(expires), uses, Timestamp.from(now),
            recipient, recipient == null && uses == 1 && link != null);
        String until = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", java.util.Locale.forLanguageTag("pt-BR"))
            .withZone(ZoneId.of("America/Sao_Paulo")).format(expires);
        String instructions = "Envie no WhatsApp do VanRides esta mensagem:\nPASSAGEIRO " + secret
            + "\nVálido até " + until + " (horário de Brasília).";
        String text = link == null ? instructions : "Abra este link e envie a mensagem que aparece no chat:\n" + link
            + "\nSe precisar, use o código abaixo. " + instructions;
        if (recipient != null) {
            return new Result("Convite colocado na fila para o WhatsApp com final " + draft.path("inviteLastDigits").asText()
                + ". O passageiro entra pelo bot. Depois do cadastro e da indicação do ponto de embarque, revise o pedido aqui.",
                id, "Olá! Seu motorista convidou você para organizar seus embarques com o VanRides. " + text
                    + "\nEu vou explicar o cadastro e como indicar o ponto de embarque.", expires, recipient);
        }
        String access = link == null
            ? "\nEnquanto o link estiver indisponível, a equipe precisa liberar o contato para usar o código."
            : uses == 1 ? "" : "\nEste convite é para contatos já liberados pela equipe.";
        String limit = uses == 1 ? "Este convite vale para uma pessoa." : "Este convite permite " + uses + " usos.";
        return new Result("Convite criado. O acesso será entregue somente nesta resposta privada.", id,
            "Encaminhe esta mensagem em privado para quem vai embarcar.\n\n" + text + "\n\n" + limit + access, expires, null);
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
