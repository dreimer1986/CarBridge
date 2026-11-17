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
    fun install(context: Context, apks: List<File>, packageName: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_USER)
            // Only a request: Android still sends STATUS_PENDING_USER_ACTION when it wants a prompt
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            for (apk in apks) {
                session.openWrite(apk.name, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallStatusReceiver::class.java)
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
                    "or its data cleared. Restore your key backup from About, or uninstall that copy and add it again."
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

    internal fun report(result: Result) {
        _events.tryEmit(result)
    }
}

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
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
}
