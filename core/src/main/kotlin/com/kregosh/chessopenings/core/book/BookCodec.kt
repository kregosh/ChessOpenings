package com.kregosh.chessopenings.core.book

import com.kregosh.chessopenings.chess.PieceType
import com.kregosh.chessopenings.chess.Square

/**
 * The binary `book.bin` format. All numbers are big-endian.
 *
 * ```
 * magic "COBK", version u16
 * metadata:  count u16, then per entry key and value as strings
 * positions: count u32, then per position:
 *            key i64, move count u8, then per move:
 *              move u16      from (6 bits) | to (6 bits) << 6 | promotion (3 bits) << 12
 *              strong u32, club u32
 *              loss u16      centipawns, 0xFFFF = not evaluated
 *              flags u8      1 = user book, 2 = bot candidate
 * names:     count u32, then per name key i64 and the name as a string
 * string:    UTF-8 byte length u16, then the bytes
 * ```
 * Positions are written in ascending key order.
 */
object BookCodec {
    private const val MAGIC = "COBK"
    const val VERSION = 1
    private const val NO_LOSS = 0xFFFF
    private const val USER_BOOK = 1
    private const val BOT_CANDIDATE = 2

    fun encode(book: OpeningBook): ByteArray {
        val out = ByteWriter()
        MAGIC.forEach { out.u8(it.code) }
        out.u16(VERSION)

        out.u16(book.metadata.size)
        for ((k, v) in book.metadata.toSortedMap()) {
            out.string(k)
            out.string(v)
        }

        out.u32(book.positionCount)
        for ((key, moves) in book.entries()) {
            require(moves.size <= 255) { "Too many moves in one position" }
            out.i64(key)
            out.u8(moves.size)
            for (m in moves) {
                out.u16(m.from.index or (m.to.index shl 6) or ((m.promotion?.let { it.ordinal } ?: 0) shl 12))
                out.u32(m.strongGames)
                out.u32(m.clubGames)
                out.u16(m.lossCp?.coerceIn(0, NO_LOSS - 1) ?: NO_LOSS)
                out.u8((if (m.userBook) USER_BOOK else 0) or (if (m.botCandidate) BOT_CANDIDATE else 0))
            }
        }

        out.u32(book.names.size)
        for ((key, name) in book.names.toSortedMap()) {
            out.i64(key)
            out.string(name)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): OpeningBook {
        val r = ByteReader(bytes)
        val magic = String(CharArray(4) { r.u8().toChar() })
        require(magic == MAGIC) { "Not an opening book file" }
        val version = r.u16()
        require(version == VERSION) { "Unsupported book version $version" }

        val metadata = LinkedHashMap<String, String>()
        repeat(r.u16()) { metadata[r.string()] = r.string() }

        val positions = HashMap<Long, List<BookMove>>()
        repeat(r.u32()) {
            val key = r.i64()
            positions[key] = List(r.u8()) {
                val bits = r.u16()
                val promo = (bits shr 12) and 7
                val strong = r.u32()
                val club = r.u32()
                val loss = r.u16()
                val flags = r.u8()
                BookMove(
                    from = Square(bits and 63),
                    to = Square((bits shr 6) and 63),
                    // Pawns can't be promoted to, so 0 means no promotion.
                    promotion = if (promo == 0) null else PieceType.entries[promo],
                    strongGames = strong,
                    clubGames = club,
                    lossCp = if (loss == NO_LOSS) null else loss,
                    userBook = flags and USER_BOOK != 0,
                    botCandidate = flags and BOT_CANDIDATE != 0,
                )
            }
        }

        val names = HashMap<Long, String>()
        repeat(r.u32()) { names[r.i64()] = r.string() }
        require(r.atEnd) { "Trailing bytes in book file" }
        return OpeningBook(metadata, positions, names)
    }
}

private class ByteWriter {
    private var buf = ByteArray(1 shl 16)
    private var size = 0

    private fun ensure(n: Int) {
        if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
    }

    fun u8(v: Int) {
        ensure(1)
        buf[size++] = v.toByte()
    }

    fun u16(v: Int) {
        require(v in 0..0xFFFF) { "u16 out of range: $v" }
        u8(v shr 8)
        u8(v)
    }

    fun u32(v: Int) {
        require(v >= 0) { "u32 out of range: $v" }
        for (shift in 24 downTo 0 step 8) u8(v shr shift)
    }

    fun i64(v: Long) {
        for (shift in 56 downTo 0 step 8) u8((v shr shift).toInt())
    }

    fun string(s: String) {
        val bytes = s.encodeToByteArray()
        u16(bytes.size)
        ensure(bytes.size)
        bytes.copyInto(buf, size)
        size += bytes.size
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

private class ByteReader(private val bytes: ByteArray) {
    private var pos = 0

    val atEnd: Boolean get() = pos == bytes.size

    fun u8(): Int {
        require(pos < bytes.size) { "Book file is truncated" }
        return bytes[pos++].toInt() and 0xFF
    }

    fun u16(): Int = (u8() shl 8) or u8()

    fun u32(): Int {
        var v = 0
        repeat(4) { v = (v shl 8) or u8() }
        require(v >= 0) { "Corrupt book file" }
        return v
    }

    fun i64(): Long {
        var v = 0L
        repeat(8) { v = (v shl 8) or u8().toLong() }
        return v
    }

    fun string(): String {
        val n = u16()
        require(pos + n <= bytes.size) { "Book file is truncated" }
        return bytes.decodeToString(pos, pos + n).also { pos += n }
    }
}
