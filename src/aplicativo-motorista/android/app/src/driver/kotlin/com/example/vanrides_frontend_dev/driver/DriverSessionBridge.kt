package com.example.vanrides_frontend_dev.driver

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.vanrides_frontend_dev.BuildConfig
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** The singleton serializes all session effects independently of Flutter/Activity lifetime. */
internal class AndroidDriverSession private constructor(context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private val origin: String? = try {
        val configured = BuildConfig.PAIRING_API_ORIGIN
        if (configured.isEmpty()) null else {
            if (BuildConfig.PAIRING_LAB_ENABLED) require(BuildConfig.DEBUG && context.packageName.endsWith(".lab") && configured == DriverSessionProtocol.LAB_ORIGIN)
            DriverSessionProtocol.origin(configured)
        }
    } catch (_: Exception) { null }
    private val core = DriverSessionCore(origin, BuildConfig.VERSION_NAME, AndroidDriverSessionKeys(),
        AndroidDriverSessionStore(context), if (origin == null) DriverSessionTransport { throw DriverSessionFailure("UNCONFIGURED") }
        else AndroidDriverSessionTransport(context, origin))

    fun invoke(method: String, visible: () -> Boolean, iconLaunch: Boolean, result: MethodChannel.Result, pairing: Pair<String,String>? = null) {
        if (method !in methods) { result.notImplemented(); return }
        if (method in setOf("beginPairing", "showPairingCode", "revokeSession", "preparePairingLink", "claimPairingLink", "completePairingReturn") && !visible()) {
            result.error("UI_NOT_VISIBLE", "UI_NOT_VISIBLE", null); return
        }
        if (method == "beginPairing" && !iconLaunch) { result.error("ACTION_NOT_ALLOWED", "ACTION_NOT_ALLOWED", null); return }
        if (!busy.compareAndSet(false, true)) { result.error("BUSY", "BUSY", null); return }
        executor.execute {
            var output: Any? = null
            var failure: String? = null
            try {
                output = when (method) {
                    "getState" -> core.getState()
                    "beginPairing" -> core.beginPairing()
                    "preparePairingLink" -> requireNotNull(pairing).let { core.preparePairingLink(it.first,it.second) }
                    "claimPairingLink" -> requireNotNull(pairing).let { core.claimPairingLink(it.first,it.second) }
                    "syncSession" -> core.syncSession()
                    "refreshSession" -> core.refreshSession()
                    "revokeSession" -> core.revokeSession()
                    "showPairingCode" -> core.showPairingCode()
                    "completePairingReturn" -> core.completePairingReturn()
                    else -> null
                }
            } catch (error: DriverSessionFailure) { failure = error.code.takeIf { it in errors } ?: "NATIVE_ERROR" }
            catch (_: IllegalArgumentException) { failure = "INVALID_CONTRACT" }
            catch (_: org.json.JSONException) { failure = "INVALID_CONTRACT" }
            catch (_: Exception) { failure = "NATIVE_ERROR" }
            main.post {
                busy.set(false)
                val code = failure
                if (code != null) result.error(code, code, null)
                else result.success(if (method == "showPairingCode" && !visible()) null else output)
            }
        }
    }
    fun tracking(path: String, body: String?, idempotency: String?, reply: (NativeHttpResult) -> Unit) {
        val started = android.os.SystemClock.elapsedRealtime()
        if (!busy.compareAndSet(false, true)) { reply(NativeHttpResult(0, null, started, "NETWORK_ERROR")); return }
        executor.execute {
            val result = try {
                val response = core.trackingRequest(path, body?.toByteArray(Charsets.UTF_8), idempotency)
                NativeHttpResult(response.status, response.body.toString(), started)
            } catch (failure: DriverSessionFailure) {
                val code = when (failure.code) {
                    "NETWORK_ERROR", "BUSY" -> "NETWORK_ERROR"
                    "SESSION_EXPIRED", "KEY_UNAVAILABLE", "UNCONFIGURED" -> "AUTH_ERROR"
                    else -> "INVALID_CONTRACT"
                }
                NativeHttpResult(if (code == "AUTH_ERROR") 401 else 0, null, started,
                    if (code == "AUTH_ERROR") null else code)
            } catch (_: Exception) { NativeHttpResult(0, null, started, "INVALID_CONTRACT") }
            main.post { busy.set(false); reply(result) }
        }
    }
    companion object {
        private val methods = setOf("getState", "beginPairing", "syncSession", "refreshSession", "revokeSession", "showPairingCode", "preparePairingLink", "claimPairingLink", "completePairingReturn")
        private val errors = setOf("UNCONFIGURED", "UI_NOT_VISIBLE", "ACTION_NOT_ALLOWED", "BUSY", "KEY_UNAVAILABLE",
            "STORAGE_ERROR", "INVALID_CONTRACT", "NETWORK_ERROR", "SERVER_REJECTED", "RATE_LIMITED", "REDIRECT_REJECTED", "SESSION_EXPIRED", "PAIRING_REVIEW_REQUIRED", "PAIRING_ACTIVE_RUN", "PAIRING_SESSION_CONFLICT")
        @Volatile private var instance: AndroidDriverSession? = null
        fun get(context: Context): AndroidDriverSession = instance ?: synchronized(this) {
            instance ?: AndroidDriverSession(context.applicationContext).also { instance = it }
        }
    }
}

internal class DriverSessionBridge(context: Context, private val visible: () -> Boolean, private val iconLaunch: () -> Boolean,
    private val onSessionUi: () -> Unit = {},
    private val pairingEntry: () -> String? = { null }, private val pairingBlocked: () -> Boolean = { true }) {
    private val runtime = AndroidDriverSession.get(context)
    fun handle(call: MethodCall, result: MethodChannel.Result) {
        if (call.method in setOf("preparePairingLink","claimPairingLink")) {
            try {
                val args = call.arguments as? Map<*, *> ?: throw IllegalArgumentException()
                require(args.keys == setOf("linkId","linkToken"))
                val id = DriverSessionProtocol.uuid(args["linkId"] as? String ?: throw IllegalArgumentException())
                val token = DriverSessionProtocol.token(args["linkToken"] as? String ?: throw IllegalArgumentException())
                val raw = pairingEntry()
                require(DriverSessionProtocol.isPairingLink(raw,BuildConfig.PAIRING_LINK_HOST) && raw!!.substringAfter('#') == "v1.$id.$token")
                if (call.method == "claimPairingLink" && pairingBlocked()) { result.error("PAIRING_ACTIVE_RUN","PAIRING_ACTIVE_RUN",null); return }
                onSessionUi(); runtime.invoke(call.method,visible,iconLaunch(),result,id to token)
            } catch (_: Exception) { result.error("INVALID_ARGUMENT","INVALID_ARGUMENT",null) }
            return
        }
        if (call.arguments != null) { result.error("INVALID_ARGUMENT", "INVALID_ARGUMENT", null); return }
        onSessionUi()
        runtime.invoke(call.method, visible, iconLaunch(), result)
    }
}
