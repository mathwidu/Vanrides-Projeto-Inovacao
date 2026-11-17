package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.commands.WaRunPreparation;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** A batch is issued under the driver fence; child intents remain the authorization source. */
final class WaStartOffers {
    private final JdbcTemplate jdbc; private final WaSettings settings; private final WaClock clock; private final WaCrypto crypto;
    WaStartOffers(JdbcTemplate jdbc,WaSettings settings,WaClock clock,WaCrypto crypto) {
        this.jdbc=jdbc; this.settings=settings; this.clock=clock; this.crypto=crypto;
    }
    ArrayNode request(UUID driver,ArrayNode choices) {
        if(!settings.newStartsEnabled()) throw WaProblem.denied();
        if(settings.startLinkBaseUrl().isEmpty()) throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        if(choices.size()<2 || choices.size()>30) throw WaProblem.invalid();
        new WaTrackingAccess(jdbc,settings,clock).lockDriver(driver);
        var devices=jdbc.queryForList("""
            SELECT d.id FROM wa.driver_device d JOIN wa.driver_authorization a ON a.driver_id=d.driver_id
            JOIN wa.profile p ON p.identity_id=d.driver_id WHERE d.driver_id=? AND d.environment=?
              AND d.status='ACTIVE' AND d.generation=a.generation AND p.status='ACTIVE' AND p.driver_enabled FOR UPDATE OF d
            """,driver,settings.environment());
        if(devices.size()!=1) throw WaProblem.denied();
        var seen=new HashSet<UUID>(); var preparation=new WaRunPreparation(jdbc,clock,settings);
        // Finish every business validation before revoking old links or inserting any child.
        for(JsonNode item:choices) {
            UUID occurrence=UUID.fromString(item.path("id").asText());
            if(!seen.add(occurrence)) throw WaProblem.invalid();
            if(!WaCrypto.same(preparation.rosterDigest(driver,occurrence),item.path("rosterDigest").asText()))
                throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            Long version=jdbc.queryForObject("SELECT version FROM wa.routine_occurrence WHERE id=? AND driver_id=?",Long.class,occurrence,driver);
            if(version==null || version!=item.path("version").asLong(-1)) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        }
        jdbc.update("UPDATE wa.start_intent SET state='REVOKED' WHERE driver_id=? AND state='PENDING'",driver);
        UUID offer=UUID.randomUUID(),device=(UUID)devices.get(0).get("id"); Instant now=clock.now();
        // PostgreSQL TIMESTAMPTZ stores microseconds; round down before binding or serializing the same deadline.
        Instant until=now.plusSeconds(300).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("INSERT INTO wa.start_offer(id,driver_id,device_id,environment,created_at,expires_at) VALUES (?,?,?,?,?,?)",
            offer,driver,device,settings.environment(),ts(now),ts(until));
        ArrayNode intents=WaJson.MAPPER.createArrayNode(); int position=0;
        for(JsonNode item:choices) {
            UUID id=UUID.randomUUID(); String token=WaCrypto.randomToken(); long version=item.path("version").asLong();
            jdbc.update("""
                INSERT INTO wa.start_intent(id,driver_id,occurrence_id,device_id,token_hash,environment,intent_action,
                  expected_version,roster_digest,expires_at,created_at,offer_id,offer_position)
                VALUES (?,?,?,?,?,?,'START',?,?,?,?,?,?)
                """,id,driver,UUID.fromString(item.path("id").asText()),device,WaCrypto.digest(token),settings.environment(),
                version,item.path("rosterDigest").asText(),ts(until),ts(now),offer,++position);
            intents.add(WaJson.object("{}").put("intentId",id.toString()).put("intentToken",token)
                .put("expectedVersion",version).put("expiresAt",until.toString()).put("action","START").put("offerId",offer.toString()));
        }
        return intents;
    }
    void attach(UUID box,UUID command,UUID driver,ObjectNode packet) {
        UUID offer=WaJson.uuid(packet,"offerId"); JsonNode options=packet.path("options");
        if(options.size()<1 || options.size()>3) throw WaProblem.invalid();
        for(JsonNode item:options) {
            if(jdbc.update("""
                UPDATE wa.start_intent SET request_command_id=?,delivery_outbox_id=?
                WHERE id=? AND driver_id=? AND offer_id=? AND environment=? AND state='PENDING'
                  AND delivery_outbox_id IS NULL AND expires_at>?
                """,command,box,WaJson.uuid(item,"intentId"),driver,offer,settings.environment(),ts(clock.now()))!=1)
                throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        }
        if(jdbc.update("""
            UPDATE wa.message_outbox SET start_intent_id=?,start_payload_ciphertext=?,expires_at=LEAST(expires_at,?)
            WHERE id=? AND recipient_identity_id=? AND command_id=?
            """,WaJson.uuid(options.get(0),"intentId"),crypto.encrypt(WaStartDelivery.aad(box,command,driver),WaJson.write(packet)),
            ts(Instant.parse(packet.path("expiresAt").asText())),box,driver,command)!=1) throw WaProblem.denied();
    }
    void consumed(UUID id,UUID driver) {
        jdbc.update("""
            UPDATE wa.start_intent SET state='REVOKED' WHERE driver_id=? AND environment=? AND state='PENDING'
              AND offer_id=(SELECT offer_id FROM wa.start_intent WHERE id=? AND driver_id=?)
            """,driver,settings.environment(),id,driver);
    }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
}
