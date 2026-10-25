package com.lumen.tvremote.protocol

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Minimal protobuf wire-format codec. The Android TV Remote v2 protocol only needs
 * varints and length-delimited fields, so a hand-rolled codec avoids pulling in the
 * protobuf runtime and code generator.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Long): ProtoWriter = apply {
        tag(field, WIRE_VARINT)
        rawVarint(value)
    }

    fun varint(field: Int, value: Int): ProtoWriter = varint(field, value.toLong())

    fun bool(field: Int, value: Boolean): ProtoWriter = varint(field, if (value) 1 else 0)

    fun bytes(field: Int, value: ByteArray): ProtoWriter = apply {
        tag(field, WIRE_LENGTH_DELIMITED)
        rawVarint(value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, block: ProtoWriter.() -> Unit): ProtoWriter =
        bytes(field, ProtoWriter().apply(block).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = rawVarint(((field shl 3) or wireType).toLong())

    private fun rawVarint(value: Long) {
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH_DELIMITED = 2
        const val WIRE_FIXED32 = 5

        /** Length-prefixes a message with a varint, the framing used on both TV ports. */
        fun frame(payload: ByteArray): ByteArray {
            val prefix = ProtoWriter().apply { rawVarint(payload.size.toLong()) }.toByteArray()
            return prefix + payload
        }
    }
}

/** A decoded protobuf message: field number to the list of raw values seen for it. */
class ProtoMessage private constructor(private val fields: Map<Int, List<Any>>) {

    fun has(field: Int): Boolean = fields.containsKey(field)

    fun long(field: Int, default: Long = 0): Long = (fields[field]?.lastOrNull() as? Long) ?: default

    fun int(field: Int, default: Int = 0): Int = long(field, default.toLong()).toInt()

    fun bool(field: Int): Boolean = long(field) != 0L

    fun bytes(field: Int): ByteArray? = fields[field]?.lastOrNull() as? ByteArray

    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)

    fun message(field: Int): ProtoMessage? = bytes(field)?.let { parseOrNull(it) }

    fun messages(field: Int): List<ProtoMessage> =
        fields[field].orEmpty().mapNotNull { (it as? ByteArray)?.let(::parseOrNull) }

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = HashMap<Int, MutableList<Any>>()
            var pos = 0

            fun readVarint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    if (pos >= data.size) throw IOException("Truncated varint")
                    val b = data[pos++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                    if (shift > 63) throw IOException("Malformed varint")
                }
            }

            while (pos < data.size) {
                val tag = readVarint()
                val field = (tag ushr 3).toInt()
                val value: Any = when ((tag and 0x7).toInt()) {
                    ProtoWriter.WIRE_VARINT -> readVarint()
                    ProtoWriter.WIRE_LENGTH_DELIMITED -> {
                        val declared = readVarint()
                        // Compared as a Long and against the remaining bytes so a hostile length cannot overflow.
                        if (declared < 0 || declared > data.size - pos) throw IOException("Truncated field $field")
                        val len = declared.toInt()
                        data.copyOfRange(pos, pos + len).also { pos += len }
                    }
                    ProtoWriter.WIRE_FIXED64 -> {
                        if (pos + 8 > data.size) throw IOException("Truncated fixed64")
                        var v = 0L
                        for (i in 0 until 8) v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
                        pos += 8
                        v
                    }
                    ProtoWriter.WIRE_FIXED32 -> {
                        if (pos + 4 > data.size) throw IOException("Truncated fixed32")
                        var v = 0L
                        for (i in 0 until 4) v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
                        pos += 4
                        v
                    }
                    else -> throw IOException("Unsupported wire type in tag $tag")
                }
                fields.getOrPut(field) { ArrayList(1) }.add(value)
            }
            return ProtoMessage(fields)
        }

        fun parseOrNull(data: ByteArray): ProtoMessage? = try {
            parse(data)
        } catch (_: IOException) {
            null
        }

        /** Reads one varint-length-prefixed frame. Returns null on clean end of stream. */
        fun readFrame(input: InputStream, maxSize: Int = 1 shl 20): ByteArray? {
            var length = 0
            var shift = 0
            while (true) {
                val b = input.read()
                if (b < 0) {
                    if (shift == 0) return null
                    throw IOException("Stream closed inside frame header")
                }
                length = length or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                if (shift > 28) throw IOException("Frame header too long")
            }
            if (length < 0 || length > maxSize) throw IOException("Invalid frame size $length")
            val buffer = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(buffer, read, length - read)
                if (n < 0) throw IOException("Stream closed inside frame")
                read += n
            }
            return buffer
        }
    }
}
