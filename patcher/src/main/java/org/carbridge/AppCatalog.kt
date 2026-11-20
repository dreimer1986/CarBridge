package org.carbridge

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.carbridge.core.CarOptions
import org.carbridge.core.CarPatch
import org.carbridge.patch.KeystoreSigner
import org.carbridge.patch.PatchPipeline

/** An app the user can add to the car. */
data class SourceApp(
    val label: String,
    val info: ApplicationInfo,
    val icon: ImageBitmap,
    val sizeBytes: Long,
    /** The app already ships its own Android Auto experience (media, messaging or templates). */
    val hasAndroidAuto: Boolean,
    val caution: Caution?,
)

/** Why a copy of this app may be the wrong choice; shown before patching. */
enum class Caution {
    /** Uses Play's integrity license check, which closes re-signed copies on launch. */
    LicenseCheck,

    /** Already patched by Morphe, where the CarBridge patch bundle does this better. */
    Morphe,
}

/** An installed car copy, with the original it came from when that is still installed. */
data class CarCopy(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val original: SourceApp?,
    val version: Version?,
    val originalVersion: Version?,
    /** The original locks some screens to portrait, so the layout choice matters. */
    val portraitLocked: Boolean,
    /** The original has a media browser service, so the audio-player choice matters. */
    val hasMediaService: Boolean,
    val options: CarOptions,
    /** Signed with a key this phone no longer has; it must be removed before it can be rebuilt. */
    val signedElsewhere: Boolean = false,
    /** Made by another CarBridge build still on the phone, e.g. CarBridge Dev, so no key was lost. */
    val madeByOtherCarBridge: Boolean = false,
    /** Made from APK files rather than an installed app; updated by picking newer files. */
    val fromFile: Boolean = false,
    /** The user hasn't opted this copy out of automatic updates. */
    val autoUpdateAllowed: Boolean = true,
    /** Why Android Auto won't list this copy, or null when it has what Android Auto needs. */
    val carProblem: CarProblem? = null,
    /** The copy's installed APKs; its data isn't readable without usage-access permission. */
    val sizeBytes: Long = 0,
) {
    /** A key backup could make this copy updatable again. */
    val needsRestore get() = signedElsewhere && !madeByOtherCarBridge

    /** True when the original app has been updated past the version this copy was made from. */
    val outdated get() = version != null && originalVersion != null && originalVersion.code > version.code

    /** A rebuilt copy would install over this one. */
    val updatable get() = original != null && !signedElsewhere
}

data class Version(val code: Long, val name: String?)

/** What an installed copy lacks that Android Auto needs to list it. */
enum class CarProblem(@StringRes val text: Int) {
    NoCarLauncher(R.string.car_problem_launcher),
    NoCarSettings(R.string.car_problem_settings),
    NoCarPermission(R.string.car_problem_permission),
}

data class Catalog(val copies: List<CarCopy>, val available: List<SourceApp>)

object AppCatalog {
    private const val ICON_PX = 144

    fun load(context: Context, options: (String) -> CarOptions): Catalog {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // Copies hidden on the phone only answer the car launcher category
        val carLauncher = Intent(Intent.ACTION_MAIN).addCategory(CarPatch.CAR_CATEGORIES[0])
        val hiddenCopies = pm.queryIntentActivities(carLauncher, 0).filter { it.activityInfo.packageName.endsWith(CAR_SUFFIX) }
        val launchable = (pm.queryIntentActivities(launcher, 0) + hiddenCopies)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName && isUserFacing(it) }

        val (copyInfos, sourceInfos) = launchable.partition { it.packageName.endsWith(CAR_SUFFIX) }
        // Launcher query results don't carry meta-data, so read it once for all apps
        val autoApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.metaData?.containsKey(CAR_APP_META) == true }
            .map { it.packageName }
            .toSet() + carLaunchable(pm)
        val sources = sourceInfos.associate { info ->
            val scan = scan(pm, info)
            val caution = when {
                info.packageName.startsWith(MORPHE_PREFIX) -> Caution.Morphe
                scan.licensed -> Caution.LicenseCheck
                else -> null
            }
            info.packageName to info.toSource(pm, scan.icon, info.packageName in autoApps, caution)
        }

        val signer = KeystoreSigner(context)
        val copies = copyInfos.map { info ->
            val originalPkg = info.packageName.removeSuffix(CAR_SUFFIX)
            val original = sources[originalPkg]
            CarCopy(
                // The sign already says it is on the car
                label = info.loadLabel(pm).toString().removeSuffix(PatchPipeline.CAR_LABEL_SUFFIX),
                packageName = info.packageName,
                icon = scan(pm, info).icon,
                original = original,
                version = versionOf(pm, info.packageName),
                originalVersion = original?.let { versionOf(pm, originalPkg) },
                portraitLocked = original != null && locksPortrait(pm, originalPkg),
                hasMediaService = original != null && hasMediaService(pm, originalPkg),
                options = options(originalPkg),
                autoUpdateAllowed = AutoUpdate.isAllowed(context, originalPkg),
                fromFile = original == null && Prefs.of(context).getBoolean(Prefs.fromFile(originalPkg), false),
                signedElsewhere = !signer.canUpdate(pm, info.packageName),
                carProblem = carProblem(pm, info.packageName),
                madeByOtherCarBridge = installer(pm, info.packageName).let { by ->
                    by != null && by != context.packageName && (by == CARBRIDGE_ID || by.startsWith("$CARBRIDGE_ID.")) && isInstalled(pm, by)
                },
                sizeBytes = info.apkBytes(),
            )
        }.sortedWith(compareBy({ !it.outdated }, { it.label.lowercase() }))

        val copied = copies.mapNotNull { it.original?.info?.packageName }.toSet()
        val available = sources.values.filter { it.info.packageName !in copied }.sortedBy { it.label.lowercase() }
        return Catalog(copies, available)
    }

    /**
     * Checks installed [pkg] the way Android Auto finds apps: an activity in the car launcher, the
     * car app meta-data and the car screen permissions. Android Auto's own settings, such as
     * Unknown sources, can't be read from here.
     */
    fun carProblem(pm: PackageManager, pkg: String): CarProblem? {
        val carLauncher = Intent(Intent.ACTION_MAIN).addCategory(CarPatch.CAR_CATEGORIES[0]).setPackage(pkg)
        if (pm.queryIntentActivities(carLauncher, 0).isEmpty()) return CarProblem.NoCarLauncher
        val info = runCatching { pm.getPackageInfo(pkg, PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS) }.getOrNull()
            ?: return CarProblem.NoCarLauncher
        if (info.applicationInfo?.metaData?.containsKey(CAR_APP_META) != true) return CarProblem.NoCarSettings
        val requested = info.requestedPermissions.orEmpty().toSet()
        if (CarPatch.CAR_PERMISSIONS.any { it !in requested }) return CarProblem.NoCarPermission
        return null
    }

    /** Apps already on the car launcher, e.g. copies made by other tools such as AAAnything. */
    private fun carLaunchable(pm: PackageManager): Set<String> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(CarPatch.CAR_CATEGORIES[0]), 0)
            .map { it.activityInfo.packageName }
            .toSet()

    private fun installer(pm: PackageManager, pkg: String): String? =
        runCatching { pm.getInstallSourceInfo(pkg).installingPackageName }.getOrNull()

    private fun isInstalled(pm: PackageManager, pkg: String) = runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess

    /** User-installed apps, plus preinstalled ones a store has updated (stock YouTube, Chrome). */
    private fun isUserFacing(info: ApplicationInfo): Boolean =
        info.flags and ApplicationInfo.FLAG_SYSTEM == 0 || info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

    /** What only changes when a package does; [updated] is its lastUpdateTime, or -1 if unknown. */
    private class Scan(val updated: Long, val icon: ImageBitmap, val licensed: Boolean)

    // Every refresh reuses these, so only new or updated apps cost an icon draw and a component scan
    private val scans = ConcurrentHashMap<String, Scan>()

    private fun scan(pm: PackageManager, info: ApplicationInfo): Scan {
        val pkg = info.packageName
        val updated = runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrDefault(-1L)
        scans[pkg]?.takeIf { updated != -1L && it.updated == updated }?.let { return it }
        return Scan(updated, pm.getApplicationIcon(info).toImageBitmap(), usesPlayLicense(pm, pkg)).also { scans[pkg] = it }
    }

    // CHECK_LICENSE alone is too common (ad SDKs, free apps) to predict a failure
    private fun usesPlayLicense(pm: PackageManager, pkg: String): Boolean = runCatching {
        val info = pm.getPackageInfo(pkg, PackageManager.GET_PROVIDERS or PackageManager.GET_ACTIVITIES)
        info.providers.orEmpty().any { it.name.startsWith(PAIRIP_PREFIX) } ||
            info.activities.orEmpty().any { it.name.startsWith(PAIRIP_PREFIX) }
    }.getOrDefault(false)

    private fun ApplicationInfo.apkBytes() = (listOf(sourceDir) + splitSourceDirs.orEmpty()).sumOf { File(it).length() }

    private fun ApplicationInfo.toSource(pm: PackageManager, icon: ImageBitmap, hasAndroidAuto: Boolean, caution: Caution?) = SourceApp(
        label = loadLabel(pm).toString(),
        info = this,
        icon = icon,
        sizeBytes = apkBytes(),
        hasAndroidAuto = hasAndroidAuto,
        caution = caution,
    )

    /** Which per-app choices matter for [pkg]: portrait locks and an Android Auto audio player. */
    fun traits(context: Context, pkg: String): Pair<Boolean, Boolean> {
        val pm = context.packageManager
        return locksPortrait(pm, pkg) to hasMediaService(pm, pkg)
    }

    private fun locksPortrait(pm: PackageManager, pkg: String): Boolean =
        runCatching {
            pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities.orEmpty()
                .any { it.screenOrientation in PORTRAIT_ORIENTATIONS }
        }.getOrDefault(false)

    private fun hasMediaService(pm: PackageManager, pkg: String): Boolean =
        CarPatch.MEDIA_SERVICE_ACTIONS.any { action ->
            pm.queryIntentServices(Intent(action).setPackage(pkg), 0).isNotEmpty()
        }

    private fun versionOf(pm: PackageManager, pkg: String): Version? =
        runCatching { pm.getPackageInfo(pkg, 0).let { Version(it.longVersionCode, it.versionName) } }.getOrNull()

    fun icon(drawable: Drawable): ImageBitmap = drawable.toImageBitmap()

    private fun Drawable.toImageBitmap(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, ICON_PX, ICON_PX)
        draw(Canvas(bitmap))
        return bitmap.asImageBitmap()
    }

    private val CAR_SUFFIX = PatchPipeline.carPackageName("")
    private val PORTRAIT_ORIENTATIONS = setOf(
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
    )
    private const val PAIRIP_PREFIX = "com.pairip.licensecheck."
    private const val MORPHE_PREFIX = "app.morphe.android."
    // CarBridge, CarBridge Dev and debug builds sign copies with different keys
    private const val CARBRIDGE_ID = "org.carbridge"
    private const val CAR_APP_META = "com.google.android.gms.car.application"
}
