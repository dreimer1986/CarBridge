package org.carbridge

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.carbridge.core.CarOptions
import org.carbridge.patch.PatchFailure
import org.carbridge.patch.PatchPipeline

/**
 * Patches, signs and installs one car copy at a time, for the home screen and background updates.
 * One at a time because every build shares the same work folder.
 */
object CopyBuilder {
    private val _building = MutableStateFlow<String?>(null)

    /** The car package being built, or null when nothing is. */
    val building: StateFlow<String?> = _building

    /**
     * Builds [app]'s copy and hands it to Android's installer; blocks, so call it off the main
     * thread. Returns false without doing anything when another copy is being built. A
     * [background] build reports its result in notifications, since it can't open screens.
     */
    fun build(
        context: Context,
        app: ApplicationInfo,
        log: (String) -> Unit,
        background: Boolean = false,
        onPatched: () -> Unit = {},
    ): Boolean {
        val carPackage = PatchPipeline.carPackageName(app.packageName)
        if (!_building.compareAndSet(null, carPackage)) return false
        try {
            val options = options(context, app.packageName)
            // Saved per copy, so later changes to the defaults leave existing copies as they are
            save(context, app.packageName, options)
            val apks = PatchPipeline(context, log).patch(app, options)
            onPatched()
            if (!ShizukuMode.install(context, apks, carPackage, background, log)) {
                Installer.install(context, apks, carPackage, background)
            }
        } finally {
            // The install session holds its own copy once committed
            PatchPipeline.clearWork(context)
            _building.value = null
        }
        return true
    }

    /**
     * The user's choices for one app. Unset choices come from [defaults] for a new copy, and from
     * [CarOptions]' own defaults for a copy made before CarBridge had defaults, so it doesn't change.
     */
    fun options(context: Context, originalPackage: String): CarOptions {
        val prefs = context.getSharedPreferences("setup", 0)
        val carPackage = PatchPipeline.carPackageName(originalPackage)
        val exists = runCatching { context.packageManager.getPackageInfo(carPackage, 0) }.isSuccess
        val base = if (exists) CarOptions() else defaults(context)
        fun choice(key: String, fallback: Boolean) =
            if (prefs.contains("$key:$originalPackage")) prefs.getBoolean("$key:$originalPackage", fallback) else fallback
        return CarOptions(
            keepMediaServices = choice("keep_media", base.keepMediaServices),
            fillScreen = !choice("keep_portrait", !base.fillScreen),
            hideOnPhone = choice("hide_on_phone", base.hideOnPhone),
        )
    }

    fun save(context: Context, originalPackage: String, options: CarOptions) {
        context.getSharedPreferences("setup", 0).edit()
            .putBoolean("keep_media:$originalPackage", options.keepMediaServices)
            .putBoolean("keep_portrait:$originalPackage", !options.fillScreen)
            .putBoolean("hide_on_phone:$originalPackage", options.hideOnPhone)
            .apply()
    }

    /** The choices new copies start with, set in Settings. */
    fun defaults(context: Context): CarOptions {
        val prefs = context.getSharedPreferences("setup", 0)
        val built = CarOptions()
        return CarOptions(
            keepMediaServices = prefs.getBoolean("default_keep_media", built.keepMediaServices),
            fillScreen = prefs.getBoolean("default_fill_screen", built.fillScreen),
            hideOnPhone = prefs.getBoolean("default_hide_on_phone", built.hideOnPhone),
        )
    }

    fun setDefaults(context: Context, options: CarOptions) {
        context.getSharedPreferences("setup", 0).edit()
            .putBoolean("default_keep_media", options.keepMediaServices)
            .putBoolean("default_fill_screen", options.fillScreen)
            .putBoolean("default_hide_on_phone", options.hideOnPhone)
            .apply()
    }

    /** A build failure in words the user can act on. */
    fun describe(e: Throwable): String = when (e) {
        is PatchFailure -> e.message.orEmpty()
        is OutOfMemoryError -> "The app is too large to patch in the memory available. Close other apps and try again."
        else -> "${e.javaClass.simpleName}: ${e.message}"
    }
}
