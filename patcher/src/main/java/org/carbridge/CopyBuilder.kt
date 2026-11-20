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
        progress: (Float) -> Unit = {},
        onPatched: () -> Unit = {},
    ): Boolean {
        val carPackage = PatchPipeline.carPackageName(app.packageName)
        if (!_building.compareAndSet(null, carPackage)) return false
        try {
            val options = options(context, app.packageName)
            // Saved per copy, so later changes to the defaults leave existing copies as they are
            save(context, app.packageName, options)
            val apks = PatchPipeline(context, log, progress).patch(app, options)
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
        val prefs = Prefs.of(context)
        val carPackage = PatchPipeline.carPackageName(originalPackage)
        val exists = runCatching { context.packageManager.getPackageInfo(carPackage, 0) }.isSuccess
        val base = if (exists) CarOptions() else defaults(context)
        fun choice(key: String, fallback: Boolean) = if (prefs.contains(key)) prefs.getBoolean(key, fallback) else fallback
        return CarOptions(
            keepMediaServices = choice(Prefs.keepMedia(originalPackage), base.keepMediaServices),
            fillScreen = !choice(Prefs.keepPortrait(originalPackage), !base.fillScreen),
            hideOnPhone = choice(Prefs.hideOnPhone(originalPackage), base.hideOnPhone),
        )
    }

    fun save(context: Context, originalPackage: String, options: CarOptions) {
        Prefs.of(context).edit()
            .putBoolean(Prefs.keepMedia(originalPackage), options.keepMediaServices)
            .putBoolean(Prefs.keepPortrait(originalPackage), !options.fillScreen)
            .putBoolean(Prefs.hideOnPhone(originalPackage), options.hideOnPhone)
            .apply()
    }

    /** The choices new copies start with, set in Settings. */
    fun defaults(context: Context): CarOptions {
        val prefs = Prefs.of(context)
        val built = CarOptions()
        return CarOptions(
            keepMediaServices = prefs.getBoolean(Prefs.DEFAULT_KEEP_MEDIA, built.keepMediaServices),
            fillScreen = prefs.getBoolean(Prefs.DEFAULT_FILL_SCREEN, built.fillScreen),
            hideOnPhone = prefs.getBoolean(Prefs.DEFAULT_HIDE_ON_PHONE, built.hideOnPhone),
        )
    }

    fun setDefaults(context: Context, options: CarOptions) {
        Prefs.of(context).edit()
            .putBoolean(Prefs.DEFAULT_KEEP_MEDIA, options.keepMediaServices)
            .putBoolean(Prefs.DEFAULT_FILL_SCREEN, options.fillScreen)
            .putBoolean(Prefs.DEFAULT_HIDE_ON_PHONE, options.hideOnPhone)
            .apply()
    }

    /** A build failure in words the user can act on. */
    fun describe(e: Throwable): String = when (e) {
        is PatchFailure -> e.message.orEmpty()
        is OutOfMemoryError -> "The app is too large to patch in the memory available. Close other apps and try again."
        else -> "${e.javaClass.simpleName}: ${e.message}"
    }
}
