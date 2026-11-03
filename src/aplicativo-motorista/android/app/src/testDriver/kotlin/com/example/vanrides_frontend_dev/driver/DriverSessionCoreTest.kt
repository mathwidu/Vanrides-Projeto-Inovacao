package com.example.vanrides_frontend_dev.driver

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class DriverSessionCoreTest {
    private val p = DriverSessionProtocol
    private val origin = p.LAB_ORIGIN
    private val time = 1793448000000L
    private val pairingId = "00000000-0000-4000-8000-000000000001"
    private val deviceId = "00000000-0000-4000-8000-000000000002"
    private val driverId = "00000000-0000-4000-8000-000000000003"
    private val nonce = p.b64(ByteArray(32) { 1 })
    private val access = p.b64(ByteArray(32) { 2 })
    private val refresh = p.b64(ByteArray(32) { 3 })
    private val newAccess = p.b64(ByteArray(32) { 4 })
    private val newRefresh = p.b64(ByteArray(32) { 5 })
    private class MemoryStore : DriverSessionStore {
        var saved: String? = null
        var loads = 0
        var saves = 0
        var failNext = false
        var afterSave: ((JSONObject) -> Unit)? = null
        override fun load(): JSONObject? { loads++; return saved?.let(::JSONObject) }
        override fun save(value: JSONObject) {
            saves++
            if (failNext) { failNext = false; throw DriverSessionFailure("STORAGE_ERROR") }
            saved = value.toString()
            afterSave?.invoke(value)
        }
    }
    private class Keys : DriverSessionKeys {
        private var pair: java.security.KeyPair? = null
        var creates = 0
        override fun exists() = pair != null
        override fun create() { creates++; if (pair == null) pair = KeyPairGenerator.getInstance("EC").run { initialize(ECGenParameterSpec("secp256r1")); generateKeyPair() } }
        override fun publicJwk(): JSONObject {
            val key = pair!!.public as ECPublicKey
            return JSONObject().put("kty", "EC").put("crv", "P-256")
                .put("x", DriverSessionProtocol.coordinate(key.w.affineX)).put("y", DriverSessionProtocol.coordinate(key.w.affineY))
        }
        override fun sign(bytes: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
            initSign(pair!!.private); update(bytes); DriverSessionProtocol.joseSignature(sign())
        }
        fun verify(request: DriverSessionRequest) {
            val parts = request.proof.split('.')
            val signature = DriverSessionProtocol.unb64(parts[2])
            assertEquals(64, signature.size)
            fun integer(bytes: ByteArray): ByteArray {
                val number = BigInteger(1, bytes).toByteArray()
                return byteArrayOf(2, number.size.toByte()) + number
            }
            val numbers = integer(signature.copyOfRange(0, 32)) + integer(signature.copyOfRange(32, 64))
            val der = byteArrayOf(48, numbers.size.toByte()) + numbers
            assertTrue(Signature.getInstance("SHA256withECDSA").run {
                initVerify(pair!!.public); update((parts[0] + "." + parts[1]).toByteArray(Charsets.US_ASCII)); verify(der)
            })
        }
    }
    private class Wire(private val store: MemoryStore) : DriverSessionTransport {
        val calls = mutableListOf<DriverSessionRequest>()
        val actions = java.util.ArrayDeque<(DriverSessionRequest) -> DriverSessionResponse>()
        fun enqueue(action: (DriverSessionRequest) -> DriverSessionResponse) { actions.add(action) }
        fun reply(body: JSONObject) = enqueue { DriverSessionResponse(200, body) }
        override fun send(request: DriverSessionRequest): DriverSessionResponse {
            calls.add(request)
            if (request.method == "POST" && !request.path.contains("/tracking-sessions/")) {
                val journal = JSONObject(store.saved!!).getJSONObject("pending")
                assertEquals(request.idempotencyKey, journal.getString("idempotencyKey"))
                assertArrayEquals(request.body, DriverSessionProtocol.unb64(journal.getString("body")))
            }
            return actions.removeFirst().invoke(request)
        }
    }
    private class Fixture {
        val store = MemoryStore(); val keys = Keys(); val wire = Wire(store)
    }
    private fun core(f: Fixture, url: String? = origin, clock: Long = time) = DriverSessionCore(url, "0.1.0+3", f.keys, f.store, f.wire, { clock })
    private fun claims(request: DriverSessionRequest) = JSONObject(String(p.unb64(request.proof.split('.')[1]), Charsets.UTF_8))
    private fun beginResponse() = JSONObject().put("pairingId", pairingId).put("userCode", "BCDFGHJZ")
        .put("expiresAt", p.iso(time + 300000)).put("serverTime", p.iso(time))
    private fun pairingResponse(state: String) = JSONObject().put("pairingId", pairingId).put("state", state)
        .put("expiresAt", p.iso(time + 300000)).put("serverTime", p.iso(time))
    private fun sessionResponse(rotated: Boolean = false, at: Long = time) = JSONObject().put("deviceId", deviceId).put("tokenType", "DPoP")
        .put("accessToken", if (rotated) newAccess else access).put("refreshToken", if (rotated) newRefresh else refresh)
        .put("accessExpiresAt", p.iso(at + 300000)).put("refreshExpiresAt", p.iso(time + 30L * 86400000)).put("serverTime", p.iso(at))
    private fun status(readiness: String = "READY", id: String = deviceId) = JSONObject().put("deviceId", id).put("driverId", driverId)
        .put("profileStatus", "ACTIVE").put("deviceState", "ACTIVE").put("readiness", readiness)
        .put("displayName", "Motorista sintético").put("runId", JSONObject.NULL).put("serverTime", p.iso(time))
    private fun fail(code: String, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (failure: DriverSessionFailure) { assertEquals(code, failure.code) }
    }
    private fun paired(f: Fixture): DriverSessionCore {
        val core = core(f)
        f.wire.reply(beginResponse()); core.beginPairing()
        f.wire.reply(pairingResponse("APPROVED")); f.wire.reply(sessionResponse()); f.wire.reply(status())
        assertEquals("READY", core.syncSession()["state"])
        return core
    }
    @Test fun readsNeverGenerateKeysOrAccessStorageWhenUnconfigured() {
        val f = Fixture(); val absent = core(f, null)
        assertEquals(mapOf("state" to "UNCONFIGURED"), absent.getState()); assertNull(absent.showPairingCode())
        assertEquals(0, f.store.loads); assertEquals(0, f.keys.creates)
        assertEquals(mapOf("state" to "UNPAIRED"), core(f).getState())
        assertEquals(0, f.keys.creates); assertEquals(0, f.wire.calls.size)
    }
    @Test fun beginNegotiatesOneNonceWithFreshSignedProofAndIdenticalJournal() {
        val f = Fixture(); val core = core(f)
        f.wire.enqueue { DriverSessionResponse(401, JSONObject().put("code", "PROOF_NONCE_REQUIRED"), nonce) }
        f.wire.reply(beginResponse())
        assertEquals("PENDING", core.beginPairing()["state"])
        assertEquals("BCDFGHJZ", core.showPairingCode())
        val first = f.wire.calls[0]; val second = f.wire.calls[1]
        assertEquals(first.idempotencyKey, second.idempotencyKey); assertArrayEquals(first.body, second.body)
        val one = claims(first); val two = claims(second)
        assertNotEquals(one.getString("jti"), two.getString("jti")); assertEquals(nonce, two.getString("nonce"))
        assertEquals(origin + first.path, one.getString("htu")); assertEquals("POST", one.getString("htm"))
        assertEquals(p.hash(first.body!!), one.getString("vr_body_sha256")); assertFalse(one.has("ath"))
        val request = JSONObject(String(first.body, Charsets.UTF_8))
        val verifier = JSONObject(f.store.saved!!).getString("verifier")
        assertEquals(p.hash(verifier.toByteArray(Charsets.US_ASCII)), request.getString("pkceChallenge"))
        f.wire.calls.forEach(f.keys::verify)
        assertFalse(core.getState().toString().contains("BCDFGHJZ")); assertEquals(1, f.keys.creates)
    }
    @Test fun missingOrRepeatedNonceChallengeIsBoundedAndKeepsBeginJournal() {
        val f = Fixture(); val core = core(f)
        repeat(2) { f.wire.enqueue { DriverSessionResponse(401, JSONObject().put("code", "PROOF_NONCE_REQUIRED"), nonce) } }
        fail("SERVER_REJECTED") { core.beginPairing() }
        assertEquals(2, f.wire.calls.size); assertEquals("REQUESTING", core.getState()["state"])
        assertNotNull(JSONObject(f.store.saved!!).getJSONObject("pending"))
    }
    @Test fun rateLimitPreservesTheExactBeginForExplicitRetry() {
        val f = Fixture(); val core = core(f)
        f.wire.enqueue { DriverSessionResponse(429, JSONObject().put("code", "RATE_LIMITED")) }
        fail("RATE_LIMITED") { core.beginPairing() }
        val first = f.wire.calls.single()
        f.wire.reply(beginResponse()); core.beginPairing()
        assertEquals(first.idempotencyKey, f.wire.calls.last().idempotencyKey)
        assertArrayEquals(first.body, f.wire.calls.last().body)
    }
    @Test fun beginResponseLossReplaysPersistedBytesAfterProcessRestart() {
        val f = Fixture(); val original = core(f)
        f.wire.enqueue { throw DriverSessionFailure("NETWORK_ERROR") }
        fail("NETWORK_ERROR") { original.beginPairing() }
        val before = f.wire.calls.single()
        f.wire.reply(beginResponse()); val recovered = core(f); recovered.beginPairing()
        val after = f.wire.calls.last()
        assertEquals(before.idempotencyKey, after.idempotencyKey); assertArrayEquals(before.body, after.body)
        assertNotEquals(claims(before).getString("jti"), claims(after).getString("jti")); assertEquals(1, f.keys.creates)
        assertEquals("PENDING", recovered.getState()["state"])
    }
    @Test fun writeFailurePreventsNetworkAndMissingKeyIsNeverRegeneratedBySync() {
        val f = Fixture(); val core = core(f); f.store.failNext = true
        fail("STORAGE_ERROR") { core.beginPairing() }; assertTrue(f.wire.calls.isEmpty())
        val pending = Fixture(); pending.store.saved = JSONObject().put("schemaVersion", 1).put("origin", origin).put("state", "REQUESTING")
            .put("pending", JSONObject().put("kind", "BEGIN").put("idempotencyKey", pairingId).put("body", "e30")).toString()
        fail("KEY_UNAVAILABLE") { core(pending).syncSession() }; assertEquals(0, pending.keys.creates)
    }
    @Test fun approvalRedeemsAndOnlyVerifiedOwnStatusBecomesReady() {
        val f = Fixture(); val core = paired(f)
        assertNull(core.showPairingCode()); assertEquals(mapOf("state" to "READY"), core.getState())
        assertEquals("SETUP_REQUIRED", core(f).getState()["state"])
        val redeemed = f.wire.calls.first { it.path.endsWith("/redeem") }
        assertEquals(pairingId, JSONObject(String(redeemed.body!!)).getString("pairingId"))
        val checked = f.wire.calls.last(); assertEquals(access, checked.accessToken)
        assertEquals(p.hash(access.toByteArray(Charsets.US_ASCII)), claims(checked).getString("ath"))
        assertFalse(claims(checked).has("vr_body_sha256"))
        assertEquals(setOf("schemaVersion", "origin", "state", "session"), JSONObject(f.store.saved!!).keys().asSequence().toSet())
        f.wire.calls.forEach(f.keys::verify)
    }
    @Test fun refreshResponseLossReplaysOldTokenAndAtomicallyInstallsRotatedSession() {
        val f = Fixture(); val original = paired(f)
        f.wire.enqueue { throw DriverSessionFailure("NETWORK_ERROR") }
        fail("NETWORK_ERROR") { original.refreshSession() }; val first = f.wire.calls.last()
        assertEquals(refresh, JSONObject(f.store.saved!!).getJSONObject("session").getString("refreshToken"))
        f.wire.reply(sessionResponse(true)); f.wire.reply(status())
        assertEquals("READY", core(f).syncSession()["state"])
        val replay = f.wire.calls[f.wire.calls.lastIndex - 1]
        assertArrayEquals(first.body, replay.body); assertEquals(first.idempotencyKey, replay.idempotencyKey)
        assertNull(replay.accessToken); assertFalse(claims(replay).has("ath"))
        assertNotEquals(claims(first).getString("jti"), claims(replay).getString("jti"))
        assertEquals(newRefresh, JSONObject(f.store.saved!!).getJSONObject("session").getString("refreshToken"))
        assertEquals(newAccess, f.wire.calls.last().accessToken)
    }
    @Test fun responsePersistenceFailureKeepsRedeemJournalForRestartRecovery() {
        val f = Fixture(); val core = core(f); f.wire.reply(beginResponse()); core.beginPairing()
        f.wire.reply(pairingResponse("APPROVED"))
        f.wire.enqueue { f.store.failNext = true; DriverSessionResponse(200, sessionResponse()) }
        fail("STORAGE_ERROR") { core.syncSession() }
        val original = f.wire.calls.last(); assertEquals("REDEEM", JSONObject(f.store.saved!!).getJSONObject("pending").getString("kind"))
        f.wire.reply(sessionResponse()); f.wire.reply(status())
        assertEquals("READY", core(f).syncSession()["state"])
        val replay = f.wire.calls[f.wire.calls.lastIndex - 1]
        assertArrayEquals(original.body, replay.body); assertEquals(original.idempotencyKey, replay.idempotencyKey)
    }
    @Test fun revocationResponseLossRetainsExactOperationAndNeverReportsSuccessEarly() {
        val f = Fixture(); val core = paired(f)
        f.wire.enqueue { throw DriverSessionFailure("NETWORK_ERROR") }
        fail("NETWORK_ERROR") { core.revokeSession() }; assertEquals("REVOKING", core.getState()["state"])
        val first = f.wire.calls.last()
        assertEquals(p.hash(access.toByteArray(Charsets.US_ASCII)), claims(first).getString("ath"))
        f.wire.reply(JSONObject().put("deviceId", deviceId).put("state", "REVOKED").put("serverTime", p.iso(time)))
        val recovered = core(f); assertEquals("REVOKED", recovered.syncSession()["state"])
        val replay = f.wire.calls.last(); assertArrayEquals(first.body, replay.body); assertEquals(first.idempotencyKey, replay.idempotencyKey)
        assertFalse(JSONObject(f.store.saved!!).has("session")); assertNull(recovered.showPairingCode())
    }
    @Test fun neverAppliedRevokeRecoversExpiredAccessAndNeverPassesThroughReady() {
        val f = Fixture(); val initial = paired(f)
        f.wire.enqueue { throw DriverSessionFailure("NETWORK_ERROR") }
        fail("NETWORK_ERROR") { initial.revokeSession() }
        val original = f.wire.calls.last()
        val recovered = core(f, clock = time + 301000)
        f.wire.enqueue { request ->
            assertEquals("REVOKING", recovered.getState()["state"]); assertEquals(original.idempotencyKey, request.idempotencyKey)
            DriverSessionResponse(401, JSONObject().put("code", "INVALID_CREDENTIAL"))
        }
        f.wire.enqueue { request ->
            assertTrue(request.path.endsWith("/refresh")); assertEquals("REVOKING", recovered.getState()["state"])
            assertEquals(refresh, JSONObject(String(request.body!!)).getString("refreshToken"))
            DriverSessionResponse(200, sessionResponse(true, time + 301000))
        }
        f.wire.enqueue { request ->
            assertTrue(request.path.endsWith("/revoke")); assertEquals("REVOKING", recovered.getState()["state"])
            assertEquals(newAccess, request.accessToken); assertNotEquals(original.idempotencyKey, request.idempotencyKey)
            DriverSessionResponse(200, JSONObject().put("deviceId", deviceId).put("state", "REVOKED").put("serverTime", p.iso(time + 301000)))
        }
        assertEquals("REVOKED", recovered.syncSession()["state"])
        assertFalse(f.wire.calls.takeLast(3).any { it.path.endsWith("/status") })
    }
    @Test fun appliedRevokeWithLostAckAndExpiredAccessEndsExpiredWhenRefreshIsRejected() {
        val f = Fixture(); val initial = paired(f)
        f.wire.enqueue { throw DriverSessionFailure("NETWORK_ERROR") }
        fail("NETWORK_ERROR") { initial.revokeSession() }
        val recovered = core(f, clock = time + 301000)
        repeat(2) { f.wire.enqueue { DriverSessionResponse(401, JSONObject().put("code", "INVALID_CREDENTIAL")) } }
        fail("SESSION_EXPIRED") { recovered.syncSession() }
        assertEquals(mapOf("state" to "EXPIRED"), recovered.getState())
        assertFalse(JSONObject(f.store.saved!!).has("session")); assertFalse(JSONObject(f.store.saved!!).has("pending"))
    }
    @Test fun restartBetweenRotatedTokensAndNewRevokePreservesUnlinkIntent() {
        val f = Fixture(); val initial = paired(f)
        f.wire.enqueue { throw DriverSessionFailure("NETWORK_ERROR") }; fail("NETWORK_ERROR") { initial.revokeSession() }
        val recovering = core(f, clock = time + 301000)
        f.wire.enqueue { DriverSessionResponse(401, JSONObject().put("code", "INVALID_CREDENTIAL")) }
        f.wire.reply(sessionResponse(true, time + 301000))
        f.store.afterSave = { saved ->
            if (saved.getString("state") == "REVOKING" && !saved.has("pending") && saved.optJSONObject("session")?.getString("accessToken") == newAccess) {
                f.store.afterSave = null
                throw DriverSessionFailure("STORAGE_ERROR") // process dies after atomic rotation, before the next journal
            }
        }
        fail("STORAGE_ERROR") { recovering.syncSession() }
        assertEquals("REVOKING", core(f, clock = time + 301000).getState()["state"])
        f.wire.enqueue { request ->
            assertTrue(request.path.endsWith("/revoke")); assertEquals(newAccess, request.accessToken)
            DriverSessionResponse(200, JSONObject().put("deviceId", deviceId).put("state", "REVOKED").put("serverTime", p.iso(time + 301000)))
        }
        assertEquals("REVOKED", core(f, clock = time + 301000).syncSession()["state"])
    }
    @Test fun rateLimitedRefreshDuringUnlinkRetainsItsJournalAndRevokingAcrossRestart() {
        val f = Fixture(); paired(f)
        val revoke = core(f, clock = time + 301000)
        f.wire.enqueue { DriverSessionResponse(429, JSONObject().put("code", "RATE_LIMITED")) }
        fail("RATE_LIMITED") { revoke.revokeSession() }
        val refreshRequest = f.wire.calls.last()
        assertEquals("REVOKING", revoke.getState()["state"]); assertTrue(refreshRequest.path.endsWith("/refresh"))
        f.wire.reply(sessionResponse(true, time + 301000))
        f.wire.reply(JSONObject().put("deviceId", deviceId).put("state", "REVOKED").put("serverTime", p.iso(time + 301000)))
        assertEquals("REVOKED", core(f, clock = time + 301000).syncSession()["state"])
        val replay = f.wire.calls[f.wire.calls.lastIndex - 1]
        assertArrayEquals(refreshRequest.body, replay.body); assertEquals(refreshRequest.idempotencyKey, replay.idempotencyKey)
    }
    @Test fun expiredRefreshDuringLostRevocationEndsExpiredWithoutInventingAnAck() {
        val f = Fixture(); val initial = paired(f)
        f.wire.enqueue { throw DriverSessionFailure("NETWORK_ERROR") }; fail("NETWORK_ERROR") { initial.revokeSession() }
        val recovered = core(f, clock = time + 31L * 86400000)
        f.wire.enqueue { DriverSessionResponse(401, JSONObject().put("code", "INVALID_CREDENTIAL")) }
        fail("SESSION_EXPIRED") { recovered.syncSession() }
        assertEquals("EXPIRED", recovered.getState()["state"])
        assertTrue(f.wire.calls.last().path.endsWith("/revoke"))
    }
    @Test fun setupRequiredAndForeignDeviceCannotProduceFalseReady() {
        val f = Fixture(); val core = paired(f)
        f.wire.reply(status("SETUP_REQUIRED")); assertEquals("SETUP_REQUIRED", core.syncSession()["state"])
        f.wire.reply(status(id = pairingId))
        try { core.syncSession(); fail("Expected invalid device binding") } catch (_: IllegalArgumentException) { }
        assertEquals("SETUP_REQUIRED", core.getState()["state"])
    }
    @Test fun expiryHidesCodeAndExpiredRefreshDoesNotStartARequest() {
        val f = Fixture(); val core = core(f); f.wire.reply(beginResponse()); core.beginPairing()
        val expired = core(f, clock = time + 300001)
        assertEquals(mapOf("state" to "EXPIRED"), expired.getState()); assertNull(expired.showPairingCode())
        val paired = Fixture(); paired(paired)
        fail("SESSION_EXPIRED") { core(paired, clock = time + 31L * 86400000).refreshSession() }
        assertEquals("EXPIRED", core(paired).getState()["state"])
    }
    @Test fun trackingUsesBoundAccessNonceAndExactBodyWithoutExposingTokens() {
        val f = Fixture(); val core = paired(f)
        val body = "{\"sessionId\":\"$deviceId\",\"generation\":1,\"state\":\"ACTIVE\"}".toByteArray()
        val path = "${p.PREFIX}/tracking-sessions/$deviceId/state"
        f.wire.reply(JSONObject().put("ok", true))
        assertEquals(200, core.trackingRequest(path, body, pairingId).status)
        val request = f.wire.calls.last()
        assertEquals(path, request.path); assertArrayEquals(body, request.body)
        assertEquals(pairingId, request.idempotencyKey); assertNotNull(request.accessToken)
        val claims = JSONObject(String(p.unb64(request.proof.split('.')[1])))
        assertEquals(origin + path, claims.getString("htu"))
        assertEquals(p.hash(body), claims.getString("vr_body_sha256"))
        assertEquals(p.hash(request.accessToken!!.toByteArray()), claims.getString("ath"))
        try { core.trackingRequest("/driver-app/v1/session/revoke", body, pairingId); fail("Expected closed route") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun closedOriginsReturnLinksAndDerConversionRejectUntrustedForms() {
        assertEquals(origin, p.origin(origin)); assertTrue(p.isReturn("vanridesdriver://pairing/return"))
        for (uri in listOf("vanridesdriver://pairing/return?code=BCDFGHJK", "vanridesdriver://pairing/return#x",
            "vanridesdriver://pairing/return/", "vanridesdriver://other/return", "vanridesdriver://pairing/begin")) assertFalse(p.isReturn(uri))
        for (url in listOf("http://localhost:18443", "$origin/", "$origin/path", "$origin?q=x", "$origin#x", "https://user@localhost:18443")) {
            try { p.origin(url); fail("Expected closed origin") } catch (_: IllegalArgumentException) { }
        }
        for (invalid in listOf("2026-02-30T00:00:00Z", "2026-10-31T00:00:00+00:00", "2026-10-31T25:00:00Z")) {
            try { p.instant(invalid); fail("Expected invalid instant") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(p.instant("2026-10-31T00:00:00.123Z"), p.instant("2026-10-31T00:00:00.123456789Z"))
        try { p.joseSignature(byteArrayOf(48, 6, 2, 1, -1, 2, 1, 1)); fail("Expected signed integer rejection") } catch (_: IllegalArgumentException) { }
        for (bytes in listOf(ByteArray(32), ByteArray(24) { it.toByte() }, byteArrayOf(1), byteArrayOf(-1, -2))) assertArrayEquals(bytes, p.unb64(p.b64(bytes)))
    }
    @Test fun coldAndWarmEntryPolicyWithholdsAllUntrustedUrisAndRequiresAnIconForBegin() {
        val host = "links.vanrides.example"
        for (phase in listOf("cold", "warm")) {
            assertEquals(DriverSessionProtocol.Entry.ICON, p.entry("android.intent.action.MAIN", true, null, host))
            assertEquals(DriverSessionProtocol.Entry.INVALID, p.entry("android.intent.action.MAIN", true, "vanridesdriver://pairing/return", host))
            assertEquals(DriverSessionProtocol.Entry.RETURN, p.entry("android.intent.action.VIEW", false, p.RETURN_URI, host))
            assertEquals(DriverSessionProtocol.Entry.RETURN, p.entry("android.intent.action.VIEW", false, "https://$host${p.HTTPS_RETURN_PATH}", host))
            assertEquals(DriverSessionProtocol.Entry.INVALID, p.entry("android.intent.action.VIEW", false, "https://$host${p.HTTPS_RETURN_PATH}"))
            for (uri in listOf("${p.RETURN_URI}?sentinel=$phase", "${p.RETURN_URI}#sentinel_$phase", "https://other.example${p.HTTPS_RETURN_PATH}",
                "https://$host${p.HTTPS_RETURN_PATH}?sentinel=$phase", "https://$host${p.HTTPS_RETURN_PATH}/", "vanridesdriver://pairing/begin")) {
                assertEquals(DriverSessionProtocol.Entry.INVALID, p.entry("android.intent.action.VIEW", false, uri, host))
            }
        }
    }
}
