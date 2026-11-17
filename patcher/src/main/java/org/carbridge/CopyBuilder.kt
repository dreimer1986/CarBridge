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
            val apks = PatchPipeline(context, log).patch(app, options(context, app.packageName))
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

    /** The user's per-app choices; by default copies fill the screen and drop media services. */
    fun options(context: Context, originalPackage: String): CarOptions {
        val prefs = context.getSharedPreferences("setup", 0)
        return CarOptions(
            keepMediaServices = prefs.getBoolean("keep_media:$originalPackage", false),
            fillScreen = !prefs.getBoolean("keep_portrait:$originalPackage", false),
        )
    }

    /** A build failure in words the user can act on. */
    fun describe(e: Throwable): String = when (e) {
        is PatchFailure -> e.message.orEmpty()
        is OutOfMemoryError -> "The app is too large to patch in the memory available. Close other apps and try again."
        else -> "${e.javaClass.simpleName}: ${e.message}"
    }
}
