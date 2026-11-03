package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Server-selected recipients only. Save after the command receipt, in its transaction. */
public final class WaPassengerResponses {
    static final String POLICY = "passenger-setup-v1";
    static final String COMMANDS = "'ADVANCE_PASSENGER_SETUP','REQUEST_LINK','APPROVE_LINK','CLOSE_LINK','PROPOSE_PICKUP','APPROVE_PICKUP','SET_PREFERENCE'";
    static final String ELIGIBLE = """
        (o.policy_version='passenger-setup-v1' AND r.command_type IN (
          'ADVANCE_PASSENGER_SETUP','REQUEST_LINK','APPROVE_LINK','CLOSE_LINK','PROPOSE_PICKUP','APPROVE_PICKUP','SET_PREFERENCE')
        AND EXISTS(SELECT 1 FROM wa.passenger_response_route pr
          WHERE pr.outbox_id=o.id AND pr.command_id=r.command_id AND pr.actor_identity_id=r.identity_id
            AND pr.recipient_identity_id=o.recipient_identity_id
            AND pr.instance_id=e.instance_id
            AND EXISTS(SELECT 1 FROM wa.service_credential sc WHERE sc.id=e.lease_credential_id
              AND sc.principal='CONVERSATION_WORKER' AND sc.allowed_instance_id=e.instance_id AND sc.environment=pr.environment)
            AND ((pr.event_kind='ACTOR' AND o.response_part='passenger-actor' AND r.identity_id=o.recipient_identity_id
                  AND pr.recipient_source_event_id=r.inbox_event_id)
              OR (pr.event_kind<>'ACTOR' AND o.response_part='passenger-peer' AND r.identity_id<>o.recipient_identity_id
                  AND r.result_json->>'errorCode' IS NULL
                  AND r.command_type=CASE pr.event_kind
                    WHEN 'REQUESTED' THEN 'REQUEST_LINK' WHEN 'POINT_CHANGE' THEN 'PROPOSE_PICKUP'
                    WHEN 'APPROVED' THEN 'APPROVE_LINK' WHEN 'POINT_APPROVED' THEN 'APPROVE_PICKUP'
                    WHEN 'RECTIFY' THEN 'ADVANCE_PASSENGER_SETUP' ELSE 'CLOSE_LINK' END))))
        """;
    private static final Set<String> POINT_KINDS = Set.of("REQUESTED", "POINT_CHANGE", "APPROVED", "POINT_APPROVED");
    private WaPassengerResponses() { }

    public static void saveActor(JdbcTemplate jdbc, UUID responseId, UUID commandId, UUID actor, String text, Instant now) {
        Source source = source(jdbc, commandId, actor);
        save(jdbc, responseId, commandId, actor, text, "passenger-actor", now);
        jdbc.update("""
            INSERT INTO wa.passenger_response_route(outbox_id,command_id,actor_identity_id,recipient_identity_id,
              recipient_source_event_id,environment,instance_id,event_kind,created_at)
            VALUES (?,?,?,?,?,?,?,'ACTOR',?)
            """, responseId, commandId, actor, actor, source.event(), source.environment(), source.instance(), Timestamp.from(now));
    }

    public static void savePeer(JdbcTemplate jdbc, UUID responseId, UUID commandId, UUID actor,
                               UUID linkId, String eventKind, String text, Instant now) {
        savePeer(jdbc, responseId, commandId, actor, linkId, null, eventKind, text, now);
    }

    /** Explicit pickup selection is required whenever history contains several relevant proposals. */
    public static void savePeer(JdbcTemplate jdbc, UUID responseId, UUID commandId, UUID actor,
                               UUID linkId, UUID pickupId, String eventKind, String text, Instant now) {
        Source source = source(jdbc, commandId, actor);
        if (!commandFor(eventKind).equals(source.command()) || source.rejected()) throw WaProblem.denied();
        var links = jdbc.queryForList("SELECT driver_id,passenger_id,state,version,requested_source_event_id FROM wa.driver_passenger_link WHERE id=?", linkId);
        if (links.size() != 1) throw WaProblem.missing();
        var link = links.get(0); UUID driver = (UUID) link.get("driver_id"), passenger = (UUID) link.get("passenger_id");
        UUID recipient = recipient(actor, driver, passenger, eventKind);
        String state = (String) link.get("state");
        Map<String, Object> point = point(jdbc, linkId, pickupId, eventKind);
        if (!validState(eventKind, state, point.isEmpty() ? null : (String) point.get("state"))) throw WaProblem.denied();
        UUID preferred = recipient.equals(passenger) ? (UUID) link.get("requested_source_event_id") : null;
        UUID destination = destination(jdbc, recipient, source.instance(), preferred);
        if (Set.of("REQUESTED", "POINT_CHANGE").contains(eventKind) && text != null && text.codePointCount(0, text.length()) > 1800)
            throw WaProblem.invalid();
        save(jdbc, responseId, commandId, recipient, text, "passenger-peer", now);
        jdbc.update("""
            INSERT INTO wa.passenger_response_route(outbox_id,command_id,actor_identity_id,recipient_identity_id,
              recipient_source_event_id,environment,instance_id,event_kind,link_id,driver_id,passenger_id,
              link_version,link_state,selection_hash,pickup_id,pickup_version,pickup_state,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, responseId, commandId, actor, recipient, destination, source.environment(), source.instance(), eventKind,
            linkId, driver, passenger, link.get("version"), state, selectionHash(jdbc, linkId),
            point.get("id"), point.get("version"), point.get("state"), Timestamp.from(now));
    }

    private record Source(UUID event, String instance, String environment, String command, boolean rejected) { }
    private static Source source(JdbcTemplate jdbc, UUID command, UUID actor) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("WA_TRANSACTION_REQUIRED");
        var rows = jdbc.query("""
            SELECT e.id,e.instance_id,c.environment,r.command_type,r.result_json->>'errorCode' IS NOT NULL AS rejected
            FROM wa.command_receipt r JOIN wa.integration_inbox e ON e.id=r.inbox_event_id AND e.identity_id=r.identity_id
            JOIN wa.service_credential c ON c.id=e.lease_credential_id AND c.principal='CONVERSATION_WORKER' AND c.allowed_instance_id=e.instance_id
            JOIN wa.identity_alias a ON a.id::text=e.payload_json->>'senderAliasId' AND a.identity_id=e.identity_id
              AND a.provider='EVOLUTION' AND a.instance_id=e.instance_id AND a.alias_type IN ('PN','LID')
            WHERE r.command_id=? AND r.identity_id=? AND e.provider='EVOLUTION' AND e.category='MESSAGE'
              AND r.command_type IN (
            """ + COMMANDS + ")", (rs, i) -> new Source(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)), command, actor);
        if (rows.size() != 1) throw WaProblem.denied();
        return rows.get(0);
    }

    private static UUID destination(JdbcTemplate jdbc, UUID recipient, String instance, UUID preferred) {
        var events = jdbc.query("""
            SELECT e.id FROM wa.integration_inbox e JOIN wa.identity_alias a
              ON a.id::text=e.payload_json->>'senderAliasId' AND a.identity_id=e.identity_id
              AND a.provider=e.provider AND a.instance_id=e.instance_id AND a.alias_type IN ('PN','LID')
            WHERE e.identity_id=? AND e.provider='EVOLUTION' AND e.instance_id=? AND e.category='MESSAGE'
              AND e.state<>'SKIPPED'
            """ + (preferred == null ? " ORDER BY e.received_order DESC LIMIT 1" : " AND e.id=?"),
            (rs, i) -> rs.getObject(1, UUID.class), preferred == null ? new Object[]{recipient, instance} : new Object[]{recipient, instance, preferred});
        if (events.size() != 1) throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        return events.get(0);
    }

    private static Map<String, Object> point(JdbcTemplate jdbc, UUID link, UUID pickup, String kind) {
        if (pickup == null && !POINT_KINDS.contains(kind)) return Map.of();
        String state = Set.of("REQUESTED", "POINT_CHANGE").contains(kind) ? "PROPOSED" : "APPROVED";
        List<Map<String, Object>> rows = pickup == null
            ? jdbc.queryForList("SELECT id,version,state FROM wa.pickup_point WHERE link_id=? AND state=? AND valid_until IS NULL", link, state)
            : jdbc.queryForList("SELECT id,version,state FROM wa.pickup_point WHERE link_id=? AND id=?", link, pickup);
        if (rows.size() != 1) throw WaProblem.denied();
        return rows.get(0);
    }

    static String commandFor(String kind) {
        if (kind == null) throw WaProblem.invalid();
        return switch (kind) {
            case "REQUESTED" -> "REQUEST_LINK";
            case "POINT_CHANGE" -> "PROPOSE_PICKUP";
            case "APPROVED" -> "APPROVE_LINK";
            case "REJECTED", "CLOSED" -> "CLOSE_LINK";
            case "RECTIFY" -> "ADVANCE_PASSENGER_SETUP";
            case "POINT_APPROVED" -> "APPROVE_PICKUP";
            default -> throw WaProblem.invalid();
        };
    }
    private static UUID recipient(UUID actor, UUID driver, UUID passenger, String kind) {
        UUID expected = Set.of("REQUESTED", "POINT_CHANGE").contains(kind) ? passenger : driver;
        if (kind.equals("CLOSED")) {
            if (actor.equals(driver)) return passenger;
            if (actor.equals(passenger)) return driver;
        } else if (actor.equals(expected)) return expected.equals(driver) ? passenger : driver;
        throw WaProblem.denied();
    }
    static boolean validState(String kind, String link, String point) {
        return switch (kind) {
            case "REQUESTED" -> link.equals("REQUESTED") && "PROPOSED".equals(point);
            case "POINT_CHANGE" -> Set.of("REQUESTED", "APPROVED").contains(link) && "PROPOSED".equals(point);
            case "APPROVED", "POINT_APPROVED" -> link.equals("APPROVED") && "APPROVED".equals(point);
            case "REJECTED" -> link.equals("REJECTED");
            case "RECTIFY" -> Set.of("REQUESTED", "APPROVED").contains(link);
            case "CLOSED" -> link.equals("CLOSED");
            default -> false;
        };
    }
    static String selectionHash(JdbcTemplate jdbc, UUID link) {
        // Aggregate versions also change when another passenger joins or the order changes.
        // Delivery binds the admitted schedule/content, not a new mutation confirmation.
        String selection = jdbc.queryForObject("""
            SELECT COALESCE(jsonb_agg(jsonb_build_array(s.routine_id,s.weekdays,r.name,r.direction,r.local_time::text,
                r.timezone,r.weekdays,r.state)
              ORDER BY s.routine_id),'[]'::jsonb)::text
            FROM wa.passenger_link_routine s JOIN wa.routine r ON r.id=s.routine_id AND r.driver_id=s.driver_id
            WHERE s.link_id=?
            """, String.class, link);
        return WaCrypto.digest(selection);
    }
    private static void save(JdbcTemplate jdbc, UUID id, UUID command, UUID recipient, String text, String part, Instant now) {
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > 2000) throw WaProblem.invalid();
        jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,command_id,response_part,policy_version,
              payload_kind,payload_json,not_before,expires_at,created_at)
            VALUES (?,?,'RESPONSE',?,?,'passenger-setup-v1','TEXT',?::jsonb,?,?,?)
            """, id, recipient, command, part, WaJson.write(Map.of("type", "TEXT", "text", text, "referenceTime", now.toString())),
            Timestamp.from(now), Timestamp.from(now.plusSeconds(300)), Timestamp.from(now));
    }
}
