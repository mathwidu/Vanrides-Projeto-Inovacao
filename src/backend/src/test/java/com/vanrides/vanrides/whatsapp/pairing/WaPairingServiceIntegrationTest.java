package com.vanrides.vanrides.whatsapp.pairing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.vanrides.vanrides.whatsapp.pairing.WaPairingTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class WaPairingServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = postgres("vanrides_wa_pairing_service");
    private WaPairingTestSupport h;
    @BeforeEach void reset() { h = new WaPairingTestSupport(postgres); }

    @Test
    void shouldBindBeginToTheProofKeyAndPersistAnEncryptedIdempotentResult() throws Exception {
        Client first = client(); Client other = client(); ObjectNode body = beginBody(first);
        problem(WaProblem.Code.INVALID_CREDENTIAL, () -> h.service.begin(other.proof(), body, "begin-key-0001"));
        assertThat(h.number("SELECT count(*) FROM wa.pairing_challenge")).isZero();
        ObjectNode started = h.service.begin(first.proof(), body, "begin-key-0001").requireSuccess();
        sameResponse(started, h.service.begin(first.proof(), body.deepCopy(), "begin-key-0001").requireSuccess());
        assertThat(h.number("SELECT count(*) FROM wa.pairing_challenge")).isEqualTo(1);
        assertThat(h.string("SELECT code_hash FROM wa.pairing_challenge"))
                .isEqualTo(h.crypto.secretHash("pairing-code", started.path("userCode").asText()));
        assertThat(h.number("SELECT octet_length(response_ciphertext) FROM wa.operation_replay"))
                .isGreaterThan(WaJson.write(started).length());
        ObjectNode changed = body.deepCopy().put("appVersion", "synthetic-2");
        problem(WaProblem.Code.IDEMPOTENCY_CONFLICT, () -> h.service.begin(first.proof(), changed, "begin-key-0001"));
        ObjectNode extra = body.deepCopy().put("actorId", UUID.randomUUID().toString());
        problem(WaProblem.Code.INVALID_PAYLOAD, () -> h.service.begin(first.proof(), extra, "begin-key-0002"));
    }

    @Test
    void shouldPreventCrossingTwoKeysOrPkceVerifiersAndInvalidateTheOtherApproval() throws Exception {
        UUID driver = h.driver(); Approved first = h.approved(client(), driver, "begin-first-0001");
        Approved second = h.approved(client(), driver, "begin-second-0001");
        problem(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE, () -> h.service.redeem(first.client().proof(),
                redeemBody(second), "redeem-cross-0001"));
        ObjectNode wrong = redeemBody(first).put("pkceVerifier", second.client().verifier());
        assertThat(h.service.redeem(first.client().proof(), wrong, "redeem-wrong-0001").error())
                .isEqualTo(WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(h.number("SELECT failed_attempts FROM wa.pairing_challenge WHERE id=?", WaJson.uuid(second.begin(), "pairingId")))
                .isZero();
        ObjectNode session = h.redeem(first, "redeem-first-0001");
        sameResponse(session, h.redeem(first, "redeem-first-0001"));
        assertThat(h.service.redeem(second.client().proof(), redeemBody(second), "redeem-second-0001").error())
                .isEqualTo(WaProblem.Code.INTENT_EXPIRED);
        assertThat(h.number("SELECT generation FROM wa.driver_authorization WHERE driver_id=?", driver)).isEqualTo(1);
        assertThat(h.number("SELECT count(*) FROM wa.driver_device WHERE status='ACTIVE'")).isEqualTo(1);
    }

    @Test
    void shouldExposePairingStatusOnlyToItsKeyWithoutCreatingEffectsOnGet() throws Exception {
        Client client = client(); ObjectNode begun = h.service.begin(client.proof(), beginBody(client), "begin-status-0001").requireSuccess();
        UUID id = WaJson.uuid(begun, "pairingId");
        Client stranger = client();
        problem(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE, () -> h.service.status(stranger.proof(), id));
        ObjectNode status = h.service.status(client.proof(), id);
        assertThat(status.size()).isEqualTo(4); assertThat(status.path("state").asText()).isEqualTo("PENDING");
        assertThat(status.has("driverId")).isFalse(); assertThat(status.has("userCode")).isFalse();
        h.time.advance(Duration.ofMinutes(5));
        assertThat(h.service.status(client.proof(), id).path("state").asText()).isEqualTo("EXPIRED");
        assertThat(h.string("SELECT state FROM wa.pairing_challenge WHERE id=?", id)).isEqualTo("PENDING");
    }

    @Test
    void shouldCommitFiveBadPkceAttemptsWithoutCountingTheSameRetryTwice() throws Exception {
        Approved pairing = h.approved(client(), h.driver(), "begin-attempts-0001");
        ObjectNode wrong = redeemBody(pairing).put("pkceVerifier", WaCrypto.randomToken());
        assertThat(h.service.redeem(pairing.client().proof(), wrong, "bad-pkce-0001").error()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(h.service.redeem(pairing.client().proof(), wrong, "bad-pkce-0001").error()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(h.number("SELECT failed_attempts FROM wa.pairing_challenge")).isEqualTo(1);
        problem(WaProblem.Code.IDEMPOTENCY_CONFLICT, () -> h.service.redeem(pairing.client().proof(),
                redeemBody(pairing), "bad-pkce-0001"));
        for (int attempt = 2; attempt <= 5; attempt++) {
            WaDecision decision = h.service.redeem(pairing.client().proof(), wrong, "bad-pkce-000" + attempt);
            assertThat(decision.error()).isEqualTo(attempt == 5 ? WaProblem.Code.RATE_LIMITED : WaProblem.Code.INVALID_CREDENTIAL);
        }
        assertThat(h.number("SELECT failed_attempts FROM wa.pairing_challenge")).isEqualTo(5);
        assertThat(h.string("SELECT state FROM wa.pairing_challenge")).isEqualTo("REVOKED");
        assertThat(h.service.redeem(pairing.client().proof(), redeemBody(pairing), "redeem-after-0001").error())
                .isEqualTo(WaProblem.Code.INTENT_EXPIRED);
        assertThat(h.number("SELECT count(*) FROM wa.device_token")).isZero();
    }

    @Test
    void shouldExpireAtFiveMinutesAndNeverReplaceAnExpiredBeginReplay() throws Exception {
        Client client = client(); UUID driver = h.driver(); ObjectNode request = beginBody(client);
        ObjectNode begun = h.service.begin(client.proof(), request, "begin-expiry-0001").requireSuccess();
        h.time.advance(Duration.ofMinutes(5));
        WaDecision decision = h.transaction.execute(status -> h.service.confirm(driver, begun.path("userCode").asText()));
        assertThat(decision).isNotNull(); assertThat(decision.error()).isEqualTo(WaProblem.Code.INTENT_EXPIRED);
        assertThat(h.string("SELECT state FROM wa.pairing_challenge")).isEqualTo("EXPIRED");
        assertThat(h.service.redeem(client.proof(), object().put("pairingId", begun.path("pairingId").asText())
                .put("pkceVerifier", client.verifier()), "redeem-expired-pending-0001").error()).isEqualTo(WaProblem.Code.INTENT_EXPIRED);
        problem(WaProblem.Code.INTENT_EXPIRED, () -> h.service.begin(client.proof(), request, "begin-expiry-0001"));
        assertThat(h.number("SELECT count(*) FROM wa.pairing_challenge")).isEqualTo(1);
        assertThat(h.number("SELECT generation FROM wa.driver_authorization WHERE driver_id=?", driver)).isZero();
    }

    @Test
    void shouldRotateRefreshAndRecoverALostResponseWithoutExtendingItsOriginalLifetime() throws Exception {
        Instant sampled = h.time.instant();
        h.time.advance(Duration.between(sampled, sampled.truncatedTo(ChronoUnit.SECONDS).plusNanos(999_999_789)));
        Approved pairing = h.approved(client(), h.driver(), "begin-refresh-0001");
        ObjectNode status = h.service.status(pairing.client().proof(), WaJson.uuid(pairing.begin(), "pairingId"));
        assertThat(status.path("expiresAt").asText()).isEqualTo(pairing.begin().path("expiresAt").asText());
        ObjectNode first = h.redeem(pairing, "redeem-refresh-0001");
        assertThat(Instant.parse(first.path("refreshExpiresAt").asText()))
                .isEqualTo(h.time.instant().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.MICROS));
        for (String kind : new String[] { "ACCESS", "REFRESH" }) {
            String field = kind.equals("ACCESS") ? "accessExpiresAt" : "refreshExpiresAt";
            Instant stored = h.jdbc.queryForObject("SELECT expires_at FROM wa.device_token WHERE kind=?",
                    java.sql.Timestamp.class, kind).toInstant();
            assertThat(Instant.parse(first.path(field).asText())).isEqualTo(stored);
        }
        h.time.advance(Duration.ofSeconds(30));
        ObjectNode rotated = h.refresh(pairing.client(), first, "refresh-lost-0001");
        sameResponse(rotated, h.refresh(pairing.client(), first, "refresh-lost-0001"));
        assertThat(rotated.path("refreshExpiresAt").asText()).isEqualTo(first.path("refreshExpiresAt").asText());
        assertThat(h.number("SELECT count(*) FROM wa.device_token WHERE consumed_at IS NOT NULL")).isEqualTo(1);
        assertThat(h.number("SELECT count(*) FROM wa.device_token WHERE kind='ACCESS' AND revoked_at IS NULL")).isEqualTo(1);
        assertThat(h.number("SELECT generation FROM wa.driver_authorization")).isEqualTo(1);
        assertThat(h.principal(rotated, false).deviceId()).isEqualTo(WaJson.uuid(first, "deviceId"));
        h.time.advance(Duration.ofDays(30));
        problem(WaProblem.Code.INVALID_CREDENTIAL, () -> h.service.refresh(pairing.client().proof(),
                refreshBody(rotated), "refresh-after-thirty-days-0001"));
        assertThat(h.number("SELECT count(*) FROM wa.device_token")).isEqualTo(4);
    }

    @Test
    void shouldNotTreatAnExpiredKnownRefreshReplayAsMaliciousReuse() throws Exception {
        Approved pairing = h.approved(client(), h.driver(), "begin-old-replay-0001");
        ObjectNode first = h.redeem(pairing, "redeem-old-replay-0001");
        ObjectNode rotated = h.refresh(pairing.client(), first, "refresh-known-0001");
        var accessBeforeExpiry = h.principal(rotated, false);
        h.time.advance(Duration.ofSeconds(301));
        problem(WaProblem.Code.INVALID_CREDENTIAL, () -> h.service.driverStatus(accessBeforeExpiry));
        problem(WaProblem.Code.INTENT_EXPIRED, () -> h.service.refresh(pairing.client().proof(), refreshBody(first), "refresh-known-0001"));
        assertThat(h.string("SELECT status FROM wa.driver_device")).isEqualTo("ACTIVE");
        assertThat(h.number("SELECT generation FROM wa.driver_authorization")).isEqualTo(1);
        h.refresh(pairing.client(), rotated, "refresh-current-0001");
        assertThat(h.string("SELECT status FROM wa.driver_device")).isEqualTo("ACTIVE");
    }

    @Test
    void shouldRequireTheSameProofKeyBeforeRevokingAReusedRefreshFamily() throws Exception {
        Approved pairing = h.approved(client(), h.driver(), "begin-reuse-0001");
        ObjectNode first = h.redeem(pairing, "redeem-reuse-0001");
        ObjectNode current = h.refresh(pairing.client(), first, "refresh-reuse-0001");
        Client attacker = client();
        problem(WaProblem.Code.INVALID_CREDENTIAL, () -> h.service.refresh(attacker.proof(), refreshBody(first), "refresh-attacker-0001"));
        assertThat(h.string("SELECT status FROM wa.driver_device")).isEqualTo("ACTIVE");
        var beforeRevocation = h.principal(current, false);
        WaDecision reuse = h.service.refresh(pairing.client().proof(), refreshBody(first), "refresh-reused-0002");
        assertThat(reuse.error()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(h.string("SELECT status FROM wa.driver_device")).isEqualTo("REVOKED");
        assertThat(h.number("SELECT count(*) FROM wa.device_token WHERE revoked_at IS NULL")).isZero();
        assertThat(h.number("SELECT generation FROM wa.driver_authorization")).isEqualTo(2);
        assertThat(h.service.refresh(pairing.client().proof(), refreshBody(first), "refresh-reused-0002").error())
                .isEqualTo(WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(h.number("SELECT generation FROM wa.driver_authorization")).isEqualTo(2);
        problem(WaProblem.Code.INVALID_CREDENTIAL, () -> h.service.driverStatus(beforeRevocation));
    }

    @Test
    void shouldNeverReplayCredentialsWhoseRefreshHasAlreadyRotated() throws Exception {
        Approved pairing = h.approved(client(), h.driver(), "begin-replay-guard-0001");
        ObjectNode initial = h.redeem(pairing, "redeem-replay-guard-0001");
        ObjectNode second = h.refresh(pairing.client(), initial, "refresh-replay-guard-0001");
        problem(WaProblem.Code.INVALID_CREDENTIAL, () -> h.service.redeem(pairing.client().proof(),
                redeemBody(pairing), "redeem-replay-guard-0001"));
        h.refresh(pairing.client(), second, "refresh-replay-guard-0002");
        problem(WaProblem.Code.INVALID_CREDENTIAL, () -> h.service.refresh(pairing.client().proof(),
                refreshBody(initial), "refresh-replay-guard-0001"));
        assertThat(h.string("SELECT status FROM wa.driver_device")).isEqualTo("ACTIVE");
    }

    @Test
    void shouldReplaceTheOldDeviceAndRequestTrackingStopWithoutClaimingItStopped() throws Exception {
        UUID driver = h.driver(); Approved first = h.approved(client(), driver, "begin-replace-0001");
        ObjectNode old = h.redeem(first, "redeem-replace-0001"); UUID tracking = h.tracking(driver, old);
        var oldPrincipal = h.principal(old, false);
        Approved next = h.approved(client(), driver, "begin-replace-0002"); ObjectNode current = h.redeem(next, "redeem-replace-0002");
        assertThat(h.number("SELECT generation FROM wa.driver_authorization WHERE driver_id=?", driver)).isEqualTo(2);
        assertThat(h.string("SELECT status FROM wa.driver_device WHERE id=?", WaJson.uuid(old, "deviceId"))).isEqualTo("REVOKED");
        assertThat(h.string("SELECT state FROM wa.tracking_session WHERE id=?", tracking)).isEqualTo("STOP_REQUESTED");
        assertThat(h.number("SELECT count(*) FROM wa.tracking_session WHERE id=? AND lease_expires_at<=? AND stopped_at IS NULL",
                tracking, java.sql.Timestamp.from(h.clock.now()))).isEqualTo(1);
        problem(WaProblem.Code.INVALID_CREDENTIAL, () -> h.service.driverStatus(oldPrincipal));
        assertThat(h.service.driverStatus(h.principal(current, false)).path("deviceId").asText())
                .isEqualTo(current.path("deviceId").asText());
    }

    @Test
    void shouldKeepAnOldRevokedDeviceFromRevokingItsReplacementAndMakeSelfRevokeIdempotent() throws Exception {
        UUID driver = h.driver(); Approved first = h.approved(client(), driver, "begin-revoke-0001");
        ObjectNode old = h.redeem(first, "redeem-revoke-0001");
        Approved next = h.approved(client(), driver, "begin-revoke-0002"); ObjectNode current = h.redeem(next, "redeem-revoke-0002");
        var oldPrincipal = h.principal(old, true);
        ObjectNode revoked = h.service.revoke(oldPrincipal, object(), "revoke-old-0001").requireSuccess();
        sameResponse(revoked, h.service.revoke(oldPrincipal, object(), "revoke-old-0001").requireSuccess());
        assertThat(h.number("SELECT generation FROM wa.driver_authorization")).isEqualTo(2);
        var currentPrincipal = h.principal(current, false);
        h.service.revoke(currentPrincipal, object(), "revoke-current-0001").requireSuccess();
        assertThat(h.number("SELECT generation FROM wa.driver_authorization")).isEqualTo(3);
        h.service.revoke(h.principal(current, true), object(), "revoke-current-0002").requireSuccess();
        assertThat(h.number("SELECT generation FROM wa.driver_authorization")).isEqualTo(3);
    }

    @Test
    void shouldEnforceEnvironmentAndCohortWithoutPretendingRealCohortsAreSynthetic() throws Exception {
        UUID driver = h.driver(); Approved pairing = h.approved(client(), driver, "begin-environment-0001");
        ObjectNode session = h.redeem(pairing, "redeem-environment-0001");
        assertThat(h.number("SELECT count(*) FROM wa.test_cohort WHERE NOT synthetic")).isEqualTo(1);
        WaPairingService other = h.service(settings(true, "other-lab"));
        problem(WaProblem.Code.INVALID_CREDENTIAL, () -> other.refresh(pairing.client().proof(), refreshBody(session), "refresh-other-env-0001"));
        problem(WaProblem.Code.INVALID_CREDENTIAL, () -> other.driverStatus(h.principal(session, false)));
        ObjectNode otherBegin = other.begin(pairing.client().proof(), beginBody(pairing.client()), "begin-other-env-0001").requireSuccess();
        problem(WaProblem.Code.ACTION_NOT_ALLOWED, () -> h.transaction.execute(status -> other.confirm(driver, otherBegin.path("userCode").asText())));
        assertThat(h.number("SELECT generation FROM wa.driver_authorization")).isEqualTo(1);
    }

    @Test
    void shouldDenyNewEffectsWhileKeepingRefreshStatusAndRevokeAvailableWhenDisabled() throws Exception {
        UUID driver = h.driver(); Approved first = h.approved(client(), driver, "begin-disabled-0001");
        ObjectNode session = h.redeem(first, "redeem-disabled-0001"); Approved pending = h.approved(client(), driver, "begin-disabled-0002");
        WaPairingService disabled = h.service(settings(false, "lab"));
        problem(WaProblem.Code.ACTION_NOT_ALLOWED, () -> disabled.begin(first.client().proof(), beginBody(first.client()), "begin-disabled-0003"));
        problem(WaProblem.Code.ACTION_NOT_ALLOWED, () -> disabled.redeem(pending.client().proof(), redeemBody(pending), "redeem-disabled-0002"));
        problem(WaProblem.Code.ACTION_NOT_ALLOWED, () -> h.transaction.execute(status -> disabled.confirm(driver, pending.begin().path("userCode").asText())));
        assertThat(disabled.status(pending.client().proof(), WaJson.uuid(pending.begin(), "pairingId")).path("state").asText()).isEqualTo("APPROVED");
        ObjectNode rotated = disabled.refresh(first.client().proof(), refreshBody(session), "refresh-disabled-0001").requireSuccess();
        var principal = h.principal(rotated, false);
        assertThat(disabled.driverStatus(principal).has("latitude")).isFalse();
        disabled.revoke(principal, object(), "revoke-disabled-0001").requireSuccess();
        assertThat(h.string("SELECT status FROM wa.driver_device")).isEqualTo("REVOKED");
    }

    @Test
    void shouldRequireTheHumanTransactionAndRevalidateProfileAndAccessAfterIngress() throws Exception {
        UUID driver = h.driver(); Client client = client();
        ObjectNode begun = h.service.begin(client.proof(), beginBody(client), "begin-tx-0001").requireSuccess();
        assertThatThrownBy(() -> h.service.confirm(driver, begun.path("userCode").asText()))
                .isInstanceOf(IllegalTransactionStateException.class);
        h.transaction.execute(status -> h.service.confirm(driver, begun.path("userCode").asText()));
        ObjectNode session = h.service.redeem(client.proof(), object().put("pairingId", begun.path("pairingId").asText())
                .put("pkceVerifier", client.verifier()), "redeem-tx-0001").requireSuccess();
        var principal = h.principal(session, false);
        h.jdbc.update("UPDATE wa.profile SET status='SUSPENDED' WHERE identity_id=?", driver);
        problem(WaProblem.Code.ACTION_NOT_ALLOWED, () -> h.service.driverStatus(principal));
        problem(WaProblem.Code.ACTION_NOT_ALLOWED, () -> h.service.refresh(client.proof(), refreshBody(session), "refresh-blocked-0001"));
        h.jdbc.update("UPDATE wa.profile SET status='ACTIVE' WHERE identity_id=?", driver);
        h.jdbc.update("UPDATE wa.device_token SET revoked_at=? WHERE id=?", java.sql.Timestamp.from(h.clock.now()), principal.credentialId());
        problem(WaProblem.Code.INVALID_CREDENTIAL, () -> h.service.driverStatus(principal));
    }

    @Test
    void shouldRejectAnActiveKeyOwnedByAnotherDriverWithoutMutatingEitherSession() throws Exception {
        Client client = client(); UUID firstDriver = h.driver(); UUID secondDriver = h.driver();
        Approved first = h.approved(client, firstDriver, "begin-key-owner-0001"); ObjectNode session = h.redeem(first, "redeem-key-owner-0001");
        Approved second = h.approved(client, secondDriver, "begin-key-owner-0002");
        problem(WaProblem.Code.ACTION_NOT_ALLOWED, () -> h.service.redeem(client.proof(), redeemBody(second), "redeem-key-owner-0002"));
        assertThat(h.number("SELECT generation FROM wa.driver_authorization WHERE driver_id=?", secondDriver)).isZero();
        assertThat(h.number("SELECT count(*) FROM wa.driver_device")).isEqualTo(1);
        assertThat(h.service.driverStatus(h.principal(session, false)).path("driverId").asText()).isEqualTo(firstDriver.toString());
    }

    static void sameResponse(ObjectNode first, ObjectNode second) {
        // Assertion failures expose digests, never raw response credentials.
        assertThat(WaCrypto.digest(WaJson.canonical(first))).isEqualTo(WaCrypto.digest(WaJson.canonical(second)));
    }
}
