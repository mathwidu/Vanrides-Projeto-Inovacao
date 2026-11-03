package com.example.vanrides_frontend_dev.driver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.example.vanrides_frontend_dev.BuildConfig
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/** Only the driver flavor loads this bridge. Deep links never activate the collector. */
class DriverActivity : FlutterActivity() {
    private val runtime by lazy { DriverTrackingRuntime.get(this) }
    private var channel: MethodChannel? = null
    private var sessionChannel: MethodChannel? = null
    private var launchChannel: MethodChannel? = null
    private var permissionReply: MethodChannel.Result? = null
    private var resumed = false
    private var awaitingNotificationPermission = false
    private var iconLaunch = false
    private var sessionUiObserved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        sanitizeEntry(intent)
        super.onCreate(savedInstanceState)
        // Prevent pairing codes from entering screenshots or the recent-apps snapshot; TalkBack is unaffected.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    override fun onNewIntent(intent: Intent) {
        val sanitized = sanitizeEntry(intent)
        super.onNewIntent(sanitized)
        setIntent(sanitized)
    }
    private fun sanitizeEntry(incoming: Intent): Intent {
        val (sanitized, fromIcon) = DriverSessionIntents.sanitize(incoming, packageName,
            DriverActivity::class.java.name, BuildConfig.PAIRING_LINK_HOST)
        iconLaunch = fromIcon
        setIntent(sanitized)
        return sanitized
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "vanrides.driver/tracking.v1").also {
            it.setMethodCallHandler { call, result -> handle(call, result) }
        }
        val sessions = DriverSessionBridge(this, { resumed }, { iconLaunch }) {
            sessionUiObserved = true
            keepLabScreenVisible()
        }
        sessionChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, DriverSessionProtocol.CHANNEL).also {
            it.setMethodCallHandler(sessions::handle)
        }
        launchChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "dev.matheusduarte.vanrides.driver/launch.v1").also {
            it.setMethodCallHandler { call, result ->
                when {
                    call.method != "isLauncherVisit" -> result.notImplemented()
                    call.arguments != null -> result.error("INVALID_ARGUMENT", "INVALID_ARGUMENT", null)
                    else -> result.success(isLauncherVisit())
                }
            }
        }
    }
    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        fun complete(code: String?) {
            if (code == null) result.success(runtime.snapshot()) else result.error(code, code, null)
        }
        try {
            when (call.method) {
                "getStatus" -> { noArguments(call); result.success(runtime.snapshot()) }
                "syncStatus" -> { noArguments(call); runtime.sync { complete(it) } }
                "stop" -> { noArguments(call); runtime.stop(); result.success(runtime.snapshot()) }
                "requestPermission" -> { noArguments(call); requestTrackingPermission(result) }
                "startRun" -> {
                    val args = arguments(call, setOf("intentId", "expectedVersion", "intentToken"))
                    val id = DriverSessionProtocol.uuid(args["intentId"] as? String ?: throw IllegalArgumentException())
                    val version = args["expectedVersion"]
                    require(version is Int || version is Long)
                    require((version as Number).toLong() in 0..9007199254740991L)
                    val secret = DriverSessionProtocol.token(args["intentToken"] as? String ?: throw IllegalArgumentException())
                    require(DriverSessionProtocol.isRunLink(intent.dataString, BuildConfig.PAIRING_LINK_HOST) &&
                        intent.data?.fragment == "v1.$id.${version.toLong()}.$secret")
                    val body = org.json.JSONObject().put("expectedVersion", version).put("intentToken", secret)
                        .put("foregroundConfirmed", "CONFIRMED").toString()
                    runtime.start(id, body) { complete(it) }
                }
                "start" -> {
                    val args = arguments(call, setOf("intentId"))
                    runtime.start(args["intentId"] as? String ?: throw IllegalArgumentException()) { complete(it) }
                }
                "configureCredential" -> {
                    if (!BuildConfig.GPS_SPIKE_ENABLED) { result.notImplemented(); return }
                    val args = arguments(call, setOf("kind", "baseUrl", "token"))
                    runtime.configure(args["kind"] as? String ?: throw IllegalArgumentException(),
                        args["baseUrl"] as? String ?: throw IllegalArgumentException(),
                        args["token"] as? String ?: throw IllegalArgumentException()) { complete(it) }
                }
                else -> result.notImplemented()
            }
        } catch (_: IllegalArgumentException) { result.error("INVALID_ARGUMENT", "INVALID_ARGUMENT", null) }
        catch (_: Exception) { result.error("NATIVE_ERROR", "NATIVE_ERROR", null) }
    }
    private fun noArguments(call: MethodCall) {
        require(call.arguments == null || (call.arguments as? Map<*, *>)?.isEmpty() == true)
    }
    private fun arguments(call: MethodCall, allowed: Set<String>): Map<*, *> {
        val args = call.arguments as? Map<*, *> ?: throw IllegalArgumentException()
        require(args.keys == allowed); return args
    }
    private fun requestTrackingPermission(result: MethodChannel.Result) {
        if (!resumed) { result.error("UI_NOT_VISIBLE", "UI_NOT_VISIBLE", null); return }
        if (permissionReply != null) { result.error("BUSY", "BUSY", null); return }
        permissionReply = result
        if (runtime.permission() != "GRANTED") {
            // Android 12+ requires coarse and fine together; approximate is reported, never accepted for start.
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION)
        } else requestNotificationPermission()
    }
    private fun requestNotificationPermission() {
        if (runtime.permission() == "GRANTED" && Build.VERSION.SDK_INT >= 33 && !runtime.notificationsGranted()) {
            if (!resumed) { awaitingNotificationPermission = true; return }
            awaitingNotificationPermission = false
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION)
        } else finishPermission()
    }
    private fun finishPermission() {
        awaitingNotificationPermission = false
        val reply = permissionReply; permissionReply = null
        reply?.success(runtime.snapshot())
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION) requestNotificationPermission()
        else if (requestCode == NOTIFICATION_PERMISSION) finishPermission()
    }
    override fun onResume() {
        super.onResume(); resumed = true; runtime.setVisible(true)
        keepLabScreenVisible()
        if (awaitingNotificationPermission && permissionReply != null) requestNotificationPermission()
    }
    override fun onPause() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        resumed = false; runtime.setVisible(false); super.onPause()
    }
    private fun keepLabScreenVisible() {
        // The spike UI never uses session.v1. Battery proof and non-lab builds retain normal screen policy.
        if (BuildConfig.DEBUG && BuildConfig.PAIRING_LAB_ENABLED && resumed && sessionUiObserved)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    internal fun isLauncherVisit(): Boolean = resumed && iconLaunch
    override fun onDestroy() {
        channel?.setMethodCallHandler(null); channel = null
        sessionChannel?.setMethodCallHandler(null); sessionChannel = null
        launchChannel?.setMethodCallHandler(null); launchChannel = null
        permissionReply?.error("CANCELLED", "CANCELLED", null); permissionReply = null
        // Destroying Flutter observation never ends a running foreground service.
        super.onDestroy()
    }
    companion object {
        private const val LOCATION_PERMISSION = 20021
        private const val NOTIFICATION_PERMISSION = 20022
    }
}
