package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static com.vanrides.vanrides.whatsapp.transport.WaDeliveryQuotaTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaDeliveryQuotaFencingIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=postgres("wa_delivery_quota_fencing");
    private WaDeliveryConnectionTestSupport f;
    @BeforeEach void setup() { f=new WaDeliveryConnectionTestSupport(POSTGRES); f.connection("open"); }
    @Test void oneRemainingGlobalSlotCannotBeSpentTwiceAcrossConcurrentTransactions() throws Exception { race(false); }
    @Test void oneRemainingRecipientSlotCannotBeSpentTwiceAcrossConcurrentTransactions() throws Exception { race(true); }
    @Test void aWindowThatMovesWhileWaitingForProfileIsRecheckedBeforeCharging() throws Exception {
        var box=f.enqueue(); var item=f.lease(); history(f,box.actor(),legacy(f,"transport-test",INSTANCE),12,NOW.minusSeconds(59));
        f.h.jdbc.update("INSERT INTO wa.profile(identity_id,role,status) VALUES (?,'PASSENGER','ACTIVE')",box.actor());
        var pool=Executors.newSingleThreadExecutor(); var pending=new AtomicReference<Future<ObjectNode>>();
        try {
            f.h.transaction.executeWithoutResult(tx->{
                f.h.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE",box.actor());
                pending.set(pool.submit(()->f.authorize(item)));
                WaDeliveryRetryTestSupport.awaitLock(f.h,"SELECT identity_id FROM wa.profile%FOR SHARE"); f.h.time.advance(2);
            });
            assertThat(pending.get().get(10,TimeUnit.SECONDS).path("attemptToken").asText()).hasSize(43);
            assertThat(f.count("message_attempt")).isEqualTo(13);
        } finally { pool.shutdownNow(); }
    }
    private void race(boolean recipient) throws Exception {
        var one=f.enqueue(); var two=recipient?f.enqueue(one.actor()):f.enqueue();
        var leases=claim(f,2).path("items"); ObjectNode first=null,second=null;
        for(var lease:leases) if(lease.path("outboxId").asText().equals(one.id().toString())) first=(ObjectNode)lease; else second=(ObjectNode)lease;
        assertThat(first).isNotNull(); assertThat(second).isNotNull();
        history(f,recipient?one.actor():null,legacy(f,"transport-test",INSTANCE),recipient?11:29,NOW);
        var firstLease=first; var secondLease=second;
        var pool=Executors.newSingleThreadExecutor(); var pending=new AtomicReference<Future<WaProblem.Code>>();
        try {
            f.h.transaction.executeWithoutResult(tx->{
                var reservation=f.authorize(firstLease); f.submitted(reservation,"synthetic_final_quota_slot");
                pending.set(pool.submit(()->f.outbox.authorize(f.worker,WaJson.uuid(secondLease,"outboxId"),json("{}").put("leaseToken",secondLease.path("leaseToken").asText())).error()));
                WaDeliveryRetryTestSupport.awaitLock(f.h,"%pg_advisory_xact_lock%");
            });
            assertThat(pending.get().get(10,TimeUnit.SECONDS)).isEqualTo(WaProblem.Code.RATE_LIMITED);
            assertThat(f.count("message_attempt")).isEqualTo(recipient?12:30);
            assertThat(f.state(two.id())).isEqualTo("PENDING");
            assertThat(f.h.jdbc.queryForObject("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?",Integer.class,two.id())).isZero();
        } finally { pool.shutdownNow(); }
    }
}
