package org.carbridge

import android.content.Context
import android.content.SharedPreferences

/** CarBridge's one settings file and every key in it, so no other file spells a key by hand. */
object Prefs {
    private const val FILE = "setup"

    fun of(context: Context): SharedPreferences = context.getSharedPreferences(FILE, 0)

    // App-wide
    const val AUTO_UPDATE = "auto_update"
    const val USE_SHIZUKU = "use_shizuku"
    const val SHOW_AUTO_APPS = "show_auto_apps"
    const val RESTORE_DECLINED = "restore_declined"
    const val DEVELOPER_MODE = "developer_mode"
    const val UNKNOWN_SOURCES = "unknown_sources"
    const val ASKED_NOTIFICATIONS = "asked_notifications"
    const val DEFAULT_KEEP_MEDIA = "default_keep_media"
    const val DEFAULT_FILL_SCREEN = "default_fill_screen"
    const val DEFAULT_HIDE_ON_PHONE = "default_hide_on_phone"

    /** Original packages a restored backup listed, until their copies are made or the offer dismissed. */
    const val RESTORE_PENDING = "restore_pending"

    /** Original packages the latest saved or checked backup lists. */
    const val BACKUP_COPIES = "backup_copies"

    // Per app, by the original's package: the user's choices, kept for when a copy is made again
    fun keepMedia(pkg: String) = "keep_media:$pkg"
    fun keepPortrait(pkg: String) = "keep_portrait:$pkg"
    fun hideOnPhone(pkg: String) = "hide_on_phone:$pkg"
    fun fromFile(pkg: String) = "from_file:$pkg"
    fun autoUpdateOff(pkg: String) = "auto_update_off:$pkg"

    // Per app: update bookkeeping, which only matters while the copy exists
    private const val NOTIFIED = "notified:"
    const val AUTO_TRIED = "auto_tried:"
    private const val OUTDATED_SINCE = "outdated_since:"

    fun notified(pkg: String) = "$NOTIFIED$pkg"
    fun autoTried(pkg: String) = "$AUTO_TRIED$pkg"
    fun outdatedSince(pkg: String, originalCode: Long) = "$OUTDATED_SINCE$pkg:$originalCode"

    /**
     * Drops update bookkeeping for apps that no longer have a copy, and for original versions a
     * copy has caught up with. [copies] maps each copy's original package to the copy's version code.
     */
    fun prune(context: Context, copies: Map<String, Long>) {
        val prefs = of(context)
        val stale = prefs.all.keys.filter { key -> isStale(key, copies) }
        if (stale.isNotEmpty()) prefs.edit().apply { stale.forEach(::remove) }.apply()
    }

    internal fun isStale(key: String, copies: Map<String, Long>): Boolean = when {
        key.startsWith(NOTIFIED) -> key.removePrefix(NOTIFIED) !in copies
        key.startsWith(AUTO_TRIED) -> key.removePrefix(AUTO_TRIED) !in copies
        key.startsWith(OUTDATED_SINCE) -> {
            val pkg = key.removePrefix(OUTDATED_SINCE).substringBeforeLast(':')
            val code = key.substringAfterLast(':').toLongOrNull()
            val copy = copies[pkg]
            copy == null || code == null || code <= copy
        }
        else -> false
    }
}
