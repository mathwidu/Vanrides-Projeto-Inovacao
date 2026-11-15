package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.transport.WaInviteMessage;
import com.vanrides.vanrides.whatsapp.transport.WaPassengerInviteChannel;
import com.vanrides.vanrides.whatsapp.transport.WaPrivateInviteDelivery;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.sql.Timestamp;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;

/** Public command/context seam; draft interpretation and aggregate authority remain in the API. */
final class WaDriverSetup {
    static final Set<String> COMMANDS = Set.of("ADVANCE_DRIVER_SETUP", "CONFIGURE_ROUTINE", "SET_STOP_ORDER", "SET_ROUTINE_STATE",
        "CREATE_INVITE", "REVOKE_INVITE", "CANCEL_OCCURRENCE", "GET_SUMMARY");
    record Offer(String command, ArrayNode options) { }
    record Result(WaDecision decision, String text, WaDriverMutations.Result delivery) { }
    private final WaDriverDraftStore store;
    private final WaDriverTargets targets;
    private final WaDriverWizard wizard;
    private final WaDriverMutations mutations;
    private final WaPrivateInviteDelivery privateDelivery;
    private final WaPassengerInviteChannel inviteChannel;
    private final BiFunction<String, String, String> confirmInvitePn;
    private final WaCrypto crypto;
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    WaDriverSetup(JdbcTemplate jdbc, WaClock clock, WaCrypto crypto, Function<String, String> botPhone,
                  BiFunction<String, String, String> confirmInvitePn) {
        this.crypto = crypto; this.jdbc = jdbc; this.clock = clock;
        this.confirmInvitePn = confirmInvitePn;
        store = new WaDriverDraftStore(jdbc, clock, crypto); targets = new WaDriverTargets(jdbc, clock);
        wizard = new WaDriverWizard(targets); mutations = new WaDriverMutations(jdbc, clock, crypto, targets, botPhone);
        inviteChannel = new WaPassengerInviteChannel(jdbc, clock);
        privateDelivery = new WaPrivateInviteDelivery(jdbc, clock, crypto);
    }
    private boolean eligible(WaProfileState actor) {
        return actor.role().equals("DRIVER") && actor.status().equals("ACTIVE") && actor.driverEnabled()
            && WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion());
    }
    Offer offer(WaProfileState actor, WaInboxAccess.Context event) {
        if (!eligible(actor)) return null;
        ObjectNode draft = store.load(actor.identityId()).draft(); String text = WaDriverWizard.text(event);
        if (!draft.isEmpty() && !WaConversationOwnership.currentDraftReply(jdbc, event))
            return new Offer("ADVANCE_DRIVER_SETUP", WaDriverDraftStore.options());
        if (text.equals("resumo")) return new Offer("GET_SUMMARY", WaDriverDraftStore.options().add(
            WaDriverDraftStore.option("SUMMARY", summaryRef(actor, event))));
        if (store.confirms(actor, event, draft)) return new Offer(draft.path("action").asText(), options(draft));
        if (draft.isEmpty() && !WaDriverWizard.starts(text)
                && !event.normalizedPayload().path("inviteContactExplicit").asBoolean()) return null;
        if (event.normalizedPayload().has("driverInviteCodeHash") || event.normalizedPayload().has("pairingCodeHash")
                || event.normalizedPayload().has("passengerInviteCodeHash")) return null;
        return new Offer("ADVANCE_DRIVER_SETUP", WaDriverDraftStore.options());
    }
    Result execute(WaProfileState actor, WaInboxAccess.Context event, String type, ObjectNode payload, UUID outbox, String instance) {
        Offer offer = offer(actor, event);
        if (offer == null || !offer.command().equals(type)) return unavailable();
        ObjectNode expected = WaDriverDraftStore.json();
        for (var option : offer.options()) {
            String field = switch (option.path("kind").asText()) {
                case "ROUTINE" -> "routineRef"; case "CONFIRMATION" -> "confirmationRef";
                case "INVITE" -> "inviteRef"; case "OCCURRENCE" -> "occurrenceRef"; case "SUMMARY" -> "summaryRef";
                default -> throw WaProblem.invalid();
            };
            expected.put(field, option.path("ref").asText());
        }
        if (!WaJson.canonical(expected).equals(WaJson.canonical(payload))) throw WaProblem.denied();
        var state = store.load(actor.identityId());
        if (!state.draft().isEmpty() && !WaConversationOwnership.currentDraftReply(jdbc, event))
            return new Result(WaDecision.ok(Map.of("changed", false)),
                "A mensagem citada não corresponde à etapa atual das rotinas. Responda à última pergunta do bot ou envie AJUDA para retomar.", null);
        if (type.equals("GET_SUMMARY")) return new Result(WaDecision.ok(Map.of("changed", false)), targets.summary(actor.identityId()), null);
        if (type.equals("ADVANCE_DRIVER_SETUP")) {
            if (!WaInviteMessage.hasContact(event.normalizedPayload())
                    && WaDriverWizard.requestsLink(WaDriverWizard.text(event), state.draft())) {
                ObjectNode share = WaDriverWizard.inviteDraft().put("maxUses", 1).put("ttlMinutes", 1440);
                WaDriverMutations.Result result = mutations.apply(actor.identityId(), share, instance);
                store.save(actor, event, state, WaDriverDraftStore.json());
                return new Result(WaDecision.ok(Map.of("changed", true)), result.text(), result);
            }
            ObjectNode draft;
            String response;
            try {
                draft = WaInviteMessage.hasContact(event.normalizedPayload())
                    ? inviteContact(actor, event, state.draft()) : wizard.advance(actor.identityId(), event, state.draft());
                response = draft.isEmpty() ? "Rascunho descartado. Envie ROTINAS para abrir o menu." : WaDriverTexts.prompt(draft);
                if (draft.path("stage").asText().equals("REVIEW")) {
                    String snapshot = targets.snapshot(actor.identityId(), draft);
                    String marker = store.marker(actor, event, draft, outbox, snapshot); response = WaDriverTexts.review(draft, marker);
                }
            } catch (WaProblem conflict) {
                return recoverTarget(actor, event, state, conflict);
            }
            store.save(actor, event, state, draft);
            return new Result(WaDecision.ok(Map.of("changed", true)), response, null);
        }
        ObjectNode draft = state.draft();
        try {
            if (!store.confirms(actor, event, draft)) throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
            if (!targets.snapshot(actor.identityId(), draft).equals(draft.path("confirmation").path("targetDigest").asText()))
                throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            if (!store.confirms(actor, event, draft)) throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
        } catch (WaProblem conflict) {
            return recoverTarget(actor, event, state, conflict);
        }
        UUID recipient = draft.hasNonNull("inviteRecipient") ? UUID.fromString(draft.path("inviteRecipient").asText()) : null;
        if (recipient != null && !inviteChannel.eligible(event.eventId(), actor.identityId(), recipient)) {
            ObjectNode fallback = WaDriverWizard.inviteDraft();
            store.save(actor, event, state, fallback);
            return new Result(WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED), WaDriverTexts.shareInstead(), null);
        }
        if (recipient != null && hasActiveDelivery(actor.identityId(), recipient)) {
            store.save(actor, event, state, WaDriverDraftStore.json());
            return new Result(WaDecision.ok(Map.of("changed", false)),
                "Já existe um convite ativo para esse WhatsApp. Envie ROTINAS se quiser consultar ou revogar seus convites.", null);
        }
        WaDriverMutations.Result result = mutations.apply(actor.identityId(), draft, instance);
        store.save(actor, event, state, WaDriverDraftStore.json());
        return new Result(WaDecision.ok(Map.of("changed", true)), result.text(), result);
    }
    private boolean hasActiveDelivery(UUID actor, UUID recipient) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM wa.driver_invite i JOIN wa.message_outbox o ON o.private_invite_id=i.id
                WHERE i.driver_id=? AND i.onboarding_identity_id=? AND NOT i.allow_first_contact
                  AND i.revoked_at IS NULL AND i.expires_at>? AND i.use_count<i.max_uses
                  AND o.state NOT IN ('CANCELED','EXPIRED','FAILED')
            )
            """, Boolean.class, actor, recipient, Timestamp.from(clock.now())));
    }
    /** Only preparation/target validation reaches here; never catch a partially applied mutation. */
    private Result recoverTarget(WaProfileState actor, WaInboxAccess.Context event, WaDriverDraftStore.State state, WaProblem conflict) {
        if (!Set.of(WaProblem.Code.VERSION_CONFLICT, WaProblem.Code.POINT_NOT_APPROVED,
                WaProblem.Code.ACTIVE_RUN_EXISTS, WaProblem.Code.RESOURCE_NOT_ACCESSIBLE,
                WaProblem.Code.ACTION_NOT_ALLOWED, WaProblem.Code.INTENT_EXPIRED,
                WaProblem.Code.AMBIGUOUS_OCCURRENCE).contains(conflict.code())) throw conflict;
        // Commit a rejection and a recovery response so a serial inbox can release this message.
        // The command service still revalidates credential/lease after saving and rolls back on expiry.
        store.save(actor, event, state, wizard.menu(actor.identityId()));
        if (conflict.code() == WaProblem.Code.AMBIGUOUS_OCCURRENCE) return new Result(WaDecision.error(conflict.code()),
            "Essa data e o horário da rotina não identificam um único horário local válido no fuso escolhido. "
                + "Nada foi cancelado. Envie ROTINAS para revisar a data ou o horário antes de tentar novamente.", null);
        return new Result(WaDecision.error(conflict.code()),
            "Essa proposta não pode mais ser aplicada. Nenhuma rotina ou convite foi alterado. "
                + "O resumo anterior foi cancelado. Envie ROTINAS para revisar as opções atuais.", null);
    }
    static Result unavailable() {
        return new Result(WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED),
            "Esse resumo não está mais disponível para confirmação. Nenhuma rotina ou convite foi alterado. "
                + "Envie ROTINAS para continuar.", null);
    }
    private ObjectNode inviteContact(WaProfileState actor, WaInboxAccess.Context event, ObjectNode current) {
        boolean pickingInvite = current.path("action").asText().equals("CREATE_INVITE");
        boolean startedFromMenu = (current.isEmpty() || current.path("stage").asText().equals("MENU"))
            && event.normalizedPayload().path("inviteContactExplicit").asBoolean();
        if (!pickingInvite && !startedFromMenu) return current.deepCopy();
        ObjectNode draft = WaDriverWizard.inviteDraft().put("revision", current.path("revision").asLong() + 1);
        String digits = WaInviteMessage.contact(event.normalizedPayload(), crypto);
        UUID recipient = inviteChannel.recipient(event.eventId(), actor.identityId(), digits, confirmInvitePn);
        if (recipient == null) return draft.put("inviteContactUnavailable", true);
        return draft.put("inviteRecipient", recipient.toString()).put("inviteLastDigits", digits.substring(digits.length() - 4))
            .put("maxUses", 1).put("ttlMinutes", 1440).put("stage", "REVIEW");
    }
    void attach(UUID box, UUID command, UUID actor, Result result) {
        if (result.delivery() != null && result.delivery().invite() != null && result.delivery().recipient() == null) privateDelivery.attach(box, command, actor,
            result.delivery().invite(), result.delivery().privateText(), result.delivery().expires());
    }
    void queuePassenger(UUID box, UUID command, Result result) {
        var invite = result.delivery();
        privateDelivery.queuePassenger(box, command, invite.recipient(), invite.invite(), invite.privateText(), invite.expires());
    }
    String help(WaProfileState actor) {
        if (!eligible(actor)) return null;
        ObjectNode draft = store.load(actor.identityId()).draft(); return draft.isEmpty() ? null : WaDriverTexts.prompt(draft);
    }
    private String summaryRef(WaProfileState actor, WaInboxAccess.Context event) {
        return "summary_" + crypto.secretHash("driver-summary", actor.identityId() + ":" + event.eventId());
    }
    private ArrayNode options(ObjectNode draft) {
        ArrayNode options = WaDriverDraftStore.options();
        switch (draft.path("action").asText()) {
            case "CONFIGURE_ROUTINE", "SET_STOP_ORDER", "SET_ROUTINE_STATE" -> options.add(WaDriverDraftStore.option("ROUTINE",
                WaDriverDraftStore.ref("routine", UUID.fromString(draft.path("routineId").asText()))));
            case "REVOKE_INVITE" -> options.add(WaDriverDraftStore.option("INVITE", WaDriverDraftStore.ref("invite", UUID.fromString(draft.path("inviteId").asText()))));
            case "CANCEL_OCCURRENCE" -> options.add(WaDriverDraftStore.option("OCCURRENCE", WaDriverDraftStore.ref("occurrence", UUID.fromString(draft.path("occurrenceId").asText()))));
            default -> { }
        }
        return options.add(WaDriverDraftStore.option("CONFIRMATION", draft.path("confirmation").path("ref").asText()));
    }
}
