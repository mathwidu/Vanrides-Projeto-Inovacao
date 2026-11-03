package com.example.vanrides_frontend_dev.driver

import org.junit.Assert.*
import org.junit.Test

/** Compiled twice by the host harness and once for the exact Gradle-selected source set. */
class DriverTrackingBuildTest {
    @Test fun selectedModeCannotAccidentallyEnableTheOtherTransport() {
        val origin = "https://gps-spike.vanrides.matheusduarte.dev.br"
        val token = "A".repeat(43)
        if (DriverTrackingBuild.SPIKE_ENABLED) {
            assertNotNull(Class.forName("com.example.vanrides_frontend_dev.driver.DriverSpikeHttpClient"))
            assertTrue(DriverTrackingBuild.acceptsKind("TEST_BEARER"))
            assertTrue(DriverTrackingBuild.validToken(token))
            assertEquals(origin, DriverTrackingBuild.baseUrl(origin, false))
            try { DriverTrackingBuild.baseUrl("https://other.example", false); fail("Expected closed origin") }
            catch (_: IllegalArgumentException) { }
        } else {
            try { Class.forName("com.example.vanrides_frontend_dev.driver.DriverSpikeHttpClient"); fail("MVP classpath cannot contain030 transport") }
            catch (_: ClassNotFoundException) { }
            assertFalse(DriverTrackingBuild.acceptsKind("TEST_BEARER"))
            assertFalse(DriverTrackingBuild.validToken(token))
            try { DriverTrackingBuild.baseUrl(origin, false); fail("MVP cannot configure a lab endpoint") }
            catch (_: IllegalArgumentException) { }
            val disabled = DriverTrackingBuild.transport { fail("MVP cannot execute030 transport") }
            assertFalse(disabled.busy)
            disabled.cancel()
            try { disabled.execute(DriverCredential(origin, token), "/driver-app/v1/status", null) { fail("No transport callback allowed") }; fail("MVP cannot transmit bearer030") }
            catch (_: IllegalStateException) { }
        }
    }
}
