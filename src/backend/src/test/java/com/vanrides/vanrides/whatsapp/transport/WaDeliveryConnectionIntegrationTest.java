package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.sql.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaDeliveryConnectionIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_delivery_connection");
    private WaDeliveryConnectionTestSupport f;
    @BeforeEach void setup() { f = new WaDeliveryConnectionTestSupport(POSTGRES); }

    @Test void unknownConnectionDoesNotLeaseOrExposeContentAndOpenAdmitsIt() {
        var box = f.enqueue();
        var unknown = f.claim();
        assertThat(unknown.size()).isEqualTo(2);
        assertThat(unknown.path("items")).isEmpty();
        assertThat(f.state(box.id())).isEqualTo("PENDING");
        assertThat(f.count("message_attempt")).isZero();
        f.connection("open");
        assertThat(f.authorize(f.lease()).path("content").path("text").asText()).isNotBlank();
    }
    @Test void closeBetweenClaimAndAuthorizeDoesNotCreateAFailedAttempt() {
        var box = f.enqueue(); f.connection("open"); var lease = f.lease();
        f.h.time.advance(1); f.connection("close");
        assertThatThrownBy(() -> f.authorize(lease)).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.DEPENDENCY_UNAVAILABLE));
        assertThat(f.state(box.id())).isEqualTo("LEASED");
        assertThat(f.count("message_attempt")).isZero();
        assertThat(f.claim().path("items")).isEmpty();
    }
    @Test void staleAndDuplicateOpenCannotReopenCloseAfterServiceRestart() {
        f.enqueue(); f.connection("open"); f.h.time.advance(10); f.connection("close");
        f.h.connection(f.channel, "open", NOW.plusSeconds(5));
        assertThat(f.h.connection(f.channel, "open", NOW).path("status").asText()).isEqualTo("DUPLICATE");
        assertThat(f.connectionState()).isEqualTo("SUSPENDED");
        assertThat(f.service().claim(f.worker, json("{\"batchLimit\":1,\"leaseSeconds\":60}")).path("items")).isEmpty();
    }
    @Test void equalTimestampAlwaysPrefersSuspensionInBothOrders() {
        f.connection("open"); f.connection("close");
        assertThat(f.connectionState()).isEqualTo("SUSPENDED");
        var other = f.h.principal(WaPrincipal.Kind.CHANNEL, "synthetic-other");
        f.h.connection(other, "connecting", NOW); f.h.connection(other, "open", NOW);
        assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.delivery_connection WHERE instance_id=?",
            String.class, other.instanceId())).isEqualTo("SUSPENDED");
    }
    @Test void onlyAuthenticatedMatchingEnvironmentAndInstanceCanAdmitDelivery() {
        f.enqueue();
        var other = f.h.principal(WaPrincipal.Kind.CHANNEL, "synthetic-other");
        f.h.connection(other, "open", NOW);
        var configuration = settings(true, true, "other-test");
        var foreign = f.h.principal(WaPrincipal.Kind.CHANNEL, INSTANCE, "other-test");
        f.h.connection(foreign, "open", NOW, configuration);
        assertThat(f.claim().path("items")).isEmpty();
        assertThatThrownBy(() -> f.h.connection(foreign, "close", NOW)).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL));
        assertThatThrownBy(() -> f.h.connection(f.worker, "open", NOW)).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED));
        f.h.time.advance(1); f.connection("open");
        assertThat(f.claim().path("items")).hasSize(1);
        assertThat(f.h.jdbc.queryForObject("SELECT channel_credential_id FROM wa.delivery_connection WHERE environment=? AND instance_id=?",
            java.util.UUID.class, "transport-test", INSTANCE)).isEqualTo(f.channel.credentialId());
    }
    @Test void resultAndMonotonicCallbacksRemainRecordableWhileClosed() {
        var box = f.enqueue(); f.connection("open"); var reservation = f.authorize(f.lease());
        f.h.time.advance(1); f.connection("close");
        f.callback("synthetic_closed_delivery", "DELIVERY_ACK");
        assertThat(f.submitted(reservation, "synthetic_closed_delivery").path("state").asText()).isEqualTo("DELIVERED");
        f.callback("synthetic_closed_delivery", "READ"); f.callback("synthetic_closed_delivery", "SERVER_ACK");
        assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.state(box.id())).isEqualTo("READ");
        assertThat(f.count("message_attempt")).isEqualTo(1);
    }
    @Test void reconnectExpiresBacklogBeforeLeasingAnyStillEligibleItem() {
        var expired = f.enqueue(); f.connection("close"); f.h.time.advance(301);
        var current = f.enqueue(); f.connection("open"); var response = f.claim();
        assertThat(f.state(expired.id())).isEqualTo("EXPIRED");
        assertThat(response.path("items")).hasSize(1);
        assertThat(response.path("items").get(0).path("outboxId").asText()).isEqualTo(current.id().toString());
        assertThat(f.count("message_attempt")).isZero();
    }
    @Test void futureOpenIsBoundedAndCannotOutrankARealClose() {
        assertThatThrownBy(() -> f.h.connection(f.channel, "open", NOW.plusSeconds(31)))
            .isInstanceOfSatisfying(WaProblem.class, error -> assertThat(error.code()).isEqualTo(WaProblem.Code.INVALID_PAYLOAD));
        assertThat(f.count("delivery_connection")).isZero();
        assertThat(f.count("integration_inbox")).isZero();
        f.h.connection(f.channel, "open", NOW.plusSeconds(30));
        assertThat(f.h.jdbc.queryForObject("SELECT ordering_at FROM wa.delivery_connection", Timestamp.class)).isEqualTo(Timestamp.from(NOW));
        f.connection("close");
        assertThat(f.connectionState()).isEqualTo("SUSPENDED");
    }
    @Test void revokedExpiredOrDeletedSourceCredentialCannotSustainAnOpenProjection() {
        f.enqueue(); f.connection("open");
        f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=? WHERE id=?", Timestamp.from(NOW.plusSeconds(2)), f.channel.credentialId());
        f.h.time.advance(3); assertThat(f.claim().path("items")).isEmpty();
        var replacement = f.h.principal(WaPrincipal.Kind.CHANNEL);
        f.h.connection(replacement, "open", f.h.clock.now()); var lease = f.lease();
        f.h.jdbc.update("UPDATE wa.service_credential SET revoked_at=? WHERE id=?", Timestamp.from(f.h.clock.now()), replacement.credentialId());
        assertThatThrownBy(() -> f.authorize(lease)).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.DEPENDENCY_UNAVAILABLE));
        f.h.jdbc.update("DELETE FROM wa.service_credential WHERE id=?", replacement.credentialId());
        assertThat(f.count("delivery_connection")).isZero();
    }
    @Test void migrationAddsNoImplicitOpenAndDatabasePinsTheSourceCredentialScope() {
        assertThat(f.h.jdbc.queryForObject("SELECT version FROM public.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1", String.class)).isEqualTo("38");
        assertThat(f.count("delivery_connection")).isZero(); f.connection("open");
        assertThatThrownBy(() -> f.h.jdbc.update("UPDATE wa.delivery_connection SET environment='other-test'"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> f.h.jdbc.update("UPDATE wa.delivery_connection SET channel_principal='DELIVERY_WORKER'"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(f.connectionState()).isEqualTo("OPEN");
    }
    @Test void reservationCannotOutliveTheChannelCredentialSupportingOpen() {
        f.enqueue(); f.connection("open");
        f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=? WHERE id=?", Timestamp.from(NOW.plusSeconds(2)), f.channel.credentialId());
        var reservation = f.authorize(f.lease());
        assertThat(reservation.path("submitBefore").asText()).isEqualTo(NOW.plusSeconds(2).toString());
    }
}
