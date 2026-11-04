package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Local operator boundary. Non-final so Spring can proxy its transaction. */
@Service
public class WaOperatorService {
    record Actor(UUID credentialId, UUID batchId, String environment, Instant now) { }
    private record Credential(UUID id, String principal, String environment, Instant expiresAt,
                              Instant revokedAt, String instance) { }
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaCrypto crypto;
    private final WaClock clock;
    private final WaOperatorMode mode;
    private final WaOperatorFixtures fixtures;
    private final WaOperatorReset reset;
    private final WaOperatorRevocation revocation;
    private final WaOperatorAdmission admission;

    public WaOperatorService(JdbcTemplate jdbc, WaSettings settings, WaCrypto crypto, WaClock clock,
            WaOperatorMode mode, WaOperatorFixtures fixtures, WaOperatorReset reset, WaOperatorRevocation revocation) {
        this.jdbc = jdbc; this.settings = settings; this.crypto = crypto; this.clock = clock;
        this.mode = mode; this.fixtures = fixtures; this.reset = reset; this.revocation = revocation;
        this.admission = new WaOperatorAdmission(jdbc,crypto,mode);
    }

    @Transactional
    public void execute(WaOperatorRequest request, Consumer<ObjectNode> resultWriter) {
        mode.requireLocal();
        if (!TransactionSynchronizationManager.isActualTransactionActive() || resultWriter == null)
            throw new WaOperatorFailure();
        Actor actor = authenticate(request);
        ObjectNode result = switch (request.operation()) {
            case CREATE_COHORT -> createCohort(actor);
            case CREATE_AUTHORIZED_COHORT -> admission.createCohort(actor);
            case ADMIT_PARTICIPANT -> admission.admit(request,actor);
            case PROVISION_CREDENTIAL -> provision(request, actor);
            case REVOKE_CREDENTIAL -> revokeCredential(request, actor);
            case DRIVER_BOOTSTRAP -> bootstrap(request, actor);
            case SEED -> fixtures.seed(request, actor);
            case COMPLETE_INBOX -> fixtures.complete(request, actor);
            case RESET -> reset.reset(actor);
            case REVOKE_DEVICE -> revocation.revoke(request, actor);
            case LIST_DELIVERY_CASES -> WaOperatorDeliveryCases.list(jdbc, request, actor);
        };
        result.put("status", "PREPARED").put("operation", request.operation().value)
                .put("environment", actor.environment()).put("batchId", actor.batchId().toString())
                .put("serverTime", actor.now().toString());
        audit(actor, request.operation().value.toUpperCase(java.util.Locale.ROOT).replace('-', '_'),
                result.has("resourceId") ? UUID.fromString(result.get("resourceId").asText()) : actor.batchId(),
                request.has("reasonCode") ? request.text("reasonCode", 1, 64)
                    : request.operation()==WaOperatorRequest.Operation.ADMIT_PARTICIPANT ? "PARTICIPANT_CONFIRMED" : "AUTHORIZED_LOCAL_OPERATION");
        requireCurrentAuthorization(actor);
        // A write/force failure is a runtime exception and rolls the database transaction back.
        resultWriter.accept(result);
        requireCurrentAuthorization(actor);
    }

    private void requireCurrentAuthorization(Actor actor) {
        Instant current = clock.now();
        if (!settings.operatorExpiresAt().isAfter(current)) throw new WaOperatorFailure();
        // authenticate() retains the credential row lock until this transaction commits.
        Boolean authorized = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.service_credential
                WHERE id=? AND principal='OPERATOR' AND environment=? AND revoked_at IS NULL AND expires_at>?)
            """, Boolean.class, actor.credentialId(), actor.environment(), time(current));
        if (!Boolean.TRUE.equals(authorized)) throw new WaOperatorFailure();
    }

    private Actor authenticate(WaOperatorRequest request) {
        Instant now = clock.now();
        if (!settings.configured() || !settings.environment().equals(request.environment())
                || !settings.operatorExpiresAt().isAfter(now)
                || !WaCrypto.same(settings.operatorCredential(), request.operatorCredential())) throw new WaOperatorFailure();
        String hash = WaCrypto.digest(request.operatorCredential());
        jdbc.update("""
            INSERT INTO wa.service_credential(id,principal,environment,token_hash,expires_at,created_at)
            VALUES (?,'OPERATOR',?,?,?,?) ON CONFLICT (token_hash) DO NOTHING
            """, UUID.randomUUID(), settings.environment(), hash, time(settings.operatorExpiresAt()), time(now));
        List<Credential> rows = jdbc.query("""
            SELECT id,principal,environment,expires_at,revoked_at,allowed_instance_id
            FROM wa.service_credential WHERE token_hash=? FOR UPDATE
            """, (rs, i) -> new Credential(rs.getObject("id", UUID.class), rs.getString("principal"),
                rs.getString("environment"), rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                rs.getString("allowed_instance_id")), hash);
        if (rows.size() != 1) throw new WaOperatorFailure();
        Credential credential = rows.get(0);
        if (!"OPERATOR".equals(credential.principal()) || !settings.environment().equals(credential.environment())
                || credential.revokedAt() != null || !credential.expiresAt().isAfter(now) || credential.instance() != null)
            throw new WaOperatorFailure();
        return new Actor(credential.id(), request.batchId(), request.environment(), now);
    }

    private ObjectNode createCohort(Actor actor) {
        nonProduction(actor.environment());
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic,created_at) VALUES (?,?,true,?) ON CONFLICT (id) DO NOTHING",
                actor.batchId(), actor.environment(), time(actor.now()));
        requireCohort(jdbc, actor);
        return object().put("resourceId", actor.batchId().toString());
    }

    private ObjectNode provision(WaOperatorRequest request, Actor actor) {
        requireCohort(jdbc, actor);
        Instant expires = future(request.instant("expiresAt"), actor.now(), 2592000);
        String principal = request.text("principal", 1, 32);
        String instance = request.text("allowedInstanceId", 1, 80);
        String raw = WaCrypto.randomToken(); UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,expires_at,created_at)
            VALUES (?,?,?,?,?,?,?)
            """, id, principal, actor.environment(), WaCrypto.digest(raw), instance, time(expires), time(actor.now()));
        return object().put("resourceId", id.toString()).put("credentialId", id.toString())
                .put("principal", principal).put("allowedInstanceId", instance).put("expiresAt", expires.toString())
                .put("credential", raw);
    }

    private ObjectNode revokeCredential(WaOperatorRequest request, Actor actor) {
        requireCohort(jdbc, actor);
        UUID id = request.uuid("credentialId");
        List<String> principals = jdbc.query("SELECT principal FROM wa.service_credential WHERE id=? AND environment=? FOR UPDATE",
                (rs, row) -> rs.getString(1), id, actor.environment());
        if (principals.size() != 1 || !WaOperatorRequest.technical(principals.get(0))) throw new WaOperatorFailure();
        jdbc.update("UPDATE wa.service_credential SET revoked_at=COALESCE(revoked_at,?) WHERE id=?", time(actor.now()), id);
        return object().put("resourceId", id.toString()).put("state", "REVOKED");
    }

    private ObjectNode bootstrap(WaOperatorRequest request, Actor actor) {
        if (request.has("inviteId")) return admission.bootstrap(request,actor);
        requireCohort(jdbc, actor);
        UUID identity = request.uuid("identityId");
        requireIdentity(jdbc, actor, identity);
        List<String> statuses = jdbc.query("SELECT status FROM wa.profile WHERE identity_id=? FOR UPDATE",
                (rs, row) -> rs.getString(1), identity);
        if (!statuses.isEmpty() && !"ONBOARDING".equals(statuses.get(0))) throw new WaOperatorFailure();
        Instant expires = future(request.instant("expiresAt"), actor.now(), 86400);
        String raw = WaCrypto.randomToken(); UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO wa.driver_bootstrap_invite(id,identity_id,code_hash,expires_at,created_by,created_at)
            VALUES (?,?,?,?,?,?)
            """, id, identity, crypto.secretHash("driver-bootstrap", raw), time(expires), actor.credentialId(), time(actor.now()));
        return object().put("resourceId", id.toString()).put("identityId", identity.toString())
                .put("driverInviteCode", raw).put("expiresAt", expires.toString());
    }

    static void requireCohort(JdbcTemplate jdbc, Actor actor) {
        nonProduction(actor.environment());
        List<Boolean> cohorts = jdbc.query("SELECT synthetic FROM wa.test_cohort WHERE id=? AND environment=? FOR SHARE",
                (rs, i) -> rs.getBoolean(1), actor.batchId(), actor.environment());
        if (cohorts.size() != 1 || !cohorts.get(0)) throw new WaOperatorFailure();
    }

    static void requireIdentity(JdbcTemplate jdbc, Actor actor, UUID identity) {
        List<UUID> rows = jdbc.query("SELECT id FROM wa.identity WHERE id=? AND test_cohort_id=? FOR SHARE",
                (rs, row) -> rs.getObject(1, UUID.class), identity, actor.batchId());
        if (rows.size() != 1) throw new WaOperatorFailure();
    }

    private void audit(Actor actor, String action, UUID resource, String reason) {
        jdbc.update("""
            INSERT INTO wa.audit_event(id,credential_id,actor_kind,action,resource_id,correlation_id,reason_code,created_at)
            VALUES (?,?,'OPERATOR',?,?,?,?,?)
            """, UUID.randomUUID(), actor.credentialId(), action, resource, UUID.randomUUID().toString(), reason, time(actor.now()));
    }
    static Instant future(Instant expires, Instant now, long maximumSeconds) {
        if (!expires.isAfter(now) || expires.isAfter(now.plusSeconds(maximumSeconds))) throw new WaOperatorFailure();
        return expires;
    }
    static void nonProduction(String environment) {
        if (environment.toLowerCase(java.util.Locale.ROOT).startsWith("prod")) throw new WaOperatorFailure();
    }
    static Timestamp time(Instant value) { return Timestamp.from(value); }
    static ObjectNode object() { return WaJson.MAPPER.createObjectNode(); }
}
