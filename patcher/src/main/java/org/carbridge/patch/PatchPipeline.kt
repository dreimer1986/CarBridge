package org.carbridge.patch

import org.carbridge.core.CarOptions

import android.content.Context
import android.content.pm.ApplicationInfo
import com.reandroid.apk.ApkModule
import com.reandroid.archive.writer.ZipAligner
import java.io.File

/** Original APK(s) in, signed car-enabled APK(s) out, base first. Runs off the main thread. */
class PatchPipeline(private val context: Context, private val log: (String) -> Unit) {

    fun patch(app: ApplicationInfo, options: CarOptions): List<File> {
        val work = File(context.cacheDir, "patch").apply { deleteRecursively(); mkdirs() }
        val newPackage = carPackageName(app.packageName)
        val splits = app.splitSourceDirs.orEmpty().map(::File)
        val base = File(app.sourceDir)

        val total = (splits + base).sumOf { it.length() }
        log("Reading ${total / MB} MB in ${splits.size + 1} APK(s)")
        // Unsigned and signed copies sit side by side while signing
        val needed = total * 2 + 64 * MB
        val free = work.usableSpace
        check(free >= needed) { "Not enough free space: needs about ${needed / MB} MB, ${free / MB} MB free" }

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
        val signer = KeystoreSigner()
        val signed = (listOf(unsignedBase) + unsignedSplits).map { unsigned ->
            File(work, unsigned.name.replace(".unsigned", "")).also { out ->
                signer.sign(unsigned, out, minSdk)
                unsigned.delete()
            }
        }

        log("Verifying signatures")
        val cert = signer.certificate()
        val problems = signed.flatMap { apk -> ApkSigning.verify(apk, minSdk, cert).map { "${apk.name}: $it" } }
        check(problems.isEmpty()) { "Signature check failed:\n" + problems.joinToString("\n") }
        log("Signed with ${ApkSigning.sha256(cert).take(17)}…")
        return signed
    }

    companion object {
        private const val MB = 1024L * 1024
        private val V1_SIGNATURE_FILES =
            Regex("META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC))", RegexOption.IGNORE_CASE).toPattern()

        const val CAR_LABEL_SUFFIX = " (Car)"

        fun carPackageName(original: String) = "$original.car"

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
