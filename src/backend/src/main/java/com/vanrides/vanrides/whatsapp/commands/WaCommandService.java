package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.pairing.WaPairingService;
import com.vanrides.vanrides.whatsapp.transport.WaFoundationResponses;
import com.vanrides.vanrides.whatsapp.transport.WaPassengerResponses;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One leased message authorizes at most one effect; callers never select its actor. */
@Service
public class WaCommandService {
    private static final Set<String> IMPLEMENTED = Set.of("START_ONBOARDING", "SET_PROFILE_NAME", "ACCEPT_NOTICE",
        "CLAIM_DRIVER_INVITE", "CONFIRM_PAIRING", "RESPOND_TO_MESSAGE", "REQUEST_SUPPORT", "GET_DRIVER_LOCATION");
    private static final Set<String> FUTURE = Set.of("FINISH_RUN",
        "SET_PREFERENCE", "REQUEST_DATA_DELETION");
    private final JdbcTemplate jdbc;
    private final WaInboxAccess inbox;
    private final WaCommandStore store;
    private final WaOnboardingCommands onboarding;
    private final WaPairingService pairing;
    private final WaSettings settings;
    private final WaClock clock;
    private final WaCrypto crypto;
    private final WaConversationPolicy conversation;
    private final WaDriverSetup driver;
    private final WaPassengerSetup passenger;
    private final WaAttendanceConversation attendance;
    private final WaRunConversation runs;
    private final WaLocationConversation locations;

    public WaCommandService(JdbcTemplate jdbc, WaInboxAccess inbox, WaCommandStore store, WaOnboardingCommands onboarding,
                            WaPairingService pairing, WaSettings settings, WaClock clock, WaCrypto crypto) {
        this.jdbc = jdbc; this.inbox = inbox; this.store = store; this.onboarding = onboarding;
        this.pairing = pairing; this.settings = settings; this.clock = clock; this.crypto = crypto;
        this.conversation = new WaConversationPolicy(jdbc, clock, settings.environment());
        this.driver = new WaDriverSetup(jdbc, clock, crypto);
        this.passenger = new WaPassengerSetup(jdbc, clock, crypto, settings);
        this.attendance = new WaAttendanceConversation(jdbc, clock, settings, crypto);
        this.runs = new WaRunConversation(jdbc, clock, settings, crypto);
        this.locations = new WaLocationConversation(jdbc, clock, settings);
    }

    @Transactional
    public ObjectNode context(WaPrincipal worker, UUID eventId, String lease) {
        WaInboxAccess.Context event = inbox.lock(eventId, lease, worker);
        WaProfileState actor = store.profile(event.identityId());
        inbox.revalidate(event);
        ObjectNode result = WaJson.object(WaJson.write(Map.of("inboxEventId", eventId.toString(), "entityVersion", actor.version(),
            "actorState", actor.role(), "step", step(actor), "allowedCommands", allowed(actor, event),
            "options", List.of(), "serverTime", clock.now().toString())));
        if(settings.teamApiOnly()) {
            result.put("registrationStep",conversation.registration(actor,settings.enabled())).put("eventFreshness",conversation.freshness(event));
            inbox.revalidate(event);return result;
        }
        var offer = driverOffer(actor, event);
        if (offer != null) result.set("options", offer.options());
        var passengerOffer = passengerOffer(actor, event);
        if (passengerOffer != null) result.set("options", passengerOffer.options());
        var attendanceOffer = attendanceOffer(actor, event);
        if (attendanceOffer != null) {
            result.set("options", attendanceOffer.options());
            if (attendanceOffer.choice() != null) result.put("attendanceChoice", attendanceOffer.choice());
        }
        var runOffer = runOffer(actor, event);
        if (runOffer != null) result.set("options", runOffer.options());
        result.put("registrationStep", conversation.registration(actor, settings.enabled())).put("eventFreshness", conversation.freshness(event));
        inbox.revalidate(event);
        return result;
    }

    @Transactional
    public WaDecision execute(WaPrincipal worker, String lease, String key, ObjectNode envelope, String correlation) {
        WaJson.fields(envelope, "commandType", "inboxEventId", "expectedVersion", "payload");
        String type = WaJson.text(envelope, "commandType", 1, 64);
        UUID eventId = WaJson.uuid(envelope, "inboxEventId");
        long version = WaJson.version(envelope);
        if (!(envelope.get("payload") instanceof ObjectNode payload)) throw WaProblem.invalid();
        if(settings.teamApiOnly() && !(type.equals("START_ONBOARDING") || type.equals("RESPOND_TO_MESSAGE")
            && payload.path("responseCode").asText().equals("HELP"))) throw WaProblem.denied();
        if (!IMPLEMENTED.contains(type) && !FUTURE.contains(type) && !WaDriverSetup.COMMANDS.contains(type)
                && !WaPassengerSetup.COMMANDS.contains(type) && !WaAttendanceConversation.COMMANDS.contains(type)
                && !WaRunConversation.COMMANDS.contains(type)) throw WaProblem.invalid();
        WaInboxAccess.Context event = inbox.lock(eventId, lease, worker);
        WaProfileState actor = store.profile(event.identityId());
        inbox.revalidate(event);
        if (actor.blocked()) throw WaProblem.denied();
        String hash = WaCrypto.digest(WaJson.canonical(envelope));
        WaDecision replay = store.replay(event, key, hash);
        if (replay != null) { inbox.revalidate(event); return replay; }
        if (version != actor.version()) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        if (!settings.enabled()) throw WaProblem.denied();
        if (FUTURE.contains(type)) throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        // Validate shape before opening the attempt window. No effect follows a thrown validation error.
        WaCommandPayloads.validate(type, payload);
        boolean business = !type.equals("RESPOND_TO_MESSAGE");
        boolean initiallyCurrent = conversation.freshness(event).equals("CURRENT");
        UUID commandId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        boolean locationCommand = type.equals("GET_DRIVER_LOCATION");
        WaLocationConversation.Result locationResult = locationCommand && allowed(actor,event).contains(type)
            ? locations.execute(actor,event) : null;
        boolean runCommand = WaRunConversation.COMMANDS.contains(type);
        WaRunConversation.Result runResult = runCommand
            ? allowed(actor, event).contains(type) ? runs.execute(actor, event, type, payload, responseId) : WaRunConversation.unavailable()
            : null;
        boolean attendanceCommand = !runCommand && attendance.command(type, payload);
        WaAttendanceConversation.Result attendanceResult = attendanceCommand
            ? allowed(actor, event).contains(type) ? attendance.execute(actor, event, type, payload) : WaAttendanceConversation.unavailable()
            : null;
        boolean driverCommand = !runCommand && !attendanceCommand && WaDriverSetup.COMMANDS.contains(type);
        boolean passengerCommand = WaPassengerSetup.COMMANDS.contains(type);
        WaPassengerSetup.Result passengerResult = passengerCommand
            ? allowed(actor, event).contains(type) ? passenger.execute(actor, event, type, payload, responseId) : WaPassengerSetup.unavailable()
            : null;
        UUID peerResponseId = passengerResult != null && passengerResult.delivery() != null && passengerResult.delivery().kind() != null
            ? UUID.randomUUID() : null;
        // A formerly offered proposal may expire before this request. Its authenticated
        // message still needs a durable rejection so the serial inbox can advance.
        WaDriverSetup.Result driverResult = driverCommand
            ? allowed(actor, event).contains(type) ? driver.execute(actor, event, type, payload, responseId) : WaDriverSetup.unavailable()
            : null;
        WaDecision decision = locationCommand ? locationResult == null ? WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED) : locationResult.decision()
            : runCommand ? runResult.decision() : attendanceCommand ? attendanceResult.decision() : passengerCommand ? passengerResult.decision()
            : driverCommand ? driverResult.decision() : apply(type, payload, actor, event, correlation);
        WaProfileState current = store.profile(actor.identityId());
        String registration = conversation.registration(current, settings.enabled());
        String response = locationCommand ? locationResult == null ? com.vanrides.vanrides.whatsapp.tracking.WaVehicleLocation.UNAVAILABLE : locationResult.text()
            : runCommand ? runResult.text() : attendanceCommand ? attendanceResult.text() : passengerCommand ? passengerResult.text()
            : driverCommand ? driverResult.text() : response(type, payload, decision, registration, current, event);
        if (decision.error() == null) {
            ObjectNode result = WaJson.object("{}");
            result.put("commandId", commandId.toString());
            result.put("outcome", decision.body().path("changed").asBoolean() ? "APPLIED" : "NO_CHANGE");
            result.put("entityVersion", current.version());
            result.put("nextStep", step(current));
            var responseIds = result.putArray("responseIds").add(responseId.toString());
            if (peerResponseId != null) responseIds.add(peerResponseId.toString());
            result.put("correlationId", correlation);
            decision = WaDecision.ok(result);
        }
        store.save(commandId, event, key, type, hash, decision);
        if (!locationCommand && !runCommand && !attendanceCommand && !driverCommand && !passengerCommand && decision.error() == null && !type.equals("REQUEST_SUPPORT")
                && (!type.equals("RESPOND_TO_MESSAGE") || Set.of("HELP", "CONTINUE").contains(payload.path("responseCode").asText())))
            conversation.emitted(current, event, registration);
        if (locationCommand) {
            WaFoundationResponses.saveConversation(jdbc,responseId,commandId,actor.identityId(),response,clock.now());
            if(locationResult!=null) locations.attach(responseId,locationResult);
        } else if (runCommand) {
            WaFoundationResponses.saveRun(jdbc, responseId, commandId, actor.identityId(), response, clock.now());
            runs.attach(responseId, commandId, actor.identityId(), runResult);
        } else if (attendanceCommand) {
            WaFoundationResponses.saveAttendance(jdbc, responseId, commandId, actor.identityId(), response, clock.now());
        } else if (passengerCommand) {
            WaPassengerResponses.saveActor(jdbc, responseId, commandId, actor.identityId(), response, clock.now());
            if (peerResponseId != null) {
                var delivery = passengerResult.delivery();
                WaPassengerResponses.savePeer(jdbc, peerResponseId, commandId, actor.identityId(), delivery.link(), delivery.pickup(),
                    delivery.kind(), delivery.peerText(), clock.now());
            }
        } else if (driverCommand) {
            WaFoundationResponses.saveDriver(jdbc, responseId, commandId, actor.identityId(), response, clock.now());
            driver.attach(responseId, commandId, actor.identityId(), driverResult);
        } else WaFoundationResponses.saveConversation(jdbc, responseId, commandId, actor.identityId(), response, clock.now());
        store.audit(actor.identityId(), worker, actor.role(), type, commandId, correlation);
        // A nested handler can wait for pairing/device locks. Expiry rolls its entire effect back.
        inbox.revalidate(event);
        if (business && initiallyCurrent) conversation.requireCurrent(event);
        // Controlled rejection is returned, not thrown, so attempt accounting survives commit.
        return decision;
    }

    private WaDecision apply(String type, ObjectNode payload, WaProfileState actor, WaInboxAccess.Context event, String correlation) {
        if (!type.equals("RESPOND_TO_MESSAGE") && !conversation.freshness(event).equals("CURRENT"))
            return WaDecision.error(WaProblem.Code.INTENT_EXPIRED);
        if (!allowed(actor, event).contains(type)) return WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
        if (type.equals("RESPOND_TO_MESSAGE")) return conversation.replyAllowed(payload.path("responseCode").asText(), actor, event)
            ? WaDecision.ok(Map.of("changed", false)) : WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
        if (type.equals("REQUEST_SUPPORT")) {
            conversation.support(actor, event, payload.path("reason").asText(), correlation);
            return WaDecision.ok(Map.of("changed", true));
        }
        if (Set.of("CLAIM_DRIVER_INVITE", "CONFIRM_PAIRING").contains(type) && !store.attempt(actor.identityId(), type))
            return WaDecision.error(WaProblem.Code.RATE_LIMITED);
        if (type.equals("ACCEPT_NOTICE") && !conversation.consentAllowed(actor, event)) return WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
        if (!type.equals("CONFIRM_PAIRING")) return onboarding.apply(type, payload, actor, event);
        String codeHash = WaCommandPayloads.codeHash(payload, event, "userCode", "pairingCodeHash", "pairing-code", crypto);
        if (codeHash == null) return WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
        WaDecision decision = pairing.confirmHash(actor.identityId(), codeHash);
        if (decision.error() == null && decision.body().path("changed").asBoolean())
            jdbc.update("UPDATE wa.profile SET version=version+1,updated_at=? WHERE identity_id=?", Timestamp.from(clock.now()), actor.identityId());
        return decision;
    }

    private String response(String type, ObjectNode payload, WaDecision decision, String registration, WaProfileState actor, WaInboxAccess.Context event) {
        if (decision.error() != null) return WaConversationResponses.rejected(decision.error());
        if(settings.teamApiOnly() && type.equals("RESPOND_TO_MESSAGE"))
            return "Este ambiente permite somente o cadastro sintético de teste. Use o roteiro recebido para iniciar e conferir o resultado.";
        if (type.equals("RESPOND_TO_MESSAGE")) {
            String help = null;
            if (Set.of("HELP", "CONTINUE").contains(payload.path("responseCode").asText())) {
                help = runs.help(actor);
                if (help == null) help = attendance.help(actor, event);
                if (help == null) help = passenger.help(actor);
                if (help == null) help = driver.help(actor);
            }
            return help == null ? WaConversationResponses.text(payload.path("responseCode").asText(), registration) : help;
        }
        if (type.equals("REQUEST_SUPPORT")) return WaConversationResponses.support();
        String text = WaFoundationResponses.text(type);
        return Set.of("ASK_NOTICE", "PAIRING").contains(registration) ? text + " " + WaConversationResponses.prompt(registration) : text;
    }

    private List<String> allowed(WaProfileState actor, WaInboxAccess.Context event) {
        if (actor.blocked() || !settings.enabled() || conversation.paused(actor.identityId())) return List.of();
        if(settings.teamApiOnly()) {
            if(!conversation.freshness(event).equals("CURRENT")) return List.of();
            if(conversation.isHelp(event)) return List.of("RESPOND_TO_MESSAGE");
            return actor.role().equals("UNREGISTERED") && conversation.hasText(event) ? List.of("START_ONBOARDING") : List.of();
        }
        List<String> commands = new ArrayList<>();
        commands.add("RESPOND_TO_MESSAGE");
        if (!conversation.freshness(event).equals("CURRENT")) return List.copyOf(commands);
        if (conversation.explicitSupport(event)) commands.add("REQUEST_SUPPORT");
        if (locations.offer(actor,event)) { commands.add("GET_DRIVER_LOCATION"); return List.copyOf(commands); }
        var runOffer = runOffer(actor, event);
        var attendanceOffer = runOffer == null ? attendanceOffer(actor, event) : null;
        var passengerOffer = runOffer == null && attendanceOffer == null ? passengerOffer(actor, event) : null;
        var offer = runOffer == null && attendanceOffer == null && passengerOffer == null ? driverOffer(actor, event) : null;
        if (runOffer != null) commands.add(runOffer.command());
        if (attendanceOffer != null) commands.add(attendanceOffer.command());
        if (passengerOffer != null) commands.add(passengerOffer.command());
        if (offer != null) commands.add(offer.command());
        boolean ordinaryText = conversation.hasText(event) && !conversation.isHelp(event)
            && !conversation.explicitSupport(event) && offer == null && passengerOffer == null && attendanceOffer == null && runOffer == null;
        if ("UNREGISTERED".equals(actor.role()) && ordinaryText) commands.add("START_ONBOARDING");
        else if (!"UNREGISTERED".equals(actor.role()) && ordinaryText) {
            commands.add("SET_PROFILE_NAME");
            if (conversation.consentAllowed(actor, event)) commands.add("ACCEPT_NOTICE");
        }
        if ("ONBOARDING".equals(actor.status()) && event.normalizedPayload().hasNonNull("driverInviteCodeHash"))
            commands.add("CLAIM_DRIVER_INVITE");
        if ("DRIVER".equals(actor.role()) && actor.driverEnabled() && "ACTIVE".equals(actor.status())
                && WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion()) && settings.newStartsEnabled()
                && event.normalizedPayload().hasNonNull("pairingCodeHash")) commands.add("CONFIRM_PAIRING");
        return List.copyOf(commands);
    }

    private WaPassengerSetup.Offer passengerOffer(WaProfileState actor, WaInboxAccess.Context event) {
        if (actor.blocked() || !settings.enabled() || conversation.paused(actor.identityId())
                || !conversation.freshness(event).equals("CURRENT") || conversation.isHelp(event) || conversation.explicitSupport(event)) return null;
        return passenger.offer(actor, event);
    }

    private WaRunConversation.Offer runOffer(WaProfileState actor, WaInboxAccess.Context event) {
        if (actor.blocked() || !settings.enabled() || conversation.paused(actor.identityId())
                || !conversation.freshness(event).equals("CURRENT") || conversation.isHelp(event) || conversation.explicitSupport(event)) return null;
        return runs.offer(actor, event);
    }

    private WaAttendanceConversation.Offer attendanceOffer(WaProfileState actor, WaInboxAccess.Context event) {
        if (actor.blocked() || !settings.enabled() || conversation.paused(actor.identityId())
                || !conversation.freshness(event).equals("CURRENT") || conversation.isHelp(event) || conversation.explicitSupport(event)) return null;
        return attendance.offer(actor, event);
    }

    private WaDriverSetup.Offer driverOffer(WaProfileState actor, WaInboxAccess.Context event) {
        if (actor.blocked() || !settings.enabled() || conversation.paused(actor.identityId())
                || !conversation.freshness(event).equals("CURRENT") || conversation.isHelp(event) || conversation.explicitSupport(event)) return null;
        return driver.offer(actor, event);
    }

    private String step(WaProfileState actor) {
        if (actor.blocked() || !settings.enabled() || conversation.paused(actor.identityId())) return "PAUSED";
        if (!"ACTIVE".equals(actor.status())) return "ONBOARDING";
        if ("DRIVER".equals(actor.role())) {
            Boolean paired = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM wa.driver_device WHERE driver_id=? AND environment=? AND status='ACTIVE')",
                Boolean.class, actor.identityId(), settings.environment());
            if (!Boolean.TRUE.equals(paired)) return "PAIRING";
        }
        return "READY";
    }
}
