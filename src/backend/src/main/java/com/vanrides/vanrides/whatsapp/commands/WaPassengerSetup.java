package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Passenger conversation seam. Offer is computed again under the same transactional authority. */
final class WaPassengerSetup {
    private static final String INVITATION_HELP =
            "Seu perfil de passageiro está pronto, mas você ainda não tem vínculo com um motorista."
                    + " Peça ao seu motorista um convite de passageiro do VanRides e envie esse"
                    + " convite aqui. Você vai revisar o motorista, as rotinas e o ponto de embarque;"
                    + " o vínculo depende da aprovação do motorista. Para atendimento, escreva"
                    + " FALAR COM SUPORTE.";

    static final Set<String> COMMANDS =
            Set.of(
                    "ADVANCE_PASSENGER_SETUP",
                    "REQUEST_LINK",
                    "APPROVE_LINK",
                    "CLOSE_LINK",
                    "PROPOSE_PICKUP",
                    "APPROVE_PICKUP");

    record Offer(String command, ArrayNode options) {}

    record Result(WaDecision decision, String text, WaPassengerMutations.Result delivery) {}

    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final WaPassengerDraftStore store;
    private final WaPassengerData data;
    private final WaPassengerWizard wizard;
    private final WaPassengerReview review;
    private final WaPassengerMutations mutations;

    WaPassengerSetup(JdbcTemplate jdbc, WaClock clock, WaCrypto crypto, WaSettings settings) {
        this.jdbc = jdbc;
        this.clock = clock;
        store = new WaPassengerDraftStore(jdbc, clock, crypto);
        data = new WaPassengerData(jdbc, clock, settings);
        wizard = new WaPassengerWizard(data);
        review = new WaPassengerReview(data);
        mutations = new WaPassengerMutations(data);
    }

    Offer offer(WaProfileState actor, WaInboxAccess.Context event) {
        ObjectNode draft = store.load(actor.identityId());
        String text = WaPassengerWizard.text(event);
        if (actor.role().equals("DRIVER")) {
            if (!actor.status().equals("ACTIVE")
                    || !actor.driverEnabled()
                    || !WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion())) {
                return null;
            }
            if (!WaPassengerWizard.starts(text) && draft.isEmpty()) {
                return null;
            }
            if (WaDriverWizard.starts(WaDriverWizard.text(event))) {
                return null;
            }
        } else {
            if (!Set.of("PASSENGER", "UNREGISTERED").contains(actor.role())) {
                return null;
            }
        }
        if (event.normalizedPayload().hasNonNull("passengerInviteCodeHash")
                && !actor.role().equals("DRIVER")) {
            return new Offer("ADVANCE_PASSENGER_SETUP", options());
        }
        if (event.normalizedPayload().has("driverInviteCodeHash")
                || event.normalizedPayload().has("pairingCodeHash")) {
            return null;
        }
        if (!draft.isEmpty() && !WaConversationOwnership.currentDraftReply(jdbc, event)) {
            return new Offer("ADVANCE_PASSENGER_SETUP", options());
        }
        if (store.confirms(actor, event, draft)) {
            String action = draft.path("action").asText();
            if (Set.of("RECTIFY", "PUBLISH_COMMON").contains(action)) {
                return new Offer("ADVANCE_PASSENGER_SETUP", options());
            }
            return new Offer(action, confirmationOptions(draft));
        }
        if (draft.isEmpty() && !WaPassengerWizard.starts(text)) {
            return null;
        }
        return new Offer("ADVANCE_PASSENGER_SETUP", options());
    }

    Result execute(
            WaProfileState actor,
            WaInboxAccess.Context event,
            String type,
            ObjectNode payload,
            UUID outbox) {
        Offer offer = offer(actor, event);
        if (offer == null || !offer.command().equals(type)) {
            return unavailable();
        }
        ObjectNode expected = WaPassengerDraftStore.json();
        for (var option : offer.options()) {
            String field =
                    switch (option.path("kind").asText()) {
                        case "LINK" -> "linkRef";
                        case "PICKUP" -> "pickupRef";
                        case "CONFIRMATION" -> "confirmationRef";
                        case "QUESTION" -> "sourceMessageId";
                        default -> throw WaProblem.invalid();
                    };
            expected.put(field, option.path("ref").asText());
        }
        if (!WaJson.canonical(payload).equals(WaJson.canonical(expected))) {
            throw WaProblem.denied();
        }
        ObjectNode draft = store.load(actor.identityId());
        if (!draft.isEmpty() && !WaConversationOwnership.currentDraftReply(jdbc, event)) {
            return new Result(WaDecision.ok(Map.of("changed", false)),
                "A mensagem citada não corresponde à etapa atual do cadastro. Responda à última pergunta do bot ou envie AJUDA para retomar.", null);
        }
        if (store.confirms(actor, event, draft)) {
            WaPassengerMutations.Prepared prepared;
            try {
                String current = data.snapshot(actor.identityId(), draft, event);
                if (!current.equals(draft.path("confirmation").path("targetDigest").asText())) {
                    throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
                }
                prepared = mutations.prepare(actor, event, draft);
                if (!store.confirms(actor, event, draft)) {
                    throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
                }
            } catch (WaProblem failure) {
                return recover(actor, event, draft, failure);
            }
            // Nothing below catches a mutation failure: transaction rollback prevents partial
            // effects.
            WaPassengerMutations.Result delivery = mutations.apply(actor, event, draft, prepared);
            if (data.teamTestDriver(UUID.fromString(draft.path("driverId").asText()))) {
                delivery = delivery.withoutPeer();
            }
            store.save(actor, event, WaPassengerDraftStore.json());
            return new Result(WaDecision.ok(Map.of("changed", true)), delivery.text(), delivery);
        }
        if (!type.equals("ADVANCE_PASSENGER_SETUP")) {
            return unavailable();
        }
        if (actor.role().equals("UNREGISTERED")) {
            if (!event.normalizedPayload().hasNonNull("passengerInviteCodeHash")) {
                return unavailable();
            }
            try {
                data.invite(
                        actor.identityId(),
                        event,
                        event.normalizedPayload().path("passengerInviteCodeHash").asText(),
                        false);
            } catch (WaProblem failure) {
                return new Result(
                        WaDecision.error(failure.code()),
                        "Esse convite não está disponível. Peça um convite válido ao motorista.",
                        null);
            }
            jdbc.update(
                    "INSERT INTO wa.profile(identity_id,role,status,created_at,updated_at) VALUES"
                            + " (?,'PASSENGER','ONBOARDING',?,?)",
                    actor.identityId(),
                    Timestamp.from(clock.now()),
                    Timestamp.from(clock.now()));
        }
        try {
            var step = wizard.advance(actor, event, draft);
            draft = step.draft();
            String lead = step.lead();
            String response = WaPassengerTexts.prompt(draft);
            if (draft.path("stage").asText().equals("REVIEW")) {
                String target = data.snapshot(actor.identityId(), draft, event);
                review.enrich(actor, event, draft);
                String marker = store.marker(actor, event, draft, outbox, target);
                response = WaPassengerTexts.review(draft, marker);
                if (response.codePointCount(0, response.length()) > 1900) {
                    draft.remove("confirmation");
                    lead = "";
                    response =
                            "O resumo excedeu o tamanho seguro desta conversa. Nenhuma confirmação"
                                    + " foi oferecida.\n\nPeça suporte para revisar o cadastro.";
                }
            }
            store.save(actor, event, draft);
            return new Result(WaDecision.ok(Map.of("changed", true)), lead + response, null);
        } catch (WaProblem failure) {
            return recover(actor, event, draft, failure);
        }
    }

    private Result recover(
            WaProfileState actor,
            WaInboxAccess.Context event,
            ObjectNode draft,
            WaProblem failure) {
        if (!Set.of(
                        WaProblem.Code.ACTION_NOT_ALLOWED,
                        WaProblem.Code.RESOURCE_NOT_ACCESSIBLE,
                        WaProblem.Code.INTENT_EXPIRED,
                        WaProblem.Code.VERSION_CONFLICT,
                        WaProblem.Code.POINT_NOT_APPROVED)
                .contains(failure.code())) {
            throw failure;
        }
        draft.remove("confirmation");
        if (failure.code() == WaProblem.Code.ACTION_NOT_ALLOWED) {
            draft = WaPassengerDraftStore.json();
        } else {
            if (actor.role().equals("DRIVER")) {
                draft = wizard.advance(actor, event, WaPassengerDraftStore.json()).draft();
            } else {
                if (draft.has("linkId")) {
                    draft.put("stage", "POINT");
                } else {
                    if (draft.has("inviteHash")) {
                        draft.put("stage", "ROUTINES");
                        draft.set(
                                "routineChoices",
                                data.routines(UUID.fromString(draft.path("driverId").asText())));
                        draft.remove("selections");
                    }
                }
            }
        }
        store.save(actor, event, draft);
        String invitation = draft.isEmpty() ? invitationHelp(actor) : null;
        return new Result(
                WaDecision.error(failure.code()),
                invitation != null
                        ? invitation
                        : "Essa proposta não pode mais ser aplicada. Nenhum vínculo ou ponto foi aprovado."
                                + "\n\nEnvie PASSAGEIROS para revisar como motorista, CORRIGIR PONTO como"
                                + " passageiro vinculado, ou um novo convite para retomar seu cadastro.",
                null);
    }

    String help(WaProfileState actor) {
        ObjectNode draft = store.load(actor.identityId());
        return draft.isEmpty() ? invitationHelp(actor) : WaPassengerTexts.prompt(draft);
    }

    private String invitationHelp(WaProfileState actor) {
        return actor.role().equals("PASSENGER")
                        && actor.status().equals("ACTIVE")
                        && actor.name() != null
                        && WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion())
                        && data.links(actor.identityId(), false).isEmpty()
                ? INVITATION_HELP : null;
    }

    static Result unavailable() {
        return new Result(
                WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED),
                "Esse resumo não está disponível para confirmação. Envie CONTINUAR CADASTRO ou"
                        + " PASSAGEIROS para revisar.",
                null);
    }

    private static ArrayNode options() {
        return WaJson.MAPPER.createArrayNode();
    }

    private static ArrayNode confirmationOptions(ObjectNode draft) {
        ArrayNode options = options();
        String action = draft.path("action").asText();
        if (Set.of("APPROVE_LINK", "CLOSE_LINK").contains(action)) {
            options.add(
                    WaDriverDraftStore.option(
                            "LINK",
                            WaPassengerDraftStore.ref(
                                    "link", UUID.fromString(draft.path("linkId").asText()))));
        }
        if (Set.of("APPROVE_LINK", "APPROVE_PICKUP").contains(action)) {
            options.add(
                    WaDriverDraftStore.option(
                            "PICKUP",
                            WaPassengerDraftStore.ref(
                                    "pickup", UUID.fromString(draft.path("pickupId").asText()))));
        }
        if (action.equals("PROPOSE_PICKUP")) {
            options.add(
                    WaDriverDraftStore.option(
                            "QUESTION",
                            draft.path(draft.has("sourceEvent") ? "sourceEvent" : "selectionEvent")
                                    .asText()));
        }
        return options.add(
                WaDriverDraftStore.option(
                        "CONFIRMATION", draft.path("confirmation").path("ref").asText()));
    }
}
