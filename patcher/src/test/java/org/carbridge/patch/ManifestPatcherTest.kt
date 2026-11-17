package org.carbridge.patch

import com.reandroid.apk.ApkModule
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import org.carbridge.core.CarOptions
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class ManifestPatcherTest {

    @Test
    fun renameValueSwapsPrefixAndPrefixesForeignParts() {
        assertEquals("a.b.car.provider", ManifestPatcher.renameValue("a.b.provider", "a.b", "a.b.car"))
        assertEquals("a.b.car.x;a.b.car.y", ManifestPatcher.renameValue("a.b.x;y", "a.b", "a.b.car"))
        assertEquals("a.b.car", ManifestPatcher.renameValue("a.b", "a.b", "a.b.car"))
    }

    /**
     * Runs the phone's patch and sign steps on a real APK, or a directory holding base.apk plus
     * split_*.apk, with a throwaway key, and verifies the signatures as Android's installer does.
     * The output can then be checked with aapt2 and apksigner. Skipped without -PpatchFixture.
     */
    @Test
    fun patchesSignsAndVerifiesFixture() {
        val fixture = System.getProperty("patchFixture")?.let(::File)
        assumeTrue("no -PpatchFixture given", fixture != null)
        val outDir = File(System.getProperty("patchOut")).parentFile.apply { deleteRecursively(); mkdirs() }

        val base = if (fixture!!.isDirectory) File(fixture, "base.apk") else fixture
        val splits = if (fixture.isDirectory) fixture.listFiles { f -> f.name.startsWith("split_") }!!.toList() else emptyList()
        val patcher = ManifestPatcher(::println)

        val newPackage = ApkModule.loadApkFile(base).use { it.androidManifest.packageName + ".car" }
        val unsignedBase = File(outDir, "base.unsigned.apk")
        val minSdk = PatchPipeline.rewrite(base, unsignedBase) { apk ->
            // Placeholder layers: aapt2 checks the resource wiring, not the PNG pixels
            val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
            patcher.patch(apk, newPackage, ManifestPatcher.Branding("Fixture (Car)", png, png), CarOptions(fillScreen = true))
        }
        val unsignedSplits = splits.map { split ->
            File(outDir, split.name.replace(".apk", ".unsigned.apk")).also { out ->
                PatchPipeline.rewrite(split, out) { apk -> patcher.patchSplit(apk, newPackage) }
            }
        }

        val (key, cert) = throwawayKey(outDir)
        for (unsigned in listOf(unsignedBase) + unsignedSplits) {
            val signed = File(outDir, unsigned.name.replace(".unsigned", ""))
            ApkSigning.sign(unsigned, signed, minSdk, key, cert)
            unsigned.delete()
            assertEquals("${signed.name} signature", emptyList<String>(), ApkSigning.verify(signed, minSdk, cert))
        }
    }

    /** A JDK keytool RSA key, standing in for the phone's Keystore key. */
    private fun throwawayKey(dir: File): Pair<PrivateKey, X509Certificate> {
        val store = File(dir, "throwaway.p12").apply { delete() }
        val password = "throwaway"
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val process = ProcessBuilder(
            keytool, "-genkeypair", "-keystore", store.path, "-storetype", "PKCS12", "-storepass", password,
            "-alias", "signer", "-keyalg", "RSA", "-keysize", "3072", "-validity", "1", "-dname", "CN=CarBridge test",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        val ks = KeyStore.getInstance("PKCS12").apply { store.inputStream().use { load(it, password.toCharArray()) } }
        store.delete()
        return ks.getKey("signer", password.toCharArray()) as PrivateKey to ks.getCertificate("signer") as X509Certificate
    }
}
