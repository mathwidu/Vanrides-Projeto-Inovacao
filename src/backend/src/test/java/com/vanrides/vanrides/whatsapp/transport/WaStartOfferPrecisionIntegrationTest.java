package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.tracking.WaStartDelivery;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class WaStartOfferPrecisionIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_start_offer_precision");

    @Test void nanosecondClockIssuesAndDeliversEveryPageWithOneExactDeadlineThatNeverExtendsTheTtl() {
        var t=new WaTrackingTestSupport(POSTGRES); var f=t.f;
        for(int i=1;i<4;i++) {
            var item=f.attendance.question(f.passenger,WaRunTestSupport.DAY,"RETURN","0"+i+":00");
            f.h.jdbc.update("UPDATE wa.routine SET stop_order_pending=false WHERE id=?",item.routine());
        }
        // Receive at the prior instant, then execute with a non-microsecond clock.
        // PostgreSQL may round inbox.not_before up; a frozen clock must not claim before it.
        UUID event=f.s.pending(f.driver,WaJson.object("{}").put("conversation","Começar viagem"),f.h.clock.now());
        Instant issued=f.h.clock.now().plusSeconds(2).plusNanos(123456789);
        assertNotEquals(0,issued.getNano()%1000); f.h.time.set(issued);
        var offered=f.execute(f.s.claim(event),true); assertNull(offered.decision().error());
        assertEquals(2,offered.decision().body().path("responseIds").size());
        Instant expected=issued.plusSeconds(300).truncatedTo(ChronoUnit.MICROS);
        assertTrue(expected.isBefore(issued.plusSeconds(300)),"Precision normalization must never extend validity");
        assertEquals(expected,f.h.jdbc.queryForObject("SELECT expires_at FROM wa.start_offer",Timestamp.class).toInstant());
        for(var row:f.h.jdbc.queryForList("SELECT expires_at FROM wa.start_intent WHERE offer_id IS NOT NULL"))
            assertEquals(expected,((Timestamp)row.get("expires_at")).toInstant());
        var remaining=new HashSet<UUID>();
        for(var id:offered.decision().body().path("responseIds")) {
            UUID box=UUID.fromString(id.asText()); remaining.add(box);
            var row=f.h.jdbc.queryForMap("SELECT command_id,start_payload_ciphertext FROM wa.message_outbox WHERE id=?",box);
            var packet=WaJson.object(f.h.crypto.decrypt(WaStartDelivery.aad(box,(UUID)row.get("command_id"),f.driver),(byte[])row.get("start_payload_ciphertext")));
            assertEquals(expected,Instant.parse(packet.path("expiresAt").asText()));
        }
        f.h.time.advance(1); // Delivery follows creation, without extending the persisted deadline.
        int pages=0,links=0; String firstLink=null;
        var pattern=Pattern.compile("/motorista/saida#v1\\.([0-9a-f-]{36})\\.([0-9]+)\\.([A-Za-z0-9_-]{43})");
        for(int batch=0;batch<10 && !remaining.isEmpty();batch++) for(var item:f.s.outboxClaim().path("items")) {
            UUID box=WaJson.uuid(item,"outboxId"); if(!remaining.remove(box)) continue;
            var authorized=f.s.outbox.authorize(f.s.delivery,box,WaJson.object("{}").put("leaseToken",item.path("leaseToken").asText())).requireSuccess();
            var matches=pattern.matcher(authorized.path("content").path("text").asText()); int pageLinks=0;
            while(matches.find()) { if(firstLink==null) firstLink=matches.group(); links++; pageLinks++; }
            assertTrue(pageLinks>0,"An exact persisted expiry must not cancel a page"); pages++;
            f.s.outbox.result(f.s.delivery,box,WaJson.object("{}").put("attemptToken",authorized.path("attemptToken").asText())
                .put("outcome","SUBMITTED").put("externalMessageId","synthetic-precision-"+UUID.randomUUID()));
        }
        assertTrue(remaining.isEmpty()); assertEquals(2,pages); assertEquals(4,links);
        assertEquals(0,f.s.number("SELECT count(*) FROM wa.operation_run"));
        var selected=pattern.matcher(firstLink); assertTrue(selected.find());
        UUID intent=UUID.fromString(selected.group(1));
        var activation=WaJson.object("{}").put("expectedVersion",Long.parseLong(selected.group(2)))
            .put("foregroundConfirmed","CONFIRMED").put("intentToken",selected.group(3));
        f.h.time.set(expected);
        assertEquals(WaProblem.Code.INTENT_EXPIRED,assertThrows(WaProblem.class,
            ()->t.tracking.activate(t.actor,intent,activation,"precision-deadline-activation")).code());
        assertEquals(0,f.s.number("SELECT count(*) FROM wa.operation_run"));
    }
}
