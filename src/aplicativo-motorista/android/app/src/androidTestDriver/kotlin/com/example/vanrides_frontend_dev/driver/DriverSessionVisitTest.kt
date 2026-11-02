package com.example.vanrides_frontend_dev.driver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** API29+ window/intent contract. No pairing action, key creation, screenshot or GPS start is requested. */
@RunWith(AndroidJUnit4::class)
class DriverSessionVisitTest {
    @Test fun coldLinkRecreationAndWarmLauncherVisitKeepTheirOwnAuthority() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val link = Intent(context, DriverActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(DriverSessionProtocol.RETURN_URI)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<DriverActivity>(link).use { scenario ->
            scenario.onActivity {
                assertFalse(it.isLauncherVisit())
                assertEquals(DriverSessionProtocol.RETURN_URI, it.intent.dataString)
                assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            scenario.recreate()
            scenario.onActivity { assertFalse(it.isLauncherVisit()) }
            val launcher = Intent(context, DriverActivity::class.java).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            scenario.onActivity { it.startActivity(launcher) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { assertTrue(it.isLauncherVisit()); assertNull(it.intent.data) }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity {
                assertFalse(it.isLauncherVisit())
                assertEquals(0, it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertTrue(it.isLauncherVisit()) }
            scenario.recreate()
            scenario.onActivity { assertTrue(it.isLauncherVisit()); assertNull(it.intent.data) }
        }
    }
}
