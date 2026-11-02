package com.example.vanrides_frontend_dev.driver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.vanrides_frontend_dev.BuildConfig
import java.util.UUID

/** Process owner. All mutable state is confined to Android's main thread. */
internal class DriverTrackingRuntime private constructor(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val spike = DriverTrackingBuild.SPIKE_ENABLED
    private val productionWire = ProductionTrackingWire()
    private val sessions by lazy { AndroidDriverSession.get(context) }
    private val store = DriverPrivateStore(context)
    private var credential: DriverCredential? = null
    private var credentialExpiry = 0L
    private var state = "UNCONFIGURED"
    private var reason: String? = null
    private var visible = false
    private var epoch = 0L
    private var owner: SessionOwner? = null
    private val activationGuard = TrackingActivationGuard()
    private var service: DriverTrackingService? = null
    private var serviceRequested = false
    private var sequence = 0L
    private var queue = SampleQueue()
    private var carriedDropped = 0
    private var accepted = 0
    private var duplicates = 0
    private var rejected = 0
    private var gpsState = "UNKNOWN"
    private val lease = MonotonicLease()
    private var batch: SampleBatch? = null
    private var batchOutstanding: Set<Long> = emptySet()
    private var stopRequestedAt: Long? = null
    private var stoppedAt: Long? = null
    private var nativeStopCalledAt: Long? = null
    private var nativeStopCompletedAt: Long? = null
    private var stopRequestedElapsedMs: Long? = null
    private var stoppedElapsedMs: Long? = null
    private var nativeStopCalledElapsedMs: Long? = null
    private var nativeStopCompletedElapsedMs: Long? = null
    private var lateCallbacksDiscarded = 0
    // Process-local metadata only. Never retain a Location, coordinates, or an owner ID.
    private var locationCallbacksReceived = 0L
    private var locationSamplesOffered = 0L
    private var lastLocationCallbackElapsedMs: Long? = null
    private var lastLocationAccuracyM: Double? = null
    private var lastLocationAccuracyState: String? = null
    private var lastLocationElapsedNanos: Long? = null
    private var lastLocationCapturedAt: Long? = null
    private var lastLocationAgeElapsedAtCallbackMs: Long? = null
    private var lastLocationAgeWallAtCallbackMs: Long? = null
    private var lastLocationElapsedStateAtCallback: String? = null
    private var lastLocationWallStateAtCallback: String? = null
    private var lastLocationCallbackDisposition: String? = null
    private var previousNativeStopCalledAt: Long? = null
    private var previousNativeStopCompletedAt: Long? = null
    private var previousNativeStopCalledElapsedMs: Long? = null
    private var previousNativeStopCompletedElapsedMs: Long? = null
    private var previousLeaseDeadlineElapsedMs: Long? = null
    private var previousDiagnosticBootCount: Int? = null
    private val diagnosticBootCount: Int? = try {
        android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }
    } catch (_: Exception) { null }
    private val energy = DriverEnergyDiagnostics()
    private val energyMonitor = DriverEnergyMonitor(context, diagnosticBootCount) { sample ->
        if (energy.observe(sample)) persist()
    }
    private var remoteStopAcknowledgedAt: Long? = null
    private var processReconciledAt: Long? = null
    private var startingReply: ((String?) -> Unit)? = null
    private var pendingStop: PendingStop? = null
    private var stopStatusChecked = false
    private var stopping = false
    private var reconcileAfterCancelledStart = false
    private var networkPending = false
    private var nextNetworkAt = 0L
    private var nextHeartbeatAt = 0L
    private val http = DriverTrackingBuild.transport { pump() }
    private data class PendingStop(val owner: SessionOwner, val credential: DriverCredential, val body: String)

    init {
        val old = store.loadDiagnostics()
        fun time(key: String): Long? = if (old.isNull(key) || !old.has(key)) null else old.optLong(key).takeIf { it > 0 }
        accepted = old.optInt("accepted", 0).coerceAtLeast(0)
        duplicates = old.optInt("duplicates", 0).coerceAtLeast(0)
        rejected = old.optInt("rejected", 0).coerceAtLeast(0)
        carriedDropped = old.optInt("dropped", 0).coerceAtLeast(0)
        stopRequestedAt = time("stopRequestedAt"); stoppedAt = time("stoppedAt")
        previousNativeStopCalledAt = time("nativeStopCalledAt") ?: time("previousNativeStopCalledAt")
        previousNativeStopCompletedAt = time("nativeStopCompletedAt") ?: time("previousNativeStopCompletedAt")
        previousNativeStopCalledElapsedMs = time("nativeStopCalledElapsedMs") ?: time("previousNativeStopCalledElapsedMs")
        previousNativeStopCompletedElapsedMs = time("nativeStopCompletedElapsedMs") ?: time("previousNativeStopCompletedElapsedMs")
        previousLeaseDeadlineElapsedMs = time("leaseDeadlineElapsedMs") ?: time("previousLeaseDeadlineElapsedMs")
        previousDiagnosticBootCount = old.optInt("diagnosticBootCount", -1).takeIf { it >= 0 }
        remoteStopAcknowledgedAt = time("remoteStopAcknowledgedAt")
        val energyInterrupted = energy.restore(store.loadEnergySession(old))
        try {
            credential = if (!spike) DriverCredential("", "") else store.loadCredential()?.also {
                DriverTrackingBuild.baseUrl(it.baseUrl, debugEmulator())
                require(DriverTrackingBuild.validToken(it.token))
            }
            if (credential != null) {
                state = "STOPPED"
                reason = "RECONCILIATION_REQUIRED"
            }
        } catch (_: Exception) { state = "ERROR"; reason = "CREDENTIAL_STORAGE_ERROR" }
        // No owner, buffer, lease, or request is restored, even when the diagnostic said TRACKING.
        if (!spike) try {
            store.loadPendingStop()?.let { saved ->
                val (previous, body) = TrackingStopJournal.read(saved)
                pendingStop = PendingStop(previous, DriverCredential("", ""), body)
                state = "STOPPED"; reason = "STOP_RECONCILIATION_REQUIRED"
            }
        } catch (_: Exception) { state = "ERROR"; reason = "STOP_STORAGE_ERROR" }
        if (energyInterrupted) persist()
    }

    fun setVisible(value: Boolean) {
        visible = value
        if (!value && state == "STARTING") stop("UI_NOT_VISIBLE")
        if (value) checkLease()
    }
    fun permission(): String = when {
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> "GRANTED"
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> "APPROXIMATE"
        else -> "DENIED"
    }
    fun notificationsGranted(): Boolean = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun snapshot(): Map<String, Any?> {
        checkLease()
        return snapshotUnchecked()
    }
    private fun snapshotUnchecked(): Map<String, Any?> = linkedMapOf(
        "state" to state, "credentialConfigured" to (credential != null), "permission" to permission(),
        "queued" to queue.size(now()), "accepted" to accepted, "duplicates" to duplicates,
        "rejected" to rejected, "dropped" to (carriedDropped + queue.dropped), "gpsState" to gpsState,
        "leaseExpiresAt" to lease.expiresAt, "reason" to reason, "stopRequestedAt" to stopRequestedAt, "stoppedAt" to stoppedAt,
        "nativeStopCalledAt" to nativeStopCalledAt, "nativeStopCompletedAt" to nativeStopCompletedAt,
        "leaseDeadlineElapsedMs" to lease.deadline.takeIf { lease.expiresAt != null },
        "stopRequestedElapsedMs" to stopRequestedElapsedMs, "stoppedElapsedMs" to stoppedElapsedMs,
        "nativeStopCalledElapsedMs" to nativeStopCalledElapsedMs, "nativeStopCompletedElapsedMs" to nativeStopCompletedElapsedMs,
        "lateCallbacksDiscarded" to lateCallbacksDiscarded,
        "previousNativeStopCalledAt" to previousNativeStopCalledAt, "previousNativeStopCompletedAt" to previousNativeStopCompletedAt,
        "previousNativeStopCalledElapsedMs" to previousNativeStopCalledElapsedMs, "previousNativeStopCompletedElapsedMs" to previousNativeStopCompletedElapsedMs,
        "previousLeaseDeadlineElapsedMs" to previousLeaseDeadlineElapsedMs,
        "diagnosticBootCount" to diagnosticBootCount, "previousDiagnosticBootCount" to previousDiagnosticBootCount,
        "remoteStopAcknowledgedAt" to remoteStopAcknowledgedAt, "processReconciledAt" to processReconciledAt,
        "notificationPermissionGranted" to notificationsGranted(), "stopPending" to (pendingStop != null || stopping),
        "reconciliationPending" to reconcileAfterCancelledStart,
        "energySession" to energy.snapshot(),
    ) + locationDiagnostics()
    private fun locationDiagnostics(): Map<String, Any?> {
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()
        val wallMs = wall()
        val fixElapsed = lastLocationElapsedNanos
        val capturedAt = lastLocationCapturedAt
        return linkedMapOf(
            "diagnosticSnapshotElapsedMs" to elapsedNanos / 1_000_000L,
            "locationCallbacksReceived" to locationCallbacksReceived,
            "locationSamplesOffered" to locationSamplesOffered,
            "lastLocationCallbackElapsedMs" to lastLocationCallbackElapsedMs,
            "lastLocationCallbackDisposition" to lastLocationCallbackDisposition,
            "lastLocationAccuracyM" to lastLocationAccuracyM,
            "lastLocationAccuracyState" to lastLocationAccuracyState,
            "lastLocationAgeElapsedAtCallbackMs" to lastLocationAgeElapsedAtCallbackMs,
            "lastLocationAgeWallAtCallbackMs" to lastLocationAgeWallAtCallbackMs,
            "lastLocationElapsedStateAtCallback" to lastLocationElapsedStateAtCallback,
            "lastLocationWallStateAtCallback" to lastLocationWallStateAtCallback,
            // Do not clamp future fixes to age zero or replace their timestamps.
            "lastLocationAgeElapsedMs" to fixElapsed?.takeIf { it > 0 }?.let { (elapsedNanos - it) / 1_000_000L },
            "lastLocationElapsedState" to fixElapsed?.let { diagnosticTimeState(it, elapsedNanos) },
            "lastLocationAgeWallMs" to capturedAt?.takeIf { it > 0 }?.let { wallMs - it },
            "lastLocationWallState" to capturedAt?.let { diagnosticTimeState(it, wallMs) },
        )
    }
    private fun diagnosticTimeState(value: Long, reference: Long): String = when {
        value <= 0 -> "INVALID"
        value > reference -> "FUTURE"
        else -> "VALID"
    }
    private fun persist() { try { store.saveDiagnostics(snapshotUnchecked()) } catch (_: Exception) { /* No sensitive fallback logs. */ } }
    private fun now(): Long = SystemClock.elapsedRealtime()
    private fun wall(): Long = System.currentTimeMillis()

    fun configure(kind: String, baseUrl: String, token: String, reply: (String?) -> Unit) {
        if (!spike) { reply("ACTION_NOT_ALLOWED"); return }
        if (!visible) { reply("UI_NOT_VISIBLE"); return }
        if (active() || stopping || pendingStop != null || networkPending || http.busy) { reply("BUSY"); return }
        if (!DriverTrackingBuild.acceptsKind(kind) || !DriverTrackingBuild.validToken(token)) { reply("INVALID_ARGUMENT"); return }
        val base = try { DriverTrackingBuild.baseUrl(baseUrl, debugEmulator()) } catch (_: Exception) { reply("INVALID_BASE_URL"); return }
        val value = DriverCredential(base, token)
        try { store.saveCredential(value) } catch (_: Exception) { reply("CREDENTIAL_STORAGE_ERROR"); return }
        epoch++; credential = value; credentialExpiry = 0; owner = null; activationGuard.terminalOwner = null
        lease.clear(); state = "READY"; reason = null; persist()
        sync(reply)
    }
    fun sync(reply: (String?) -> Unit) {
        checkLease()
        val value = credential ?: run { reply("NOT_CONFIGURED"); return }
        if (state == "STARTING" || stopping) { reply("BUSY"); return }
        // A restored stop has no sensor to restart. Keep the pending snapshot
        // observable while the existing pump reconciles and retries its ACK.
        if (pendingStop != null) { pump(); reply(null); return }
        if (networkPending || http.busy) { reply("BUSY"); return }
        val token = epoch
        send(value, "/driver-app/v1/status", null) { response ->
            if (token != epoch) { reply("CANCELLED"); return@send }
            if (!successful(response)) {
                val code = handleFailure(response)
                if (code != "NETWORK_RETRY") reconcileAfterCancelledStart = false
                reply(code); return@send
            }
            val status = try { TrackingWire.status(response.body!!) } catch (_: Exception) {
                reconcileAfterCancelledStart = false; fail("INVALID_CONTRACT"); reply("INVALID_CONTRACT"); return@send
            }
            credentialExpiry = status.credentialExpiresAt
            reconcileAfterCancelledStart = false
            if (active()) {
                if (owner != status.owner || status.sessionState != "RUNNING") stop("REMOTE_STOP")
                else try { applyLease(status.serverTime, status.leaseExpiresAt!!, response.requestStarted); gpsState = status.gpsState }
                catch (_: Exception) { stop("INVALID_LEASE") }
            } else if (status.owner != null && status.sessionState in setOf("RUNNING", "STOP_PENDING")) {
                reconcile(status.owner)
            } else {
                activationGuard.observe(status)
                if (state != "STOPPED") state = "READY"
                reason = null; gpsState = status.gpsState
            }
            persist(); reply(null)
        }
    }
    fun start(intentId: String, activation: String? = null, reply: (String?) -> Unit) {
        if (!spike && activation == null) { reply("ACTION_NOT_ALLOWED"); return }
        if (!visible) { reply("UI_NOT_VISIBLE"); return }
        if (permission() != "GRANTED") { reply("PRECISE_LOCATION_REQUIRED"); return }
        if (!notificationsGranted()) { reply("NOTIFICATION_PERMISSION_REQUIRED"); return }
        val value = credential ?: run { reply("NOT_CONFIGURED"); return }
        try { TrackingProtocol.id(intentId) } catch (_: Exception) { reply("INVALID_ARGUMENT"); return }
        if (active() || stopping || pendingStop != null || reconcileAfterCancelledStart || http.busy || networkPending) { reply("BUSY"); return }
        epoch++; val token = epoch
        state = "STARTING"; reason = null; startingReply = reply
        stopRequestedAt = null; stoppedAt = null; nativeStopCalledAt = null; nativeStopCompletedAt = null
        stopRequestedElapsedMs = null; stoppedElapsedMs = null; nativeStopCalledElapsedMs = null; nativeStopCompletedElapsedMs = null
        remoteStopAcknowledgedAt = null; lease.clear(); gpsState = "UNKNOWN"; persist()
        // Reconciliation is online and mandatory before *each* explicit activation.
        send(value, "/driver-app/v1/status", null) { response ->
            if (token != epoch) return@send
            if (!successful(response)) { finishStart(handleFailure(response)); return@send }
            val status = try { TrackingWire.status(response.body!!) } catch (_: Exception) {
                fail("INVALID_CONTRACT"); finishStart("INVALID_CONTRACT"); return@send
            }
            credentialExpiry = status.credentialExpiresAt
            if (!visible) { stop("UI_NOT_VISIBLE"); return@send }
            if (spike && status.owner != null && status.sessionState in setOf("RUNNING", "STOP_PENDING")) {
                reconcile(status.owner); finishStart("NEW_INTENT_REQUIRED"); return@send
            }
            if (status.owner != null && status.sessionState == "STOP_PENDING") {
                reconcile(status.owner); finishStart("NEW_INTENT_REQUIRED"); return@send
            }
            activationGuard.observe(status)
            activate(value, intentId, token, activation)
        }
    }
    private fun activate(value: DriverCredential, intentId: String, token: Long, activation: String?) {
        send(value, "/driver-app/v1/start-intents/$intentId/activate", activation ?: "{\"schemaVersion\":1}") { response ->
            if (token != epoch) return@send
            if (!successful(response)) { finishStart(handleFailure(response)); return@send }
            val control = try { TrackingWire.control(response.body!!) } catch (_: Exception) {
                fail("INVALID_CONTRACT"); finishStart("INVALID_CONTRACT"); return@send
            }
            owner = control.owner
            if (!activationGuard.accepts(control)) { stop("SESSION_TERMINAL"); return@send }
            try { applyLease(control.serverTime, control.leaseExpiresAt, response.requestStarted) }
            catch (_: Exception) { stop("INVALID_LEASE"); return@send }
            if (!visible || permission() != "GRANTED" || !notificationsGranted()) { stop("START_NOT_ALLOWED"); return@send }
            sequence = 0; batch = null; batchOutstanding = emptySet(); queue.clear(); nextNetworkAt = 0; gpsState = control.gpsState
            serviceRequested = true
            try {
                val intent = Intent(context, DriverTrackingService::class.java).setAction(DriverTrackingService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (_: Exception) { serviceStartFailed() }
        }
    }
    fun expectsService(): Boolean = serviceRequested && state == "STARTING" && owner != null && visible && checkLease()
    fun owns(value: DriverTrackingService): Boolean = service === value
    fun validToken(token: Long): Boolean = token == epoch && active() && owner != null && checkLease()
    fun serviceStarted(value: DriverTrackingService) {
        if (!expectsService()) { value.finish(); return }
        serviceRequested = false; service = value
        value.begin(epoch, lease.remaining(now()))
    }
    fun serviceStartFailed() { serviceRequested = false; stop("FOREGROUND_SERVICE_FAILED") }
    fun providerStarted(token: Long) {
        if (!validToken(token)) return
        if (!energy.observing) {
            val initial = energyMonitor.begin()
            energy.begin(initial.snapshot, initial.observerAvailable)
        }
        state = "TRACKING"; reason = null; nextHeartbeatAt = now() + 30_000; persist()
        finishStart(null); pump()
    }
    fun providerFailed(token: Long) { if (token == epoch && active()) stop("LOCATION_PROVIDER_FAILED") }
    fun serviceDestroyed(value: DriverTrackingService) {
        if (service === value) { stop("SERVICE_DESTROYED"); service = null }
    }
    private fun active(): Boolean = state == "STARTING" || state == "TRACKING"
    private fun checkLease(): Boolean {
        if (active() && owner != null && lease.expiresAt != null && lease.remaining(now()) <= 0) {
            stop("LEASE_EXPIRED"); return false
        }
        return true
    }
    private var leaseTimer: Runnable? = null
    private fun applyLease(serverTime: Long, expiry: Long, requestStarted: Long) {
        val oldExpiry = lease.expiresAt
        lease.update(serverTime, expiry, requestStarted, now(), credentialExpiry)
        leaseTimer?.let { main.removeCallbacks(it) }
        leaseTimer = Runnable { checkLease() }.also { main.postDelayed(it, lease.remaining(now())) }
        if (expiry != oldExpiry && state == "TRACKING") service?.renew(epoch, lease.remaining(now()))
    }
    fun location(token: Long, value: Location) {
        // One invocation per delivered Location, including members of a provider batch.
        locationCallbacksReceived++
        val callbackElapsedNanos = SystemClock.elapsedRealtimeNanos()
        val callbackWallMs = wall()
        lastLocationCallbackElapsedMs = callbackElapsedNanos / 1_000_000L
        val accuracy = value.accuracy.toDouble()
        lastLocationAccuracyM = accuracy.takeIf { value.hasAccuracy() && it.isFinite() && it >= 0 }
        lastLocationAccuracyState = when {
            !value.hasAccuracy() -> "MISSING"
            !accuracy.isFinite() || accuracy < 0 -> "INVALID"
            else -> "VALID"
        }
        lastLocationElapsedNanos = value.elapsedRealtimeNanos
        lastLocationCapturedAt = value.time
        lastLocationAgeElapsedAtCallbackMs = value.elapsedRealtimeNanos.takeIf { it > 0 }?.let { (callbackElapsedNanos - it) / 1_000_000L }
        lastLocationAgeWallAtCallbackMs = value.time.takeIf { it > 0 }?.let { callbackWallMs - it }
        lastLocationElapsedStateAtCallback = diagnosticTimeState(value.elapsedRealtimeNanos, callbackElapsedNanos)
        lastLocationWallStateAtCallback = diagnosticTimeState(value.time, callbackWallMs)
        lastLocationCallbackDisposition = "RECEIVED"
        if (!validToken(token) || state != "TRACKING") {
            lastLocationCallbackDisposition = "DISCARDED"; discardedCallback(); return
        }
        if (permission() != "GRANTED") {
            lastLocationCallbackDisposition = "PERMISSION_REVOKED"; stop("LOCATION_PERMISSION_REVOKED"); return
        }
        val sample = TrackingSample(++sequence, value.time, value.latitude, value.longitude,
            if (value.hasAccuracy()) value.accuracy.toDouble() else Double.NaN, value.elapsedRealtimeNanos / 1_000_000L,
            if (value.hasSpeed()) value.speed.toDouble() else null,
            if (value.hasBearing()) value.bearing.toDouble() else null)
        locationSamplesOffered++; lastLocationCallbackDisposition = "OFFERED"
        queue.offer(sample, now()); pump()
    }
    fun discardedCallback() { lateCallbacksDiscarded++; persist() }
    fun stop(stopReason: String = "USER_STOP") {
        if (stopping) return
        if (!active() && service == null && owner == null) {
            if (stopRequestedAt == null) { stopRequestedAt = wall(); stoppedAt = wall(); stopRequestedElapsedMs = now(); stoppedElapsedMs = now() }
            state = if (credential == null) "UNCONFIGURED" else "STOPPED"; reason = stopReason; persist(); pump(); return
        }
        val stoppedOwner = owner
        val stoppedCredential = credential
        if (state == "STARTING" && stoppedOwner == null) reconcileAfterCancelledStart = true
        epoch++; state = "STOPPED"; reason = stopReason; serviceRequested = false; stopping = true
        stopRequestedAt = wall(); stopRequestedElapsedMs = now(); stoppedAt = null; stoppedElapsedMs = null; gpsState = "UNKNOWN"
        leaseTimer?.let { main.removeCallbacks(it) }; leaseTimer = null
        queue.clear(); batch = null; batchOutstanding = emptySet(); owner = null; http.cancel(); persist()
        finishStart("START_CANCELLED")
        val sensor = service
        // Invalidate callbacks, clear samples, and invoke native removal *before* a STOPPED packet exists.
        val nativeRequestExisted = sensor?.hasOwnRequest() == true
        if (nativeRequestExisted) { nativeStopCalledAt = wall(); nativeStopCalledElapsedMs = now() }
        persist()
        fun completed(success: Boolean) {
            if (!success) {
                reason = "NATIVE_STOP_FAILED"; persist()
                main.postDelayed({ sensor?.removeOwnRequest { completed(it) } ?: completed(true) }, 1_000L)
                return
            }
            if (nativeRequestExisted) { nativeStopCompletedAt = wall(); nativeStopCompletedElapsedMs = now() }
            energyMonitor.stopConfirmed()?.let { energy.stopConfirmed(it) }
            stoppedAt = wall(); stoppedElapsedMs = now(); stopping = false
            if (service === sensor) service = null
            sensor?.finish()
            if (stoppedOwner != null) activationGuard.terminalOwner = stoppedOwner
            if (stoppedOwner != null && stoppedCredential != null) pendingStop = PendingStop(stoppedOwner, stoppedCredential,
                TrackingWire.state(stoppedOwner, true, stopReason, stopRequestedAt, stoppedAt))
            stopStatusChecked = false; saveStopJournal()
            nextNetworkAt = 0; persist(); pump()
        }
        if (sensor == null) completed(true) else sensor.removeOwnRequest { completed(it) }
    }
    private fun reconcile(remote: SessionOwner) {
        // A recreated process has no local request. These timestamps describe reconciliation now, never process death.
        require(service == null && owner == null)
        finishStart("NEW_INTENT_REQUIRED")
        owner = remote; state = "STOPPED"; processReconciledAt = wall()
        stop("PROCESS_RECREATED")
    }
    private fun finishStart(error: String?) {
        val reply = startingReply; startingReply = null
        if (error != null && state == "STARTING") { state = "ERROR"; reason = error; persist() }
        reply?.invoke(error)
    }
    private fun fail(code: String) {
        if (active() || owner != null) stop(code)
        else { state = "ERROR"; reason = code; persist() }
    }
    private fun successful(result: NativeHttpResult): Boolean = result.failure == null && result.status in 200..299 && result.body != null
    private fun handleFailure(result: NativeHttpResult): String {
        val code = TrackingProtocol.failureCode(result.status, result.failure)
        if (code == "NETWORK_RETRY") {
            reason = code; nextNetworkAt = now() + 2_000L
            main.postDelayed({ pump() }, 2_000L)
        } else fail(code)
        return code
    }
    private fun send(value: DriverCredential, path: String, body: String?, callback: (NativeHttpResult) -> Unit) {
        check(!networkPending && !http.busy)
        networkPending = true
        if (spike) http.execute(value, path, body) { response ->
            main.post { networkPending = false; callback(response); pump() }
        } else {
            val route = if (path == "/driver-app/v1/status") "/driver-app/v1/tracking-status" else path
            val outgoing = try { productionWire.request(route, body) } catch (_: Exception) {
                networkPending = false; callback(NativeHttpResult(0, null, now(), "INVALID_CONTRACT")); return
            }
            sessions.tracking(route, outgoing.first, outgoing.second) { raw ->
                val result = if (raw.status in 200..299 && raw.body != null) try {
                    raw.copy(body = productionWire.response(route, raw.body))
                } catch (_: Exception) { raw.copy(body = null, failure = "INVALID_CONTRACT") } else raw
                main.post { networkPending = false; callback(result); pump() }
            }
        }
    }
    private fun saveStopJournal() {
        if (spike) return
        try {
            val pending = pendingStop
            store.savePendingStop(pending?.let { TrackingStopJournal.record(it.owner, it.body) })
        } catch (_: Exception) { reason = "STOP_STORAGE_ERROR"; persist() }
    }
    private fun pump() {
        checkLease()
        if (networkPending || http.busy || now() < nextNetworkAt) return
        val stopPacket = pendingStop
        if (stopPacket != null) {
            if (!spike && !stopStatusChecked) {
                send(stopPacket.credential, "/driver-app/v1/status", null) { response ->
                    if (pendingStop !== stopPacket) return@send
                    if (!successful(response)) {
                        val code = TrackingProtocol.failureCode(response.status, response.failure)
                        if (code == "NETWORK_RETRY") handleFailure(response)
                        else {
                            reason = code; nextNetworkAt = now() + 30_000
                            main.postDelayed({ pump() }, 30_000); persist()
                        }
                        return@send
                    }
                    val status = try { TrackingWire.status(response.body!!) } catch (_: Exception) { reason = "INVALID_CONTRACT"; nextNetworkAt = now() + 30_000; main.postDelayed({ pump() }, 30_000); persist(); return@send }
                    if (status.owner != stopPacket.owner) { reason = "SESSION_OWNER_MISMATCH"; nextNetworkAt = now() + 30_000; main.postDelayed({ pump() }, 30_000); persist(); return@send }
                    if (status.sessionState == "STOPPED") {
                        pendingStop = null; remoteStopAcknowledgedAt = wall(); reason = "STOP_CONFIRMED"; saveStopJournal()
                    } else stopStatusChecked = true
                    persist()
                }
                return
            }
            send(stopPacket.credential, "/driver-app/v1/tracking-sessions/${stopPacket.owner.id}/state", stopPacket.body) { response ->
                if (pendingStop !== stopPacket) return@send
                if (successful(response)) {
                    val control = try { TrackingWire.control(response.body!!) } catch (_: Exception) { null }
                    if (control == null || control.owner != stopPacket.owner || control.action != "STOP") {
                        if (spike) pendingStop = null
                        reason = "INVALID_CONTRACT"; nextNetworkAt = now() + 30_000
                    } else { pendingStop = null; remoteStopAcknowledgedAt = wall(); reason = "STOP_CONFIRMED" }
                } else {
                    val code = TrackingProtocol.failureCode(response.status, response.failure)
                    if (code == "NETWORK_RETRY") handleFailure(response)
                    else { if (spike) pendingStop = null; reason = code; nextNetworkAt = now() + 30_000 }
                }
                stopStatusChecked = false; saveStopJournal(); persist()
                if (!spike && pendingStop != null && now() < nextNetworkAt) main.postDelayed({ pump() }, (nextNetworkAt - now()).coerceAtLeast(1))
            }
            return
        }
        if (reconcileAfterCancelledStart && !stopping) { sync { }; return }
        if (state != "TRACKING" || !checkLease()) return
        if (permission() != "GRANTED" || !notificationsGranted()) { stop("PERMISSION_REVOKED"); return }
        val current = owner ?: return
        val value = credential ?: return
        val token = epoch
        if (now() >= nextHeartbeatAt) {
            send(value, "/driver-app/v1/tracking-sessions/${current.id}/state", TrackingWire.state(current, false)) { response ->
                if (token != epoch || !checkLease()) return@send
                if (!successful(response)) { handleFailure(response); return@send }
                val control = try { TrackingWire.control(response.body!!) } catch (_: Exception) { fail("INVALID_CONTRACT"); return@send }
                if (!applyControl(current, control, response.requestStarted)) return@send
                nextHeartbeatAt = now() + 30_000L; persist()
            }
            return
        }
        if (batch != null && !queue.containsAll(batchOutstanding, now())) { batch = null; batchOutstanding = emptySet() }
        if (batch == null) {
            val samples = queue.batch(now())
            if (samples.isEmpty()) {
                main.removeCallbacks(heartbeatPump); main.postDelayed(heartbeatPump, (nextHeartbeatAt - now()).coerceAtLeast(1)); return
            }
            batch = TrackingWire.batch(current, samples, UUID.randomUUID().toString(), wall())
            batchOutstanding = batch!!.sequences
        }
        val packet = batch ?: return
        send(value, "/driver-app/v1/tracking-sessions/${current.id}/samples", packet.body) { response ->
            if (token != epoch || !checkLease()) return@send
            if (!successful(response)) { handleFailure(response); return@send }
            val receipt = try { TrackingWire.receipt(response.body!!) } catch (_: Exception) { fail("INVALID_CONTRACT"); return@send }
            if (!applyControl(current, receipt.control, response.requestStarted)) return@send
            try { queue.acknowledge(receipt.accepted, receipt.duplicates, receipt.rejected, packet.sequences) }
            catch (_: Exception) { fail("INVALID_CONTRACT"); return@send }
            accepted += receipt.accepted.intersect(batchOutstanding).size
            duplicates += receipt.duplicates.intersect(batchOutstanding).size
            rejected += receipt.rejected.intersect(batchOutstanding).size
            batchOutstanding = batchOutstanding - receipt.accepted - receipt.duplicates - receipt.rejected
            if (batchOutstanding.isEmpty()) batch = null
            else {
                // Keep the exact original body for retry. Explicitly confirmed members may have left the queue.
                nextNetworkAt = now() + 2_000L; main.postDelayed({ pump() }, 2_000L)
            }
            reason = null; persist()
        }
    }
    private val heartbeatPump = Runnable { pump() }
    private fun applyControl(expected: SessionOwner, control: RemoteControl, requestStarted: Long): Boolean {
        if (expected != control.owner) { stop("SESSION_OWNER_MISMATCH"); return false }
        if (control.action == "STOP") { stop("REMOTE_STOP"); return false }
        try { applyLease(control.serverTime, control.leaseExpiresAt, requestStarted) }
        catch (_: Exception) { stop("INVALID_LEASE"); return false }
        gpsState = control.gpsState; return true
    }
    companion object {
        @Volatile private var instance: DriverTrackingRuntime? = null
        fun get(context: Context): DriverTrackingRuntime = instance ?: synchronized(this) {
            instance ?: DriverTrackingRuntime(context.applicationContext).also { instance = it }
        }
        private fun debugEmulator(): Boolean = BuildConfig.DEBUG && (Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") || Build.MODEL.contains("Emulator") || Build.MODEL.contains("Android SDK") ||
            Build.HARDWARE in setOf("goldfish", "ranchu"))
    }
}
