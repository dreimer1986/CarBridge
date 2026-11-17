package org.carbridge.patch

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.reandroid.apk.ApkModule
import java.io.File
import java.util.zip.ZipFile

/** A file the user picked that CarBridge can't make a copy from; its message is shown as is. */
class ImportFailure(message: String) : Exception(message)

/**
 * APK files the user picked instead of an installed app: one base APK plus its split APKs, all of
 * one package and version. Bundles (.apks, .xapk) are zips of those and get unpacked first.
 */
class ApkImport(val base: File, val splits: List<File>, val packageName: String, val versionCode: Int) {

    /** The app as Android reads it from the files, so the pipeline and icon code treat it like an installed one. */
    fun applicationInfo(pm: PackageManager): ApplicationInfo {
        val info = pm.getPackageArchiveInfo(base.path, 0)?.applicationInfo
            ?: throw ImportFailure("Android can't read ${base.name} as an app.")
        return info.apply {
            sourceDir = base.path
            publicSourceDir = base.path
            splitSourceDirs = splits.map { it.path }.toTypedArray().takeIf { it.isNotEmpty() }
        }
    }

    companion object {
        /** Where picked files are copied; apart from the patch folder, which each build empties. */
        fun workDir(cacheDir: File) = File(cacheDir, "import")

        /** Reads [files] (already copied into [dir]) and unpacks bundles into it. */
        fun read(files: List<File>, dir: File): ApkImport {
            if (files.isEmpty()) throw ImportFailure("No file was picked.")
            val apks = files.flatMap { file -> if (isBundle(file)) unpack(file, dir) else listOf(file) }
            if (apks.isEmpty()) throw ImportFailure("There's no APK in that file.")

            val parts = apks.map(::describe)
            val packages = parts.map { it.packageName }.distinct()
            if (packages.size > 1) throw ImportFailure("These files are from different apps: ${packages.joinToString()}.")
            val packageName = packages.single()
            if (packageName.endsWith(PatchPipeline.carPackageName(""))) {
                throw ImportFailure("This is already a car copy. Pick the original app's APK.")
            }
            if (parts.map { it.versionCode }.distinct().size > 1) {
                throw ImportFailure("These files are from different versions of the app. Pick files from one download.")
            }
            val bases = parts.filter { it.split == null }
            when {
                bases.isEmpty() -> throw ImportFailure("The main APK is missing; only split APKs were picked.")
                bases.size > 1 -> throw ImportFailure("More than one main APK was picked. Pick one app at a time.")
            }
            val splits = parts.filter { it.split != null }.distinctBy { it.split }.map { it.file }
            return ApkImport(bases.single().file, splits, packageName, parts.first().versionCode)
        }

        private class Part(val file: File, val packageName: String, val split: String?, val versionCode: Int)

        private fun describe(file: File): Part = try {
            ApkModule.loadApkFile(file).use { apk ->
                val manifest = apk.androidManifest ?: throw ImportFailure("${file.name} isn't an Android app.")
                Part(
                    file,
                    manifest.packageName ?: throw ImportFailure("${file.name} has no package name."),
                    manifest.split?.takeIf { it.isNotEmpty() },
                    manifest.versionCode ?: 0,
                )
            }
        } catch (e: ImportFailure) {
            throw e
        } catch (e: Exception) {
            // APKMirror's .apkm bundles are encrypted, which lands here too
            throw ImportFailure("${file.name} can't be read as an APK. Encrypted bundles such as .apkm aren't supported.")
        }

        /** A zip of APKs rather than an APK itself, which always has a manifest at its root. */
        private fun isBundle(file: File): Boolean = runCatching {
            ZipFile(file).use { zip ->
                zip.getEntry("AndroidManifest.xml") == null && zip.entries().asSequence().any { it.name.endsWith(".apk") }
            }
        }.getOrDefault(false)

        private fun unpack(bundle: File, dir: File): List<File> = ZipFile(bundle).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".apk") }
                .mapIndexed { i, entry ->
                    // Index-based names: entry paths can't escape the folder or collide
                    File(dir, "bundle-$i.apk").also { out ->
                        zip.getInputStream(entry).use { input -> out.outputStream().use(input::copyTo) }
                    }
                }
                .toList()
        }
    }
}
