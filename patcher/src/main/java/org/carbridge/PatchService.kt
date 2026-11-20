package org.carbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/**
 * Keeps CarBridge's process alive while a car copy is made, so leaving the app or turning the
 * screen off doesn't let Android kill a long patch. The work itself runs in [HomeModel].
 */
class PatchService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.patch_default_title)
        val text = intent?.getStringExtra(EXTRA_TEXT)
        startForeground(NOTIFICATION_ID, notification(this, title, text), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        // A killed process loses the patch anyway, so there's nothing to restart
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "patching"
        private const val NOTIFICATION_ID = 2
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"

        private var running = false

        /** Starts the service on the first call, then only updates its notification. */
        fun show(context: Context, title: String, text: String) {
            if (running) {
                context.getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification(context, title, text))
                return
            }
            running = true
            val intent = Intent(context, PatchService::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            if (!running) return
            running = false
            context.stopService(Intent(context, PatchService::class.java))
        }

        private fun notification(context: Context, title: String, text: String?): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_patching), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_patching_desc)
                },
            )
            val open = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }
    }
}
