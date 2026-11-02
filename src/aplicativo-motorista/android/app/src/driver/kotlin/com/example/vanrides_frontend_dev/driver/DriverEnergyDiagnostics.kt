package com.example.vanrides_frontend_dev.driver

/** Coarse battery metadata only. This is not an energy meter or a tracking owner. */
internal data class DriverEnergySnapshot(
    val elapsedMs: Long,
    val batteryPercent: Int?,
    val chargeState: String,
    val powerSource: String,
    val powerSaveMode: Boolean?,
    val bootCount: Int?,
) {
    val unplugged: Boolean get() = powerSource == "NONE" && chargeState in setOf("DISCHARGING", "NOT_CHARGING", "FULL")
    val powerUncertain: Boolean get() = powerSource == "UNKNOWN" ||
        (powerSource == "NONE" && chargeState in setOf("UNKNOWN", "CHARGING"))

    fun toMap(): Map<String, Any> = buildMap {
        put("elapsedMs", elapsedMs)
        batteryPercent?.let { put("batteryPercent", it) }
        put("chargeState", chargeState); put("powerSource", powerSource)
        powerSaveMode?.let { put("powerSaveMode", it) }
        bootCount?.let { put("bootCount", it) }
    }

    companion object {
        val keys = setOf("elapsedMs", "batteryPercent", "chargeState", "powerSource", "powerSaveMode", "bootCount")
        private val chargeStates = setOf("UNKNOWN", "CHARGING", "DISCHARGING", "NOT_CHARGING", "FULL")
        private val powerSources = setOf("UNKNOWN", "NONE", "AC", "USB", "WIRELESS", "DOCK", "OTHER")
        fun fromMap(data: Map<*, *>): DriverEnergySnapshot? = runCatching {
            val elapsed = energyInteger(data["elapsedMs"]) ?: error("INVALID_ENERGY_METADATA")
            val percent = data["batteryPercent"]?.let { requireNotNull(energyInteger(it)).also { value -> require(value in 0..100) }.toInt() }
            val charge = data["chargeState"] as? String; require(charge in chargeStates)
            val source = data["powerSource"] as? String; require(source in powerSources)
            val saver = data["powerSaveMode"]?.let { require(it is Boolean); it }
            val boot = data["bootCount"]?.let { requireNotNull(energyInteger(it)).also { value -> require(value <= Int.MAX_VALUE) }.toInt() }
            DriverEnergySnapshot(elapsed, percent, charge!!, source!!, saver, boot)
        }.getOrNull()
    }
}

/** One current/last session; no IDs, location, wall clock, rates, or acceptance decisions. */
internal class DriverEnergyDiagnostics {
    private var session: Session? = null
    private var lastObserved: DriverEnergySnapshot? = null
    private var lastKnownPowerSaveMode: Boolean? = null
    val observing: Boolean get() = session?.state == "OBSERVING"

    fun restore(data: Map<*, *>?): Boolean {
        lastObserved = null; lastKnownPowerSaveMode = null
        session = data?.let { Session.fromMap(it) }
        val old = session ?: return false
        if (old.state != "OBSERVING") return false
        // Reopening the app cannot reconstruct when collection or battery observation ended.
        session = old.copy(state = "INCOMPLETE", unpluggedWindowState =
            if (old.unpluggedWindowState == "OBSERVING") "INCOMPLETE" else old.unpluggedWindowState)
        return true
    }

    fun begin(sample: DriverEnergySnapshot, observerAvailable: Boolean) {
        if (observing) return
        val window = when {
            !observerAvailable -> "UNAVAILABLE"
            sample.powerUncertain -> "POWER_UNKNOWN"
            sample.unplugged -> "OBSERVING"
            else -> "NOT_STARTED"
        }
        session = Session("OBSERVING", if (observerAvailable) "SESSION_BROADCASTS" else "SNAPSHOTS_ONLY",
            sample, unpluggedStart = sample.takeIf { window == "OBSERVING" },
            unpluggedWindowState = window, powerStateUncertain = sample.powerUncertain)
        lastObserved = sample
        lastKnownPowerSaveMode = sample.powerSaveMode
    }

    /** Returns true only when an exported marker changed; battery broadcasts do not cause polling. */
    fun observe(sample: DriverEnergySnapshot): Boolean {
        val old = session?.takeIf { observing } ?: return false
        val previous = lastObserved ?: old.start
        if (sample.elapsedMs < previous.elapsedMs ||
            (old.start.bootCount != null && sample.bootCount != null && old.start.bootCount != sample.bootCount)) {
            session = old.copy(state = "INCOMPLETE", unpluggedWindowState =
                if (old.unpluggedWindowState == "OBSERVING") "INCOMPLETE" else old.unpluggedWindowState)
            return true
        }
        lastObserved = sample
        val saverChanged = sample.powerSaveMode?.let { current ->
            val changed = lastKnownPowerSaveMode?.let { it != current } ?: false
            lastKnownPowerSaveMode = current
            changed
        } ?: false
        var next = old.copy(
            powerSaveModeChanged = old.powerSaveModeChanged || saverChanged,
            powerStateUncertain = old.powerStateUncertain || sample.powerUncertain,
        )
        if (old.observationMode == "SESSION_BROADCASTS") {
            next = when {
                sample.powerUncertain && old.unpluggedWindowState in setOf("NOT_STARTED", "OBSERVING") ->
                    next.copy(unpluggedWindowState = "POWER_UNKNOWN", unpluggedEnd = sample.takeIf { old.unpluggedStart != null })
                old.unpluggedWindowState == "NOT_STARTED" && sample.unplugged ->
                    next.copy(unpluggedWindowState = "OBSERVING", unpluggedStart = sample)
                old.unpluggedWindowState == "OBSERVING" && !sample.unplugged ->
                    next.copy(unpluggedWindowState = "POWER_RETURNED", unpluggedEnd = sample)
                else -> next
            }
        }
        session = next
        return next != old
    }

    fun stopConfirmed(sample: DriverEnergySnapshot) {
        if (!observing) return
        observe(sample)
        val old = session?.takeIf { observing } ?: return
        session = old.copy(state = "STOP_CONFIRMED", stop = sample,
            unpluggedWindowState = if (old.unpluggedWindowState == "OBSERVING") "STOP_CONFIRMED" else old.unpluggedWindowState,
            unpluggedEnd = if (old.unpluggedWindowState == "OBSERVING") sample else old.unpluggedEnd)
    }

    fun snapshot(): Map<String, Any>? = session?.toMap()

    private data class Session(
        val state: String,
        val observationMode: String,
        val start: DriverEnergySnapshot,
        val stop: DriverEnergySnapshot? = null,
        val unpluggedStart: DriverEnergySnapshot? = null,
        val unpluggedEnd: DriverEnergySnapshot? = null,
        val unpluggedWindowState: String,
        val powerSaveModeChanged: Boolean = false,
        val powerStateUncertain: Boolean = false,
    ) {
        fun toMap(): Map<String, Any> = buildMap {
            put("state", state); put("observationMode", observationMode); put("start", start.toMap())
            stop?.let { put("stop", it.toMap()) }
            unpluggedStart?.let { put("unpluggedStart", it.toMap()) }
            unpluggedEnd?.let { put("unpluggedEnd", it.toMap()) }
            put("unpluggedWindowState", unpluggedWindowState)
            put("powerSaveModeChanged", powerSaveModeChanged); put("powerStateUncertain", powerStateUncertain)
        }

        companion object {
            fun fromMap(data: Map<*, *>): Session? = runCatching {
                val state = data["state"] as? String; require(state in setOf("OBSERVING", "STOP_CONFIRMED", "INCOMPLETE"))
                val mode = data["observationMode"] as? String; require(mode in setOf("SESSION_BROADCASTS", "SNAPSHOTS_ONLY"))
                val window = data["unpluggedWindowState"] as? String
                require(window in setOf("NOT_STARTED", "OBSERVING", "STOP_CONFIRMED", "POWER_RETURNED", "POWER_UNKNOWN", "INCOMPLETE", "UNAVAILABLE"))
                fun sample(key: String): DriverEnergySnapshot? = data[key]?.let {
                    require(it is Map<*, *>); requireNotNull(DriverEnergySnapshot.fromMap(it))
                }
                val start = requireNotNull(sample("start")); val stop = sample("stop")
                require((state == "STOP_CONFIRMED") == (stop != null))
                val saverChanged = data["powerSaveModeChanged"]; require(saverChanged is Boolean)
                val uncertain = data["powerStateUncertain"]; require(uncertain is Boolean)
                Session(state!!, mode!!, start, stop, sample("unpluggedStart"), sample("unpluggedEnd"), window!!, saverChanged, uncertain)
            }.getOrNull()
        }
    }

    companion object {
        val scalarKeys = setOf("state", "observationMode", "unpluggedWindowState", "powerSaveModeChanged", "powerStateUncertain")
        val snapshotKeys = setOf("start", "stop", "unpluggedStart", "unpluggedEnd")
        fun sanitizeSession(data: Map<*, *>): Map<String, Any>? = Session.fromMap(data)?.toMap()
    }
}

private fun energyInteger(value: Any?): Long? = when (value) {
    is Int -> value.toLong().takeIf { it >= 0 }
    is Long -> value.takeIf { it >= 0 }
    else -> null
}
