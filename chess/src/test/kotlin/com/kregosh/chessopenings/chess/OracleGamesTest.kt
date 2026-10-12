package com.kregosh.chessopenings.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Replays random games recorded with python-chess (tools/oracle/generate_fixture.py) and
 * checks every position against it: FEN, Polyglot key, the SAN of every legal move, and
 * the position reached by the move played.
 */
class OracleGamesTest {
    private data class Row(val fen: String, val key: String, val sans: List<String>, val played: String)

    private val rows: List<Row> =
        javaClass.getResource("/random-games.txt")!!.readText().trim().lines().map { line ->
            val (fen, key, sans, played) = line.split('|')
            Row(fen, key, if (sans.isEmpty()) emptyList() else sans.split(' '), played)
        }

    @Test
    fun matchesPythonChess() {
        var position = Position.start
        for ((i, row) in rows.withIndex()) {
            if (i == 0 || rows[i - 1].played == "-") position = Position.start
            val where = "row ${i + 1} (${row.fen})"

            assertEquals(row.fen, position.toFen(), "FEN, $where")
            assertEquals(row.fen, Position.fromFen(row.fen).toFen(), "FEN round trip, $where")
            assertEquals(row.key, position.key.toULong().toString(16).padStart(16, '0'), "Polyglot key, $where")
            assertEquals(row.sans, position.legalMoves.map { San.write(position, it) }.sorted(), "legal moves, $where")
            for (san in row.sans) assertNotNull(San.parse(position, san), "parse $san, $where")

            if (row.played != "-") {
                val move = assertNotNull(Uci.parse(position, row.played), "UCI ${row.played}, $where")
                assertEquals(row.played, move.uci)
                position = position.play(move)
            }
        }
    }
}
