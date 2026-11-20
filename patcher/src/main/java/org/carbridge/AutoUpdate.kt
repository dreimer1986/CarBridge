package org.carbridge

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.carbridge.patch.PatchPipeline

/**
 * Rebuilds outdated car copies in the background while the phone charges, when the user turned
 * that on. One copy per job run: Android stops background jobs after about ten minutes.
 */
object AutoUpdate {
    private const val TAG = "CarBridge"
    private const val JOB_ID = 2
    private const val CHANNEL = "copy_auto_updates"
    private const val GROUP = "org.carbridge.AUTO_UPDATES"
    // One id for every notice; the tag tells them apart without hash collisions
    private const val NOTIFICATION_ID = 3

    /** After this long without charging, the user gets the manual update notice instead. */
    val MAX_WAIT_MS = TimeUnit.DAYS.toMillis(2)

    private fun prefs(context: Context) = context.getSharedPreferences("setup", 0)

    fun isEnabled(context: Context) = prefs(context).getBoolean("auto_update", false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("auto_update", on).apply()
        if (on) schedule(context)
    }

    fun isAllowed(context: Context, originalPackage: String) = !prefs(context).getBoolean("auto_update_off:$originalPackage", false)

    fun setAllowed(context: Context, originalPackage: String, allowed: Boolean) {
        prefs(context).edit().putBoolean("auto_update_off:$originalPackage", !allowed).apply()
    }

    /**
     * Copies this job should handle now: outdated, allowed, not yet tried at this original version,
     * and not waiting so long that the user should hear about it instead.
     */
    fun pending(context: Context, outdated: List<UpdateCheck.Outdated>): List<UpdateCheck.Outdated> {
        if (!isEnabled(context)) return emptyList()
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        return outdated.filter { o ->
            val since = prefs.getLong("outdated_since:${o.originalPackage}:${o.originalCode}", now)
            isAllowed(context, o.originalPackage) &&
                prefs.getLong("auto_tried:${o.originalPackage}", 0) < o.originalCode &&
                now - since < MAX_WAIT_MS
        }
    }

    /** Remembers when each original version was first seen, for [MAX_WAIT_MS]. */
    fun noteOutdated(context: Context, outdated: List<UpdateCheck.Outdated>) {
        val prefs = prefs(context)
        val edit = prefs.edit()
        for (o in outdated) {
            val key = "outdated_since:${o.originalPackage}:${o.originalCode}"
            if (!prefs.contains(key)) edit.putLong(key, System.currentTimeMillis())
        }
        edit.apply()
    }

    fun schedule(context: Context) {
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, AutoUpdateService::class.java))
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(job)
    }

    /** Builds the next pending copy; returns true when more are waiting. */
    fun runOnce(context: Context): Boolean {
        val outdated = UpdateCheck.findOutdated(context, forced = consumeForce(context))
        noteOutdated(context, outdated)
        val next = pending(context, outdated).firstOrNull() ?: return false
        val app = runCatching { context.packageManager.getApplicationInfo(next.originalPackage, 0) }.getOrNull() ?: return false
        // The user is building a copy; try again on the next run without counting this one
        if (CopyBuilder.building.value != null) return true
        prefs(context).edit().putLong("auto_tried:${next.originalPackage}", next.originalCode).apply()
        try {
            if (!CopyBuilder.build(context, app, log = { Log.i(TAG, it) }, background = true)) return true
        } catch (e: Throwable) {
            Log.e(TAG, "background update failed", e)
            notifyResult(context, next.originalPackage, next.label, context.getString(R.string.auto_failed), CopyBuilder.describe(e))
        }
        return pending(context, UpdateCheck.findOutdated(context)).isNotEmpty()
    }

    /** Debuggable builds only: `run-as <app> touch files/force-auto-update` makes one run treat every copy as outdated. */
    private fun consumeForce(context: Context): Boolean {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return false
        val flag = File(context.filesDir, "force-auto-update")
        if (!flag.exists()) return false
        flag.delete()
        prefs(context).all.keys.filter { it.startsWith("auto_tried:") }.forEach { prefs(context).edit().remove(it).apply() }
        return true
    }

    /**
     * A quiet notice about one copy; [details] go in the expanded text. Notices with the same [key],
     * the original's package, replace each other.
     */
    fun notifyResult(context: Context, key: String, label: String, text: String, details: String? = null, action: PendingIntent? = null) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_auto), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_auto_desc)
            },
        )
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val body = listOfNotNull(text, details).joinToString("\n")
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(label)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(action ?: open)
            .setAutoCancel(true)
            .setGroup(GROUP)
            .build()
        manager.notify(key, NOTIFICATION_ID, notification)
    }

    /** The label users know a copy by: its original's. */
    fun labelOf(context: Context, carPackage: String): String {
        val pm = context.packageManager
        return runCatching { pm.getApplicationInfo(carPackage, 0).loadLabel(pm).toString() }
            .getOrDefault(carPackage)
            .removeSuffix(PatchPipeline.CAR_LABEL_SUFFIX)
    }
}

class AutoUpdateService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread {
            val more = runCatching { AutoUpdate.runOnce(this) }.getOrDefault(false)
            jobFinished(params, false)
            // Scheduled after finishing: rescheduling a running job's id would cancel it
            if (more) AutoUpdate.schedule(this)
        }
        return true
    }

    // A stopped build finishes or dies with the process; the next run picks up what's left
    override fun onStopJob(params: JobParameters): Boolean {
        val building = CopyBuilder.building.value
        // Android allows background jobs about ten minutes; very large apps may need longer
        if (params.stopReason == JobParameters.STOP_REASON_TIMEOUT && building != null) {
            val original = PatchPipeline.originalPackageName(building)
            AutoUpdate.notifyResult(this, original, AutoUpdate.labelOf(this, building), getString(R.string.auto_too_long))
        }
        return true
    }
}
