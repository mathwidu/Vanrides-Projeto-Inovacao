package com.vanrides.vanrides.whatsapp.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.vanrides.vanrides.whatsapp.operator.WaOperatorService.*;

/** Synthetic fixture preparation, deliberately separate from the future channel/claim HTTP APIs. */
@Component
public final class WaOperatorFixtures {
    private record Worker(String instance, Instant expiresAt) { }
    private final JdbcTemplate jdbc;
    private final WaCrypto crypto;
    public WaOperatorFixtures(JdbcTemplate jdbc, WaCrypto crypto) { this.jdbc = jdbc; this.crypto = crypto; }

    ObjectNode seed(WaOperatorRequest request, Actor actor) {
        requireCohort(jdbc, actor);
        UUID workerId = request.uuid("workerCredentialId");
        List<Worker> workers = jdbc.query("""
            SELECT allowed_instance_id,expires_at FROM wa.service_credential
            WHERE id=? AND principal='CONVERSATION_WORKER' AND environment=?
              AND revoked_at IS NULL AND expires_at>? FOR SHARE
            """, (rs, row) -> new Worker(rs.getString(1), rs.getTimestamp(2).toInstant()),
            workerId, actor.environment(), time(actor.now()));
        if (workers.size() != 1 || workers.get(0).instance() == null) throw new WaOperatorFailure();
        Worker worker = workers.get(0);
        Instant expires = future(request.instant("leaseExpiresAt"), actor.now(), 60);
        if (expires.isAfter(worker.expiresAt())) throw new WaOperatorFailure();
        UUID identity = request.uuid("identityId");
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id,created_at) VALUES (?,?,?) ON CONFLICT (id) DO NOTHING",
                identity, actor.batchId(), time(actor.now()));
        requireIdentity(jdbc, actor, identity);
        String alias = "synthetic-" + identity + "@vanrides.invalid";
        jdbc.update("""
            INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value,created_at)
            VALUES (?,?,'SYNTHETIC',?,'JID',?,?) ON CONFLICT (provider,instance_id,alias_type,alias_value) DO NOTHING
            """, UUID.randomUUID(), identity, worker.instance(), alias, time(actor.now()));
        Boolean validAlias = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.identity_alias
            WHERE identity_id=? AND provider='SYNTHETIC' AND instance_id=? AND alias_type='JID' AND alias_value=?)
            """, Boolean.class, identity, worker.instance(), alias);
        if (!Boolean.TRUE.equals(validAlias)) throw new WaOperatorFailure();
        ObjectNode metadata = object().put("occurredAt", actor.now().toString());
        if (request.has("messageText")) metadata.put("text", request.text("messageText", 1, 2000));
        if (request.has("pairingCode")) metadata.put("pairingCodeHash", crypto.secretHash("pairing-code", request.text("pairingCode", 8, 8)));
        if (request.has("driverInviteCode")) metadata.put("driverInviteCodeHash", crypto.secretHash("driver-bootstrap", request.text("driverInviteCode", 43, 43)));
        String lease = WaCrypto.randomToken(); UUID event = request.uuid("inboxEventId");
        jdbc.update("""
            INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,normalized_kind,
                payload_json,state,lease_credential_id,lease_token_hash,lease_generation,lease_expires_at,created_at)
            VALUES (?,?,'SYNTHETIC',?,?,'MESSAGE','TEXT',?::jsonb,'CLAIMED',?,?,1,?,?)
            """, event, identity, worker.instance(), "synthetic:" + event, WaJson.write(metadata), workerId,
            WaCrypto.digest(lease), time(expires), time(actor.now()));
        return object().put("resourceId", event.toString()).put("identityId", identity.toString())
                .put("inboxEventId", event.toString()).put("leaseToken", lease).put("leaseGeneration", 1)
                .put("leaseExpiresAt", expires.toString()).put("workerCredentialId", workerId.toString());
    }

    ObjectNode complete(WaOperatorRequest request, Actor actor) {
        requireCohort(jdbc, actor);
        UUID event = request.uuid("inboxEventId");
        List<UUID> identities = jdbc.query("""
            SELECT i.identity_id FROM wa.integration_inbox i JOIN wa.identity a ON a.id=i.identity_id
            WHERE i.id=? AND a.test_cohort_id=? AND i.provider='SYNTHETIC' AND i.category='MESSAGE'
              AND i.state='CLAIMED'
              AND EXISTS(SELECT 1 FROM wa.command_receipt c WHERE c.inbox_event_id=i.id AND c.identity_id=i.identity_id)
            FOR UPDATE OF i
            """, (rs, row) -> rs.getObject(1, UUID.class), event, actor.batchId());
        if (identities.size() != 1) throw new WaOperatorFailure();
        jdbc.update("""
            UPDATE wa.integration_inbox SET state='COMPLETED',lease_credential_id=NULL,
                lease_token_hash=NULL,lease_expires_at=NULL WHERE id=?
            """, event);
        return object().put("resourceId", event.toString()).put("state", "COMPLETED");
    }
}
