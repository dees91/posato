package app.posato.feature.sync.data

internal class CanonicalWriter(
    initialCapacity: Int = 128,
) {
    private var bytes = ByteArray(initialCapacity)
    private var size = 0

    fun writeByte(value: Int) {
        require(value in 0..MAX_UNSIGNED_BYTE)
        ensureCapacity(1)
        bytes[size] = value.toByte()
        size += 1
    }

    fun writeU16(value: Int) {
        require(value in 0..MAX_UNSIGNED_SHORT)
        writeByte(value ushr Byte.SIZE_BITS)
        writeByte(value and MAX_UNSIGNED_BYTE)
    }

    fun writeU32(value: Long) {
        require(value in 0..MAX_UNSIGNED_INT)
        repeat(Int.SIZE_BYTES) { offset ->
            writeByte(((value ushr ((Int.SIZE_BYTES - 1 - offset) * Byte.SIZE_BITS)) and MAX_UNSIGNED_BYTE.toLong()).toInt())
        }
    }

    fun writeLong(value: Long) {
        repeat(Long.SIZE_BYTES) { offset ->
            writeByte(((value ushr ((Long.SIZE_BYTES - 1 - offset) * Byte.SIZE_BITS)) and MAX_UNSIGNED_BYTE.toLong()).toInt())
        }
    }

    fun writeBytes(value: ByteArray) {
        ensureCapacity(value.size)
        value.copyInto(bytes, destinationOffset = size)
        size += value.size
    }

    fun toByteArray(): ByteArray {
        return bytes.copyOf(size)
    }

    private fun ensureCapacity(additionalBytes: Int) {
        val requiredSize = size + additionalBytes
        if (requiredSize > bytes.size) {
            var newSize = bytes.size.coerceAtLeast(1)
            while (newSize < requiredSize) {
                newSize = newSize.coerceAtMost(Int.MAX_VALUE / 2) * 2
            }
            bytes = bytes.copyOf(newSize)
        }
    }
}

internal class CanonicalReader(
    private val bytes: ByteArray,
) {
    private var offset = 0

    val remaining: Int
        get() = bytes.size - offset

    fun readByte(): Int? {
        if (remaining < 1) {
            return null
        }
        val value = bytes[offset].toInt() and 0xFF
        offset += 1

        return value
    }

    fun readU16(): Int? {
        val first = readByte()
        val second = first?.let { readByte() }

        return if (first != null && second != null) first shl Byte.SIZE_BITS or second else null
    }

    fun readU32(): Long? {
        var value = 0L
        repeat(Int.SIZE_BYTES) {
            val next = readByte() ?: return null
            value = value shl Byte.SIZE_BITS or next.toLong()
        }

        return value
    }

    fun readLong(): Long? {
        var value = 0L
        repeat(Long.SIZE_BYTES) {
            val next = readByte() ?: return null
            value = value shl Byte.SIZE_BITS or next.toLong()
        }

        return value
    }

    fun readBytes(count: Int): ByteArray? {
        if (count < 0 || remaining < count) {
            return null
        }
        val result = bytes.copyOfRange(offset, offset + count)
        offset += count

        return result
    }
}

private const val MAX_UNSIGNED_BYTE = 0xFF
private const val MAX_UNSIGNED_SHORT = 0xFFFF
private const val MAX_UNSIGNED_INT = 0xFFFF_FFFFL
