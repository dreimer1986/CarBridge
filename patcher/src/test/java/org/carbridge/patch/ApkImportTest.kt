package org.carbridge.patch

import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkImportTest {
    private val dir = File(System.getProperty("patchOut")).parentFile.resolve("import").apply {
        deleteRecursively(); mkdirs()
    }

    @Test
    fun findsBaseAndSplits() {
        val base = apk("base.apk", "com.example")
        val split = apk("split_config.arm64_v8a.apk", "com.example", split = "config.arm64_v8a")
        val import = ApkImport.read(listOf(split, base), dir)
        assertEquals(base, import.base)
        assertEquals(listOf(split), import.splits)
        assertEquals("com.example", import.packageName)
    }

    @Test
    fun unpacksBundles() {
        val bundle = File(dir, "app.apks")
        ZipOutputStream(bundle.outputStream()).use { zip ->
            for ((name, file) in listOf(
                "base.apk" to apk("b.apk", "com.example"),
                "splits/../../evil.apk" to apk("s.apk", "com.example", split = "config.en"),
            )) {
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("info.json"))
            zip.closeEntry()
        }
        val import = ApkImport.read(listOf(bundle), dir)
        assertEquals("com.example", import.packageName)
        assertEquals(1, import.splits.size)
        // Unpacked files stay inside the import folder whatever the entry names say
        assertTrue((import.splits + import.base).all { it.parentFile == dir })
    }

    @Test
    fun refusesBundlesLargerThanTheRoomLeft() {
        val bundle = bundle("big.apks", List(3) { i -> "part$i.apk" to apk("p$i.apk", "com.example", split = if (i == 0) null else "config.c$i") })
        val needed = ZipFile(bundle).use { zip -> zip.entries().asSequence().sumOf { it.size } }
        val failure = assertThrows(ImportFailure::class.java) { ApkImport.read(listOf(bundle), dir, room = needed - 1) }
        assertTrue(failure.message!!.contains("free space"))
        ApkImport.read(listOf(bundle), dir, room = needed)
    }

    @Test
    fun refusesBundlesWithTooManyApks() {
        val part = apk("one.apk", "com.example")
        val failure = assertThrows(ImportFailure::class.java) {
            ApkImport.read(listOf(bundle("many.apks", List(201) { "p$it.apk" to part })), dir)
        }
        assertTrue(failure.message!!.contains("too many"))
    }

    @Test
    fun rejectsMixedApps() {
        val failure = assertThrows(ImportFailure::class.java) {
            ApkImport.read(listOf(apk("a.apk", "com.a"), apk("b.apk", "com.b", split = "config.en")), dir)
        }
        assertTrue(failure.message!!.contains("different apps"))
    }

    @Test
    fun rejectsMixedVersions() {
        assertThrows(ImportFailure::class.java) {
            ApkImport.read(listOf(apk("a.apk", "com.a", version = 1), apk("b.apk", "com.a", split = "config.en", version = 2)), dir)
        }
    }

    @Test
    fun needsExactlyOneBase() {
        assertThrows(ImportFailure::class.java) { ApkImport.read(listOf(apk("s.apk", "com.a", split = "config.en")), dir) }
        assertThrows(ImportFailure::class.java) { ApkImport.read(listOf(apk("a.apk", "com.a"), apk("b.apk", "com.a")), dir) }
    }

    @Test
    fun rejectsCarCopiesAndNonApks() {
        assertThrows(ImportFailure::class.java) { ApkImport.read(listOf(apk("c.apk", "com.a.car")), dir) }
        val text = File(dir, "notes.apk").apply { writeText("not an apk") }
        assertThrows(ImportFailure::class.java) { ApkImport.read(listOf(text), dir) }
    }

    private fun bundle(name: String, parts: List<Pair<String, File>>): File = File(dir, name).also { out ->
        ZipOutputStream(out.outputStream()).use { zip ->
            for ((entry, file) in parts) {
                zip.putNextEntry(ZipEntry(entry))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun apk(name: String, pkg: String, split: String? = null, version: Int = 7): File {
        val manifest = AndroidManifestBlock().apply {
            packageName = pkg
            versionCode = version
            if (split != null) setSplit(split, true)
        }
        manifest.getOrCreateApplicationElement()
        return File(dir, name).also { out -> ApkModule().apply { setManifest(manifest) }.use { it.writeApk(out) } }
    }
}
