package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.admission.WaAdmissionStore;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.team.WaTeamSettings;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable ingress only. Status reconciliation and provider submission belong to the outbox service. */
@Service
public class WaEventService {
    private record Identity(UUID id, UUID senderAliasId, String ignoredReason) { }
    private record Admitted(UUID identity, WaEvolutionNormalizer.Alias lidToBind) { }
    private final JdbcTemplate jdbc;
    private final WaTransportAccess access;
    private final WaEvolutionNormalizer normalizer;
    private final WaSettings settings;
    private final WaClock clock;
    private final WaTeamSettings team;
    private final WaAdmissionStore admission;
    private final WaDeliveryConnection connection;

    public WaEventService(JdbcTemplate jdbc, WaTransportAccess access, WaEvolutionNormalizer normalizer,
                          WaSettings settings, WaClock clock) {
        this(jdbc, access, normalizer, settings, clock, null, null);
    }

    @Autowired
    public WaEventService(JdbcTemplate jdbc, WaTransportAccess access, WaEvolutionNormalizer normalizer,
                          WaSettings settings, WaClock clock, WaTeamSettings team, WaAdmissionStore admission) {
        this.jdbc = jdbc; this.access = access; this.normalizer = normalizer; this.settings = settings; this.clock = clock;
        this.team = team; this.admission = admission;
        this.connection = new WaDeliveryConnection(jdbc, settings, clock);
    }

    /** The Spring transaction interceptor commits before a controller can return the accepted receipt. */
    @Transactional
    public ObjectNode accept(WaPrincipal channel, ObjectNode envelope) {
        WaTransportAccess.Grant grant = access.lock(channel, WaPrincipal.Kind.CHANNEL);
        String instance = WaJson.text(envelope, "instance", 1, 80);
        if (!instance.equals(grant.instance())) throw WaProblem.denied();
        WaEvolutionNormalizer.Event event = normalizer.normalize(envelope);
        boolean strict = team != null && team.strictIngress(instance);
        if (strict && !Set.of("messages.upsert", "messages.update", "connection.update").contains(envelope.path("event").asText()))
            return ignored(grant);
        if (strict && envelope.path("event").asText().equals("messages.upsert")
                && (!event.humanMessage() || envelope.path("data").path("key").has("remoteJidAlt") != (event.aliases().size() == 2)))
            return ignored(grant);
        // Message and callback namespaces are deliberately distinct.
        String dedup = event.category().equals("MESSAGE") ? event.messageId() : event.category() + "\0" + event.eventKey();
        lock("EVENT", instance, dedup, grant);
        Admitted admitted = null;
        if (strict && event.humanMessage()) {
            if (!settings.enabled()) return ignored(grant);
            admitted = resolveAdmitted(instance, event.aliases(), grant);
            if (admitted == null) return ignored(grant);
        }
        List<UUID> duplicate = event.category().equals("MESSAGE")
            ? jdbc.query("SELECT id FROM wa.integration_inbox WHERE instance_id=? AND message_id=? AND category='MESSAGE'",
                (rs, i) -> rs.getObject(1, UUID.class), instance, event.messageId())
            : jdbc.query("SELECT id FROM wa.integration_inbox WHERE provider='EVOLUTION' AND instance_id=? AND category=? AND event_key=?",
                (rs, i) -> rs.getObject(1, UUID.class), instance, event.category(), event.eventKey());
        if (!duplicate.isEmpty()) {
            access.revalidate(grant);
            return receipt(duplicate.get(0), "DUPLICATE");
        }
        UUID identity = null;
        String state = event.state();
        ObjectNode payload = event.payload();
        if (event.humanMessage()) {
            Identity actor = admitted != null ? bindVerifiedLid(instance, event.aliases().get(0), admitted)
                : settings.enabled() ? resolve(instance, event.aliases(), grant) : new Identity(null, null, "DISABLED");
            identity = actor.id();
            if (actor.ignoredReason() != null) { state = "SKIPPED"; payload = WaEvolutionNormalizer.reason(actor.ignoredReason()); }
            else payload = payload.deepCopy().put("senderAliasId", actor.senderAliasId().toString());
        }
        access.revalidate(grant);
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.now());
        jdbc.update("""
            INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,event_key,
                category,normalized_kind,payload_json,state,created_at,not_before)
            VALUES (?,?,'EVOLUTION',?,?,?,?,?,?::jsonb,?,?,?)
            """, id, identity, instance, event.messageId(), event.eventKey(), event.category(), event.kind(), payload.toString(), state, now, now);
        if (event.category().equals("CONNECTION") && state.equals("COMPLETED"))
            connection.record(grant, id, payload);
        access.revalidate(grant);
        return receipt(id, state.equals("SKIPPED") ? "IGNORED" : "PERSISTED");
    }

    private Admitted resolveAdmitted(String instance, List<WaEvolutionNormalizer.Alias> aliases, WaTransportAccess.Grant grant) {
        admission.lockAliases(instance, aliases);
        access.revalidate(grant);
        UUID identity = null;
        WaEvolutionNormalizer.Alias lidToBind = null;
        for (var alias : aliases) {
            UUID owner = admission.owner(instance, alias);
            if (owner == null) {
                if (!alias.type().equals("LID") || aliases.size() != 2) return null;
                lidToBind = alias;
            } else {
                if (identity != null && !identity.equals(owner)) return null;
                identity = owner;
            }
        }
        if (identity == null) return null;
        try {
            admission.requireCohort(settings.environment(), team.cohort(), false);
            admission.requireIdentity(identity, team.cohort(), true);
            for (var alias : aliases) admission.requireCompatible(instance, identity, alias);
        } catch (WaProblem denied) { return null; }
        if (!access.allowedIdentity(identity) || !admission.hasPhone(instance, identity) || !admission.teamAdmitted(identity))
            return null;
        return new Admitted(identity, lidToBind);
    }

    private Identity bindVerifiedLid(String instance, WaEvolutionNormalizer.Alias sender, Admitted admitted) {
        // The CHANNEL is bound to this Evolution instance, and the alias and identity locks are still held.
        // Only a paired, previously admitted PN can attach an unbound LID. Duplicate events never attach aliases.
        if (admitted.lidToBind() != null) admission.bind(instance, admitted.identity(), admitted.lidToBind(), clock.now());
        return new Identity(admitted.identity(), senderAlias(admitted.identity(), instance, sender), null);
    }

    private ObjectNode ignored(WaTransportAccess.Grant grant) {
        access.revalidate(grant);
        return receipt(UUID.randomUUID(), "IGNORED");
    }

    private Identity resolve(String instance, List<WaEvolutionNormalizer.Alias> aliases, WaTransportAccess.Grant grant) {
        for (String value : aliases.stream().map(a -> a.type() + "\0" + a.value()).sorted().toList())
            lock("ALIAS", instance, value, grant);
        Set<UUID> identities = aliases.stream().flatMap(alias -> owners(instance, alias).stream()).collect(Collectors.toSet());
        if (identities.size() > 1) return new Identity(null, null, "ALIAS_CONFLICT");
        if (identities.isEmpty()) {
            if (settings.cohortOnly()) return new Identity(null, null, "COHORT_REQUIRED");
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO wa.identity(id,created_at) VALUES (?,?)", id, Timestamp.from(clock.now()));
            for (var alias : aliases) addAlias(id, instance, alias);
            if (!access.allowedIdentity(id)) throw WaProblem.denied();
            return new Identity(id, senderAlias(id, instance, aliases.get(0)), null);
        }
        UUID id = identities.iterator().next();
        // Same order as claim: actor before its new queue entry. Also serialize additions of an alias type.
        jdbc.query("SELECT id FROM wa.identity WHERE id=? FOR UPDATE", (rs, i) -> rs.getObject(1, UUID.class), id);
        access.revalidate(grant);
        for (var alias : aliases) {
            Boolean different = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM wa.identity_alias
                  WHERE identity_id=? AND provider='EVOLUTION' AND instance_id=? AND alias_type=? AND alias_value<>?)
                """, Boolean.class, id, instance, alias.type(), alias.value());
            if (Boolean.TRUE.equals(different)) return new Identity(null, null, "ALIAS_CONFLICT");
        }
        if (!access.allowedIdentity(id)) return new Identity(id, null, "IDENTITY_NOT_ALLOWED");
        for (var alias : aliases) addAlias(id, instance, alias);
        return new Identity(id, senderAlias(id, instance, aliases.get(0)), null);
    }
    private List<UUID> owners(String instance, WaEvolutionNormalizer.Alias alias) {
        return jdbc.query("""
            SELECT identity_id FROM wa.identity_alias
              WHERE provider='EVOLUTION' AND instance_id=? AND alias_type=? AND alias_value=?
            """, (rs, i) -> rs.getObject(1, UUID.class), instance, alias.type(), alias.value());
    }
    private void addAlias(UUID id, String instance, WaEvolutionNormalizer.Alias alias) {
        jdbc.update("""
            INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value,created_at)
            VALUES (?,?,'EVOLUTION',?,?,?,?) ON CONFLICT (provider,instance_id,alias_type,alias_value) DO NOTHING
            """, UUID.randomUUID(), id, instance, alias.type(), alias.value(), Timestamp.from(clock.now()));
    }
    private UUID senderAlias(UUID identity, String instance, WaEvolutionNormalizer.Alias alias) {
        return jdbc.queryForObject("""
            SELECT id FROM wa.identity_alias
              WHERE identity_id=? AND provider='EVOLUTION' AND instance_id=? AND alias_type=? AND alias_value=?
            """, UUID.class, identity, instance, alias.type(), alias.value());
    }
    private void lock(String namespace, String instance, String value, WaTransportAccess.Grant grant) {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", (rs, i) -> 0, lockKey(namespace, instance, value));
        access.revalidate(grant);
    }
    /** Also used by the private operator admission so inbound resolution cannot race an alias reservation. */
    public static long lockKey(String namespace, String instance, String value) {
        return ByteBuffer.wrap(WaCrypto.sha256(("EVOLUTION_" + namespace + "\0" + instance + "\0" + value)
            .getBytes(StandardCharsets.UTF_8))).getLong();
    }
    private ObjectNode receipt(UUID id, String status) {
        return WaJson.MAPPER.createObjectNode().put("receiptId", id.toString()).put("status", status).put("serverTime", clock.now().toString());
    }
}
