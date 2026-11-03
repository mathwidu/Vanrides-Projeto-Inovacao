package com.example.vanrides_frontend_dev.driver

import android.content.Intent
import android.net.Uri

/** Called before Flutter/app_links can observe a cold or warm Activity intent. */
internal object DriverSessionIntents {
    fun sanitize(incoming: Intent, packageName: String, activityName: String, linkHost: String): Pair<Intent, Boolean> {
        val entry = DriverSessionProtocol.entry(incoming.action, incoming.hasCategory(Intent.CATEGORY_LAUNCHER), incoming.dataString, linkHost)
        val clean = Intent().setClassName(packageName, activityName)
        if (entry == DriverSessionProtocol.Entry.ICON) {
            clean.action = Intent.ACTION_MAIN; clean.addCategory(Intent.CATEGORY_LAUNCHER)
        } else {
            clean.action = Intent.ACTION_VIEW
            clean.data = Uri.parse(when (entry) { DriverSessionProtocol.Entry.RETURN -> DriverSessionProtocol.RETURN_URI; DriverSessionProtocol.Entry.RUN -> incoming.dataString!!; else -> DriverSessionProtocol.INVALID_RETURN_URI })
        }
        // No original extras, ClipData, selector, source bounds or URI survive, including on valid returns.
        clean.flags = incoming.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        return clean to (entry == DriverSessionProtocol.Entry.ICON)
    }
}
