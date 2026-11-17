package org.carbridge.patch

import com.android.apksig.SigningCertificateLineage
import java.security.KeyPairGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyBackupTest {
    private val backup by lazy {
        val a = keyPair()
        val b = keyPair()
        val certA = SelfSignedCertificate.create(a, "A", years = 1)
        val certB = SelfSignedCertificate.create(b, "B", years = 1)
        val lineage = SigningCertificateLineage.Builder(
            SigningCertificateLineage.SignerConfig.Builder(a.private, certA).build(),
            SigningCertificateLineage.SignerConfig.Builder(b.private, certB).build(),
        ).setMinSdkVersion(28).build()
        KeyBackup(b.private, certB, lineage)
    }

    @Test
    fun opensWhatItSeals() {
        val code = KeyBackup.newRecoveryCode()
        val opened = KeyBackup.open(backup.seal(code), code)
        assertArrayEquals(backup.key.encoded, opened.key.encoded)
        assertEquals(backup.cert, opened.cert)
        assertEquals(backup.lineage.certificatesInLineage, opened.lineage.certificatesInLineage)
    }

    @Test
    fun acceptsHandCopiedCode() {
        val code = KeyBackup.newRecoveryCode()
        val sloppy = code.lowercase().replace("-", " ").replace('0', 'o').replace('1', 'l')
        KeyBackup.open(backup.seal(code), sloppy)
    }

    @Test
    fun recoveryCodeIsSixGroupsOfFour() {
        val code = KeyBackup.newRecoveryCode()
        assertTrue(code, Regex("([0-9A-HJKMNP-TV-Z]{4}-){5}[0-9A-HJKMNP-TV-Z]{4}").matches(code))
        assertTrue(KeyBackup.isWellFormed(code))
        assertFalse(KeyBackup.isWellFormed(code.dropLast(1)))
    }

    @Test
    fun rejectsWrongCodeAndDamage() {
        val code = KeyBackup.newRecoveryCode()
        val sealed = backup.seal(code)
        assertThrows(BackupFailure::class.java) { KeyBackup.open(sealed, KeyBackup.newRecoveryCode()) }
        val damaged = sealed.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertThrows(BackupFailure::class.java) { KeyBackup.open(damaged, code) }
        // The header is authenticated too
        val newSalt = sealed.copyOf().also { it[6] = (it[6] + 1).toByte() }
        assertThrows(BackupFailure::class.java) { KeyBackup.open(newSalt, code) }
    }

    @Test
    fun rejectsOtherFiles() {
        val failure = assertThrows(BackupFailure::class.java) { KeyBackup.open("hello".toByteArray(), KeyBackup.newRecoveryCode()) }
        assertEquals("This isn't a CarBridge key backup.", failure.message)
    }

    private fun keyPair() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
}
