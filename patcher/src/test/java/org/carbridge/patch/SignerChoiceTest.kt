package org.carbridge.patch

import com.android.apksig.SigningCertificateLineage
import java.security.KeyPair
import java.security.KeyPairGenerator
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SignerChoiceTest {
    private val pairA = keyPair()
    private val pairB = keyPair()
    private val pairNew = keyPair()
    private val certA = SelfSignedCertificate.create(pairA, "A", years = 1)
    private val certB = SelfSignedCertificate.create(pairB, "B", years = 1)
    private val certNew = SelfSignedCertificate.create(pairNew, "A after reinstall", years = 1)
    private val lineage = SigningCertificateLineage.Builder(
        SigningCertificateLineage.SignerConfig.Builder(pairA.private, certA).build(),
        SigningCertificateLineage.SignerConfig.Builder(pairB.private, certB).build(),
    ).setMinSdkVersion(28).build()

    private val ownBeforeLoss = Signing(pairA.private, certA, null)
    private val ownAfterReinstall = Signing(pairNew.private, certNew, null)
    private val recovered = Signing(pairB.private, certB, lineage)

    @Test
    fun ownKeyUpdatesItsCopies() {
        assertSame(ownBeforeLoss, KeystoreSigner.choose(certA.encoded, ownBeforeLoss, null))
    }

    @Test
    fun restoredKeyUpdatesCopiesOfTheLostKeyAndItsOwn() {
        assertSame(recovered, KeystoreSigner.choose(certA.encoded, ownAfterReinstall, recovered))
        assertSame(recovered, KeystoreSigner.choose(certB.encoded, ownAfterReinstall, recovered))
    }

    @Test
    fun copiesMadeAfterReinstallKeepTheirKey() {
        assertSame(ownAfterReinstall, KeystoreSigner.choose(certNew.encoded, ownAfterReinstall, recovered))
    }

    @Test
    fun nothingUpdatesAnUnknownSigner() {
        assertNull(KeystoreSigner.choose(certA.encoded, ownAfterReinstall, null))
        // Android won't roll a copy rotated to B back to A
        assertNull(KeystoreSigner.choose(certB.encoded, ownBeforeLoss, null))
    }

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
}
