package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.tracking.WaVehicleLocation;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Selection is interpreted by the API from its own menu, not from a model-selected run. */
final class WaLocationConversation {
    record Result(WaDecision decision,String text,UUID run,boolean location) { }
    private final JdbcTemplate jdbc; private final WaClock clock; private final WaVehicleLocation locations;
    WaLocationConversation(JdbcTemplate jdbc,WaClock clock,WaSettings settings) { this.jdbc=jdbc; this.clock=clock; locations=new WaVehicleLocation(jdbc,clock,settings); }
    boolean offer(WaProfileState actor,WaInboxAccess.Context event) {
        if(!Set.of("PASSENGER","DRIVER").contains(actor.role()) || actor.role().equals("DRIVER") && !actor.driverEnabled()
            || !actor.status().equals("ACTIVE") || !"wa-mvp-v1".equals(actor.noticeVersion())
            || !event.normalizedKind().equals("TEXT")) return false;
        String text=text(event);
        return entry(text) || WaConversationOwnership.owner(jdbc,actor.identityId()).equals("LOCATION")
            && (text.matches("[1-8]") || Set.of("proxima","anterior","abandonar").contains(text));
    }
    Result execute(WaProfileState actor,WaInboxAccess.Context event) {
        var live=locations.runs(actor.identityId(),true); var draft=draft(actor.identityId()); String text=text(event);
        if(!entry(text) && !WaConversationOwnership.currentDraftReply(jdbc,event)) {
            save(actor,event,WaJson.object("{}"));
            return new Result(WaDecision.ok(Map.of("changed",true)),"Essa resposta cita um menu de localização anterior. Envie LOCALIZAÇÃO para escolher novamente.",null,false);
        }
        UUID selected=null;
        if(entry(text) && live.size()==1) selected=(UUID)live.get(0).get("id");
        else if(!entry(text) && text.matches("[1-8]")) {
            int i=draft.path("page").asInt()*8+Integer.parseInt(text)-1;
            if(i<draft.path("runs").size()) {
                String ref=draft.path("runs").get(i).asText();
                selected=live.stream().map(row->(UUID)row.get("id")).filter(id->id.toString().equals(ref)).findFirst().orElse(null);
            }
        }
        if(selected!=null || live.isEmpty() || text.equals("abandonar")) {
            save(actor,event,WaJson.object("{}"));
            return new Result(WaDecision.ok(Map.of("changed",true)),WaVehicleLocation.UNAVAILABLE,selected,!text.equals("abandonar"));
        }
        if(entry(text) || draft.isEmpty()) {
            draft=WaJson.object("{}").put("page",0).put("expiresAt",clock.now().plusSeconds(300).toString());
            var ids=draft.putArray("runs"); live.stream().limit(100).forEach(row->ids.add(row.get("id").toString()));
        } else if(Set.of("proxima","anterior").contains(text)) draft.put("page",WaAttendanceSelection.page(menuDraft(draft),text));
        save(actor,event,draft);
        StringBuilder message=new StringBuilder("Escolha o veículo da ocorrência. ");
        int page=draft.path("page").asInt();
        for(int i=page*8;i<Math.min(page*8+8,draft.path("runs").size());i++) {
            String id=draft.path("runs").get(i).asText(); var row=live.stream().filter(r->r.get("id").toString().equals(id)).findFirst().orElse(null);
            message.append(i-page*8+1).append(" — ").append(row==null?"opção atualizada":row.get("name")+" "+row.get("direction")+" "+row.get("local_date")).append(". ");
        }
        if(page*8+8<draft.path("runs").size()) message.append("Envie PRÓXIMA. ");
        if(page>0) message.append("Envie ANTERIOR. ");
        return new Result(WaDecision.ok(Map.of("changed",true)),message.append("Responda com o número ou ABANDONAR.").toString(),null,false);
    }
    void attach(UUID box,Result result) {
        if(result.location()) jdbc.update("UPDATE wa.message_outbox SET purpose='PIN',policy_version=?,response_part='driver-location',location_requested=true,run_id=?,expires_at=created_at+INTERVAL '300 seconds' WHERE id=?",
            WaVehicleLocation.POLICY,result.run(),box);
        else jdbc.update("UPDATE wa.message_outbox SET policy_version=?,response_part='driver-location' WHERE id=?",WaVehicleLocation.POLICY,box);
    }
    private ObjectNode draft(UUID actor) {
        var rows=jdbc.query("SELECT context_json->'locationDraft' FROM wa.conversation_state WHERE identity_id=?",(rs,n)->rs.getString(1),actor);
        if(rows.isEmpty() || rows.get(0)==null) return WaJson.object("{}");
        var d=WaJson.object(rows.get(0));
        try { if(Instant.parse(d.path("expiresAt").asText()).isAfter(clock.now())) return d; } catch(RuntimeException ignored) { }
        return WaJson.object("{}");
    }
    private void save(WaProfileState actor,WaInboxAccess.Context event,ObjectNode draft) {
        var rows=jdbc.query("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=? FOR UPDATE",(rs,n)->WaJson.object(rs.getString(1)),actor.identityId());
        ObjectNode context=rows.isEmpty()?WaJson.object("{}"):rows.get(0);
        if(draft.isEmpty()) context.remove("locationDraft"); else context.set("locationDraft",draft);
        context.put("conversationOwner",draft.isEmpty()?"READY":"LOCATION");
        if(!draft.isEmpty()) WaConversationOwnership.prompt(context,"LOCATION",event.eventId());
        jdbc.update("""
            INSERT INTO wa.conversation_state(identity_id,stage,context_json,last_event_id,updated_at) VALUES (?,'READY',?::jsonb,?,?)
            ON CONFLICT(identity_id) DO UPDATE SET stage='READY',context_json=EXCLUDED.context_json,last_event_id=EXCLUDED.last_event_id,
              updated_at=EXCLUDED.updated_at,version=wa.conversation_state.version+1
            """,actor.identityId(),context.toString(),event.eventId(),Timestamp.from(clock.now()));
        jdbc.update("UPDATE wa.profile SET version=version+1 WHERE identity_id=?",actor.identityId());
    }
    private static ObjectNode menuDraft(ObjectNode draft) { var result=draft.deepCopy(); result.set("choices",draft.path("runs")); return result; }
    private static String text(WaInboxAccess.Context event) { return WaAttendanceAnswers.normalize(event.normalizedPayload().path("text").asText()).replace("?","").trim(); }
    private static boolean entry(String text) { return Set.of("onde esta","onde esta a van","onde a van esta","onde esta o veiculo","onde esta o motorista","localizacao","localizacao da van").contains(text); }
}
