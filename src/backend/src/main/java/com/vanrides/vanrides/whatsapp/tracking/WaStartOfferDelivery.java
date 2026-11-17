package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.commands.WaRunPreparation;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** The persisted 1:N binding and the cipher must name exactly the same ordered options. */
final class WaStartOfferDelivery {
    private final JdbcTemplate jdbc; private final WaClock clock; private final WaSettings settings; private final WaCrypto crypto;
    WaStartOfferDelivery(JdbcTemplate jdbc,WaClock clock,WaSettings settings,WaCrypto crypto) {
        this.jdbc=jdbc; this.clock=clock; this.settings=settings; this.crypto=crypto;
    }
    boolean offered(UUID anchor) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM wa.start_intent WHERE id=? AND offer_id IS NOT NULL)",Boolean.class,anchor));
    }
    ObjectNode reveal(UUID box,UUID driver,ObjectNode content,Map<String,Object> outbox) {
        if(!settings.newStartsEnabled() || settings.startLinkBaseUrl().isEmpty() || outbox.get("start_payload_ciphertext")==null) return null;
        var rows=jdbc.queryForList("""
            SELECT i.*,g.expires_at offer_expires,d.status device_status,d.generation device_generation,a.generation current_generation
            FROM wa.start_intent i JOIN wa.start_offer g ON g.id=i.offer_id AND g.driver_id=i.driver_id
              AND g.device_id=i.device_id AND g.environment=i.environment
            JOIN wa.driver_device d ON d.id=i.device_id AND d.driver_id=i.driver_id
            JOIN wa.driver_authorization a ON a.driver_id=i.driver_id
            WHERE i.delivery_outbox_id=? AND i.driver_id=? AND i.request_command_id=? AND i.environment=?
            ORDER BY i.offer_position FOR SHARE OF i,g,d,a
            """,box,driver,outbox.get("command_id"),settings.environment());
        if(rows.isEmpty() || rows.size()>3 || !rows.get(0).get("id").equals(outbox.get("start_intent_id"))) return null;
        ObjectNode packet=WaJson.object(crypto.decrypt(WaStartDelivery.aad(box,(UUID)outbox.get("command_id"),driver),
            (byte[])outbox.get("start_payload_ciphertext")));
        WaJson.fields(packet,"v","offerId","expiresAt","header","options");
        UUID offer=WaJson.uuid(packet,"offerId"); Instant expiry=Instant.parse(packet.path("expiresAt").asText());
        JsonNode options=packet.path("options");
        if(packet.path("v").asInt()!=2 || !options.isArray() || options.size()!=rows.size() || !expiry.isAfter(clock.now())) return null;
        String header=WaJson.multilineText(packet,"header",1,400); var plain=new StringBuilder(header);
        var fragments=new ArrayList<String>(); int pending=0;
        for(int n=0;n<rows.size();n++) {
            var row=rows.get(n); JsonNode item=options.get(n);
            WaJson.fields(item,"intentId","text","fragment");
            if(!offer.equals(row.get("offer_id")) || !WaJson.uuid(item,"intentId").equals(row.get("id"))
                    || !expiry.equals(((Timestamp)row.get("expires_at")).toInstant())
                    || !expiry.equals(((Timestamp)row.get("offer_expires")).toInstant())
                    || !"ACTIVE".equals(row.get("device_status")) || !row.get("device_generation").equals(row.get("current_generation"))) return null;
            String fragment=item.path("fragment").asText();
            if(!fragment.matches("#v1\\."+row.get("id")+"\\."+row.get("expected_version")+"\\.[A-Za-z0-9_-]{43}")
                    || !WaCrypto.same((String)row.get("token_hash"),WaCrypto.digest(fragment.substring(fragment.lastIndexOf('.')+1)))) return null;
            plain.append("\n\n").append(WaJson.multilineText(item,"text",1,2000)); fragments.add(fragment);
            if("PENDING".equals(row.get("state"))) pending++;
        }
        if(pending==0 || !plain.toString().equals(content.path("text").asText())) return null;
        var rendered=new StringBuilder(header); var preparation=new WaRunPreparation(jdbc,clock,settings);
        for(int n=0;n<rows.size();n++) {
            var row=rows.get(n); boolean valid="PENDING".equals(row.get("state"));
            if(valid) try {
                valid=WaCrypto.same(preparation.rosterDigestForDelivery(driver,(UUID)row.get("occurrence_id")),(String)row.get("roster_digest"));
            } catch(WaProblem changed) { valid=false; }
            rendered.append("\n\n").append(options.get(n).path("text").asText()).append("\n");
            rendered.append(valid?"Abrir viagem: "+settings.startLinkBaseUrl()+"/motorista/saida"+fragments.get(n)
                :"Esta viagem mudou. Envie Começar viagem para atualizar.");
        }
        if(!expiry.isAfter(clock.now())) return null;
        ObjectNode result=content.deepCopy().put("text",rendered.toString());
        WaJson.multilineText(result,"text",1,2000); return result;
    }
}
