package com.lumen.tvremote.connection

import com.lumen.tvremote.protocol.PairingProtocol
import com.lumen.tvremote.protocol.ProtoMessage
import com.lumen.tvremote.protocol.ProtoWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

sealed interface PairingResult {
    data object Success : PairingResult
    /** The code failed the local checksum; the session stays open so the user can retry. */
    data object InvalidCode : PairingResult
    /** The TV rejected the code and ended the session; a new code must be requested. */
    data object CodeRejected : PairingResult
    data class Failed(val message: String) : PairingResult
}

/**
 * One pairing attempt against the TV's pairing port. [start] makes the TV display a
 * six-character code; [submitCode] completes pairing with the code the user typed.
 */
class PairingSession(
    private val identity: ClientIdentity,
    private val host: String,
    private val port: Int = DEFAULT_PAIRING_PORT,
    private val clientName: String,
) {
    private var socket: SSLSocket? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        close()
        val s = identity.openSocket(host, port, TIMEOUT_MS)
        socket = s
        exchange(PairingProtocol.pairingRequest(SERVICE_NAME, clientName), PairingProtocol.F_PAIRING_REQUEST_ACK)
        exchange(PairingProtocol.options(), PairingProtocol.F_OPTIONS)
        exchange(PairingProtocol.configuration(), PairingProtocol.F_CONFIGURATION_ACK)
    }

    suspend fun submitCode(rawCode: String): PairingResult = withContext(Dispatchers.IO) {
        val code = rawCode.trim().uppercase()
        if (code.length != PairingProtocol.CODE_LENGTH || !code.all { it in HEX_CHARS }) {
            return@withContext PairingResult.InvalidCode
        }
        val s = socket ?: return@withContext PairingResult.Failed("Pairing session expired. Start again.")
        val serverCert = s.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: return@withContext PairingResult.Failed("The TV did not present a certificate.")

        val secret = computeSecret(identity.certificate, serverCert, code)
        if (secret[0] != code.substring(0, 2).toInt(16).toByte()) {
            return@withContext PairingResult.InvalidCode
        }
        try {
            exchange(PairingProtocol.secret(secret), PairingProtocol.F_SECRET_ACK)
            PairingResult.Success
        } catch (e: PairingStatusException) {
            if (e.status == PairingProtocol.STATUS_BAD_SECRET) PairingResult.CodeRejected
            else PairingResult.Failed("The TV rejected pairing (status ${e.status}).")
        } catch (e: IOException) {
            PairingResult.Failed(e.message ?: "Connection lost during pairing.")
        } finally {
            close()
        }
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun exchange(request: ByteArray, expectedField: Int) {
        val s = socket ?: throw IOException("Pairing session closed")
        s.outputStream.apply {
            write(ProtoWriter.frame(request))
            flush()
        }
        val frame = ProtoMessage.readFrame(s.inputStream) ?: throw IOException("TV closed the pairing connection")
        val response = ProtoMessage.parse(frame)
        val status = PairingProtocol.status(response)
        if (status != PairingProtocol.STATUS_OK) throw PairingStatusException(status)
        if (!response.has(expectedField)) throw IOException("Unexpected pairing response from TV")
    }

    private class PairingStatusException(val status: Int) : IOException("Pairing status $status")

    companion object {
        const val DEFAULT_PAIRING_PORT = 6467
        private const val SERVICE_NAME = "com.lumen.tvremote"
        private const val TIMEOUT_MS = 10_000
        private const val HEX_CHARS = "0123456789ABCDEF"

        /** SHA-256 over both public keys and the last four code digits, as defined by the polo protocol. */
        fun computeSecret(client: X509Certificate, server: X509Certificate, code: String): ByteArray {
            val clientKey = client.publicKey as RSAPublicKey
            val serverKey = server.publicKey as? RSAPublicKey
                ?: throw IOException("TV certificate does not use an RSA key")
            return MessageDigest.getInstance("SHA-256").run {
                update(clientKey.modulus.unsignedBytes())
                update(clientKey.publicExponent.unsignedBytes())
                update(serverKey.modulus.unsignedBytes())
                update(serverKey.publicExponent.unsignedBytes())
                update(hexToBytes(code.substring(2)))
                digest()
            }
        }

        private fun BigInteger.unsignedBytes(): ByteArray {
            val bytes = toByteArray()
            return if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        }

        private fun hexToBytes(hex: String): ByteArray =
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
