package com.vanrides.vanrides.whatsapp.pairing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier.Proof;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.pairing.WaPairingRepository.Device;
import com.vanrides.vanrides.whatsapp.pairing.WaPairingRepository.Driver;
import com.vanrides.vanrides.whatsapp.pairing.WaPairingRepository.Pairing;
import com.vanrides.vanrides.whatsapp.pairing.WaPairingRepository.Token;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** App-key possession is checked by ingress and again against every resource here. */
@Service
public class WaPairingService {
    private static final Duration SHORT_TTL = Duration.ofMinutes(5);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final UUID NO_PAIRING = new UUID(0, 0);
    private final WaSettings settings;
    private final WaClock clock;
    private final WaCrypto crypto;
    private final WaPairingRepository store;
    private final WaReplayStore replay;

    public WaPairingService(JdbcTemplate jdbc, WaSettings settings, WaClock clock, WaCrypto crypto) {
        this.settings = settings; this.clock = clock; this.crypto = crypto;
        store = new WaPairingRepository(jdbc); replay = new WaReplayStore(jdbc, crypto, clock);
    }

    @Transactional
    public WaDecision begin(Proof proof, ObjectNode body, String idemKey) {
        requireNewEffects(); key(idemKey);
        WaJson.fields(body, "publicJwk", "pkceChallenge", "pkceMethod", "platform", "appVersion");
        String challenge = WaJson.text(body, "pkceChallenge", 43, 43);
        WaDpopVerifier.PublicMaterial material;
        try {
            material = WaDpopVerifier.publicMaterial(body.get("publicJwk"));
            WaDpopVerifier.canonical32(challenge);
        } catch (WaProblem invalidBody) { throw WaProblem.invalid(); }
        if (proof == null || !WaCrypto.same(proof.jkt(), material.jkt())) throw WaProblem.unauthorized();
        if (!"S256".equals(WaJson.text(body, "pkceMethod", 4, 4))) throw WaProblem.invalid();
        String platform = WaJson.text(body, "platform", 3, 7);
        String version = WaJson.text(body, "appVersion", 1, 40);
        if (!Set.of("ANDROID", "IOS").contains(platform) || !version.matches("[A-Za-z0-9.+_-]+")) throw WaProblem.invalid();
        UUID scope = beginScope(proof.jkt());
        store.serialize("PAIR_BEGIN", scope.toString());
        ObjectNode previous = replay.read("PAIR_BEGIN", scope, idemKey, body);
        if (previous != null) {
            Pairing pairing = boundPairing(proof, WaJson.uuid(previous, "pairingId"), false);
            if (pairing.driverId() != null) {
                requireDriver(store.lockDriver(pairing.driverId()), true);
                pairing = boundPairing(proof, pairing.id(), true);
            }
            if (expired(pairing) || !Set.of("PENDING", "APPROVED").contains(pairing.state()))
                throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
            return WaDecision.ok(previous);
        }
        return createBegin(material, challenge, platform, version, scope, idemKey, body);
    }

    @Transactional(readOnly = true)
    public ObjectNode status(Proof proof, UUID pairingId) {
        Pairing pairing = boundPairing(proof, pairingId, false);
        String state = expired(pairing) && Set.of("PENDING", "APPROVED").contains(pairing.state()) ? "EXPIRED" : pairing.state();
        return object().put("pairingId", pairing.id().toString()).put("state", state)
                .put("expiresAt", pairing.expiresAt().toString()).put("serverTime", clock.now().toString());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WaDecision confirm(UUID driverId, String userCode) {
        if (userCode == null || !userCode.matches("[BCDFGHJKMNPQRSTVWXYZ]{8}")) throw WaProblem.invalid();
        return confirmHash(driverId, crypto.secretHash("pairing-code", userCode));
    }

    /** Internal input from the authenticated inbox; no HTTP route accepts a code hash. */
    @Transactional(propagation = Propagation.MANDATORY)
    public WaDecision confirmHash(UUID driverId, String hash) {
        requireNewEffects();
        Driver driver = store.lockDriver(driverId);
        requireDriver(driver, true);
        if (hash == null || !hash.matches("[0-9a-f]{64}")) throw WaProblem.invalid();
        Pairing pairing = store.pairingByCode(hash, settings.environment());
        if (pairing == null) return WaDecision.error(WaProblem.Code.INVALID_CREDENTIAL);
        if (expired(pairing)) { store.expire(pairing); return WaDecision.error(WaProblem.Code.INTENT_EXPIRED); }
        if (pairing.driverId() != null && !pairing.driverId().equals(driverId)) return WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
        if ("APPROVED".equals(pairing.state()) && pairing.approvalGeneration() == driver.generation())
            return WaDecision.ok(object().put("changed", false));
        if (!"PENDING".equals(pairing.state())) return WaDecision.error(WaProblem.Code.INTENT_EXPIRED);
        store.approve(pairing, driver);
        return WaDecision.ok(object().put("changed", true));
    }

    @Transactional
    public WaDecision redeem(Proof proof, ObjectNode body, String idemKey) {
        requireNewEffects(); key(idemKey); WaJson.fields(body, "pairingId", "pkceVerifier");
        UUID pairingId = WaJson.uuid(body, "pairingId");
        String verifier = WaJson.text(body, "pkceVerifier", 43, 128);
        if (!verifier.matches("[A-Za-z0-9._~-]{43,128}")) throw WaProblem.invalid();
        Pairing lookup = boundPairing(proof, pairingId, false);
        // A pending request has no driver to lock. Do not lock it then try to
        // acquire a driver lock if confirmation wins the race in between.
        if (lookup.driverId() == null) return WaDecision.error(expired(lookup) || !"PENDING".equals(lookup.state())
                ? WaProblem.Code.INTENT_EXPIRED : WaProblem.Code.ACTION_NOT_ALLOWED);
        Driver driver = store.lockDriver(lookup.driverId()); requireDriver(driver, true);
        Pairing pairing = boundPairing(proof, pairingId, true);
        ObjectNode previous = replay.read("PAIR_REDEEM", pairingId, idemKey, body);
        if (previous != null) return redeemedReplay(previous, pairing, driver);
        if (!"APPROVED".equals(pairing.state())) return WaDecision.error(WaProblem.Code.INTENT_EXPIRED);
        if (expired(pairing) || pairing.approvalGeneration() != driver.generation()) {
            store.expire(pairing); return WaDecision.error(WaProblem.Code.INTENT_EXPIRED);
        }
        if (!WaCrypto.same(pairing.pkce(), pkce(verifier))) {
            WaProblem.Code denial = store.failedPkce(pairing) == 5 ? WaProblem.Code.RATE_LIMITED : WaProblem.Code.INVALID_CREDENTIAL;
            return saveError("PAIR_REDEEM", pairingId, idemKey, body, denial, pairing.expiresAt());
        }
        return activate(pairing, driver, idemKey, body);
    }

    @Transactional
    public WaDecision refresh(Proof proof, ObjectNode body, String idemKey) {
        key(idemKey); WaJson.fields(body, "refreshToken");
        String raw = WaJson.text(body, "refreshToken", 43, 43); WaDpopVerifier.canonical32(raw);
        var lookup = store.tokenOwner(WaCrypto.digest(raw), "REFRESH");
        if (lookup == null || proof == null || !WaCrypto.same(lookup.jkt(), proof.jkt())
                || !settings.environment().equals(lookup.environment())) throw WaProblem.unauthorized();
        Driver driver = store.lockDriver(lookup.driverId());
        Device device = store.lockDevice(lookup.token().deviceId());
        Token token = store.lockToken(lookup.token().id());
        requireTokenBinding(proof, driver, device, token);
        ObjectNode previous = replay.read("REFRESH", token.id(), idemKey, body);
        if (previous != null) return sessionReplay(previous, driver, device);
        if (!token.expiresAt().isAfter(clock.now())) throw WaProblem.unauthorized();
        if (token.consumedAt() != null) {
            revokeOwnDevice(driver, device);
            return saveError("REFRESH", token.id(), idemKey, body, WaProblem.Code.INVALID_CREDENTIAL, clock.now().plus(SHORT_TTL));
        }
        requireDriver(driver, false); requireLiveDevice(driver, device);
        if (token.revokedAt() != null) throw WaProblem.unauthorized();
        Instant now = clock.now(); store.revokeAccess(device.id(), now);
        Issued issued = issue(device.id(), token.familyId(), token.expiresAt(), now);
        store.rotate(token, issued.refreshId(), now);
        replay.save("REFRESH", token.id(), idemKey, body, issued.body(), now.plus(SHORT_TTL));
        store.audit(driver.id(), device.id(), "REFRESH_ROTATED", now);
        return WaDecision.ok(issued.body());
    }

    @Transactional
    public WaDecision revoke(WaPrincipal principal, ObjectNode body, String idemKey) {
        key(idemKey); WaJson.fields(body);
        Session session = session(principal, true);
        ObjectNode previous = replay.read("SESSION_REVOKE", session.device().id(), idemKey, body);
        if (previous != null) return WaDecision.ok(previous);
        revokeOwnDevice(session.driver(), session.device());
        ObjectNode response = object().put("deviceId", session.device().id().toString())
                .put("state", "REVOKED").put("serverTime", clock.now().toString());
        replay.save("SESSION_REVOKE", session.device().id(), idemKey, body, response, clock.now().plus(SHORT_TTL));
        return WaDecision.ok(response);
    }

    @Transactional
    public ObjectNode driverStatus(WaPrincipal principal) {
        Session session = session(principal, false);
        Driver driver = session.driver(); Device device = session.device();
        ObjectNode response = object().put("deviceId", device.id().toString()).put("driverId", driver.id().toString())
                .put("profileStatus", driver.status()).put("deviceState", device.state())
                .put("readiness", driver.noticeAccepted() && driver.name() != null ? "READY" : "SETUP_REQUIRED")
                .put("serverTime", clock.now().toString());
        if (driver.name() == null) response.putNull("displayName"); else response.put("displayName", driver.name());
        UUID run = store.activeRun(driver.id());
        if (run == null) response.putNull("runId"); else response.put("runId", run.toString());
        return response;
    }

    private WaDecision createBegin(WaDpopVerifier.PublicMaterial material, String challenge, String platform,
                                   String version, UUID scope, String key, ObjectNode body) {
        Instant now = clock.now(); Instant expiry = persistedDeadline(now, SHORT_TTL);
        for (int attempt = 0; attempt < 5; attempt++) {
            UUID id = UUID.randomUUID(); String code = WaCrypto.userCode();
            if (!store.createPairing(id, material.json(), material.jkt(), challenge, platform, version,
                    settings.environment(), crypto.secretHash("pairing-code", code), now, expiry)) continue;
            ObjectNode response = object().put("pairingId", id.toString()).put("userCode", code)
                    .put("expiresAt", expiry.toString()).put("serverTime", now.toString());
            replay.save("PAIR_BEGIN", scope, key, body, response, expiry);
            return WaDecision.ok(response);
        }
        throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
    }

    private WaDecision activate(Pairing pairing, Driver driver, String key, ObjectNode body) {
        store.serialize("DEVICE_JKT", pairing.jkt());
        if (store.hasForeignActiveKey(pairing.jkt(), driver.id())
                || store.hasForeignEnvironment(driver.id(), settings.environment())) throw WaProblem.denied();
        // The cross-driver key lock can outlive the approval. No current
        // device, token or generation changes until its deadline is rechecked.
        Instant now = clock.now();
        if (!pairing.expiresAt().isAfter(now)) {
            store.expire(pairing); return WaDecision.error(WaProblem.Code.INTENT_EXPIRED);
        }
        long generation = store.advanceGeneration(driver.id());
        store.replaceDevices(driver.id(), now);
        store.invalidateApprovals(driver.id(), generation, pairing.id());
        UUID device = store.createDevice(pairing, generation, now);
        Issued issued = issue(device, UUID.randomUUID(), persistedDeadline(now, REFRESH_TTL), now);
        replay.save("PAIR_REDEEM", pairing.id(), key, body, issued.body(), now.plus(SHORT_TTL));
        store.audit(driver.id(), device, "PAIRING_REDEEMED", now);
        return WaDecision.ok(issued.body());
    }

    // Return exactly the deadline the database can store; rounding up would extend the lease.
    private static Instant persistedDeadline(Instant now, Duration ttl) {
        return now.plus(ttl).truncatedTo(ChronoUnit.MICROS);
    }

    private Issued issue(UUID device, UUID family, Instant refreshExpiry, Instant now) {
        String access = WaCrypto.randomToken(); String refresh = WaCrypto.randomToken();
        UUID refreshId = UUID.randomUUID(); Instant accessExpiry = persistedDeadline(now, SHORT_TTL);
        store.createToken(UUID.randomUUID(), device, access, "ACCESS", family, now, accessExpiry);
        store.createToken(refreshId, device, refresh, "REFRESH", family, now, refreshExpiry);
        return new Issued(object().put("deviceId", device.toString()).put("accessToken", access).put("refreshToken", refresh)
                .put("tokenType", "DPoP").put("accessExpiresAt", accessExpiry.toString())
                .put("refreshExpiresAt", refreshExpiry.toString()).put("serverTime", now.toString()), refreshId);
    }

    private WaDecision redeemedReplay(ObjectNode previous, Pairing pairing, Driver driver) {
        if (previous.has("_waError")) return errorReplay(previous);
        if (!"REDEEMED".equals(pairing.state()) || pairing.deviceId() == null) throw WaProblem.unauthorized();
        return sessionReplay(previous, driver, store.lockDevice(pairing.deviceId()));
    }

    private WaDecision sessionReplay(ObjectNode previous, Driver driver, Device device) {
        if (previous.has("_waError")) return errorReplay(previous);
        requireDriver(driver, false); requireLiveDevice(driver, device);
        if (!WaJson.uuid(previous, "deviceId").equals(device.id()) || !store.responseTokensUsable(device.id(),
                WaJson.text(previous, "accessToken", 43, 43), WaJson.text(previous, "refreshToken", 43, 43), clock.now()))
            throw WaProblem.unauthorized();
        return WaDecision.ok(previous);
    }

    private WaDecision saveError(String kind, UUID scope, String key, ObjectNode request, WaProblem.Code code, Instant expiry) {
        replay.save(kind, scope, key, request, object().put("_waError", code.name()), expiry);
        return WaDecision.error(code);
    }

    private static WaDecision errorReplay(ObjectNode stored) {
        return WaDecision.error(WaProblem.Code.valueOf(stored.path("_waError").asText()));
    }

    private Session session(WaPrincipal principal, boolean allowRevoked) {
        if (principal == null || principal.kind() != WaPrincipal.Kind.DEVICE || principal.driverId() == null
                || principal.deviceId() == null || principal.credentialId() == null) throw WaProblem.unauthorized();
        Driver driver = store.lockDriver(principal.driverId());
        Device device = store.lockDevice(principal.deviceId()); Token token = store.lockToken(principal.credentialId());
        if (driver == null || device == null || token == null || !"ACCESS".equals(token.kind())
                || !token.deviceId().equals(device.id()) || !device.driverId().equals(driver.id())
                || !WaCrypto.same(device.jkt(), principal.jkt()) || device.generation() != principal.generation()
                || !settings.environment().equals(device.environment()) || !token.expiresAt().isAfter(clock.now()))
            throw WaProblem.unauthorized();
        requireCohort(driver);
        if (!allowRevoked) {
            requireDriver(driver, false); requireLiveDevice(driver, device);
            if (principal.revoked() || token.revokedAt() != null || token.consumedAt() != null) throw WaProblem.unauthorized();
        }
        return new Session(driver, device);
    }

    private void requireTokenBinding(Proof proof, Driver driver, Device device, Token token) {
        if (driver == null || device == null || token == null || !"REFRESH".equals(token.kind())
                || !device.driverId().equals(driver.id()) || !token.deviceId().equals(device.id())
                || !WaCrypto.same(device.jkt(), proof.jkt()) || !settings.environment().equals(device.environment()))
            throw WaProblem.unauthorized();
        requireCohort(driver);
    }

    private void revokeOwnDevice(Driver driver, Device device) {
        if ("REVOKED".equals(device.state())) return;
        Instant now = clock.now();
        store.revokeDevice(device.id(), now);
        if ("ACTIVE".equals(device.state()) && device.generation() == driver.generation()) {
            long generation = store.advanceGeneration(driver.id());
            store.invalidateApprovals(driver.id(), generation, NO_PAIRING);
            store.audit(driver.id(), device.id(), "DEVICE_REVOKED", now);
        }
    }

    private Pairing boundPairing(Proof proof, UUID id, boolean lock) {
        Pairing pairing = store.pairing(id, lock);
        if (pairing == null || proof == null || !WaCrypto.same(pairing.jkt(), proof.jkt())
                || !settings.environment().equals(pairing.environment())) throw WaProblem.missing();
        return pairing;
    }
    private void requireDriver(Driver driver, boolean notice) {
        if (driver == null || !"DRIVER".equals(driver.role()) || !"ACTIVE".equals(driver.status()) || !driver.enabled()
                || notice && !driver.noticeAccepted()) throw WaProblem.denied();
        requireCohort(driver);
    }
    private void requireCohort(Driver driver) {
        if (settings.cohortOnly() && !settings.environment().equals(driver.cohortEnvironment())) throw WaProblem.denied();
    }
    private static void requireLiveDevice(Driver driver, Device device) {
        if (device == null || !device.driverId().equals(driver.id()) || !"ACTIVE".equals(device.state())
                || device.generation() != driver.generation()) throw WaProblem.unauthorized();
    }
    private boolean expired(Pairing pairing) { return !pairing.expiresAt().isAfter(clock.now()); }
    private void requireNewEffects() { if (!settings.enabled()) throw WaProblem.denied(); }
    private UUID beginScope(String jkt) {
        ByteBuffer digest = ByteBuffer.wrap(WaCrypto.sha256((settings.environment() + "\0" + jkt).getBytes(StandardCharsets.UTF_8)));
        return new UUID(digest.getLong(), digest.getLong());
    }
    private static void key(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{8,128}")) throw WaProblem.invalid();
    }
    private static String pkce(String verifier) { return WaCrypto.base64(WaCrypto.sha256(verifier.getBytes(StandardCharsets.US_ASCII))); }
    private static ObjectNode object() { return WaJson.MAPPER.createObjectNode(); }
    private record Session(Driver driver, Device device) { }
    private record Issued(ObjectNode body, UUID refreshId) { }
}
