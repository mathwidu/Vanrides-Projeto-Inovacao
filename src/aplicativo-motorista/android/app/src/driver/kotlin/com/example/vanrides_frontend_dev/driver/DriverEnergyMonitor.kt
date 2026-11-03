package com.example.vanrides_frontend_dev.driver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock

/** Registered only between the provider's effective start and confirmed native stop. */
internal class DriverEnergyMonitor(
    private val context: Context,
    private val bootCount: Int?,
    private val onSample: (DriverEnergySnapshot) -> Unit,
) {
    private var receiver: BroadcastReceiver? = null
    private var started = false
    data class Start(val snapshot: DriverEnergySnapshot, val observerAvailable: Boolean)

    fun begin(): Start {
        if (started) return Start(snapshot(batteryIntent()), receiver != null)
        started = true
        val candidate = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (receiver !== this) return
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> onSample(snapshot(intent))
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> onSample(snapshot(batteryIntent()))
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply { addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) }
        return try {
            val sticky = if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(candidate, filter, Context.RECEIVER_NOT_EXPORTED)
                else context.registerReceiver(candidate, filter)
            receiver = candidate
            Start(snapshot(sticky), true)
        } catch (_: Exception) {
            // Missing diagnostics must never block collection or create a fictitious observed window.
            Start(snapshot(batteryIntent()), false)
        }
    }

    fun stopConfirmed(): DriverEnergySnapshot? {
        if (!started) return null
        val final = snapshot(batteryIntent())
        val old = receiver
        receiver = null; started = false
        if (old != null) try { context.unregisterReceiver(old) } catch (_: Exception) { /* Diagnostics cannot block the confirmed stop. */ }
        return final
    }

    private fun batteryIntent(): Intent? = try {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    } catch (_: Exception) { null }

    private fun snapshot(intent: Intent?): DriverEnergySnapshot {
        fun extra(key: String): Int = try { intent?.getIntExtra(key, -1) ?: -1 } catch (_: Exception) { -1 }
        val level = extra(BatteryManager.EXTRA_LEVEL)
        val scale = extra(BatteryManager.EXTRA_SCALE)
        val percent = if (scale > 0 && level in 0..scale) (level.toLong() * 100L / scale).toInt() else null
        val chargeState = when (extra(BatteryManager.EXTRA_STATUS)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            else -> "UNKNOWN"
        }
        val source = when (val plugged = extra(BatteryManager.EXTRA_PLUGGED)) {
            0 -> "NONE"
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "DOCK"
            else -> if (plugged > 0) "OTHER" else "UNKNOWN"
        }
        val saver = try { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode } catch (_: Exception) { null }
        return DriverEnergySnapshot(SystemClock.elapsedRealtime(), percent, chargeState, source, saver, bootCount)
    }
}
