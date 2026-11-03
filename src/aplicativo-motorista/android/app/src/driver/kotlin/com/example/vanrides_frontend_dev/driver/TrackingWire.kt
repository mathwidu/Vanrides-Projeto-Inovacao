package com.example.vanrides_frontend_dev.driver

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal data class DriverCredential(val baseUrl: String, val token: String)
internal data class RemoteControl(
    val owner: SessionOwner, val serverTime: Long, val leaseExpiresAt: Long,
    val action: String, val gpsState: String,
)
internal data class RemoteStatus(
    val serverTime: Long, val credentialExpiresAt: Long, val owner: SessionOwner?,
    val sessionState: String?, val leaseExpiresAt: Long?, val gpsState: String,
)
/** A RUNNING status can be an activation whose response was lost. It is never terminal evidence. */
internal class TrackingActivationGuard {
    var terminalOwner: SessionOwner? = null
    fun observe(status: RemoteStatus) {
        terminalOwner = if (status.sessionState in setOf("STOPPED", "EXPIRED", "REVOKED")) status.owner else null
    }
    fun accepts(control: RemoteControl): Boolean = control.action == "CONTINUE" && control.owner != terminalOwner
}
internal data class SampleReceipt(
    val control: RemoteControl, val accepted: Set<Long>, val duplicates: Set<Long>, val rejected: Set<Long>,
)
internal data class SampleBatch(val id: String, val body: String, val sequences: Set<Long>)

internal object TrackingWire {
    private val controlKeys = setOf("schemaVersion", "environment", "sessionId", "generation", "serverTime", "leaseExpiresAt", "action", "gpsState")
    private val gpsStates = setOf("UNKNOWN", "FRESH", "STALE", "CLOCK_SKEW")
    private fun JSONObject.keysSet(): Set<String> = keys().asSequence().toSet()
    private fun JSONObject.exact(keys: Set<String>) { require(keysSet() == keys) { "INVALID_CONTRACT" } }
    private fun JSONObject.int64(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long) { "INVALID_CONTRACT" }
        return (value as Number).toLong()
    }
    private fun JSONObject.text(key: String): String = (get(key) as? String) ?: error("INVALID_CONTRACT")
    private fun JSONObject.common() {
        require(int64("schemaVersion") == 1L && text("environment") in setOf("gps-spike", "driver-mvp") && int64("serverTime") > 0) { "INVALID_CONTRACT" }
    }
    private fun json(body: String): JSONObject {
        val tokener = JSONTokener(body)
        val value = tokener.nextValue()
        require(value is JSONObject && tokener.nextClean() == '\u0000') { "INVALID_CONTRACT" }
        return value
    }
    private fun control(obj: JSONObject): RemoteControl {
        obj.common()
        val action = obj.text("action")
        val gps = obj.text("gpsState")
        require(action in setOf("CONTINUE", "STOP") && gps in gpsStates) { "INVALID_CONTRACT" }
        val lease = obj.int64("leaseExpiresAt")
        require(lease > 0) { "INVALID_CONTRACT" }
        return RemoteControl(SessionOwner(obj.text("sessionId"), obj.int64("generation")), obj.int64("serverTime"), lease, action, gps)
    }
    fun control(body: String): RemoteControl = json(body).let { it.exact(controlKeys); control(it) }
    fun status(body: String): RemoteStatus {
        val obj = json(body)
        obj.exact(setOf("schemaVersion", "environment", "serverTime", "credentialExpiresAt", "session")); obj.common()
        val expiry = obj.int64("credentialExpiresAt")
        require(expiry > obj.int64("serverTime") && (obj.text("environment") == "driver-mvp" || expiry - obj.int64("serverTime") <= 14_400_000L)) { "INVALID_CONTRACT" }
        if (obj.isNull("session")) return RemoteStatus(obj.int64("serverTime"), expiry, null, null, null, "UNKNOWN")
        val session = obj.getJSONObject("session")
        session.exact(setOf("sessionId", "generation", "state", "leaseExpiresAt", "gpsState"))
        val state = session.text("state")
        val gps = session.text("gpsState")
        require(state in setOf("RUNNING", "STOP_PENDING", "STOPPED", "EXPIRED", "REVOKED") && gps in gpsStates) { "INVALID_CONTRACT" }
        val lease = session.int64("leaseExpiresAt")
        require(lease > 0 && lease <= expiry) { "INVALID_CONTRACT" }
        return RemoteStatus(obj.int64("serverTime"), expiry, SessionOwner(session.text("sessionId"), session.int64("generation")), state, lease, gps)
    }
    fun receipt(body: String): SampleReceipt {
        val obj = json(body)
        obj.exact(controlKeys + setOf("accepted", "duplicate", "rejected"))
        fun numbers(array: JSONArray): Set<Long> {
            val values = (0 until array.length()).map { index ->
                val number = array.get(index)
                require(number is Int || number is Long) { "INVALID_ACK" }
                (number as Number).toLong().also { require(it >= 1) { "INVALID_ACK" } }
            }
            require(values.distinct().size == values.size) { "INVALID_ACK" }; return values.toSet()
        }
        val rejectedArray = obj.getJSONArray("rejected")
        val rejected = (0 until rejectedArray.length()).map { index ->
            val item = rejectedArray.getJSONObject(index)
            item.exact(setOf("sequence", "code"))
            require(Regex("[A-Z0-9_]{1,80}").matches(item.text("code"))) { "INVALID_ACK" }
            item.int64("sequence").also { require(it >= 1) { "INVALID_ACK" } }
        }
        require(rejected.distinct().size == rejected.size) { "INVALID_ACK" }
        return SampleReceipt(control(obj), numbers(obj.getJSONArray("accepted")), numbers(obj.getJSONArray("duplicate")), rejected.toSet())
    }
    fun batch(owner: SessionOwner, samples: List<TrackingSample>, batchId: String, deviceTime: Long): SampleBatch {
        require(samples.size in 1..TrackingProtocol.MAX_BATCH) { "INVALID_BATCH" }
        val array = JSONArray()
        samples.forEach { sample ->
            require(sample.valid()) { "INVALID_SAMPLE" }
            val obj = JSONObject().put("sequence", sample.sequence).put("capturedAt", sample.capturedAt)
                .put("latitude", sample.latitude).put("longitude", sample.longitude).put("accuracyM", sample.accuracyM)
            sample.speedMps?.let { obj.put("speedMps", it) }; sample.headingDeg?.let { obj.put("headingDeg", it) }
            array.put(obj)
        }
        val body = JSONObject().put("schemaVersion", 1).put("sessionId", owner.id).put("generation", owner.generation)
            .put("batchId", TrackingProtocol.id(batchId)).put("deviceTime", deviceTime).put("samples", array).toString()
        require(body.toByteArray(Charsets.UTF_8).size <= TrackingProtocol.MAX_BODY_BYTES) { "INVALID_BATCH" }
        return SampleBatch(batchId, body, samples.map { it.sequence }.toSet())
    }
    fun state(owner: SessionOwner, stopped: Boolean, reason: String? = null, requested: Long? = null, stoppedAt: Long? = null): String {
        val obj = JSONObject().put("schemaVersion", 1).put("generation", owner.generation).put("state", if (stopped) "STOPPED" else "RUNNING")
        reason?.let { obj.put("reason", it) }; requested?.let { obj.put("stopRequestedAt", it) }; stoppedAt?.let { obj.put("stoppedAt", it) }
        return obj.toString()
    }
}

/** Convert the Spring110 wire only at the native boundary; queue/lease stay monotonic. */
internal class ProductionTrackingWire {
    var stopReference: String? = null
        private set
    private val retries = mutableMapOf<String, String>()
    fun request(path: String, original: String?): Pair<String?, String?> {
        if (original == null) return null to null
        val body = JSONObject(original)
        val key: String
        if (path.endsWith("/activate")) {
            DriverSessionProtocol.fields(body, setOf("expectedVersion", "foregroundConfirmed", "intentToken"))
            require(body.getString("foregroundConfirmed") == "CONFIRMED")
            DriverSessionProtocol.token(body.getString("intentToken"))
            key = path.substringAfter("/start-intents/").substringBefore('/')
        } else if (path.endsWith("/samples")) {
            body.remove("schemaVersion")
            body.put("deviceTime", DriverSessionProtocol.iso(body.getLong("deviceTime")))
            val samples = body.getJSONArray("samples")
            for (index in 0 until samples.length()) {
                val sample = samples.getJSONObject(index)
                sample.put("capturedAt", DriverSessionProtocol.iso(sample.getLong("capturedAt")))
            }
            key = body.getString("batchId")
        } else {
            val owner = path.substringAfter("/tracking-sessions/").substringBefore('/')
            val state = if (body.getString("state") == "STOPPED") "STOPPED" else "ACTIVE"
            val generation = body.getLong("generation")
            val clean = JSONObject().put("sessionId", owner).put("generation", generation).put("state", state)
            if (state == "STOPPED") stopReference?.let { clean.put("stopRequestRef", it) }
            val retryKey = path + clean.toString()
            return clean.toString() to if (state == "STOPPED") owner else retries.getOrPut(retryKey) { java.util.UUID.randomUUID().toString() }
        }
        return body.toString() to key
    }
    fun response(path: String, original: String): String {
        val body = JSONObject(original)
        fun time(name: String): Long = DriverSessionProtocol.instant(body.getString(name))
        val server = time("serverTime")
        stopReference = null
        if (body.has("stopRequestRef") && !body.isNull("stopRequestRef")) stopReference = DriverSessionProtocol.uuid(body.getString("stopRequestRef"))
        fun control(): JSONObject {
            val id = if (body.has("trackingSessionId")) body.getString("trackingSessionId") else body.getString("sessionId")
            return JSONObject().put("schemaVersion", 1).put("environment", "driver-mvp")
                .put("sessionId", DriverSessionProtocol.uuid(id)).put("generation", body.getLong("generation"))
                .put("serverTime", server).put("leaseExpiresAt", time("leaseExpiresAt"))
                .put("action", body.getString("action")).put("gpsState", body.getString("gpsState"))
        }
        val normalized: JSONObject
        if (path.endsWith("/tracking-status")) {
            val session = if (body.isNull("trackingSessionId") || body.optString("sessionState") == "IDLE") JSONObject.NULL else {
                JSONObject().put("sessionId", DriverSessionProtocol.uuid(body.getString("trackingSessionId")))
                    .put("generation", body.getLong("generation")).put("state", body.getString("sessionState"))
                    .put("leaseExpiresAt", time("leaseExpiresAt")).put("gpsState", body.getString("gpsState"))
            }
            normalized = JSONObject().put("schemaVersion", 1).put("environment", "driver-mvp")
                .put("serverTime", server).put("credentialExpiresAt", time("credentialExpiresAt")).put("session", session)
        } else {
            normalized = control()
            if (path.endsWith("/samples")) for (name in listOf("accepted", "duplicate", "rejected")) normalized.put(name, body.get(name))
        }
        retries.keys.removeAll { it.startsWith(path) }
        return normalized.toString()
    }
}

/** Encrypted by its adapter; this schema cannot contain a sample or an access token. */
internal object TrackingStopJournal {
    fun record(owner: SessionOwner, body: String): JSONObject {
        val value = JSONObject().put("sessionId", owner.id).put("generation", owner.generation).put("body", body)
        read(value)
        return value
    }
    fun read(value: JSONObject): Pair<SessionOwner, String> {
        val p = DriverSessionProtocol
        p.fields(value, setOf("sessionId", "generation", "body"))
        val owner = SessionOwner(p.uuid(value.getString("sessionId")), value.getLong("generation"))
        val body = value.getString("body"); require(body.toByteArray().size <= 4096)
        val packet = JSONObject(body)
        p.fields(packet, setOf("schemaVersion", "generation", "state", "reason", "stopRequestedAt", "stoppedAt"), setOf("stopRequestRef"))
        require(packet.getInt("schemaVersion") == 1 && packet.getString("state") == "STOPPED" && packet.getLong("generation") == owner.generation)
        require(Regex("[A-Z0-9_]{1,80}").matches(packet.getString("reason")))
        require(packet.getLong("stopRequestedAt") > 0 && packet.getLong("stoppedAt") > 0)
        if (packet.has("stopRequestRef")) p.uuid(packet.getString("stopRequestRef"))
        return owner to body
    }
}
