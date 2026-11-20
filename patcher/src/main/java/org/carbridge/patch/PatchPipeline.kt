package org.carbridge.patch

import org.carbridge.core.CarOptions

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.storage.StorageManager
import com.reandroid.apk.ApkModule
import com.reandroid.archive.writer.ZipAligner
import java.io.File

/** A failure the user can act on; its message is shown as is. */
class PatchFailure(message: String) : Exception(message)

/**
 * Original APK(s) in, signed car-enabled APK(s) out, base first. Runs off the main thread.
 * [progress] gets 0..1, weighted by APK size; signing has no inner steps, so it moves in jumps.
 */
class PatchPipeline(
    private val context: Context,
    private val log: (String) -> Unit,
    private val progress: (Float) -> Unit = {},
) {

    fun patch(app: ApplicationInfo, options: CarOptions): List<File> {
        val work = workDir(context).apply { deleteRecursively(); mkdirs() }
        val newPackage = carPackageName(app.packageName)
        val splits = app.splitSourceDirs.orEmpty().map(::File)
        val base = File(app.sourceDir)

        val total = (splits + base).sumOf { it.length() }
        log("Reading ${total / MB} MB in ${splits.size + 1} APK(s)")
        // Unsigned and signed copies sit side by side while signing
        val needed = total * 2 + 64 * MB
        // Includes cache Android would clear to make room, unlike File.usableSpace
        val storage = context.getSystemService(StorageManager::class.java)
        val free = storage.getAllocatableBytes(storage.getUuidForPath(work))
        if (free < needed) {
            throw PatchFailure("Not enough free space: needs about ${needed / MB} MB, ${free / MB} MB free.")
        }

        val meter = Meter(listOf(base) + splits, progress)
        val patcher = ManifestPatcher(log)
        val unsignedBase = File(work, "base.unsigned.apk")
        val branding = IconBadger.branding(context, app)
        val minSdk = rewrite(base, unsignedBase, meter.writing(base)) { apk -> patcher.patch(apk, newPackage, branding, options) }
        val unsignedSplits = splits.mapIndexed { i, split ->
            File(work, "split$i.unsigned.apk").also { out ->
                rewrite(split, out, meter.writing(split)) { apk -> patcher.patchSplit(apk, newPackage) }
            }
        }

        log("Signing")
        val signing = KeystoreSigner(context).signerFor(context.packageManager, newPackage)
        val signed = (listOf(unsignedBase) + unsignedSplits).zip(listOf(base) + splits) { unsigned, original ->
            File(work, unsigned.name.replace(".unsigned", "")).also { out ->
                signing.sign(unsigned, out, minSdk)
                unsigned.delete()
                meter.signed(original)
            }
        }

        log("Verifying signatures")
        val problems = signed.flatMap { apk -> signing.verify(apk, minSdk).map { "${apk.name}: $it" } }
        if (problems.isNotEmpty()) {
            throw PatchFailure("The signed copy didn't pass Android's signature check.\n" + problems.joinToString("\n"))
        }
        val restored = if (signing.lineage != null) " (restored key)" else ""
        log("Signed with ${signing.fingerprint.take(17)}…$restored")
        return signed
    }

    companion object {
        private const val MB = 1024L * 1024
        private val V1_SIGNATURE_FILES =
            Regex("META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC))", RegexOption.IGNORE_CASE).toPattern()

        const val CAR_LABEL_SUFFIX = " (Car)"

        fun carPackageName(original: String) = "$original.car"

        fun originalPackageName(car: String) = car.removeSuffix(".car")

        private fun workDir(context: Context) = File(context.cacheDir, "patch")

        /** Frees the patched APKs, which can run to hundreds of MB, once installing has copied them. */
        fun clearWork(context: Context) {
            workDir(context).deleteRecursively()
        }

        /** Applies [edit], writes an unsigned APK and returns its minSdk. */
        internal fun rewrite(input: File, output: File, written: (Float) -> Unit = {}, edit: (ApkModule) -> Unit): Int =
            ApkModule.loadApkFile(input).use { apk ->
                apk.setApkSignatureBlock(null)
                apk.zipEntryMap.removeIf(V1_SIGNATURE_FILES)
                edit(apk)
                written(0f)
                val writer = apk.createApkFileWriter(output)
                val entries = apk.zipEntryMap.size().coerceAtLeast(1)
                var done = 0
                writer.setWriteProgress { _, _, _ -> written(++done / entries.toFloat()) }
                writer.zipAligner = ZipAligner().apply {
                    setDefaultAlignment(4)
                    // Uncompressed native libs must be page-aligned; 16 KB covers 4 KB devices too
                    setFileAlignment(ZipAligner.PREDICATE_NATIVE_LIBS, 16 * 1024)
                }
                writer.write()
                apk.androidManifest.minSdkVersion ?: 1
            }
    }
}

/**
 * Turns per-APK steps into one 0..1 figure, each APK weighted by size. All APKs are rewritten
 * first ([REWRITE] of the bar: loading and patching, then writing per zip entry), then signed.
 */
internal class Meter(apks: List<File>, private val report: (Float) -> Unit) {
    private val total = apks.sumOf { it.length() }.coerceAtLeast(1).toFloat()
    private val share = apks.associateWith { it.length() / total }
    private var rewritten = 0f
    private var signed = 0f
    private var last = -1f

    fun writing(apk: File): (Float) -> Unit {
        val start = rewritten
        val size = share.getValue(apk)
        rewritten += size
        return { written -> emit(REWRITE * (start + size * (LOADED + (1 - LOADED) * written.coerceIn(0f, 1f)))) }
    }

    fun signed(apk: File) {
        signed += share.getValue(apk)
        emit(REWRITE + (1 - REWRITE) * signed)
    }

    // Zip writes call in per entry; only whole-percent changes reach the UI
    private fun emit(value: Float) {
        if (value - last < 0.01f && value < 1f) return
        last = value
        report(value.coerceIn(0f, 1f))
    }

    private companion object {
        const val REWRITE = 0.7f
        const val LOADED = 0.4f
    }
}
