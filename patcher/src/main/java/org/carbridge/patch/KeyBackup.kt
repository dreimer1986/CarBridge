package org.carbridge.patch

import androidx.annotation.StringRes
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
import org.carbridge.R
import org.carbridge.core.CarOptions

/** Why a backup can't be opened; [Reason.text] is what the user sees, the message is for logs. */
class BackupFailure(val reason: Reason, message: String) : Exception(message) {
    enum class Reason(@StringRes val text: Int) {
        NotABackup(R.string.backup_not_backup),
        Newer(R.string.backup_newer),
        CodeFormat(R.string.backup_code_format),
        WrongCodeOrDamaged(R.string.backup_code_wrong),
        Damaged(R.string.backup_damaged),
    }
}

/** A car copy listed in a backup, so a new phone can make it again with the same choices. */
data class BackedUpCopy(val originalPackage: String, val options: CarOptions, val autoUpdate: Boolean)

/**
 * The backup signing key, its certificate, the lineage from the phone's Keystore key and, from
 * version 2, the copies it was made for, sealed with a recovery code. Layout: magic, version,
 * salt, nonce, AES-256-GCM ciphertext.
 */
class KeyBackup(
    val key: PrivateKey,
    val cert: X509Certificate,
    val lineage: SigningCertificateLineage,
    val copies: List<BackedUpCopy> = emptyList(),
) {

    /** True when restoring this backup lets CarBridge update copies signed with [signer]. */
    fun covers(signer: X509Certificate): Boolean = lineage.certificatesInLineage.any { it.encoded.contentEquals(signer.encoded) }

    /** [version] is for tests of older files; version 1 has no copy list. */
    fun seal(code: String, version: Byte = VERSION): ByteArray {
        val salt = random(SALT_BYTES)
        val nonce = random(NONCE_BYTES)
        val header = MAGIC + version + salt + nonce
        val cipher = cipher(Cipher.ENCRYPT_MODE, normalize(code), salt, nonce, header)
        return header + cipher.doFinal(encodeContents(version))
    }

    private fun encodeContents(version: Byte): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            val parts = listOf(key.encoded, cert.encoded, lineage.bytes) + if (version >= 2) listOf(encodeCopies()) else emptyList()
            for (part in parts) {
                out.writeInt(part.size)
                out.write(part)
            }
        }
    }.toByteArray()

    private fun encodeCopies(): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(copies.size)
            for (copy in copies) {
                out.writeUTF(copy.originalPackage)
                out.writeInt(flags(copy))
            }
        }
    }.toByteArray()

    companion object {
        const val FILE_EXTENSION = "cbkey"
        const val MIME_TYPE = "application/octet-stream"

        private val MAGIC = "CBKEY".toByteArray()
        // 2 adds the copy list; version 1 files still open
        private const val VERSION: Byte = 2
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
                throw BackupFailure(BackupFailure.Reason.NotABackup, "This isn't a CarBridge key backup.")
            }
            val version = file[MAGIC.size]
            if (version < 1 || version > VERSION) {
                throw BackupFailure(BackupFailure.Reason.Newer, "This backup was made by a newer CarBridge.")
            }
            if (!isWellFormed(code)) throw BackupFailure(BackupFailure.Reason.CodeFormat, "A recovery code has 24 letters and digits.")
            val header = file.copyOfRange(0, HEADER_BYTES)
            val salt = header.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + SALT_BYTES)
            val nonce = header.copyOfRange(HEADER_BYTES - NONCE_BYTES, HEADER_BYTES)
            val contents = try {
                cipher(Cipher.DECRYPT_MODE, normalize(code), salt, nonce, header).doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
            } catch (e: GeneralSecurityException) {
                // GCM can't tell a wrong code from a damaged file
                throw BackupFailure(BackupFailure.Reason.WrongCodeOrDamaged, "Wrong recovery code or damaged file.")
            }
            return decodeContents(contents, version)
        }

        private fun decodeContents(contents: ByteArray, version: Byte): KeyBackup {
            val parts = DataInputStream(contents.inputStream()).use { input ->
                List(if (version >= 2) 4 else 3) { ByteArray(input.readInt()).also(input::readFully) }
            }
            val (keyBytes, certBytes, lineageBytes) = parts
            val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(certBytes.inputStream()) as X509Certificate
            val lineage = SigningCertificateLineage.readFromBytes(lineageBytes)
            if (!lineage.isCertificateLatestInLineage(cert)) throw BackupFailure(BackupFailure.Reason.Damaged, "The key doesn't match its lineage.")
            return KeyBackup(key, cert, lineage, parts.getOrNull(3)?.let(::decodeCopies).orEmpty())
        }

        private fun decodeCopies(bytes: ByteArray): List<BackedUpCopy> = DataInputStream(bytes.inputStream()).use { input ->
            List(input.readInt()) {
                val pkg = input.readUTF()
                val flags = input.readInt()
                BackedUpCopy(
                    pkg,
                    CarOptions(
                        keepMediaServices = flags and KEEP_MEDIA != 0,
                        fillScreen = flags and FILL_SCREEN != 0,
                        hideOnPhone = flags and HIDE_ON_PHONE != 0,
                    ),
                    autoUpdate = flags and AUTO_UPDATE != 0,
                )
            }
        }

        // Bits, so later choices can be added without changing the layout
        private const val KEEP_MEDIA = 1
        private const val FILL_SCREEN = 2
        private const val HIDE_ON_PHONE = 4
        private const val AUTO_UPDATE = 8

        private fun flags(copy: BackedUpCopy): Int =
            (if (copy.options.keepMediaServices) KEEP_MEDIA else 0) or
                (if (copy.options.fillScreen) FILL_SCREEN else 0) or
                (if (copy.options.hideOnPhone) HIDE_ON_PHONE else 0) or
                (if (copy.autoUpdate) AUTO_UPDATE else 0)

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
