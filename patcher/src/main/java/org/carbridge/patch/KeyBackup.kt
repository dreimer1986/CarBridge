package org.carbridge.patch

import com.android.apksig.SigningCertificateLineage
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** A backup the user can act on; its message is shown as is. */
class BackupFailure(message: String) : Exception(message)

/**
 * The backup signing key, its certificate and the lineage from the phone's Keystore key, sealed
 * with a recovery code. Layout: magic, version, salt, nonce, AES-256-GCM ciphertext.
 */
class KeyBackup(val key: PrivateKey, val cert: X509Certificate, val lineage: SigningCertificateLineage) {

    fun seal(code: String): ByteArray {
        val salt = random(SALT_BYTES)
        val nonce = random(NONCE_BYTES)
        val header = MAGIC + VERSION + salt + nonce
        val cipher = cipher(Cipher.ENCRYPT_MODE, normalize(code), salt, nonce, header)
        return header + cipher.doFinal(encodeContents())
    }

    private fun encodeContents(): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            for (part in listOf(key.encoded, cert.encoded, lineage.bytes)) {
                out.writeInt(part.size)
                out.write(part)
            }
        }
    }.toByteArray()

    companion object {
        const val FILE_EXTENSION = "cbkey"
        const val MIME_TYPE = "application/octet-stream"

        private val MAGIC = "CBKEY".toByteArray()
        private const val VERSION: Byte = 1
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private val HEADER_BYTES = MAGIC.size + 1 + SALT_BYTES + NONCE_BYTES
        // The code carries 120 random bits, so iterations only slow down a stolen file a little
        private const val ITERATIONS = 100_000

        // Crockford base32: no I, L, O or U, so a hand-copied code has nothing to confuse
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val CODE_CHARS = 24

        /** A fresh recovery code, shown as six groups of four, e.g. `7KQ2-…`. */
        fun newRecoveryCode(): String {
            val rng = SecureRandom()
            return (1..CODE_CHARS).map { ALPHABET[rng.nextInt(ALPHABET.length)] }
                .chunked(4).joinToString("-") { it.joinToString("") }
        }

        /** Uppercases and drops separators; reads O as 0 and I or L as 1, as Crockford allows. */
        fun normalize(code: String): String = code.uppercase()
            .filter { it.isLetterOrDigit() }
            .map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
            .joinToString("")

        fun isWellFormed(code: String): Boolean = normalize(code).let { c -> c.length == CODE_CHARS && c.all { it in ALPHABET } }

        fun open(file: ByteArray, code: String): KeyBackup {
            if (file.size <= HEADER_BYTES || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                throw BackupFailure("This isn't a CarBridge key backup.")
            }
            if (file[MAGIC.size] != VERSION) {
                throw BackupFailure("This backup was made by a newer CarBridge. Update CarBridge, then try again.")
            }
            if (!isWellFormed(code)) throw BackupFailure("A recovery code has 24 letters and digits.")
            val header = file.copyOfRange(0, HEADER_BYTES)
            val salt = header.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + SALT_BYTES)
            val nonce = header.copyOfRange(HEADER_BYTES - NONCE_BYTES, HEADER_BYTES)
            val contents = try {
                cipher(Cipher.DECRYPT_MODE, normalize(code), salt, nonce, header).doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
            } catch (e: GeneralSecurityException) {
                // GCM can't tell a wrong code from a damaged file
                throw BackupFailure("The recovery code doesn't match this backup, or the file is damaged.")
            }
            return decodeContents(contents)
        }

        private fun decodeContents(contents: ByteArray): KeyBackup {
            val (keyBytes, certBytes, lineageBytes) = DataInputStream(contents.inputStream()).use { input ->
                List(3) { ByteArray(input.readInt()).also(input::readFully) }
            }
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(certBytes.inputStream()) as X509Certificate
            val lineage = SigningCertificateLineage.readFromBytes(lineageBytes)
            if (!lineage.isCertificateLatestInLineage(cert)) throw BackupFailure("This backup is damaged: its key doesn't match its history.")
            return KeyBackup(key, cert, lineage)
        }

        private fun cipher(mode: Int, code: String, salt: ByteArray, nonce: ByteArray, header: ByteArray): Cipher {
            val spec = PBEKeySpec(code.toCharArray(), salt, ITERATIONS, 256)
            val secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(header)
            }
        }

        private fun random(n: Int) = ByteArray(n).also(SecureRandom()::nextBytes)
    }
}
