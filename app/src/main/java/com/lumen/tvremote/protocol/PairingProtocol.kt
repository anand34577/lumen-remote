package com.lumen.tvremote.protocol

/**
 * Messages of the "polo" pairing protocol spoken on port 6467.
 *
 * OuterMessage: 1 protocol_version, 2 status, 10 pairing_request, 11 pairing_request_ack,
 * 20 options, 30 configuration, 31 configuration_ack, 40 secret, 41 secret_ack.
 */
object PairingProtocol {
    const val STATUS_OK = 200
    const val STATUS_ERROR = 400
    const val STATUS_BAD_CONFIGURATION = 401
    const val STATUS_BAD_SECRET = 402

    private const val PROTOCOL_VERSION = 2
    private const val ENCODING_HEXADECIMAL = 3
    private const val ROLE_INPUT = 1
    const val CODE_LENGTH = 6

    private const val F_VERSION = 1
    private const val F_STATUS = 2
    const val F_PAIRING_REQUEST_ACK = 11
    const val F_OPTIONS = 20
    const val F_CONFIGURATION_ACK = 31
    const val F_SECRET_ACK = 41

    private fun outer(block: ProtoWriter.() -> Unit): ByteArray = ProtoWriter()
        .varint(F_VERSION, PROTOCOL_VERSION)
        .varint(F_STATUS, STATUS_OK)
        .apply(block)
        .toByteArray()

    fun pairingRequest(serviceName: String, clientName: String): ByteArray = outer {
        message(10) {
            string(1, serviceName)
            string(2, clientName)
        }
    }

    fun options(): ByteArray = outer {
        message(20) {
            message(1) { // input_encodings
                varint(1, ENCODING_HEXADECIMAL)
                varint(2, CODE_LENGTH)
            }
            varint(3, ROLE_INPUT)
        }
    }

    fun configuration(): ByteArray = outer {
        message(30) {
            message(1) {
                varint(1, ENCODING_HEXADECIMAL)
                varint(2, CODE_LENGTH)
            }
            varint(2, ROLE_INPUT)
        }
    }

    fun secret(secret: ByteArray): ByteArray = outer {
        message(40) { bytes(1, secret) }
    }

    fun status(message: ProtoMessage): Int = message.int(F_STATUS, STATUS_ERROR)
}
