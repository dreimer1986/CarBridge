package org.carbridge.patch

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A minimal self-signed X.509 v3 certificate for an RSA key held in software. Android has no
 * public certificate builder, and Keystore only certifies keys it generates itself.
 */
object SelfSignedCertificate {
    private val SHA256_WITH_RSA = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B)
    private val COMMON_NAME = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)

    fun create(keys: KeyPair, commonName: String, years: Int): X509Certificate {
        val start = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val end = (start.clone() as Calendar).apply { add(Calendar.YEAR, years) }
        val algorithm = seq(SHA256_WITH_RSA, byteArrayOf(0x05, 0x00))
        val name = seq(tlv(0x31, seq(COMMON_NAME, tlv(0x0C, commonName.toByteArray()))))
        val serial = BigInteger(63, SecureRandom()).add(BigInteger.ONE)
        val tbs = seq(
            tlv(0xA0, int(BigInteger.TWO)),
            int(serial),
            algorithm,
            name,
            seq(time(start.time), time(end.time)),
            name,
            keys.public.encoded,
        )
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keys.private)
            update(tbs)
            sign()
        }
        val der = seq(tbs, algorithm, tlv(0x03, byteArrayOf(0) + signature))
        return CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()) as X509Certificate
    }

    // RFC 5280: UTCTime through 2049, GeneralizedTime after
    private fun time(date: Date): ByteArray {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
        val (tag, pattern) = if (utc.get(Calendar.YEAR) < 2050) 0x17 to "yyMMddHHmmss'Z'" else 0x18 to "yyyyMMddHHmmss'Z'"
        val text = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
        return tlv(tag, text.toByteArray())
    }

    private fun int(value: BigInteger) = tlv(0x02, value.toByteArray())

    private fun seq(vararg parts: ByteArray) = tlv(0x30, parts.fold(ByteArray(0), ByteArray::plus))

    private fun tlv(tag: Int, content: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(tag)
        val n = content.size
        when {
            n < 0x80 -> write(n)
            n < 0x100 -> { write(0x81); write(n) }
            n < 0x10000 -> { write(0x82); write(n shr 8); write(n and 0xFF) }
            else -> { write(0x83); write(n shr 16); write((n shr 8) and 0xFF); write(n and 0xFF) }
        }
        write(content)
    }.toByteArray()
}
