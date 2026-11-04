package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.commands.WaCommandService;
import com.vanrides.vanrides.whatsapp.commands.WaCommandStore;
import com.vanrides.vanrides.whatsapp.commands.WaInboxAccess;
import com.vanrides.vanrides.whatsapp.commands.WaOnboardingCommands;
import com.vanrides.vanrides.whatsapp.pairing.WaPairingService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.testcontainers.containers.PostgreSQLContainer;

import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Inbox fixtures and PostgreSQL coordination, composed with the shared transport harness. */
final class WaInboxTestSupport {
    final WaTransportTestSupport transport;
    final WaInboxService service;
    final WaPrincipal worker;
    private final PostgreSQLContainer<?> postgres;

    WaInboxTestSupport(PostgreSQLContainer<?> postgres) {
        this(postgres, new WaTransportTestSupport(postgres));
    }

    WaInboxTestSupport(PostgreSQLContainer<?> postgres, WaTransportTestSupport transport) {
        this.postgres = postgres;
        this.transport = transport;
        service = transport.proxy(new WaInboxService(transport.jdbc, transport.access, transport.settings, transport.clock));
        worker = transport.principal(WaPrincipal.Kind.CONVERSATION_WORKER);
    }

    WaCommandService commands() {
        return transport.proxy(new WaCommandService(transport.jdbc,
            new WaInboxAccess(transport.jdbc, transport.settings, transport.clock),
            new WaCommandStore(transport.jdbc, transport.clock), new WaOnboardingCommands(transport.jdbc, transport.crypto, transport.clock),
            transport.proxy(new WaPairingService(transport.jdbc, transport.settings, transport.clock, transport.crypto)),
            transport.settings, transport.clock, transport.crypto));
    }

    ObjectNode claim(WaPrincipal principal, int limit, int seconds) {
        return service.claim(principal, json("{\"batchLimit\":" + limit + ",\"leaseSeconds\":" + seconds + "}"));
    }

    ObjectNode result(WaPrincipal principal, ObjectNode item, String request) {
        return service.result(principal, UUID.fromString(item.path("inboxEventId").asText()), item.path("inboxLease").asText(), json(request));
    }

    static ObjectNode first(ObjectNode response) {
        assertThat(response.path("items")).isNotEmpty();
        return (ObjectNode) response.path("items").get(0);
    }

    static ObjectNode itemFor(ObjectNode response, UUID event) {
        for (var item : response.path("items")) if (item.path("inboxEventId").asText().equals(event.toString())) return (ObjectNode) item;
        throw new AssertionError("Expected synthetic event was not claimed");
    }

    String state(UUID id) { return transport.jdbc.queryForObject("SELECT state FROM wa.integration_inbox WHERE id=?", String.class, id); }
    long number(String sql, Object... args) { return transport.jdbc.queryForObject(sql, Long.class, args); }

    void expireCredentialIn(int seconds) {
        transport.jdbc.update("UPDATE wa.service_credential SET expires_at=? WHERE id=?",
            Timestamp.from(transport.clock.now().plusSeconds(seconds)), worker.credentialId());
    }

    void profile(UUID actor, String status) {
        transport.jdbc.update("INSERT INTO wa.profile(identity_id,role,status) VALUES (?,'PASSENGER',?)", actor, status);
    }

    void receipt(UUID actor, UUID event, String response) {
        transport.jdbc.update("""
            INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,request_hash,result_json,created_at,expires_at)
            VALUES (?,?,?,?, 'START_ONBOARDING',?,?::jsonb,?,?)
            """, UUID.randomUUID(), actor, event, "synthetic-receipt-" + event, WaCrypto.digest("synthetic-request"), response,
            Timestamp.from(transport.clock.now()), Timestamp.from(transport.clock.now().plusSeconds(86400)));
    }

    static void problem(WaProblem.Code code, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(WaProblem.class)
            .satisfies(error -> assertThat(((WaProblem) error).code()).isEqualTo(code));
    }

    ObjectNode whileHolding(String sql, UUID key, Supplier<ObjectNode> action) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection owner = connection()) {
            owner.setAutoCommit(false);
            lock(owner, sql, key);
            try { return executor.submit(action::get).get(5, TimeUnit.SECONDS); }
            finally { owner.rollback(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
        } finally { executor.shutdownNow(); }
    }

    Outcome whileWaiting(String sql, UUID key, Supplier<ObjectNode> action, Runnable beforeRelease) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection owner = connection()) {
            owner.setAutoCommit(false);
            lock(owner, sql, key);
            long pid;
            try (var statement = owner.createStatement(); var rows = statement.executeQuery("SELECT pg_backend_pid()")) {
                rows.next(); pid = rows.getLong(1);
            }
            try {
                Future<Outcome> waiting = executor.submit(() -> capture(action));
                awaitDatabaseWait(pid);
                assertThat(waiting.isDone()).isFalse();
                beforeRelease.run(); owner.commit();
                return waiting.get(10, TimeUnit.SECONDS);
            } finally { owner.rollback(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
        } finally { executor.shutdownNow(); }
    }

    private Connection connection() throws Exception { return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); }

    private static void lock(Connection owner, String sql, UUID key) throws Exception {
        try (var statement = owner.prepareStatement(sql)) { statement.setObject(1, key); statement.executeQuery().close(); }
    }

    private void awaitDatabaseWait(long ownerPid) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (number("""
                    SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()
                      AND wait_event_type='Lock' AND ?::integer=ANY(pg_blocking_pids(pid))
                    """, ownerPid) > 0) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Inbox request did not wait for the expected PostgreSQL owner");
    }

    static List<Outcome> race(Supplier<ObjectNode> first, Supplier<ObjectNode> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            Future<Outcome> a = executor.submit(() -> simultaneous(first, ready, start));
            Future<Outcome> b = executor.submit(() -> simultaneous(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally { start.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    private static Outcome simultaneous(Supplier<ObjectNode> action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Inbox race start timed out");
        return capture(action);
    }

    private static Outcome capture(Supplier<ObjectNode> action) {
        try { return new Outcome(action.get(), null); }
        catch (WaProblem problem) { return new Outcome(null, problem.code()); }
    }

    record Outcome(ObjectNode body, WaProblem.Code failure) { }
}
