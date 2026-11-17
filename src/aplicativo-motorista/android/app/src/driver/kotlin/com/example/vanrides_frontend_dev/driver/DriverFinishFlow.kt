package com.example.vanrides_frontend_dev.driver

import org.json.JSONObject
import java.util.UUID

/** Purpose-bound, encrypted journal. No access token, proof, sample or diagnostic export. */
internal object DriverFinishContract {
    private val p = DriverSessionProtocol
    fun authorization(args: JSONObject): JSONObject {
        p.fields(args, setOf("intentId", "expectedVersion", "intentToken"))
        p.uuid(args.getString("intentId")); p.token(args.getString("intentToken"))
        require((args.get("expectedVersion") is Int || args.get("expectedVersion") is Long) && args.getLong("expectedVersion") in 0..9007199254740991L)
        return JSONObject(args.toString())
    }
    fun read(args: JSONObject) = JSONObject().put("intentToken", args.getString("intentToken")).put("expectedVersion", args.getLong("expectedVersion"))
    fun same(first: JSONObject?, second: JSONObject): Boolean = first != null && listOf("intentId","intentToken","expectedVersion").all { first.get(it) == second.get(it) }
    fun preview(value: JSONObject, args: JSONObject): JSONObject {
        p.fields(value, setOf("intentId","runId","trackingSessionId","sessionGeneration","expectedVersion","expiresAt","state","trip","stopAcknowledged"))
        binding(value, args); p.instant(value.getString("expiresAt"))
        require(value.getString("state") in setOf("READY","COMPLETED") && value.get("stopAcknowledged") is Boolean)
        val trip = value.getJSONObject("trip")
        p.fields(trip, setOf("name","direction","localDate","localTime","timezone"))
        require(trip.getString("name").length in 1..80 && trip.getString("direction") in setOf("OUTBOUND","RETURN"))
        java.time.LocalDate.parse(trip.getString("localDate")); java.time.LocalTime.parse(trip.getString("localTime"))
        java.time.ZoneId.of(trip.getString("timezone"))
        return value
    }
    private fun binding(value: JSONObject, args: JSONObject) {
        require(value.getString("intentId") == args.getString("intentId") && value.getLong("expectedVersion") == args.getLong("expectedVersion"))
        p.uuid(value.getString("runId")); p.uuid(value.getString("trackingSessionId")); require(value.getLong("sessionGeneration") >= 1)
    }
    fun receipt(value: JSONObject, expected: JSONObject): JSONObject {
        p.fields(value, setOf("intentId","runId","trackingSessionId","sessionGeneration","expectedVersion","state","finishConsumedAt","stopAcknowledged","stopRequestRef","stoppedAt"))
        for (key in listOf("intentId","runId","trackingSessionId","sessionGeneration","expectedVersion")) require(value.get(key) == expected.get(key))
        require(value.getString("state") == "COMPLETED" && value.get("stopAcknowledged") is Boolean)
        p.instant(value.getString("finishConsumedAt"))
        if (!value.isNull("stopRequestRef")) p.uuid(value.getString("stopRequestRef"))
        if (!value.isNull("stoppedAt")) p.instant(value.getString("stoppedAt"))
        if (value.getBoolean("stopAcknowledged")) require(!value.isNull("stoppedAt"))
        return value
    }
    fun owner(value: JSONObject) = SessionOwner(value.getString("trackingSessionId"), value.getLong("sessionGeneration"))
    fun stoppedPacket(expected: SessionOwner, ref: String?, current: SessionOwner?, providerStopped: Boolean, now: Long): String {
        require(providerStopped && (current == null || current == expected) && now > 0)
        DriverSessionProtocol.uuid(expected.id)
        val body = JSONObject(TrackingWire.state(expected,true,"USER_FINISH",now,now))
        if (ref != null) body.put("stopRequestRef",DriverSessionProtocol.uuid(ref))
        TrackingStopJournal.record(expected,body.toString())
        return body.toString()
    }
}

/** The callback belongs to an explicit FINISH, never to a status poll or restored journal. */
internal class DriverFinishFlow(
    private val request: (String, String, String?, (NativeHttpResult) -> Unit) -> Unit,
    private val load: () -> JSONObject?, private val save: (JSONObject?) -> Unit,
    private val localOwner: () -> SessionOwner?, private val localOff: () -> Boolean,
    private val available: () -> Boolean, private val stopLocal: () -> Unit,
    private val prepareStop: (SessionOwner) -> Unit,
    private val bindStop: (SessionOwner, String?) -> Unit,
    private val clearStop: (SessionOwner, String?) -> Unit,
    private val wake: (Long) -> Unit, private val now: () -> Long,
) {
    private var journal: JSONObject? = null
    private var storageAvailable = true
    init {
        try { journal = load()?.also {
            DriverSessionProtocol.fields(it, setOf("authorization","preview","key"), setOf("receipt"))
            DriverFinishContract.authorization(it.getJSONObject("authorization"))
            DriverFinishContract.preview(it.getJSONObject("preview"), it.getJSONObject("authorization"))
            DriverSessionProtocol.uuid(it.getString("key"))
        } } catch (_: Exception) { storageAvailable = false }
    }
    private var offered: JSONObject? = null
    private var offeredArgs: JSONObject? = null
    private var reply: ((Map<String, Any?>?, String?) -> Unit)? = null
    private var first = false
    private var recovering = false
    private var prepared = false
    private var nextAt = 0L
    var networkBusy = false; private set
    var holdStop = false; private set

    fun cancelAction() { val previous = reply; reply = null; holdStop = false; previous?.invoke(null, "CANCELLED") }
    fun pending(): Map<String,Any?>? = journal?.getJSONObject("preview")?.let { jsonMap(it) }
    fun retry(args: JSONObject, previewOnly: Boolean, callback: (Map<String,Any?>?,String?) -> Unit) {
        val record = journal
        if (record == null || args.getString("intentId") != record.getJSONObject("authorization").getString("intentId") || args.getLong("expectedVersion") != record.getJSONObject("authorization").getLong("expectedVersion")) {
            callback(null,"FINISH_REVIEW_REQUIRED"); return
        }
        offered = record.getJSONObject("preview"); offeredArgs = record.getJSONObject("authorization")
        if (previewOnly) callback(jsonMap(offered!!),null) else finish(offeredArgs!!,callback)
    }
    fun preview(args: JSONObject, callback: (Map<String, Any?>?, String?) -> Unit) {
        if (networkBusy || !available()) { callback(null,"BUSY"); return }
        val auth = DriverFinishContract.authorization(args)
        networkBusy = true
        request("/driver-app/v1/finish-intents/${auth.getString("intentId")}/preview", DriverFinishContract.read(auth).toString(), null) { response ->
            networkBusy = false
            try {
                require(response.status == 200 && response.failure == null && response.body != null)
                val value = DriverFinishContract.preview(JSONObject(response.body!!), auth)
                offered = value; offeredArgs = auth
                callback(jsonMap(value), null)
            } catch (_: Exception) { callback(null,"FINISH_REVIEW_REQUIRED") }
            wake(0)
        }
    }
    fun finish(args: JSONObject, callback: (Map<String, Any?>?, String?) -> Unit) {
        if (reply != null || networkBusy) { callback(null,"BUSY"); return }
        val auth = DriverFinishContract.authorization(args)
        val preview = offered
        if (preview == null || !DriverFinishContract.same(offeredArgs, auth)) { callback(null,"FINISH_REVIEW_REQUIRED"); return }
        val expected = DriverFinishContract.owner(preview)
        if (localOwner()?.let { it != expected } == true) { callback(null,"SESSION_OWNER_MISMATCH"); return }
        val existing = journal?.takeIf { DriverFinishContract.same(it.getJSONObject("authorization"), auth) }
        val existed = existing != null
        try {
            check(storageAvailable)
            val next = existing ?: JSONObject().put("authorization",auth).put("preview",preview).put("key",UUID.randomUUID().toString())
            save(next); journal = next
        } catch (_: Exception) { stopLocal(); callback(null,"STOP_STORAGE_ERROR"); return }
        recovering = existed && !journal!!.has("receipt")
        reply = callback; holdStop = true; first = true; prepared = false; nextAt = 0
        stopLocal() // pump is held, not the provider.
        tick(); wake(100)
    }
    fun tick() {
        val record = journal ?: return
        if (reply == null || !localOff()) return
        val preview = record.getJSONObject("preview")
        if (localOwner()?.let { it != DriverFinishContract.owner(preview) } == true) { fail("SESSION_OWNER_MISMATCH"); return }
        if (!prepared) {
            try { prepareStop(DriverFinishContract.owner(preview)); prepared = true }
            catch (_: Exception) { fail("STOP_STORAGE_ERROR"); return }
        }
        if (networkBusy || now() < nextAt) return
        if (!available()) { wake(100); return }
        val args = record.getJSONObject("authorization")
        val consume = first && !recovering && !record.has("receipt")
        first = false; networkBusy = true
        val path = if (consume) "/driver-app/v1/runs/${preview.getString("runId")}/finish" else "/driver-app/v1/finish-intents/${args.getString("intentId")}/receipt"
        val body = if (consume) JSONObject(args.toString()).put("foregroundConfirmed","CONFIRMED") else DriverFinishContract.read(args)
        request(path, body.toString(), if (consume) record.getString("key") else null) { response ->
            networkBusy = false; holdStop = false
            if (journal !== record || reply == null) { wake(0); return@request }
            if (recovering && response.status == 404) {
                recovering = false; first = true; wake(0); return@request
            }
            recovering = false
            try {
                require(response.status == 200 && response.failure == null && response.body != null)
                val json = JSONObject(response.body!!)
                val receipt = DriverFinishContract.receipt(if (consume) json.getJSONObject("receipt") else json, preview)
                if (consume) {
                    DriverSessionProtocol.fields(json,setOf("receipt","control"))
                    val control = json.getJSONObject("control")
                    DriverSessionProtocol.fields(control,setOf("runId","state","entityVersion","trackingSessionId","generation","serverTime","action","gpsState","leaseExpiresAt","stopRequestRef"))
                    require(control.getString("action") == "STOP" && control.getString("trackingSessionId") == preview.getString("trackingSessionId")
                        && control.getLong("generation") == preview.getLong("sessionGeneration")
                        && control.getString("runId") == preview.getString("runId") && control.getString("state") == "COMPLETED"
                        && control.get("stopRequestRef") == receipt.get("stopRequestRef"))
                }
                val prior = record.optJSONObject("receipt")
                if (prior != null) require(prior.get("stopRequestRef") == receipt.get("stopRequestRef") && prior.getString("finishConsumedAt") == receipt.getString("finishConsumedAt"))
                record.put("receipt",receipt); save(record)
                val ref = if (receipt.isNull("stopRequestRef")) null else receipt.getString("stopRequestRef")
                if (!receipt.getBoolean("stopAcknowledged")) bindStop(DriverFinishContract.owner(preview), ref)
                if (receipt.getBoolean("stopAcknowledged") && localOff()) {
                    clearStop(DriverFinishContract.owner(preview), ref)
                    save(null); journal = null
                    val callback = reply; reply = null
                    callback?.invoke(mapOf("receipt" to jsonMap(receipt),"providerStopped" to true),null)
                } else { nextAt = now() + 2000; wake(2000) }
            } catch (_: Exception) { fail(if (response.status == 409 || response.status == 410) "FINISH_REVIEW_REQUIRED" else "FINISH_PENDING") }
            wake(0)
        }
    }
    private fun fail(code: String) { holdStop = false; val callback = reply; reply = null; callback?.invoke(null,code); wake(0) }
    companion object {
        fun jsonMap(value: JSONObject): Map<String,Any?> = value.keys().asSequence().associateWith { key ->
            when (val item = value.get(key)) { JSONObject.NULL -> null; is JSONObject -> jsonMap(item); else -> item }
        }
    }
}
