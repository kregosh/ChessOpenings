package com.kregosh.chessopenings.bookgen

import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.San
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgnAndCountingTest {
    private fun sample() = javaClass.getResourceAsStream("/sample.pgn")!!.bufferedReader().use { PgnReader(it).games().toList() }

    private fun line(vararg sans: String) = sans.fold(Position.start) { p, s -> p.play(San.parse(p, s)!!) }

    private fun game(white: Int, black: Int, moves: String, event: String = "Rated Blitz game", tc: String = "300+0") =
        PgnGame(mapOf("Event" to event, "WhiteElo" to "$white", "BlackElo" to "$black", "TimeControl" to tc), moves.split(' '))

    @Test
    fun readsLichessPgn() {
        val games = sample()
        assertEquals(5, games.size)
        assertEquals("2105", games[0].tags["WhiteElo"])
        assertEquals("e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6".split(' '), games[0].sans)
        // NAGs and variations are skipped.
        assertEquals("e4 e5 Nf3 Nc6 Bc4 Bc5 c3 Nf6".split(' '), games[1].sans)
        // A game whose tags follow the movetext without a blank line is still separated.
        assertEquals("d4 d5".split(' '), games[3].sans)
        assertEquals("d4 d5 c4 e6".split(' '), games[4].sans)
    }

    @Test
    fun movetextEdgeCases() {
        assertEquals(listOf("e4", "e5", "Nf3"), PgnReader.parseMovetext("1.e4 e5 2.Nf3 *"))
        assertEquals(listOf("e4", "e5"), PgnReader.parseMovetext("1. e4 {a {nested} comment} 1... e5 ; rest of line"))
        assertEquals(listOf("e4"), PgnReader.parseMovetext("1. e4 e5 2. Nf3", maxPlies = 1))
        assertEquals(emptyList(), PgnReader.parseMovetext("1-0"))
    }

    @Test
    fun ratingBandsAndFilters() {
        val f = GameFilter()
        assertEquals(Band.STRONG, f.band(game(2000, 2300, "e4")))
        assertEquals(Band.CLUB, f.band(game(1200, 1200, "e4")))
        assertEquals(Band.CLUB, f.band(game(2100, 1500, "e4"))) // average 1800
        assertNull(f.band(game(1900, 1950, "e4")))               // between the bands
        assertNull(f.band(game(1000, 1100, "e4")))
        assertNull(f.band(game(1500, 1500, "e4", tc = "120+1")))
        assertNull(f.band(game(1500, 1500, "e4", event = "Casual Blitz game")))
        assertNull(f.band(PgnGame(mapOf("WhiteElo" to "?", "BlackElo" to "1500"), listOf("e4"))))
        assertNull(f.band(PgnGame(mapOf("WhiteElo" to "1500", "BlackElo" to "1500", "Variant" to "Chess960"), listOf("e4"))))
    }

    @Test
    fun countsPerBandAndMergesTranspositions() {
        val counter = MoveCounter(fenAfter = 1)
        sample().forEach(counter::add)
        assertEquals(5, counter.gamesRead)
        assertEquals(1, counter.gamesPerBand[Band.STRONG.ordinal])
        assertEquals(2, counter.gamesPerBand[Band.CLUB.ordinal])

        val start = counter.positions.getValue(Position.start.key)
        assertEquals(listOf(1, 1), start.moves.getValue("e2e4").toList())
        assertEquals(listOf(0, 1), start.moves.getValue("d2d4").toList())
        assertEquals(Position.START_FEN, start.fen)

        // Different move orders reaching the same position count together.
        val c = MoveCounter()
        repeat(3) { c.add(game(1500, 1500, "Nf3 d5 d4 Nf6")) }
        repeat(2) { c.add(game(1500, 1500, "d4 d5 Nf3 Nf6")) }
        assertEquals(5, c.positions.getValue(line("d4", "d5", "Nf3").key).moves.getValue("g8f6")[Band.CLUB.ordinal])
    }

    @Test
    fun stopsAtIllegalMovesAndStoresFenLate() {
        val c = MoveCounter(fenAfter = 3)
        c.add(game(1500, 1500, "e4 e5 Ke3"))
        assertEquals(1, c.illegalGames)
        assertNull(c.positions[line("e4", "e5").key])
        assertNull(c.positions.getValue(Position.start.key).fen)
        repeat(2) { c.add(game(1500, 1500, "e4")) }
        assertNotNull(c.positions.getValue(Position.start.key).fen)
    }

    @Test
    fun lossyCountingKeepsPopularPositions() {
        val c = MoveCounter(maxPositions = 50)
        // One popular line, plus many games that each wander into unique positions.
        val rare = listOf("a3", "a4", "b3", "b4", "c3", "c4", "d3", "e3", "f3", "f4", "g3", "g4", "h3", "h4", "Na3", "Nc3", "Nf3", "Nh3")
        val replies = listOf("a6", "a5", "b6", "b5", "c6", "c5", "d6", "e6", "f6", "f5", "g6", "g5", "h6", "h5", "Na6", "Nc6", "Nf6", "Nh6")
        repeat(30) { c.add(game(1500, 1500, "e4 e5 Nf3 Nc6 Bb5 a6")) }
        for (w in rare) for (b in replies) c.add(game(1500, 1500, "$w $b"))
        assertTrue(c.positions.size <= 50)
        assertEquals(30, c.positions.getValue(line("e4", "e5", "Nf3", "Nc6").key).total)
    }
}
