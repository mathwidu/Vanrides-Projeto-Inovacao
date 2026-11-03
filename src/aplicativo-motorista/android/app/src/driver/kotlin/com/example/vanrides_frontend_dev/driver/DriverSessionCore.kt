package com.example.vanrides_frontend_dev.driver

import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

/** Synchronous state machine. The Android bridge runs it on one serial executor. */
internal class DriverSessionCore(
    private val origin: String?, private val appVersion: String,
    private val keys: DriverSessionKeys, private val store: DriverSessionStore,
    private val transport: DriverSessionTransport, private val now: () -> Long = System::currentTimeMillis,
    private val random: (Int) -> ByteArray = { size -> ByteArray(size).also { SecureRandom().nextBytes(it) } },
) {
    private var loaded = false
    private var value: JSONObject? = null
    private var verifiedReadiness: String? = null
    private var nonce: String? = null
    private val p = DriverSessionProtocol
    private fun fresh(state: String) = JSONObject().put("schemaVersion", 1).put("origin", origin).put("state", state)
    private fun data(): JSONObject {
        if (!loaded) {
            val saved = try { store.load() } catch (_: Exception) { throw DriverSessionFailure("STORAGE_ERROR") }
            if (saved != null) {
                try {
                    p.fields(saved, setOf("schemaVersion", "origin", "state"), setOf("verifier", "pairing", "session", "pending"))
                    require(saved.getInt("schemaVersion") == 1 && saved.getString("origin") == origin)
                    require(saved.getString("state") in p.states && saved.getString("state") != "READY")
                } catch (_: Exception) { throw DriverSessionFailure("STORAGE_ERROR") }
            }
            value = saved ?: fresh("UNPAIRED"); loaded = true
        }
        return value!!
    }
    private fun commit(next: JSONObject) {
        try { store.save(next) } catch (_: Exception) { throw DriverSessionFailure("STORAGE_ERROR") }
        value = next; loaded = true
    }
    private fun edit(action: (JSONObject) -> Unit) {
        val next = JSONObject(data().toString()); action(next); commit(next)
    }
    private fun configured() { if (origin == null) throw DriverSessionFailure("UNCONFIGURED") }
    private fun keyRequired() { if (!keys.exists()) throw DriverSessionFailure("KEY_UNAVAILABLE") }
    private fun session(): JSONObject? = data().optJSONObject("session")
    private fun pending(): JSONObject? = data().optJSONObject("pending")
    private fun terminal(state: String) { commit(fresh(state)); verifiedReadiness = null }

    fun getState(): Map<String, String> {
        if (origin == null) return mapOf("state" to "UNCONFIGURED")
        val saved = data()
        var state = saved.getString("state")
        val pairing = saved.optJSONObject("pairing")
        if (state == "PENDING" && pairing != null && p.instant(pairing.getString("expiresAt")) <= now()) state = "EXPIRED"
        if (session() != null && pending() == null && state != "REVOKING") state = verifiedReadiness ?: "SETUP_REQUIRED"
        return if (state == "PENDING" && pairing != null) mapOf("state" to state, "expiresAt" to pairing.getString("expiresAt"))
            else mapOf("state" to state)
    }
    fun showPairingCode(): String? {
        if (origin == null || getState()["state"] != "PENDING") return null
        return data().optJSONObject("pairing")?.getString("userCode")?.let(p::code)
    }
    fun beginPairing(): Map<String, String> {
        configured()
        if (pending() != null) {
            if (pending()!!.getString("kind") != "BEGIN") throw DriverSessionFailure("BUSY")
            runPending(); return getState()
        }
        if (session() != null) throw DriverSessionFailure("ACTION_NOT_ALLOWED")
        if (getState()["state"] == "PENDING") return getState()
        keys.create(); keyRequired()
        val verifier = p.b64(random(32))
        val body = JSONObject().put("publicJwk", keys.publicJwk()).put("pkceChallenge", p.hash(verifier.toByteArray(Charsets.US_ASCII)))
            .put("pkceMethod", "S256").put("platform", "ANDROID").put("appVersion", appVersion)
        val initial = fresh("REQUESTING").put("verifier", verifier).put("pending", journal("BEGIN", body))
        commit(initial); verifiedReadiness = null
        runPending(); return getState()
    }
    fun syncSession(): Map<String, String> {
        if (origin == null) return getState()
        data()
        if (pending() != null) runPending()
        if (data().getString("state") == "REVOKING") { continueRevocation(); return getState() }
        if (session() != null) {
            verifySession(); return getState()
        }
        val pairing = data().optJSONObject("pairing") ?: return getState()
        keyRequired()
        val id = p.uuid(pairing.getString("pairingId"))
        val response = request("GET", "${p.PREFIX}/pairings/$id/status")
        if (response.status == 404 || response.status == 410) { terminal("EXPIRED"); return getState() }
        requireSuccess(response)
        val body = response.body
        p.fields(body, setOf("pairingId", "state", "expiresAt", "serverTime"))
        require(p.uuid(p.string(body, "pairingId")) == id)
        p.instant(p.string(body, "expiresAt")); p.instant(p.string(body, "serverTime"))
        when (p.string(body, "state")) {
            "PENDING" -> Unit
            "APPROVED" -> {
                val verifier = p.token(data().getString("verifier"))
                edit { it.put("pending", journal("REDEEM", JSONObject().put("pairingId", id).put("pkceVerifier", verifier))) }
                runPending(); verifySession()
            }
            "EXPIRED", "REVOKED", "REDEEMED" -> terminal("EXPIRED")
            else -> throw DriverSessionFailure("INVALID_CONTRACT")
        }
        return getState()
    }
    fun refreshSession(): Map<String, String> {
        configured()
        if (data().getString("state") == "REVOKING") { continueRevocation(); return getState() }
        if (pending() != null && pending()!!.getString("kind") != "REFRESH") throw DriverSessionFailure("BUSY")
        if (pending() == null) startRefresh()
        runPending(); verifySession(); return getState()
    }
    fun revokeSession(): Map<String, String> {
        configured()
        if (data().getString("state") != "REVOKING") {
            if (pending() != null) throw DriverSessionFailure("BUSY")
            if (session() == null) return getState()
            // Persist the user's unlink intent BEFORE a possible refresh. A restart may never return READY in between.
            edit { it.put("state", "REVOKING") }; verifiedReadiness = null
        }
        continueRevocation(); return getState()
    }
    private fun continueRevocation() {
        if (pending() != null) {
            require(pending()!!.getString("kind") in setOf("REVOKE", "REFRESH"))
            runPending()
        }
        if (data().getString("state") != "REVOKING" || session() == null) return
        val session = session()!!
        if (p.instant(session.getString("accessExpiresAt")) <= now() + 1000) { startRefresh(); runPending() }
        edit { it.put("pending", journal("REVOKE", JSONObject())) }
        runPending()
    }
    private fun startRefresh() {
        val session = session() ?: throw DriverSessionFailure("ACTION_NOT_ALLOWED")
        if (p.instant(session.getString("refreshExpiresAt")) <= now()) { terminal("EXPIRED"); throw DriverSessionFailure("SESSION_EXPIRED") }
        val refresh = p.token(session.getString("refreshToken"))
        edit { it.put("state", if (it.getString("state") == "REVOKING") "REVOKING" else "SETUP_REQUIRED")
            .put("pending", journal("REFRESH", JSONObject().put("refreshToken", refresh))) }
        verifiedReadiness = null
    }
    private fun journal(kind: String, body: JSONObject): JSONObject = JSONObject().put("kind", kind)
        .put("idempotencyKey", UUID.randomUUID().toString())
        .put("body", p.b64(body.toString().toByteArray(Charsets.UTF_8)))
    private fun runPending() {
        keyRequired()
        val operation = pending() ?: return
        p.fields(operation, setOf("kind", "idempotencyKey", "body"))
        val kind = p.string(operation, "kind")
        val path = when (kind) {
            "BEGIN" -> "${p.PREFIX}/pairings/begin"
            "REDEEM" -> "${p.PREFIX}/pairings/redeem"
            "REFRESH" -> "${p.PREFIX}/session/refresh"
            "REVOKE" -> "${p.PREFIX}/session/revoke"
            else -> throw DriverSessionFailure("STORAGE_ERROR")
        }
        val bytes = p.unb64(p.string(operation, "body")); require(bytes.size <= p.MAX_BYTES)
        val idempotency = p.uuid(operation.getString("idempotencyKey"))
        val access = if (kind == "REVOKE") p.token(session()!!.getString("accessToken")) else null
        val response = request("POST", path, bytes, access, idempotency)
        if (kind == "REVOKE" && response.status == 401 && errorCode(response) == "INVALID_CREDENTIAL" &&
            p.instant(session()!!.getString("accessExpiresAt")) <= now() + 1000) {
            // The old access can no longer recover its receipt. Rotate with a durable unlink intent;
            // if the earlier revoke did apply, refresh is rejected and we report EXPIRED, never an invented ACK.
            startRefresh(); runPending(); continueRevocation(); return
        }
        if (response.status == 410 || response.status == 401 && errorCode(response) == "INVALID_CREDENTIAL" && kind in setOf("REDEEM", "REFRESH")) {
            terminal("EXPIRED"); throw DriverSessionFailure("SESSION_EXPIRED")
        }
        requireSuccess(response)
        when (kind) {
            "BEGIN" -> acceptBegin(response.body)
            "REDEEM", "REFRESH" -> acceptSession(response.body, kind == "REFRESH")
            "REVOKE" -> {
                p.fields(response.body, setOf("deviceId", "state", "serverTime"))
                require(p.uuid(p.string(response.body, "deviceId")) == session()!!.getString("deviceId") &&
                    p.string(response.body, "state") == "REVOKED")
                p.instant(p.string(response.body, "serverTime")); terminal("REVOKED")
            }
        }
    }
    private fun acceptBegin(body: JSONObject) {
        p.fields(body, setOf("pairingId", "userCode", "expiresAt", "serverTime"))
        p.uuid(p.string(body, "pairingId")); p.code(p.string(body, "userCode"))
        val expires = p.instant(p.string(body, "expiresAt")); val server = p.instant(p.string(body, "serverTime"))
        require(expires > server && expires - server <= 300000)
        edit { root -> root.put("pairing", JSONObject(body.toString()).apply { remove("serverTime") }).put("state", "PENDING"); root.remove("pending") }
    }
    private fun acceptSession(body: JSONObject, refresh: Boolean) {
        p.fields(body, setOf("deviceId", "accessToken", "refreshToken", "tokenType", "accessExpiresAt", "refreshExpiresAt", "serverTime"))
        val id = p.uuid(p.string(body, "deviceId"))
        p.token(p.string(body, "accessToken")); p.token(p.string(body, "refreshToken"))
        require(p.string(body, "tokenType") == "DPoP")
        val server = p.instant(p.string(body, "serverTime"))
        val access = p.instant(p.string(body, "accessExpiresAt")); val expiry = p.instant(p.string(body, "refreshExpiresAt"))
        require(access > server && access - server <= 300000 && expiry > server && expiry - server <= 30L * 86400000)
        if (refresh) require(session()!!.getString("deviceId") == id)
        val revoking = data().getString("state") == "REVOKING"
        edit { root ->
            root.put("session", JSONObject(body.toString()).apply { remove("serverTime"); remove("tokenType") })
                .put("state", if (revoking) "REVOKING" else "SETUP_REQUIRED")
            root.remove("pending"); root.remove("pairing"); root.remove("verifier")
        }
        verifiedReadiness = null
    }
    private fun verifySession() {
        verifiedReadiness = null
        var session = session() ?: return
        keyRequired()
        if (p.instant(session.getString("accessExpiresAt")) <= now() + 1000) { startRefresh(); runPending(); session = session() ?: return }
        var response = request("GET", "${p.PREFIX}/status", access = p.token(session.getString("accessToken")))
        if (response.status == 401 && errorCode(response) == "INVALID_CREDENTIAL") {
            verifiedReadiness = null
            startRefresh(); runPending(); session = session() ?: return
            response = request("GET", "${p.PREFIX}/status", access = p.token(session.getString("accessToken")))
        }
        requireSuccess(response)
        val body = response.body
        p.fields(body, setOf("deviceId", "driverId", "profileStatus", "deviceState", "readiness", "runId", "serverTime"), setOf("displayName"))
        require(p.uuid(p.string(body, "deviceId")) == session.getString("deviceId"))
        p.uuid(p.string(body, "driverId")); p.instant(p.string(body, "serverTime"))
        if (!body.isNull("runId")) p.uuid(p.string(body, "runId"))
        if (body.has("displayName") && !body.isNull("displayName")) require(p.string(body, "displayName").length in 1..80)
        require(p.string(body, "profileStatus") in setOf("ACTIVE", "ONBOARDING", "SUSPENDED", "CLOSED"))
        require(p.string(body, "deviceState") == "ACTIVE")
        val readiness = p.string(body, "readiness")
        require(readiness in setOf("READY", "SETUP_REQUIRED"))
        verifiedReadiness = if (body.getString("profileStatus") == "ACTIVE") readiness else "SETUP_REQUIRED"
    }
    /** Same serialized060 owner; tokens and signing keys never cross the bridge. */
    fun trackingRequest(path: String, body: ByteArray?, idempotency: String?): DriverSessionResponse {
        configured(); keyRequired()
        require(path == "${p.PREFIX}/tracking-status" ||
            Regex("${p.PREFIX}/(?:start-intents/[0-9a-f-]{36}/activate|tracking-sessions/[0-9a-f-]{36}/(?:state|samples))").matches(path))
        if (data().getString("state") in setOf("REVOKING", "REVOKED", "EXPIRED")) throw DriverSessionFailure("SESSION_EXPIRED")
        if (pending() != null) {
            if (pending()!!.getString("kind") != "REFRESH") throw DriverSessionFailure("BUSY")
            runPending()
        }
        var current = session() ?: throw DriverSessionFailure("SESSION_EXPIRED")
        if (p.instant(current.getString("accessExpiresAt")) <= now() + 15000) { startRefresh(); runPending(); current = session()!! }
        val method = if (body == null) "GET" else "POST"
        var response = request(method, path, body, p.token(current.getString("accessToken")), idempotency)
        if (response.status == 401 && errorCode(response) == "INVALID_CREDENTIAL") {
            startRefresh(); runPending(); current = session() ?: throw DriverSessionFailure("SESSION_EXPIRED")
            response = request(method, path, body, p.token(current.getString("accessToken")), idempotency)
        }
        return response
    }
    private fun errorCode(response: DriverSessionResponse): String = response.body.optString("code", "")
    private fun requireSuccess(response: DriverSessionResponse) {
        if (response.status != 200) throw DriverSessionFailure(when {
            response.status == 429 -> "RATE_LIMITED"
            response.status >= 500 -> "NETWORK_ERROR"
            else -> "SERVER_REJECTED"
        })
    }
    private fun request(method: String, path: String, body: ByteArray? = null, access: String? = null,
                        idempotency: String? = null): DriverSessionResponse {
        configured(); keyRequired()
        repeat(2) { attempt ->
            // This write precedes every POST, including its nonce retry. No journal mutation follows a lost response.
            if (method == "POST") commit(JSONObject(data().toString()))
            val proof = proof(method, path, body, access)
            val response = transport.send(DriverSessionRequest(method, path, body?.copyOf(), proof, access, idempotency))
            response.nonce?.let { nonce = p.token(it) }
            if (response.status == 401 && errorCode(response) == "PROOF_NONCE_REQUIRED") {
                if (attempt == 0 && response.nonce != null) return@repeat
                throw DriverSessionFailure("SERVER_REJECTED")
            }
            return response
        }
        throw DriverSessionFailure("SERVER_REJECTED")
    }
    private fun proof(method: String, path: String, body: ByteArray?, access: String?): String {
        val header = JSONObject().put("typ", "dpop+jwt").put("alg", "ES256").put("jwk", keys.publicJwk())
        val claims = JSONObject().put("jti", p.b64(random(24))).put("iat", now() / 1000)
            .put("htm", method).put("htu", origin + path)
        nonce?.let { claims.put("nonce", it) }
        access?.let { claims.put("ath", p.hash(it.toByteArray(Charsets.US_ASCII))) }
        body?.let { claims.put("vr_body_sha256", p.hash(it)) }
        val unsigned = p.b64(header.toString().toByteArray(Charsets.UTF_8)) + "." + p.b64(claims.toString().toByteArray(Charsets.UTF_8))
        val signature = keys.sign(unsigned.toByteArray(Charsets.US_ASCII)); require(signature.size == 64)
        return unsigned + "." + p.b64(signature)
    }
}
