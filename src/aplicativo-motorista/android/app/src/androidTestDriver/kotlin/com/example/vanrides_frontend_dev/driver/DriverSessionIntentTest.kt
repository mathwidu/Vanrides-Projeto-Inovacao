package com.example.vanrides_frontend_dev.driver

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Safe to run on API29+: exercises the exact boundary used before Flutter/app_links, without network or storage. */
@RunWith(AndroidJUnit4::class)
class DriverSessionIntentTest {
    @Test fun coldAndWarmInputsNeverForwardOriginalUriExtrasClipOrSelector() {
        val marker = "SYNTHETIC_PRIVATE_SENTINEL_060"
        val host = "links.vanrides.example"
        for (phase in listOf("cold", "warm")) {
            for ((uri, expected) in listOf(
                DriverSessionProtocol.RETURN_URI to DriverSessionProtocol.RETURN_URI,
                "https://$host${DriverSessionProtocol.HTTPS_RETURN_PATH}" to DriverSessionProtocol.RETURN_URI,
                "${DriverSessionProtocol.RETURN_URI}?code=$marker" to DriverSessionProtocol.INVALID_RETURN_URI,
                "https://$host${DriverSessionProtocol.HTTPS_RETURN_PATH}#token=$marker" to DriverSessionProtocol.INVALID_RETURN_URI,
                "vanridesdriver://pairing/begin?code=$marker" to DriverSessionProtocol.INVALID_RETURN_URI,
            )) {
                val original = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    putExtra("private_$phase", marker)
                    clipData = ClipData.newPlainText("private", marker)
                    selector = Intent(Intent.ACTION_VIEW, Uri.parse("https://invalid.example/$marker"))
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val (clean, icon) = DriverSessionIntents.sanitize(original, "dev.matheusduarte.vanrides.driver.lab",
                    "com.example.vanrides_frontend_dev.driver.DriverActivity", host)
                assertFalse(icon); assertEquals(Intent.ACTION_VIEW, clean.action); assertEquals(expected, clean.dataString)
                assertNull(clean.extras); assertNull(clean.clipData); assertNull(clean.selector)
                assertEquals(0, clean.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
                assertFalse(clean.toUri(0).contains(marker)); assertFalse(clean.toString().contains(marker))
            }
        }
    }
    @Test fun launcherKeepsOnlyTheLauncherContractAndCannotCarryAUri() {
        val original = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).putExtra("private", "SYNTHETIC_SENTINEL")
        val (clean, icon) = DriverSessionIntents.sanitize(original, "dev.matheusduarte.vanrides.driver.lab", "driver.Activity", "")
        assertTrue(icon); assertEquals(Intent.ACTION_MAIN, clean.action); assertNull(clean.data); assertNull(clean.extras)
        original.data = Uri.parse(DriverSessionProtocol.RETURN_URI)
        val (rejected, fromIcon) = DriverSessionIntents.sanitize(original, "dev.matheusduarte.vanrides.driver.lab", "driver.Activity", "")
        assertFalse(fromIcon); assertEquals(DriverSessionProtocol.INVALID_RETURN_URI, rejected.dataString)
    }
}
