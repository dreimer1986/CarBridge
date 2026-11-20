package org.carbridge.patch

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import com.android.apksig.SigningCertificateLineage
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar
import javax.security.auth.x500.X500Principal

/** A key that signs copies, with the lineage that lets it update copies signed by older keys. */
class Signing(val key: PrivateKey, val cert: X509Certificate, val lineage: SigningCertificateLineage?) {

    fun sign(input: File, output: File, minSdk: Int) = ApkSigning.sign(input, output, minSdk, key, cert, lineage)

    fun verify(apk: File, minSdk: Int): List<String> =
        ApkSigning.verify(apk, if (lineage == null) minSdk else ApkSigning.COPY_MIN_SDK, cert)

    /** True when Android would install a copy signed this way over one whose current signer is [installed]. */
    fun updates(installed: ByteArray): Boolean =
        (lineage?.certificatesInLineage ?: listOf(cert)).any { it.encoded.contentEquals(installed) }

    val fingerprint: String get() = ApkSigning.sha256(cert)
}

/**
 * Signs patched APKs with keys kept in the Android Keystore. The phone's own key signs copies
 * until a backup is restored; then the restored key, with its lineage, signs them, so copies
 * made before CarBridge was reinstalled can update again.
 */
class KeystoreSigner(context: Context) {
    private val dir = File(context.filesDir, "signing").apply { mkdirs() }
    private val recoveredLineage = File(dir, "recovered.lineage")
    private val backedUp = File(dir, "backed-up")
    private val ks by lazy { KeyStore.getInstance(KEYSTORE).apply { load(null) } }

    /** The key for [pkg]: the one that can update its installed copy, else the one for new copies. */
    fun signerFor(pm: PackageManager, pkg: String): Signing {
        val installed = currentSigner(pm, pkg)
        if (installed == null) return active(create = true)!!
        return choose(installed, own(create = false), recovered())
            ?: throw PatchFailure(
                "This car copy was signed with a key this phone no longer has. Restore your key backup, " +
                    "or uninstall the copy and add it again.",
            )
    }

    /**
     * False when installed [pkg] was signed with a key this phone no longer has, so Android would
     * refuse a rebuilt copy as an update. True when it isn't installed or its signers can't be read.
     */
    fun canUpdate(pm: PackageManager, pkg: String): Boolean {
        val installed = currentSigner(pm, pkg) ?: return true
        return choose(installed, own(create = false), recovered()) != null
    }

    /** True when the key new copies are signed with is in a backup the user saved. */
    fun hasBackup(): Boolean {
        val active = active(create = false) ?: return false
        return runCatching { backedUp.readText() }.getOrNull() == active.fingerprint
    }

    /**
     * A new backup of the key new copies are signed with: a software key that key vouches for,
     * sealed with a fresh recovery code. Neither is kept on the phone.
     */
    fun createBackup(): Pair<ByteArray, String> {
        val parent = active(create = true)!!
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS) }.generateKeyPair()
        val cert = SelfSignedCertificate.create(pair, "CarBridge backup signer", YEARS)
        val parentConfig = SigningCertificateLineage.SignerConfig.Builder(parent.key, parent.cert).build()
        val childConfig = SigningCertificateLineage.SignerConfig.Builder(pair.private, cert).build()
        val lineage = parent.lineage?.spawnDescendant(parentConfig, childConfig)
            ?: SigningCertificateLineage.Builder(parentConfig, childConfig).setMinSdkVersion(28).build()
        val code = KeyBackup.newRecoveryCode()
        return KeyBackup(pair.private, cert, lineage).seal(code) to code
    }

    /** Call once the user has saved the file from [createBackup]. */
    fun markBackedUp() {
        active(create = false)?.let { backedUp.writeText(it.fingerprint) }
    }

    /** Imports the key in [file]; from then on it signs new copies and updates the ones it covers. */
    fun restore(file: ByteArray, code: String): KeyBackup {
        val backup = KeyBackup.open(file, code)
        ks.setEntry(
            ALIAS_RECOVERED,
            KeyStore.PrivateKeyEntry(backup.key, arrayOf(backup.cert)),
            KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                .setDigests(*DIGESTS)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build(),
        )
        recoveredLineage.writeBytes(backup.lineage.bytes)
        // The restored file is itself the backup of the key now in use
        backedUp.writeText(ApkSigning.sha256(backup.cert))
        return backup
    }

    /**
     * Opens [file] without importing it. True when it can restore the key new copies are signed
     * with; then that key counts as backed up, since the user has just shown the file works.
     */
    fun check(file: ByteArray, code: String): Boolean {
        val backup = KeyBackup.open(file, code)
        val active = active(create = false) ?: return false
        if (!backup.covers(active.cert)) return false
        backedUp.writeText(active.fingerprint)
        return true
    }

    /** The current signer of installed [pkg]; null when it isn't installed or can't be read. */
    private fun currentSigner(pm: PackageManager, pkg: String): ByteArray? = runCatching {
        pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
    }.getOrNull()

    private fun active(create: Boolean): Signing? = recovered() ?: own(create)

    private fun recovered(): Signing? {
        if (!recoveredLineage.exists()) return null
        val entry = ks.getEntry(ALIAS_RECOVERED, null) as KeyStore.PrivateKeyEntry? ?: return null
        val lineage = SigningCertificateLineage.readFromBytes(recoveredLineage.readBytes())
        return Signing(entry.privateKey, entry.certificate as X509Certificate, lineage)
    }

    private fun own(create: Boolean): Signing? {
        if (!ks.containsAlias(ALIAS)) {
            if (!create) return null
            generateKey()
        }
        val entry = ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        return Signing(entry.privateKey, entry.certificate as X509Certificate, null)
    }

    private fun generateKey() {
        val start = Calendar.getInstance()
        val end = Calendar.getInstance().apply { add(Calendar.YEAR, YEARS) }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setKeySize(KEY_BITS)
            .setDigests(*DIGESTS)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setCertificateSubject(X500Principal("CN=CarBridge local signer"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(start.time)
            .setCertificateNotAfter(end.time)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE).apply {
            initialize(spec)
            generateKeyPair()
        }
    }

    internal companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "carbridge-signer"
        const val ALIAS_RECOVERED = "carbridge-signer-recovered"
        const val KEY_BITS = 3072
        const val YEARS = 30

        // SHA-1 is required for v1 signatures on apps with minSdk < 18
        val DIGESTS = arrayOf(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)

        /** The signer that can update a copy whose current signer is [installed], or null if none can. */
        fun choose(installed: ByteArray, own: Signing?, recovered: Signing?): Signing? =
            listOfNotNull(own, recovered).firstOrNull { it.updates(installed) }
    }
}
