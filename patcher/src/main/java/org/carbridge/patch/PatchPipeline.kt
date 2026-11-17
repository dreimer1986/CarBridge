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

/** Original APK(s) in, signed car-enabled APK(s) out, base first. Runs off the main thread. */
class PatchPipeline(private val context: Context, private val log: (String) -> Unit) {

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

        val patcher = ManifestPatcher(log)
        val unsignedBase = File(work, "base.unsigned.apk")
        val branding = IconBadger.branding(context, app)
        val minSdk = rewrite(base, unsignedBase) { apk -> patcher.patch(apk, newPackage, branding, options) }
        val unsignedSplits = splits.mapIndexed { i, split ->
            File(work, "split$i.unsigned.apk").also { out ->
                rewrite(split, out) { apk -> patcher.patchSplit(apk, newPackage) }
            }
        }

        log("Signing")
        val signing = KeystoreSigner(context).signerFor(context.packageManager, newPackage)
        val signed = (listOf(unsignedBase) + unsignedSplits).map { unsigned ->
            File(work, unsigned.name.replace(".unsigned", "")).also { out ->
                signing.sign(unsigned, out, minSdk)
                unsigned.delete()
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
        internal fun rewrite(input: File, output: File, edit: (ApkModule) -> Unit): Int =
            ApkModule.loadApkFile(input).use { apk ->
                apk.setApkSignatureBlock(null)
                apk.zipEntryMap.removeIf(V1_SIGNATURE_FILES)
                edit(apk)
                val writer = apk.createApkFileWriter(output)
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
