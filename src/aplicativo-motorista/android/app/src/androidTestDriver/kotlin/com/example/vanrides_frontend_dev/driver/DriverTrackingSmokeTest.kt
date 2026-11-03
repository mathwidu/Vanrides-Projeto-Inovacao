package com.example.vanrides_frontend_dev.driver

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.vanrides_frontend_dev.BuildConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Debug emulator only. The operator supplies a short lived fixture through private stdin/run-as. */
@RunWith(AndroidJUnit4::class)
class DriverTrackingSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun runtime() = DriverTrackingRuntime.get(context)
    private data class Fixture(val kind: String, val baseUrl: String, val token: String, val startIntentId: String)
    private fun fixture(): Fixture {
        val file = File(context.cacheDir, "GPS_SPIKE_TEST.json")
        return try {
            require(file.isFile && file.length() in 1..4096)
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            require(obj.keys().asSequence().toSet() == setOf("kind", "baseUrl", "token", "startIntentId"))
            require(obj.getString("kind") == "TEST_BEARER")
            require(DriverTrackingBuild.baseUrl(obj.getString("baseUrl"), true) == "http://10.0.2.2:18765")
            require(DriverTrackingBuild.validToken(obj.getString("token")))
            TrackingProtocol.id(obj.getString("startIntentId"))
            Fixture(obj.getString("kind"), obj.getString("baseUrl"), obj.getString("token"), obj.getString("startIntentId"))
        } catch (_: Exception) { throw AssertionError("FIXTURE_INVALID") }
        finally { file.delete() }
    }
    private fun call(timeoutSeconds: Long = 25, operation: (((String?) -> Unit)) -> Unit) {
        val done = CountDownLatch(1)
        val error = AtomicReference<String?>(null)
        instrumentation.runOnMainSync { operation { code -> error.set(code); done.countDown() } }
        assertTrue("METHOD_DEADLINE", done.await(timeoutSeconds, TimeUnit.SECONDS))
        assertTrue("METHOD_FAILED_${error.get() ?: "NONE"}", error.get() == null)
    }
    private fun snapshot(): Map<String, Any?> {
        val value = AtomicReference<Map<String, Any?>>()
        instrumentation.runOnMainSync { value.set(runtime().snapshot()) }
        return value.get()
    }
    private fun awaitSnapshot(timeoutMs: Long, predicate: (Map<String, Any?>) -> Boolean): Map<String, Any?> {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val value = snapshot()
            if (predicate(value)) return value
            Thread.sleep(200)
        }
        throw AssertionError("SNAPSHOT_DEADLINE")
    }
    @Test fun authenticatedGpsReceiptAndNativeStop() {
        val emulator = Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("Emulator") || Build.MODEL.contains("Android SDK") || Build.HARDWARE in setOf("goldfish", "ranchu")
        assumeTrue("DEBUG_EMULATOR_REQUIRED", BuildConfig.DEBUG && emulator && Build.VERSION.SDK_INT >= 28)
        assumeTrue("EXPLICIT_SPIKE_TARGET_REQUIRED", BuildConfig.GPS_SPIKE_ENABLED && DriverTrackingBuild.SPIKE_ENABLED)
        val fixture = fixture()
        val packageName = context.packageName
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
        val scenario = ActivityScenario.launch(DriverActivity::class.java)
        try {
            awaitSnapshot(10_000) { it["permission"] == "GRANTED" }
            call { done -> runtime().configure(fixture.kind, fixture.baseUrl, fixture.token, done) }
            awaitSnapshot(12_000) { it["stopPending"] == false }
            val before = (snapshot()["accepted"] as Number).toInt()
            call { done -> runtime().start(fixture.startIntentId, reply = done) }
            val tracked = awaitSnapshot(60_000) { it["state"] == "TRACKING" && (it["accepted"] as Number).toInt() > before }
            instrumentation.runOnMainSync { runtime().stop() }
            val invalidResponse = InstrumentationRegistry.getArguments().getString("stopFailure") == "INVALID_CONTRACT"
            val stopped = awaitSnapshot(25_000) {
                assertTrue("STOP_RESTARTED_COLLECTION", it["state"] != "TRACKING")
                it["state"] == "STOPPED" && it["stopPending"] == false &&
                    if (invalidResponse) it["reason"] == "INVALID_CONTRACT" else it["remoteStopAcknowledgedAt"] != null
            }
            assertEquals("QUEUE_AFTER_STOP", 0, stopped["queued"])
            assertNotNull("NATIVE_STOP_CALL_MISSING", stopped["nativeStopCalledAt"])
            assertNotNull("NATIVE_STOP_COMPLETION_MISSING", stopped["nativeStopCompletedAt"])
            assertTrue("STOP_TIME_ORDER", (stopped["nativeStopCompletedElapsedMs"] as Long) >= (stopped["nativeStopCalledElapsedMs"] as Long))
            Thread.sleep(6_000)
            val after = snapshot()
            assertEquals("COLLECTION_RESUMED", "STOPPED", after["state"])
            assertEquals("LATE_QUEUE_AFTER_STOP", 0, after["queued"])
            assertEquals("LATE_ACCEPTED_AFTER_STOP", stopped["accepted"], after["accepted"])
            if (invalidResponse) {
                assertEquals("INVALID_RESPONSE_RETRIED", "INVALID_CONTRACT", after["reason"])
                assertEquals("INVALID_RESPONSE_ACK", null, after["remoteStopAcknowledgedAt"])
            }
            println("ANDROID_GPS_SMOKE acceptedDelta=${(tracked["accepted"] as Number).toInt() - before}" +
                " nativeStopCalledElapsedMs=${stopped["nativeStopCalledElapsedMs"]}" +
                " nativeStopCompletedElapsedMs=${stopped["nativeStopCompletedElapsedMs"]}" +
                " leaseDeadlineElapsedMs=${stopped["leaseDeadlineElapsedMs"]}" +
                " lateCallbacksDiscarded=${after["lateCallbacksDiscarded"]}")
        } finally {
            instrumentation.runOnMainSync { runtime().stop() }
            scenario.close()
        }
    }
}
