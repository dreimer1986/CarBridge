package org.carbridge.patch

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.apksig.SigningCertificateLineage
import java.io.File
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate

/** Signs and verifies APKs with any key, so the phone's Keystore key and a test key share one path. */
object ApkSigning {
    private const val PAGE_16K = 16 * 1024

    /** The oldest Android a car copy installs on: CarBridge's own minSdk. */
    const val COPY_MIN_SDK = 35

    /**
     * Signs with [key]. With a [lineage] ending in [cert], the copy updates copies signed by any
     * older key in it, even when this phone no longer holds those keys.
     */
    fun sign(
        input: File,
        output: File,
        minSdk: Int,
        key: PrivateKey,
        cert: X509Certificate,
        lineage: SigningCertificateLineage? = null,
    ) {
        val signer = ApkSigner.SignerConfig.Builder("CarBridge", key, listOf(cert)).build()
        val builder = ApkSigner.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(output)
            .setV3SigningEnabled(true)
            .setLibraryPageAlignmentBytes(PAGE_16K)
            .setCreatedBy("CarBridge")
        if (lineage == null) {
            builder.setMinSdkVersion(minSdk).setV1SigningEnabled(minSdk < 24).setV2SigningEnabled(true)
        } else {
            // v1 and v2 are signed by the lineage's oldest key, which a restored phone doesn't have
            builder.setMinSdkVersion(COPY_MIN_SDK)
                .setMinSdkVersionForRotation(28)
                .setSigningCertificateLineage(lineage)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(false)
        }
        builder.build().sign()
    }

    /** Problems Android's installer would find in [apk]; empty when it verifies and [cert] signed it. */
    fun verify(apk: File, minSdk: Int, cert: X509Certificate): List<String> {
        val result = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(minSdk).build().verify()
        val problems = (result.errors + result.v2SchemeSigners.flatMap { it.errors } + result.v3SchemeSigners.flatMap { it.errors })
            .map { it.toString() }
            .toMutableList()
        val signer = result.signerCertificates.firstOrNull()
        if (signer != null && !signer.encoded.contentEquals(cert.encoded)) problems += "signed with an unexpected certificate"
        if (!result.isVerified && problems.isEmpty()) problems += "signature did not verify"
        return problems
    }

    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02x".format(it) }
}
