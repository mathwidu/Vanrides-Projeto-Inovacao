package com.example.vanrides_frontend_dev.driver

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Never sticky, never exported, never started by boot, links, or a Flutter widget lifecycle. */
class DriverTrackingService : Service() {
    private lateinit var runtime: DriverTrackingRuntime
    private lateinit var fused: FusedLocationProviderClient
    private var callback: LocationCallback? = null
    private var stoppingCallback: LocationCallback? = null
    private var callbackToken = 0L
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        runtime = DriverTrackingRuntime.get(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runtime.stop("USER_STOP")
            if (!runtime.owns(this)) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || !runtime.expectsService()) { stopSelf(); return START_NOT_STICKY }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Coleta GPS de ensaio", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 1, Intent(this, DriverTrackingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(this, 2, Intent(this, DriverActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
            val notification = builder.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("VanRides · GPS de ensaio").setContentText("Coleta ativa. Toque em Parar para encerrar.")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "Parar", stop).build()).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION_ID, notification)
            runtime.serviceStarted(this)
        } catch (_: Exception) {
            runtime.serviceStartFailed()
            stopSelf()
        }
        return START_NOT_STICKY
    }
    internal fun begin(token: Long, remaining: Long) {
        callbackToken = token
        val requestCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location: Location -> runtime.location(token, location) }
            }
        }
        callback = requestCallback
        request(requestCallback, token, remaining, true)
    }
    internal fun renew(token: Long, remaining: Long) {
        if (callbackToken == token) callback?.let { request(it, token, remaining, false) }
    }
    private fun request(requestCallback: LocationCallback, token: Long, remaining: Long, first: Boolean) {
        if (remaining <= 0 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            runtime.providerFailed(token); return
        }
        try {
            // A bounded partial lock makes our stop timer run while locked/asleep; FLP's duration is an independent cutoff.
            val power = getSystemService(PowerManager::class.java)
            if (wakeLock == null) wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VanRides:DriverLease").apply { setReferenceCounted(false) }
            wakeLock?.acquire(remaining + 5_000L)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                .setMinUpdateIntervalMillis(1_000L).setMinUpdateDistanceMeters(0f)
                .setMaxUpdateDelayMillis(0L).setMaxUpdateAgeMillis(0L).setDurationMillis(remaining).build()
            fused.requestLocationUpdates(request, requestCallback, Looper.getMainLooper())
                .addOnSuccessListener {
                    if (callback !== requestCallback || !runtime.validToken(token)) { runtime.discardedCallback(); fused.removeLocationUpdates(requestCallback) }
                    else if (first) runtime.providerStarted(token)
                }.addOnFailureListener { if (callback === requestCallback) runtime.providerFailed(token) }
        } catch (_: Exception) { runtime.providerFailed(token) }
    }
    internal fun hasOwnRequest(): Boolean = callback != null || stoppingCallback != null
    internal fun removeOwnRequest(completed: (Boolean) -> Unit) {
        val old = callback ?: stoppingCallback
        callback = null
        stoppingCallback = old
        try {
            if (old == null) completed(true)
            else fused.removeLocationUpdates(old).addOnCompleteListener {
                if (it.isSuccessful && stoppingCallback === old) stoppingCallback = null
                completed(it.isSuccessful)
            }
        } catch (_: Exception) { completed(false) }
    }
    internal fun finish() {
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        runtime.serviceDestroyed(this)
        // Keep an in-progress removal callback until its Task confirms the actual native stop.
        if (callback != null) removeOwnRequest { }
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        super.onDestroy()
    }
    companion object {
        internal const val ACTION_START = "vanrides.driver.tracking.START"
        internal const val ACTION_STOP = "vanrides.driver.tracking.STOP"
        private const val CHANNEL = "driver-gps-spike-v1"
        private const val NOTIFICATION_ID = 20020
    }
}
