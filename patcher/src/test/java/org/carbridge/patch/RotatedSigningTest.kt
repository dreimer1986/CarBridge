package org.carbridge.patch

import com.android.apksig.ApkVerifier
import com.android.apksig.SigningCertificateLineage
import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.File
import java.text.SimpleDateFormat
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A copy signed with the backup key plus the lineage from the original key, without the original's private key. */
class RotatedSigningTest {
    private val dir = File(System.getProperty("patchOut")).parentFile.resolve("rotation").apply {
        deleteRecursively(); mkdirs()
    }

    @Test
    fun signsWithBackupKeyAloneAndKeepsLineage() {
        val (keyA, certA) = throwawayKey("a")
        // The backup key is made in software, with a certificate CarBridge builds itself
        val pairB = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val keyB = pairB.private
        val certB = SelfSignedCertificate.create(pairB, "CarBridge backup signer", years = 30)
        certB.verify(pairB.public)
        assertTrue(certB.notAfter.after(SimpleDateFormat("yyyy").parse("2050")))
        val lineage = SigningCertificateLineage.Builder(
            SigningCertificateLineage.SignerConfig.Builder(keyA, certA).build(),
            SigningCertificateLineage.SignerConfig.Builder(keyB, certB).build(),
        ).setMinSdkVersion(28).build()

        val unsigned = minimalApk(minSdk = 21)
        // Signed like today's copies, to install before rotated.apk when checking updates on a phone
        ApkSigning.sign(unsigned, File(dir, "original.apk"), 21, keyA, certA)
        val out = File(dir, "rotated.apk")
        ApkSigning.sign(unsigned, out, 21, keyB, certB, lineage)
        val verified = ApkVerifier.Builder(out).setMinCheckedPlatformVersion(35).build().verify()
        assertEquals(emptyList<Any>(), verified.allErrors)
        assertTrue(verified.isVerifiedUsingV3Scheme)
        assertEquals(listOf(certA, certB), verified.signingCertificateLineage.certificatesInLineage)
    }

    private fun minimalApk(minSdk: Int): File {
        val manifest = AndroidManifestBlock().apply {
            packageName = "org.carbridge.rotation.car"
            setMinSdkVersion(minSdk)
            setTargetSdkVersion(35)
        }
        manifest.getOrCreateApplicationElement()
            .getOrCreateAndroidAttribute("hasCode", ID_HAS_CODE).setValueAsBoolean(false)
        val out = File(dir, "unsigned.apk")
        ApkModule().apply { setManifest(manifest) }.use { it.writeApk(out) }
        return out
    }

    private fun throwawayKey(name: String): Pair<PrivateKey, X509Certificate> {
        val store = File(dir, "$name.p12").apply { delete() }
        val password = "throwaway"
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val process = ProcessBuilder(
            keytool, "-genkeypair", "-keystore", store.path, "-storetype", "PKCS12", "-storepass", password,
            "-alias", name, "-keyalg", "RSA", "-keysize", "3072", "-validity", "1", "-dname", "CN=CarBridge $name",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        val ks = KeyStore.getInstance("PKCS12").apply { store.inputStream().use { load(it, password.toCharArray()) } }
        return ks.getKey(name, password.toCharArray()) as PrivateKey to ks.getCertificate(name) as X509Certificate
    }

    private companion object {
        const val ID_HAS_CODE = 0x0101000c // android.R.attr.hasCode
    }
}
