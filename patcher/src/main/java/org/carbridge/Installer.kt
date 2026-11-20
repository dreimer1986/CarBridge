package org.carbridge

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object Installer {
    /** Outcome of one install session; [message] is null on success. */
    data class Result(val packageName: String?, val success: Boolean, val cancelled: Boolean, val message: String?)

    private val _events = MutableSharedFlow<Result>(extraBufferCapacity = 8)
    val events: SharedFlow<Result> = _events

    /**
     * Streams the base and split APKs into one install session. Android asks the user to confirm,
     * except for updates of a copy CarBridge installed whose targetSdk is recent enough.
     */
    fun install(context: Context, apks: List<File>, packageName: String, background: Boolean = false) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
            // Only a request: Android still sends STATUS_PENDING_USER_ACTION when it wants a prompt
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            write(installer, sessionId, apks, context, packageName, background)
        } catch (e: Throwable) {
            // Otherwise the session and its staged APKs stay behind until Android expires them
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    private fun write(installer: PackageInstaller, sessionId: Int, apks: List<File>, context: Context, packageName: String, background: Boolean) {
        installer.openSession(sessionId).use { session ->
            for (apk in apks) {
                session.openWrite(apk.name, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallStatusReceiver::class.java)
                .putExtra(EXTRA_CAR_PACKAGE, packageName)
                .putExtra(EXTRA_BACKGROUND, background)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    /** What a PackageInstaller failure means for a car copy, followed by Android's own message. */
    fun describeFailure(status: Int, systemMessage: String?): String {
        val meaning = when (status) {
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "An existing car copy was signed with another key, e.g. before CarBridge was reinstalled " +
                    "or its data cleared. Restore your key backup in Settings, or uninstall that copy and add it again."
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage for the car copy."
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                "Android says the copy doesn't fit this phone, e.g. a missing split APK or another CPU type."
            PackageInstaller.STATUS_FAILURE_INVALID -> "Android rejected the APK as invalid."
            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                "The install was blocked, by a device policy or because CarBridge may not install apps."
            PackageInstaller.STATUS_FAILURE_ABORTED -> "The install was cancelled."
            PackageInstaller.STATUS_FAILURE_TIMEOUT -> "The install timed out."
            else -> "The install failed."
        }
        return "$meaning\nAndroid (status $status): ${systemMessage ?: "no message"}"
    }

    const val EXTRA_CAR_PACKAGE = "org.carbridge.extra.CAR_PACKAGE"
    const val EXTRA_BACKGROUND = "org.carbridge.extra.BACKGROUND"

    internal fun report(result: Result) {
        _events.tryEmit(result)
    }
}

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: intent.getStringExtra(Installer.EXTRA_CAR_PACKAGE)
        if (intent.getBooleanExtra(Installer.EXTRA_BACKGROUND, false) && pkg != null) reportInBackground(context, intent, status, pkg)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (intent.getBooleanExtra(Installer.EXTRA_BACKGROUND, false)) return
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Installer.report(Installer.Result(pkg, true, false, null))
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val cancelled = status == PackageInstaller.STATUS_FAILURE_ABORTED
                Installer.report(Installer.Result(pkg, false, cancelled, Installer.describeFailure(status, msg)))
            }
        }
    }

    /** Background updates can't open screens, so their outcome, or the prompt, is a notification. */
    private fun reportInBackground(context: Context, intent: Intent, status: Int, pkg: String) {
        val label = AutoUpdate.labelOf(context, pkg)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) ?: return
                val tap = PendingIntent.getActivity(
                    context, pkg.hashCode(), confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
                )
                AutoUpdate.notifyResult(context, label, context.getString(R.string.auto_confirm), action = tap)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val version = runCatching { context.packageManager.getPackageInfo(pkg, 0).versionName }.getOrNull()
                AutoUpdate.notifyResult(
                    context, label,
                    if (version != null) context.getString(R.string.auto_updated_to, version) else context.getString(R.string.auto_updated),
                )
            }
            else -> AutoUpdate.notifyResult(
                context, label, context.getString(R.string.auto_failed),
                Installer.describeFailure(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)),
            )
        }
    }
}
