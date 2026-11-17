package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.tracking.WaTrackingService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** In-memory secrets never enter the public command receipt or the plain outbox payload. */
final class WaRunStartOffers {
    record Page(UUID box,String text,ObjectNode packet) { }
    private final WaTrackingService tracking; private final WaSettings settings;
    WaRunStartOffers(WaTrackingService tracking,WaSettings settings) { this.tracking=tracking; this.settings=settings; }
    List<Page> create(UUID driver,ArrayNode choices,UUID firstBox) {
        // Check the complete delivered text, including every URL, before issuing or revoking links.
        for(int offset=0;offset<choices.size();offset+=3) {
            StringBuilder text=new StringBuilder(WaRunTexts.offerHeader(choices.size(),offset+1,Math.min(offset+3,choices.size())));
            for(int i=offset;i<Math.min(offset+3,choices.size());i++) {
                String link="Abrir viagem: "+settings.startLinkBaseUrl()+"/motorista/saida#v1."+new UUID(0,0)+"."
                    +choices.get(i).path("version").asLong()+"."+"X".repeat(43);
                text.append("\n\n").append(choices.get(i).path("summary").asText()).append("\n").append(link);
            }
            if(text.codePointCount(0,text.length())>2000) throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        }
        ArrayNode intents=tracking.requestStartOffer(driver,choices); var pages=new ArrayList<Page>();
        for(int offset=0;offset<choices.size();offset+=3) {
            String header=WaRunTexts.offerHeader(choices.size(),offset+1,Math.min(offset+3,choices.size()));
            ObjectNode packet=WaJson.object("{}").put("v",2).put("offerId",intents.get(0).path("offerId").asText())
                .put("expiresAt",intents.get(0).path("expiresAt").asText()).put("header",header);
            ArrayNode options=packet.putArray("options"); StringBuilder plain=new StringBuilder(header);
            for(int i=offset;i<Math.min(offset+3,choices.size());i++) {
                var intent=intents.get(i); String summary=choices.get(i).path("summary").asText();
                options.add(WaJson.object("{}").put("intentId",intent.path("intentId").asText()).put("text",summary)
                    .put("fragment","#v1."+intent.path("intentId").asText()+"."+intent.path("expectedVersion").asLong()+"."+intent.path("intentToken").asText()));
                plain.append("\n\n").append(summary);
            }
            pages.add(new Page(offset==0?firstBox:UUID.randomUUID(),plain.toString(),packet));
        }
        return List.copyOf(pages);
    }
}
