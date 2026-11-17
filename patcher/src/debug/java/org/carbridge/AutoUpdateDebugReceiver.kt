package org.carbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

/**
 * Runs the background updater once as if every copy were outdated, without waiting for an app
 * update: adb shell am broadcast -n org.carbridge.debug/org.carbridge.AutoUpdateDebugReceiver
 * then adb shell cmd jobscheduler run -f org.carbridge.debug 2
 */
class AutoUpdateDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        File(context.filesDir, "force-auto-update").createNewFile()
        AutoUpdate.schedule(context)
    }
}
