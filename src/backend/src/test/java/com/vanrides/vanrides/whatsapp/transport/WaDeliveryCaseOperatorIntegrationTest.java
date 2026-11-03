package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.operator.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static com.vanrides.vanrides.whatsapp.transport.WaDeliveryRecoveryTestSupport.unknown;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaDeliveryCaseOperatorIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_delivery_case_operator");
    private WaDeliveryConnectionTestSupport f;
    private WaDeliveryRecoveryTestSupport health;
    private WaOperatorService operator;
    @BeforeEach void setup() {
        f = new WaDeliveryConnectionTestSupport(POSTGRES); f.connection("open"); health = new WaDeliveryRecoveryTestSupport(f.h);
        var mode = new WaOperatorMode(new StaticApplicationContext(), new MockEnvironment().withProperty("spring.main.web-application-type", "none"));
        operator = f.h.proxy(new WaOperatorService(f.h.jdbc, f.h.settings, f.h.crypto, f.h.clock, mode,
            new WaOperatorFixtures(f.h.jdbc, f.h.crypto), new WaOperatorReset(f.h.jdbc), new WaOperatorRevocation(f.h.jdbc)));
    }
    @Test void listingIsAuditedMetadataOnlyAndShowsLateAttemptProgressWithoutRawProviderId() {
        var box = f.enqueue(); var reservation = f.authorize(f.lease()); f.h.time.advance(301); health.tick(100, null);
        UUID cohort = cohort(box.actor()); var request = request(cohort, 100);
        var before = execute(request); var item = before.path("cases").get(0);
        assertThat(item.path("attemptState").asText()).isEqualTo("UNKNOWN"); assertThat(item.path("hasExternalMessageId").asBoolean()).isFalse();
        f.submitted(reservation, "synthetic_private_provider_id"); f.callback("synthetic_private_provider_id", "READ");
        f.h.time.advance(60); health.tick(100, null);
        var result = execute(request); var entry = result.path("cases").get(0);
        assertThat(entry.path("attemptState").asText()).isEqualTo("READ"); assertThat(entry.path("hasExternalMessageId").asBoolean()).isTrue();
        assertThat(entry.path("state").asText()).isEqualTo("OPEN");
        var fields = new HashSet<String>(); entry.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).isEqualTo(Set.of("caseId", "outboxId", "state", "reasonCode", "correlationId", "createdAt", "attemptState", "hasExternalMessageId"));
        assertThat(result.toString()).doesNotContain("synthetic_private_provider_id", "content", "recipient", "identityId", "@", f.h.settings.operatorCredential());
        assertThat(f.h.jdbc.queryForObject("SELECT count(*) FROM wa.audit_event WHERE action='LIST_DELIVERY_CASES'", Integer.class)).isEqualTo(2);
    }
    @Test void listingIsPaginatedAndCannotCrossCohortInstanceOrEnvironment() {
        var one = f.enqueue(); unknown(f, f.authorize(f.lease())); UUID cohort = cohort(one.actor());
        var two = f.enqueue(); f.h.jdbc.update("UPDATE wa.identity SET test_cohort_id=? WHERE id=?", cohort, two.actor()); unknown(f, f.authorize(f.lease()));
        var foreign = f.enqueue(); unknown(f, f.authorize(f.lease()));
        f.h.time.advance(300); health.tick(100, null);
        var first = execute(request(cohort, 1)); assertThat(first.path("cases")).hasSize(1); assertThat(first.path("nextAfterCaseId").isNull()).isFalse();
        var second = execute(request(cohort, 1).put("afterCaseId", first.path("nextAfterCaseId").asText()));
        assertThat(second.path("cases")).hasSize(1); assertThat(second.path("nextAfterCaseId").isNull()).isTrue();
        assertThat(second.path("cases").get(0).path("caseId")).isNotEqualTo(first.path("cases").get(0).path("caseId"));
        assertThat(execute(request(cohort, 100).put("allowedInstanceId", "synthetic-other")).path("cases")).isEmpty();
        assertThat(execute(request(cohort(foreign.actor()), 100)).path("cases")).hasSize(1);
        assertThatThrownBy(() -> execute(request(cohort, 100).put("environment", "other-test"))).isInstanceOf(WaOperatorFailure.class);
    }
    @Test void revokedOperatorCannotReadCaseMetadataOrAddAnAuditRecord() {
        var box = f.enqueue(); unknown(f, f.authorize(f.lease())); f.h.time.advance(300); health.tick(100, null);
        var request = request(cohort(box.actor()), 100); execute(request);
        f.h.jdbc.update("UPDATE wa.service_credential SET revoked_at=? WHERE principal='OPERATOR'", Timestamp.from(f.h.clock.now()));
        assertThatThrownBy(() -> execute(request)).isInstanceOf(WaOperatorFailure.class);
        assertThat(f.h.jdbc.queryForObject("SELECT count(*) FROM wa.audit_event WHERE action='LIST_DELIVERY_CASES'", Integer.class)).isEqualTo(1);
    }
    @Test void outputFailureRollsBackTheReadAuditAndNeverPublishesAnUncommittedResult() {
        var box = f.enqueue(); unknown(f, f.authorize(f.lease())); f.h.time.advance(300); health.tick(100, null);
        var request = request(cohort(box.actor()), 100);
        assertThatThrownBy(() -> operator.execute(parse(request), result -> { throw new IllegalStateException("synthetic writer failure"); }))
            .isInstanceOf(IllegalStateException.class);
        assertThat(f.h.jdbc.queryForObject("SELECT count(*) FROM wa.audit_event WHERE action='LIST_DELIVERY_CASES'", Integer.class)).isZero();
        assertThat(f.count("support_case")).isEqualTo(1);
    }
    @Test void resetOwningIdentityLetsHealthDeferWithoutWaitingOnItsOutbox() throws Exception {
        var box = f.enqueue(); unknown(f, f.authorize(f.lease())); UUID cohort = cohort(box.actor()); f.h.time.advance(300);
        var pool = Executors.newSingleThreadExecutor();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                holdResetScope(cohort);
                var pending = pool.submit(() -> health.tick(100, null));
                try { assertThat(pending.get(10, TimeUnit.SECONDS).path("processed").asInt()).isEqualTo(1); }
                catch (Exception failure) { throw new AssertionError("Health must not wait on reset identity", failure); }
                assertThat(f.state(box.id())).isEqualTo("UNKNOWN"); assertThat(f.count("support_case")).isZero();
                execute(reset(cohort));
            });
            assertThat(f.count("message_outbox")).isZero(); assertThat(f.count("support_case")).isZero();
        } finally { pool.shutdownNow(); }
    }
    @Test void lateResultRollsBackWhenResetOwnsIdentityInsteadOfBypassingExpiry() throws Exception {
        var box = f.enqueue(); var reservation = f.authorize(f.lease()); UUID cohort = cohort(box.actor());
        f.h.time.advance(11); health.tick(100, null); f.h.time.advance(289);
        var pool = Executors.newSingleThreadExecutor();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                holdResetScope(cohort);
                var pending = pool.submit(() -> {
                    try { f.submitted(reservation, "synthetic_no_expiry_bypass"); return "COMMITTED"; }
                    catch (com.vanrides.vanrides.whatsapp.api.WaProblem error) { return error.code().name(); }
                });
                try { assertThat(pending.get(10, TimeUnit.SECONDS)).isEqualTo("DEPENDENCY_UNAVAILABLE"); }
                catch (Exception failure) { throw new AssertionError("Late result must fail closed without waiting", failure); }
                assertThat(f.state(box.id())).isEqualTo("UNKNOWN"); assertThat(f.count("support_case")).isZero();
                assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.message_attempt", String.class)).isEqualTo("UNKNOWN");
                assertThat(f.h.jdbc.queryForObject("SELECT result_request_hash FROM wa.message_attempt", String.class)).isNull();
                execute(reset(cohort));
            });
            assertThat(f.count("message_outbox")).isZero();
        } finally { pool.shutdownNow(); }
    }
    @Test void healthOwningIdentityAndOutboxCommitsBeforeTheOfficialResetContinues() throws Exception {
        var box = f.enqueue(); unknown(f, f.authorize(f.lease())); UUID cohort = cohort(box.actor()); f.h.time.advance(300);
        var pool = Executors.newSingleThreadExecutor(); var pending = new AtomicReference<Future<ObjectNode>>();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                health.tick(100, null); assertThat(f.state(box.id())).isEqualTo("EXPIRED");
                pending.set(pool.submit(() -> execute(reset(cohort))));
                WaDeliveryRetryTestSupport.awaitLock(f.h, "SELECT id FROM wa.identity WHERE test_cohort_id%FOR UPDATE%");
            });
            assertThat(pending.get().get(10, TimeUnit.SECONDS).path("identitiesDeleted").asInt()).isEqualTo(1);
            assertThat(f.count("message_outbox")).isZero(); assertThat(f.count("support_case")).isZero();
        } finally { pool.shutdownNow(); }
    }
    private void holdResetScope(UUID cohort) {
        f.h.jdbc.queryForList("SELECT id FROM wa.test_cohort WHERE id=? FOR UPDATE", cohort);
        f.h.jdbc.queryForList("SELECT id FROM wa.identity WHERE test_cohort_id=? ORDER BY id FOR UPDATE", cohort);
    }
    private ObjectNode reset(UUID cohort) {
        var request = request(cohort, 1).put("operation", "reset-test-data");
        request.remove(java.util.List.of("allowedInstanceId", "limit")); return request;
    }
    private UUID cohort(UUID actor) { return f.h.jdbc.queryForObject("SELECT test_cohort_id FROM wa.identity WHERE id=?", UUID.class, actor); }
    private ObjectNode request(UUID cohort, int limit) {
        return WaJson.object("{}").put("operation", "list-delivery-cases").put("environment", "transport-test")
            .put("operatorCredential", f.h.settings.operatorCredential()).put("batchId", cohort.toString())
            .put("allowedInstanceId", INSTANCE).put("limit", limit);
    }
    private WaOperatorRequest parse(ObjectNode request) { return WaOperatorRequest.parse(request.toString().getBytes(StandardCharsets.UTF_8)); }
    private ObjectNode execute(ObjectNode request) {
        var result = new java.util.concurrent.atomic.AtomicReference<ObjectNode>(); operator.execute(parse(request), result::set); return result.get();
    }
}
