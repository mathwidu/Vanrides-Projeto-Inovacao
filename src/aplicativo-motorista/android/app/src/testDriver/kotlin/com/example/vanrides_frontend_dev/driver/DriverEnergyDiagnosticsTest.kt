package com.example.vanrides_frontend_dev.driver

import org.junit.Assert.*
import org.junit.Test

class DriverEnergyDiagnosticsTest {
    private fun sample(elapsed: Long, percent: Int = 80, source: String = "NONE", saver: Boolean? = false, boot: Int = 7) =
        DriverEnergySnapshot(elapsed, percent, if (source == "NONE") "DISCHARGING" else "CHARGING", source, saver, boot)
    private fun record(energy: DriverEnergyDiagnostics) = requireNotNull(energy.snapshot())

    @Test fun usbStartWaitsForUnpluggingAndStopsAtFirstPowerReturn() {
        val energy = DriverEnergyDiagnostics()
        energy.begin(sample(100, source = "USB"), true)
        assertEquals("NOT_STARTED", record(energy)["unpluggedWindowState"])
        assertFalse(record(energy).containsKey("unpluggedStart"))
        assertTrue(energy.observe(sample(200)))
        val unpluggedStart = record(energy)["unpluggedStart"]
        assertFalse(energy.observe(sample(300, 79))) // A level event causes no additional persisted snapshot.
        assertTrue(energy.observe(sample(3_600_200, 74, "USB")))
        val unpluggedEnd = record(energy)["unpluggedEnd"]
        energy.observe(sample(3_700_000, 75, "USB"))
        energy.observe(sample(3_800_000, 75)) // Never restart a second window in the same session.
        energy.stopConfirmed(sample(3_900_000, 75))
        assertEquals("POWER_RETURNED", record(energy)["unpluggedWindowState"])
        assertEquals(unpluggedStart, record(energy)["unpluggedStart"])
        assertEquals(unpluggedEnd, record(energy)["unpluggedEnd"])
        assertEquals(sample(3_900_000, 75).toMap(), record(energy)["stop"])
        assertEquals("STOP_CONFIRMED", record(energy)["state"])
    }

    @Test fun confirmedStopClosesUnpoweredWindowAndRemainsStableAfterward() {
        val energy = DriverEnergyDiagnostics()
        energy.begin(sample(100), true)
        energy.observe(sample(200, saver = true))
        energy.observe(sample(300, saver = false))
        energy.stopConfirmed(sample(3_600_100, 74))
        val finished = record(energy)
        assertEquals("STOP_CONFIRMED", finished["unpluggedWindowState"])
        assertEquals(true, finished["powerSaveModeChanged"])
        assertEquals(sample(3_600_100, 74).toMap(), finished["unpluggedEnd"])
        assertFalse(energy.observe(sample(3_700_000, 76, "USB")))
        energy.stopConfirmed(sample(3_800_000, 77, "USB"))
        assertEquals(finished, record(energy))
    }

    @Test fun recreatedProcessLeavesOpenObservationIncompleteWithoutInventingEnd() {
        val first = DriverEnergyDiagnostics()
        first.begin(sample(100, source = "USB"), true)
        first.observe(sample(200))
        val restored = DriverEnergyDiagnostics()
        assertTrue(restored.restore(record(first)))
        assertEquals("INCOMPLETE", record(restored)["state"])
        assertEquals("INCOMPLETE", record(restored)["unpluggedWindowState"])
        assertFalse(record(restored).containsKey("stop"))
        assertFalse(record(restored).containsKey("unpluggedEnd"))
        restored.stopConfirmed(sample(500, 79, "USB"))
        assertFalse(record(restored).containsKey("stop"))
    }

    @Test fun missingInitialAndIntermediateSaverReadingsDoNotHideKnownChange() {
        val energy = DriverEnergyDiagnostics()
        energy.begin(sample(100, saver = null), true)
        assertFalse(energy.observe(sample(200, saver = false))) // First known value is a baseline, not a change.
        assertFalse(energy.observe(sample(300, saver = null))) // Missing values do not erase that baseline.
        assertTrue(energy.observe(sample(400, saver = true)))
        assertEquals(true, record(energy)["powerSaveModeChanged"])
        energy.stopConfirmed(sample(500, saver = null))
        assertEquals(true, record(energy)["powerSaveModeChanged"])
    }

    @Test fun neverKnownSaverStaysAbsentAndDoesNotCarryBaselineIntoNextSession() {
        val energy = DriverEnergyDiagnostics()
        energy.begin(sample(100, saver = null), true)
        assertFalse(energy.observe(sample(200, saver = null)))
        energy.stopConfirmed(sample(300, saver = null))
        val unknown = record(energy)
        assertEquals(false, unknown["powerSaveModeChanged"]) // No observed change does not assert stability.
        assertFalse((unknown["start"] as Map<*, *>).containsKey("powerSaveMode"))
        assertFalse((unknown["stop"] as Map<*, *>).containsKey("powerSaveMode"))
        energy.begin(sample(400, saver = true), true)
        energy.stopConfirmed(sample(500, saver = true))
        energy.begin(sample(600, saver = null), true)
        assertFalse(energy.observe(sample(700, saver = false)))
        assertEquals(false, record(energy)["powerSaveModeChanged"])
    }

    @Test fun completedSessionSurvivesRestoreAndIsReplacedOnlyByEffectiveStart() {
        val first = DriverEnergyDiagnostics()
        first.begin(sample(100), true)
        first.stopConfirmed(sample(200, 79))
        val restored = DriverEnergyDiagnostics()
        assertFalse(restored.restore(record(first)))
        assertEquals(record(first), record(restored))
        restored.begin(sample(300, 81, "USB"), true)
        assertEquals("OBSERVING", record(restored)["state"])
        assertEquals(sample(300, 81, "USB").toMap(), record(restored)["start"])
        assertFalse(record(restored).containsKey("stop"))
    }

    @Test fun unavailableObserverKeepsOnlyRawEndpointsWithoutUnpoweredClaim() {
        val energy = DriverEnergyDiagnostics()
        energy.begin(sample(100), false)
        energy.stopConfirmed(sample(3_600_100, 74))
        assertEquals("SNAPSHOTS_ONLY", record(energy)["observationMode"])
        assertEquals("UNAVAILABLE", record(energy)["unpluggedWindowState"])
        assertFalse(record(energy).containsKey("unpluggedStart"))
        assertFalse(record(energy).containsKey("unpluggedEnd"))
    }

    @Test fun unknownPowerEndsObservationConservatively() {
        val energy = DriverEnergyDiagnostics()
        energy.begin(sample(100), true)
        energy.observe(DriverEnergySnapshot(200, null, "UNKNOWN", "UNKNOWN", null, 7))
        val end = record(energy)["unpluggedEnd"]
        energy.observe(sample(300, 79))
        energy.stopConfirmed(sample(400, 78))
        assertEquals("POWER_UNKNOWN", record(energy)["unpluggedWindowState"])
        assertEquals(true, record(energy)["powerStateUncertain"])
        assertEquals(end, record(energy)["unpluggedEnd"])
    }

    @Test fun monotonicOrBootDiscontinuityNeverCreatesConfirmedStop() {
        for (end in listOf(sample(99), sample(200, boot = 8))) {
            val energy = DriverEnergyDiagnostics()
            energy.begin(sample(100), true)
            energy.stopConfirmed(end)
            assertEquals("INCOMPLETE", record(energy)["state"])
            assertFalse(record(energy).containsKey("stop"))
        }
    }

    @Test fun persistenceRebuildsAllowlistAndRejectsMalformedMetadata() {
        val energy = DriverEnergyDiagnostics()
        energy.begin(sample(100), true)
        val dirty = record(energy) + mapOf("sessionId" to "PRIVATE", "approved" to true,
            "start" to (sample(100).toMap() + mapOf("token" to "PRIVATE", "latitude" to 90)))
        assertEquals(record(energy), DriverEnergyDiagnostics.sanitizeSession(dirty))
        assertNull(DriverEnergyDiagnostics.sanitizeSession(dirty + ("start" to (sample(100).toMap() + ("batteryPercent" to 101)))))
        assertNull(DriverEnergyDiagnostics.sanitizeSession(dirty + ("state" to "PRIVATE")))
        assertNull(DriverEnergyDiagnostics.sanitizeSession(dirty + ("state" to "STOP_CONFIRMED")))
    }
}
