package com.lumen.tvremote.protocol

import com.lumen.tvremote.connection.PairingSession
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date

class ProtocolTest {

    @Test
    fun varintAndStringsRoundTrip() {
        val bytes = ProtoWriter()
            .varint(1, 300)
            .string(2, "héllo")
            .bool(3, true)
            .message(4) { varint(1, 7) }
            .toByteArray()
        val msg = ProtoMessage.parse(bytes)
        assertEquals(300, msg.int(1))
        assertEquals("héllo", msg.string(2))
        assertTrue(msg.bool(3))
        assertEquals(7, msg.message(4)?.int(1))
    }

    @Test
    fun framingReadsBackToBackMessages() {
        val a = ByteArray(200) { it.toByte() } // needs a two-byte varint length
        val b = byteArrayOf(1, 2, 3)
        val stream = ByteArrayInputStream(ProtoWriter.frame(a) + ProtoWriter.frame(b))
        assertArrayEquals(a, ProtoMessage.readFrame(stream))
        assertArrayEquals(b, ProtoMessage.readFrame(stream))
        assertNull(ProtoMessage.readFrame(stream))
    }

    @Test
    fun decodesPingVolumeAndPower() {
        val ping = ProtoWriter().message(8) { varint(1, 42) }.toByteArray()
        assertEquals(RemoteEvent.Ping(42), RemoteProtocol.decode(ping))

        val volume = ProtoWriter().message(50) {
            string(3, "tv")
            varint(6, 100)
            varint(7, 23)
            bool(8, true)
        }.toByteArray()
        assertEquals(RemoteEvent.Volume(level = 23, max = 100, muted = true), RemoteProtocol.decode(volume))

        // proto3 omits false booleans, leaving an empty RemoteStart message.
        val powerOff = ProtoWriter().bytes(40, ByteArray(0)).toByteArray()
        assertEquals(RemoteEvent.Power(false), RemoteProtocol.decode(powerOff))
    }

    @Test
    fun decodesImeBatchEditCounters() {
        val edit = ProtoWriter().message(21) {
            varint(1, 5)
            varint(2, 9)
            message(3) {
                varint(1, 1)
                message(2) { string(3, "netflix") }
            }
        }.toByteArray()
        assertEquals(RemoteEvent.ImeBatchEdit(imeCounter = 5, fieldCounter = 9, text = "netflix"), RemoteProtocol.decode(edit))
    }

    @Test
    fun keyMessageLayout() {
        val msg = ProtoMessage.parse(RemoteProtocol.key(TvKey.HOME.code, RemoteProtocol.DIRECTION_START_LONG))
        val inject = msg.message(10)!!
        assertEquals(3, inject.int(1)) // KEYCODE_HOME
        assertEquals(1, inject.int(2))
    }

    @Test
    fun imeTextReplacesWholeField() {
        val msg = ProtoMessage.parse(RemoteProtocol.imeText(3, 4, "abc")).message(21)!!
        assertEquals(3, msg.int(1))
        assertEquals(4, msg.int(2))
        val status = msg.messages(3).single().message(2)!!
        assertEquals(2, status.int(1))
        assertEquals(2, status.int(2))
        assertEquals("abc", status.string(3))
    }

    @Test
    fun configureAdvertisesFeatures() {
        val msg = ProtoMessage.parse(RemoteProtocol.configure("Pixel", "Google", "1.0")).message(1)!!
        assertEquals(RemoteProtocol.FEATURES, msg.int(1))
        assertEquals("Pixel", msg.message(2)?.string(1))
    }

    @Test
    fun pairingSecretMatchesReferenceEncoding() {
        val client = selfSigned()
        val server = selfSigned()
        val code = "ABCDEF"
        val secret = PairingSession.computeSecret(client, server, code)

        // Reference: androidtvremote2 hashes the hex form of each number ("%X", with the
        // exponent zero-padded to even length) decoded back to bytes.
        fun hexBytes(n: BigInteger): ByteArray {
            var hex = n.toString(16).uppercase()
            if (hex.length % 2 == 1) hex = "0$hex"
            return ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
        val c = client.publicKey as RSAPublicKey
        val s = server.publicKey as RSAPublicKey
        val expected = MessageDigest.getInstance("SHA-256").run {
            update(hexBytes(c.modulus))
            update(hexBytes(c.publicExponent))
            update(hexBytes(s.modulus))
            update(hexBytes(s.publicExponent))
            update(byteArrayOf(0xCD.toByte(), 0xEF.toByte()))
            digest()
        }
        assertArrayEquals(expected, secret)
    }

    @Test
    fun pairingFramesCarryStatusOk() {
        val msg = ProtoMessage.parse(PairingProtocol.pairingRequest("svc", "phone"))
        assertEquals(PairingProtocol.STATUS_OK, PairingProtocol.status(msg))
        assertEquals("svc", msg.message(10)?.string(1))
        assertEquals("phone", msg.message(10)?.string(2))
    }

    @Test
    fun hugeFieldLengthIsRejectedWithoutOverflow() {
        // Field 1, length-delimited, declaring a length of 2^31 - 1 with no bytes behind it.
        val data = byteArrayOf(0x0A, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x07)
        assertNull(ProtoMessage.parseOrNull(data))
    }

    private fun selfSigned(): X509Certificate {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=test")
        val holder = JcaX509v3CertificateBuilder(
            name, BigInteger.ONE, Date(), Date(System.currentTimeMillis() + 86_400_000), name, kp.public,
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
        return JcaX509CertificateConverter().getCertificate(holder)
    }
}
