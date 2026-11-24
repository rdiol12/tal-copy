// Pure-Kotlin java.util.zip look-alikes: a ZIP writer (stored entries) and a
// ZIP reader that understands stored and deflated entries. Enough for the
// .xlsx files the app writes and re-reads.
package compat.zip

import compat.io.ByteArrayOutputStream
import compat.io.IOException
import compat.io.InputStream
import compat.io.OutputStream

class ZipException(message: String) : IOException(message)

class ZipEntry(val name: String) {
    var size: Long = -1
    var compressedSize: Long = -1
    var crc: Long = -1
    val isDirectory: Boolean get() = name.endsWith("/")
    override fun toString(): String = name
}

object CRC32Table {
    private val table = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }
    fun compute(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Long {
        var crc = -1
        for (i in offset until offset + length) crc = table[(crc xor bytes[i].toInt()) and 0xFF] xor (crc ushr 8)
        return (crc.inv()).toLong() and 0xFFFFFFFFL
    }
}

class CRC32 {
    private val data = ByteArrayOutputStream()
    fun update(bytes: ByteArray) = data.write(bytes, 0, bytes.size)
    fun update(bytes: ByteArray, offset: Int, length: Int) = data.write(bytes, offset, length)
    fun reset() = data.reset()
    val value: Long get() = CRC32Table.compute(data.toByteArray())
}

class ZipOutputStream(private val out: OutputStream) : OutputStream() {
    private class Written(val name: ByteArray, val crc: Long, val size: Int, val offset: Int)

    private val written = ArrayList<Written>()
    private val names = HashSet<String>()
    private var current: ZipEntry? = null
    private var buffer = ByteArrayOutputStream()
    private var position = 0
    private var finished = false

    fun putNextEntry(entry: ZipEntry) {
        closeEntry()
        if (!names.add(entry.name)) throw ZipException("duplicate entry: ${entry.name}")
        current = entry
        buffer = ByteArrayOutputStream()
    }

    override fun write(byte: Int) {
        if (current == null) throw ZipException("no current ZIP entry")
        buffer.write(byte)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (current == null) throw ZipException("no current ZIP entry")
        buffer.write(bytes, offset, length)
    }

    fun closeEntry() {
        val entry = current ?: return
        current = null
        val data = buffer.toByteArray()
        val name = entry.name.encodeToByteArray()
        val crc = CRC32Table.compute(data)
        val offset = position
        emit(header(LOCAL_SIGNATURE, name, crc, data.size))
        emit(name)
        emit(data)
        entry.size = data.size.toLong()
        entry.compressedSize = data.size.toLong()
        entry.crc = crc
        written.add(Written(name, crc, data.size, offset))
    }

    fun finish() {
        if (finished) return
        closeEntry()
        finished = true
        val start = position
        for (w in written) {
            val h = ByteArrayOutputStream()
            h.write(header(CENTRAL_SIGNATURE, w.name, w.crc, w.size))
            // comment length, disk number, internal attrs (2 bytes each), external attrs, local header offset (4 bytes each)
            h.write(le16(0)); h.write(le16(0)); h.write(le16(0)); h.write(le32(0)); h.write(le32(w.offset.toLong()))
            emit(h.toByteArray())
            emit(w.name)
        }
        val size = position - start
        val end = ByteArrayOutputStream()
        end.write(le32(END_SIGNATURE)); end.write(le16(0)); end.write(le16(0))
        end.write(le16(written.size)); end.write(le16(written.size))
        end.write(le32(size.toLong())); end.write(le32(start.toLong())); end.write(le16(0))
        emit(end.toByteArray())
        out.flush()
    }

    override fun flush() = out.flush()
    override fun close() { finish(); out.close() }

    private fun emit(bytes: ByteArray) { out.write(bytes, 0, bytes.size); position += bytes.size }

    /** Shared prefix of the local and central headers. UTF-8 names (flag bit 11), method 0 = stored. */
    private fun header(signature: Long, name: ByteArray, crc: Long, size: Int): ByteArray {
        val h = ByteArrayOutputStream()
        h.write(le32(signature))
        if (signature == CENTRAL_SIGNATURE) h.write(le16(20))   // version made by
        h.write(le16(20))                                      // version needed
        h.write(le16(0x0800))                                  // flags: UTF-8
        h.write(le16(0))                                       // method: stored
        h.write(le16(0)); h.write(le16(0x21))                  // time 00:00, date 1980-01-01
        h.write(le32(crc)); h.write(le32(size.toLong())); h.write(le32(size.toLong()))
        h.write(le16(name.size)); h.write(le16(0))             // name length, extra length
        return h.toByteArray()
    }

    private companion object {
        const val LOCAL_SIGNATURE = 0x04034b50L
        const val CENTRAL_SIGNATURE = 0x02014b50L
        const val END_SIGNATURE = 0x06054b50L
        fun le16(v: Int) = byteArrayOf(v.toByte(), (v ushr 8).toByte())
        fun le32(v: Long) = byteArrayOf(v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte())
    }
}

class ZipInputStream(source: InputStream) : InputStream() {
    private class Item(val entry: ZipEntry, val method: Int, val dataStart: Int, val compressedSize: Int)

    private val bytes: ByteArray = source.readBytes().also { source.close() }
    private val items: List<Item> = readDirectory()
    private var index = -1
    private var data: ByteArray = ByteArray(0)
    private var position = 0

    /** Advances to the next entry on every access, like getNextEntry(). */
    val nextEntry: ZipEntry?
        get() {
            index++
            if (index >= items.size) { data = ByteArray(0); position = 0; return null }
            val item = items[index]
            data = when (item.method) {
                0 -> bytes.copyOfRange(item.dataStart, item.dataStart + item.compressedSize)
                8 -> Inflater(bytes, item.dataStart, item.dataStart + item.compressedSize).inflate()
                else -> throw ZipException("unsupported compression method ${item.method}")
            }
            if (item.entry.crc >= 0 && CRC32Table.compute(data) != item.entry.crc) throw ZipException("invalid entry CRC: ${item.entry.name}")
            position = 0
            return item.entry
        }

    fun closeEntry() { position = data.size }
    fun available(): Int = if (position < data.size) 1 else 0

    override fun read(): Int = if (position < data.size) data[position++].toInt() and 0xFF else -1
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= data.size) return -1
        val n = minOf(length, data.size - position)
        data.copyInto(buffer, offset, position, position + n)
        position += n
        return n
    }
    override fun readBytes(): ByteArray = data.copyOfRange(position, data.size).also { position = data.size }

    private fun u16(at: Int): Int = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
    private fun u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)

    private fun readDirectory(): List<Item> {
        if (bytes.size < 22) { if (bytes.isEmpty()) return emptyList() else throw ZipException("not a ZIP archive") }
        var end = bytes.size - 22
        val limit = maxOf(0, bytes.size - 22 - 65535)
        while (end >= limit && u32(end) != 0x06054b50L) end--
        if (end < limit) throw ZipException("ZIP end record not found")
        val count = u16(end + 10)
        var at = u32(end + 16).toInt()
        val result = ArrayList<Item>(count)
        repeat(count) {
            if (at + 46 > bytes.size || u32(at) != 0x02014b50L) throw ZipException("invalid ZIP central directory")
            val method = u16(at + 10)
            val crc = u32(at + 16)
            val compressed = u32(at + 20)
            val size = u32(at + 24)
            val nameLength = u16(at + 28)
            val extraLength = u16(at + 30)
            val commentLength = u16(at + 32)
            val local = u32(at + 42).toInt()
            val name = bytes.decodeToString(at + 46, at + 46 + nameLength)
            if (local + 30 > bytes.size || u32(local) != 0x04034b50L) throw ZipException("invalid ZIP local header")
            val dataStart = local + 30 + u16(local + 26) + u16(local + 28)
            if (compressed > Int.MAX_VALUE || dataStart + compressed > bytes.size) throw ZipException("truncated ZIP entry: $name")
            val entry = ZipEntry(name).also { it.size = size; it.compressedSize = compressed; it.crc = crc }
            result.add(Item(entry, method, dataStart, compressed.toInt()))
            at += 46 + nameLength + extraLength + commentLength
        }
        return result.sortedBy { it.dataStart }
    }
}

/** DEFLATE decoder (RFC 1951). */
internal class Inflater(private val src: ByteArray, private var pos: Int, private val end: Int) {
    private var bitBuffer = 0
    private var bitCount = 0
    private var out = ByteArray(maxOf(64, (end - pos) * 3))
    private var outSize = 0

    private fun reserve(extra: Int) {
        if (outSize + extra > out.size) out = out.copyOf(maxOf(out.size * 2, outSize + extra))
    }

    private class Huffman(val count: IntArray, val symbol: IntArray)

    private fun bits(need: Int): Int {
        var value = bitBuffer
        while (bitCount < need) {
            if (pos >= end) throw ZipException("unexpected end of deflate stream")
            value = value or ((src[pos++].toInt() and 0xFF) shl bitCount)
            bitCount += 8
        }
        bitBuffer = value ushr need
        bitCount -= need
        return value and ((1 shl need) - 1)
    }

    private fun build(lengths: IntArray, offset: Int, n: Int): Huffman {
        val count = IntArray(16)
        for (i in 0 until n) count[lengths[offset + i]]++
        count[0] = 0
        val offsets = IntArray(16)
        for (len in 1 until 15) offsets[len + 1] = offsets[len] + count[len]
        val symbol = IntArray(n)
        for (i in 0 until n) if (lengths[offset + i] != 0) symbol[offsets[lengths[offset + i]]++] = i
        return Huffman(count, symbol)
    }

    private fun decode(h: Huffman): Int {
        var code = 0
        var first = 0
        var index = 0
        for (len in 1..15) {
            code = code or bits(1)
            val count = h.count[len]
            if (code - count < first) return h.symbol[index + (code - first)]
            index += count
            first += count
            first = first shl 1
            code = code shl 1
        }
        throw ZipException("invalid deflate code")
    }

    private fun codes(lengthCodes: Huffman, distanceCodes: Huffman) {
        while (true) {
            val symbol = decode(lengthCodes)
            when {
                symbol < 256 -> { reserve(1); out[outSize++] = symbol.toByte() }
                symbol == 256 -> return
                else -> {
                    val s = symbol - 257
                    if (s >= 29) throw ZipException("invalid deflate length symbol")
                    val length = LENGTH_BASE[s] + bits(LENGTH_EXTRA[s])
                    val d = decode(distanceCodes)
                    if (d >= 30) throw ZipException("invalid deflate distance symbol")
                    val distance = DISTANCE_BASE[d] + bits(DISTANCE_EXTRA[d])
                    if (distance > outSize) throw ZipException("invalid deflate distance")
                    reserve(length)
                    // Byte by byte on purpose: the source range may overlap the bytes being written.
                    for (i in 0 until length) { out[outSize] = out[outSize - distance]; outSize++ }
                }
            }
        }
    }

    fun inflate(): ByteArray {
        var last: Int
        do {
            last = bits(1)
            when (bits(2)) {
                0 -> {
                    bitBuffer = 0; bitCount = 0
                    if (pos + 4 > end) throw ZipException("unexpected end of deflate stream")
                    val length = (src[pos].toInt() and 0xFF) or ((src[pos + 1].toInt() and 0xFF) shl 8)
                    pos += 4
                    if (pos + length > end) throw ZipException("unexpected end of deflate stream")
                    reserve(length)
                    src.copyInto(out, outSize, pos, pos + length)
                    outSize += length
                    pos += length
                }
                1 -> codes(FIXED_LENGTH, FIXED_DISTANCE)
                2 -> {
                    val nlen = bits(5) + 257
                    val ndist = bits(5) + 1
                    val ncode = bits(4) + 4
                    if (nlen > 286 || ndist > 30) throw ZipException("invalid deflate header")
                    val lengths = IntArray(320)
                    for (i in 0 until ncode) lengths[ORDER[i]] = bits(3)
                    val lengthCode = build(lengths, 0, 19)
                    val all = IntArray(nlen + ndist)
                    var i = 0
                    while (i < nlen + ndist) {
                        val symbol = decode(lengthCode)
                        if (symbol < 16) { all[i++] = symbol }
                        else {
                            var previous = 0
                            val repeat = when (symbol) {
                                16 -> { if (i == 0) throw ZipException("invalid deflate repeat"); previous = all[i - 1]; 3 + bits(2) }
                                17 -> 3 + bits(3)
                                else -> 11 + bits(7)
                            }
                            if (i + repeat > nlen + ndist) throw ZipException("invalid deflate repeat")
                            repeat(repeat) { all[i++] = previous }
                        }
                    }
                    codes(build(all, 0, nlen), build(all, nlen, ndist))
                }
                else -> throw ZipException("invalid deflate block type")
            }
        } while (last == 0)
        return out.copyOf(outSize)
    }

    private companion object {
        val LENGTH_BASE = intArrayOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258)
        val LENGTH_EXTRA = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
        val DISTANCE_BASE = intArrayOf(1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577)
        val DISTANCE_EXTRA = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13)
        val ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
        val FIXED_LENGTH: Huffman
        val FIXED_DISTANCE: Huffman
        init {
            val lengths = IntArray(288)
            for (i in 0 until 144) lengths[i] = 8
            for (i in 144 until 256) lengths[i] = 9
            for (i in 256 until 280) lengths[i] = 7
            for (i in 280 until 288) lengths[i] = 8
            FIXED_LENGTH = buildStatic(lengths, 288)
            FIXED_DISTANCE = buildStatic(IntArray(30) { 5 }, 30)
        }
        private fun buildStatic(lengths: IntArray, n: Int): Huffman {
            val count = IntArray(16)
            for (i in 0 until n) count[lengths[i]]++
            count[0] = 0
            val offsets = IntArray(16)
            for (len in 1 until 15) offsets[len + 1] = offsets[len] + count[len]
            val symbol = IntArray(n)
            for (i in 0 until n) if (lengths[i] != 0) symbol[offsets[lengths[i]]++] = i
            return Huffman(count, symbol)
        }
    }
}
