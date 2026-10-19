package com.lumen.tvremote.protocol

/**
 * Messages of the Android TV Remote v2 control channel spoken on port 6466.
 * Field numbers follow `remotemessage.proto` from the Google TV remote service.
 */
object RemoteProtocol {
    /** PING | KEY | IME | POWER | VOLUME | APP_LINK */
    const val FEATURES = 1 or 2 or 4 or 32 or 64 or 512

    const val DIRECTION_START_LONG = 1
    const val DIRECTION_END_LONG = 2
    const val DIRECTION_SHORT = 3

    fun configure(model: String, vendor: String, appVersion: String): ByteArray = ProtoWriter()
        .message(1) {
            varint(1, FEATURES)
            message(2) {
                string(1, model)
                string(2, vendor)
                varint(3, 1)
                string(4, "1")
                string(5, "androidtv-remote")
                string(6, appVersion)
            }
        }.toByteArray()

    fun setActive(): ByteArray = ProtoWriter().message(2) { varint(1, FEATURES) }.toByteArray()

    fun pingResponse(value: Int): ByteArray = ProtoWriter().message(9) { varint(1, value) }.toByteArray()

    fun key(keyCode: Int, direction: Int = DIRECTION_SHORT): ByteArray = ProtoWriter()
        .message(10) {
            varint(1, keyCode)
            varint(2, direction)
        }.toByteArray()

    /** Replaces the content of the focused TV text field with [text]. */
    fun imeText(imeCounter: Int, fieldCounter: Int, text: String): ByteArray {
        val cursor = (text.length - 1).coerceAtLeast(0)
        return ProtoWriter().message(21) {
            varint(1, imeCounter)
            varint(2, fieldCounter)
            message(3) {
                varint(1, 1)
                message(2) {
                    varint(1, cursor)
                    varint(2, cursor)
                    string(3, text)
                }
            }
        }.toByteArray()
    }

    fun appLink(link: String): ByteArray = ProtoWriter().message(90) { string(1, link) }.toByteArray()

    fun decode(frame: ByteArray): RemoteEvent? {
        val msg = ProtoMessage.parseOrNull(frame) ?: return null
        return when {
            msg.has(8) -> RemoteEvent.Ping(msg.message(8)?.int(1) ?: 0)
            msg.has(1) -> {
                val info = msg.message(1)?.message(2)
                RemoteEvent.Configure(
                    model = info?.string(1).orEmpty(),
                    vendor = info?.string(2).orEmpty(),
                )
            }
            msg.has(2) -> RemoteEvent.SetActive
            msg.has(3) -> RemoteEvent.Error
            msg.has(40) -> RemoteEvent.Power(msg.message(40)?.bool(1) ?: false)
            msg.has(50) -> {
                val v = msg.message(50)
                RemoteEvent.Volume(
                    level = v?.int(7) ?: 0,
                    max = v?.int(6) ?: 0,
                    muted = v?.bool(8) ?: false,
                )
            }
            msg.has(20) -> {
                val inject = msg.message(20)
                val app = inject?.message(1)
                val field = inject?.message(2)
                RemoteEvent.ImeKeyInject(
                    appPackage = app?.string(12),
                    fieldCounter = field?.takeIf { it.has(1) }?.int(1),
                    text = field?.string(2),
                    label = field?.string(6),
                )
            }
            msg.has(21) -> {
                val edit = msg.message(21)
                val value = edit?.messages(3)?.lastOrNull()?.message(2)?.string(3)
                RemoteEvent.ImeBatchEdit(
                    imeCounter = edit?.int(1) ?: 0,
                    fieldCounter = edit?.int(2) ?: 0,
                    text = value,
                )
            }
            msg.has(22) -> {
                val field = msg.message(22)?.message(2)
                RemoteEvent.ImeShow(text = field?.string(2), label = field?.string(6))
            }
            else -> RemoteEvent.Unknown
        }
    }
}

sealed interface RemoteEvent {
    data class Configure(val model: String, val vendor: String) : RemoteEvent
    data object SetActive : RemoteEvent
    data object Error : RemoteEvent
    data class Ping(val value: Int) : RemoteEvent
    data class Power(val on: Boolean) : RemoteEvent
    data class Volume(val level: Int, val max: Int, val muted: Boolean) : RemoteEvent
    data class ImeKeyInject(val appPackage: String?, val fieldCounter: Int?, val text: String?, val label: String?) : RemoteEvent
    data class ImeBatchEdit(val imeCounter: Int, val fieldCounter: Int, val text: String?) : RemoteEvent
    data class ImeShow(val text: String?, val label: String?) : RemoteEvent
    data object Unknown : RemoteEvent
}
