package com.kregosh.chessopenings.bookgen

import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.San
import com.kregosh.chessopenings.core.book.BookCodec
import com.kregosh.chessopenings.core.book.OpeningBook
import java.io.File
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookBuilderTest {
    private fun line(vararg sans: String) = sans.fold(Position.start) { p, s -> p.play(San.parse(p, s)!!) }

    /** Counts for the start position: (uci, strong, club). */
    private fun counts(vararg moves: Triple<String, Int, Int>, position: Position = Position.start): Map<Long, PositionCounts> {
        val c = PositionCounts().apply { fen = position.toFen() }
        for ((uci, strong, club) in moves) {
            c.add(uci, Band.STRONG, strong)
            c.add(uci, Band.CLUB, club)
        }
        return mapOf(position.key to c)
    }

    private fun build(counts: Map<Long, PositionCounts>, losses: Map<String, Int>? = null, params: BookParams = BookParams()): OpeningBook {
        val evaluate = losses?.let { l ->
            { jobs: List<Pair<String, List<String>>> -> jobs.map { (_, moves) -> Evaluation(0, moves.associateWith { -(l[it] ?: 0) }) } }
        }
        return BookBuilder(params).build(counts, emptyMap(), evaluate)
    }

    @Test
    fun popularityDecidesUserBookAndBotCandidates() {
        val book = build(
            counts(
                Triple("e2e4", 500, 5000),
                Triple("d2d4", 400, 3000),
                Triple("c2c4", 60, 100),  // 6% strong, 1.2% club
                Triple("b2b3", 10, 400),  // 0.9% strong, 4.8% club
                Triple("g2g4", 5, 50),
            ),
        )
        val moves = book.moves(Position.start).associateBy { it.uci }
        assertEquals(setOf("e2e4", "d2d4", "c2c4", "b2b3"), moves.keys)
        assertTrue(moves.getValue("c2c4").userBook && !moves.getValue("c2c4").botCandidate)
        assertTrue(!moves.getValue("b2b3").userBook && moves.getValue("b2b3").botCandidate)
        assertTrue(moves.getValue("e2e4").userBook && moves.getValue("e2e4").botCandidate)
        assertNull(moves.getValue("e2e4").lossCp)
        assertEquals(listOf("e2e4", "d2d4"), book.moves(Position.start).take(2).map { it.uci })
    }

    @Test
    fun engineLossFiltersUnsoundMoves() {
        val book = build(
            counts(Triple("e2e4", 500, 5000), Triple("f2f3", 100, 1000), Triple("g2g4", 100, 1000)),
            losses = mapOf("e2e4" to 0, "f2f3" to 60, "g2g4" to 150),
        )
        val moves = book.moves(Position.start).associateBy { it.uci }
        assertEquals(0, moves.getValue("e2e4").lossCp)
        // 60 cp: too much for the user's book, fine for the bot.
        assertTrue(!moves.getValue("f2f3").userBook && moves.getValue("f2f3").botCandidate)
        // 150 cp: out entirely.
        assertNull(moves["g2g4"])
    }

    @Test
    fun thinPositionsDontMakeBook() {
        // 50 strong and 100 club games are below the default minimums.
        val book = build(counts(Triple("e2e4", 50, 100)))
        assertTrue(book.moves(Position.start).isEmpty())
        val lowered = build(counts(Triple("e2e4", 50, 100)), params = BookParams(minStrongGames = 10, minClubGames = 10))
        assertEquals(1, lowered.moves(Position.start).size)
    }

    @Test
    fun countsFileRoundTripAndMerge() {
        val counter = MoveCounter(fenAfter = 1)
        repeat(3) { counter.add(PgnGame(mapOf("WhiteElo" to "2100", "BlackElo" to "2100"), listOf("e4", "c5"))) }
        repeat(2) { counter.add(PgnGame(mapOf("WhiteElo" to "1500", "BlackElo" to "1500"), listOf("d4"))) }
        val text = StringWriter().also { CountsFile.write(counter, it, minGames = 1, header = listOf("test")) }.toString()
        assertTrue(text.startsWith("# test\n"))

        val merged = CountsFile.read(text.reader().buffered())
        CountsFile.read(text.reader().buffered(), merged)
        val start = merged.getValue(Position.start.key)
        assertEquals(listOf(6, 0), start.moves.getValue("e2e4").toList())
        assertEquals(listOf(0, 4), start.moves.getValue("d2d4").toList())
        assertEquals(Position.START_FEN, start.fen)
        assertEquals(6, merged.getValue(line("e4").key).moves.getValue("c7c5")[Band.STRONG.ordinal])

        // Positions below minGames are left out.
        val small = StringWriter().also { CountsFile.write(counter, it, minGames = 4) }.toString()
        assertEquals(1, small.lines().count { it.startsWith("P") })
    }

    @Test
    fun openingNamesFromLichessData() {
        val names = OpeningNames.read(
            File("../../data/openings").listFiles()!!.filter { it.name.endsWith(".tsv") }.asSequence().flatMap { it.readLines() },
        )
        assertTrue(names.size > 3000)
        assertEquals("Sicilian Defense", names[line("e4", "c5").key])
        assertEquals("Ruy Lopez", names[line("e4", "e5", "Nf3", "Nc6", "Bb5").key])
        // Reached by transposition: still named.
        assertEquals(names[line("d4", "Nf6", "c4", "e6").key], names[line("c4", "e6", "d4", "Nf6").key])
    }

    @Test
    fun endToEndFromPgn() {
        val dir = kotlin.io.path.createTempDirectory("bookgen").toFile()
        val pgn = File(dir, "games.pgn")
        // 300 strong games of 1.e4 c5 / 1.d4 d5, and 300 club games of 1.e4 e5.
        pgn.writeText(buildString {
            repeat(150) { append("[WhiteElo \"2200\"]\n[BlackElo \"2200\"]\n\n1. e4 c5 2. Nf3 *\n\n") }
            repeat(150) { append("[WhiteElo \"2200\"]\n[BlackElo \"2200\"]\n\n1. d4 d5 *\n\n") }
            repeat(300) { append("[WhiteElo \"1500\"]\n[BlackElo \"1500\"]\n\n1. e4 e5 *\n\n") }
        })
        val counts = File(dir, "counts.txt")
        val bookFile = File(dir, "book.bin")
        main(arrayOf("count", "--out", counts.path, "--min-write", "10", pgn.path))
        main(arrayOf("build", "--counts", counts.path, "--openings", "../../data/openings", "--out", bookFile.path, "--source", "test"))

        val book = OpeningBook.decode(bookFile.readBytes())
        assertEquals("test", book.metadata["source"])
        val start = book.moves(Position.start).associateBy { it.uci }
        assertEquals(150, start.getValue("d2d4").strongGames)
        assertEquals(300, start.getValue("e2e4").clubGames)
        assertTrue(start.getValue("e2e4").userBook && start.getValue("e2e4").botCandidate)
        assertTrue(start.getValue("d2d4").userBook && !start.getValue("d2d4").botCandidate)
        val afterE4 = book.moves(line("e4")).associateBy { it.uci }
        assertTrue(afterE4.getValue("c7c5").userBook)
        assertTrue(afterE4.getValue("e7e5").botCandidate)
        assertEquals("Sicilian Defense", book.name(line("e4", "c5")))
        assertEquals(book.entries().toList(), OpeningBook.decode(BookCodec.encode(book)).entries().toList())
        dir.deleteRecursively()
    }
}
