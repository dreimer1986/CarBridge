package org.carbridge

import android.content.Context
import android.net.Uri
import android.os.Build
import org.carbridge.core.CarOptions

/**
 * A prefilled GitHub issue for COMPATIBILITY.md. It opens in the browser, so the user sees and
 * edits everything before anything is sent.
 */
object CompatReport {
    private const val NEW_ISSUE = "https://github.com/jmwerk/CarBridge/issues/new"
    private const val ANDROID_AUTO = "com.google.android.projection.gearhead"

    data class Facts(
        val app: String,
        val packageName: String,
        val version: String,
        val splits: Int,
        val options: CarOptions,
        val carBridge: String,
        val androidAuto: String,
        val phone: String,
        val android: String,
    )

    fun facts(context: Context, copy: CarCopy): Facts {
        val pm = context.packageManager
        fun versionOf(pkg: String) = runCatching { pm.getPackageInfo(pkg, 0).versionName }.getOrNull() ?: "unknown"
        return Facts(
            app = copy.label,
            packageName = copy.original?.info?.packageName ?: copy.packageName,
            version = copy.version?.name ?: "unknown",
            splits = copy.original?.info?.splitSourceDirs?.size ?: 0,
            options = copy.options,
            carBridge = versionOf(context.packageName),
            androidAuto = versionOf(ANDROID_AUTO),
            phone = "${Build.MANUFACTURER} ${Build.MODEL}",
            android = Build.VERSION.RELEASE,
        )
    }

    fun title(f: Facts) = "Compatibility: ${f.app} ${f.version}"

    fun body(f: Facts) = """
        |**Result:** Works / Works with caveats / Doesn't work
        |
        |**What happened:**
        |
        || | |
        ||---|---|
        || App | ${f.app} (`${f.packageName}`) ${f.version} |
        || Split APKs | ${f.splits} |
        || Layout | ${if (f.options.fillScreen) "Fill the car screen" else "Keep portrait"} |
        || Audio | ${if (f.options.keepMediaServices) "Android Auto's audio player" else "Full app"} |
        || CarBridge | ${f.carBridge} |
        || Android Auto | ${f.androidAuto} |
        || Phone | ${f.phone}, Android ${f.android} |
        || Car or head unit | |
    """.trimMargin()

    fun uri(f: Facts): Uri = Uri.parse(NEW_ISSUE).buildUpon()
        .appendQueryParameter("title", title(f))
        .appendQueryParameter("body", body(f))
        .build()
}
