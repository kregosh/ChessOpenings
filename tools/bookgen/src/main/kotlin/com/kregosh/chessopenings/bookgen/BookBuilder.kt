package com.kregosh.chessopenings.bookgen

import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.San
import com.kregosh.chessopenings.chess.Uci
import com.kregosh.chessopenings.core.book.BookMove
import com.kregosh.chessopenings.core.book.OpeningBook

/** Thresholds deciding what goes into the book; see docs/ARCHITECTURE.md, 4.2. */
data class BookParams(
    /** Strong games needed in a position before its moves can be user book. */
    val minStrongGames: Int = 100,
    /** Club games needed in a position before the bot takes replies from it. */
    val minClubGames: Int = 200,
    /** Share of the position's strong games a move needs to be user book. */
    val userShare: Double = 0.05,
    /** Share of the position's club games a move needs to be a bot candidate. */
    val botShare: Double = 0.03,
    /** Most centipawns a user book move may lose against the best move. */
    val userMaxLoss: Int = 40,
    /** Most centipawns a bot candidate may lose against the best move. */
    val botMaxLoss: Int = 80,
) {
    fun describe(): Map<String, String> = mapOf(
        "minStrongGames" to "$minStrongGames",
        "minClubGames" to "$minClubGames",
        "userShare" to "$userShare",
        "botShare" to "$botShare",
        "userMaxLoss" to "$userMaxLoss",
        "botMaxLoss" to "$botMaxLoss",
    )
}

/** A move that passed the popularity test and still needs the engine's verdict. */
internal data class Candidate(val uci: String, val strong: Int, val club: Int, val popularForUser: Boolean, val popularForBot: Boolean)

class BookBuilder(private val params: BookParams = BookParams(), private val log: (String) -> Unit = {}) {

    /**
     * The positions with moves popular enough to be considered, with their FEN and those
     * moves, in ascending key order (so separate evaluation jobs agree on it).
     */
    internal fun popular(counts: Map<Long, PositionCounts>): LinkedHashMap<Long, Pair<String, List<Candidate>>> {
        val popular = LinkedHashMap<Long, Pair<String, List<Candidate>>>()
        for ((key, c) in counts.entries.sortedBy { it.key }) {
            val fen = c.fen ?: continue
            val strongTotal = c.games(Band.STRONG)
            val clubTotal = c.games(Band.CLUB)
            val candidates = c.moves.map { (uci, n) ->
                val strong = n[Band.STRONG.ordinal]
                val club = n[Band.CLUB.ordinal]
                Candidate(
                    uci, strong, club,
                    popularForUser = strongTotal >= params.minStrongGames && strong >= strongTotal * params.userShare,
                    popularForBot = clubTotal >= params.minClubGames && club >= clubTotal * params.botShare,
                )
            }.filter { it.popularForUser || it.popularForBot }
            if (candidates.isNotEmpty()) popular[key] = fen to candidates.sortedWith(compareByDescending<Candidate> { it.strong + it.club }.thenBy { it.uci })
        }
        log("positions with popular moves: ${popular.size} of ${counts.size}")
        return popular
    }

    /** The evaluation jobs (FEN, moves to score) for [popular] positions, in the same order. */
    internal fun jobs(popular: Map<Long, Pair<String, List<Candidate>>>): List<Pair<Long, Pair<String, List<String>>>> =
        popular.map { (key, entry) -> key to (entry.first to entry.second.map { it.uci }) }

    /**
     * Builds the book from move counts. [evaluate] scores the popular moves of all kept
     * positions at once (so it can run in parallel); null skips evaluation, and then
     * popularity alone decides.
     */
    fun build(
        counts: Map<Long, PositionCounts>,
        names: Map<Long, String>,
        evaluate: ((List<Pair<String, List<String>>>) -> List<Evaluation>)?,
        metadata: Map<String, String> = emptyMap(),
    ): OpeningBook {
        val popular = popular(counts)
        val evaluations = evaluate?.let { e ->
            val jobs = jobs(popular)
            jobs.map { it.first }.zip(e(jobs.map { it.second })).toMap()
        }
        return classify(popular, names, evaluations, metadata)
    }

    /** Builds the book from counts and evaluations computed elsewhere (e.g. by sharded jobs). */
    fun build(
        counts: Map<Long, PositionCounts>,
        names: Map<Long, String>,
        evaluations: Map<Long, Evaluation>,
        metadata: Map<String, String> = emptyMap(),
    ): OpeningBook {
        val popular = popular(counts)
        val missing = popular.keys.count { it !in evaluations }
        require(missing == 0) { "$missing of ${popular.size} positions have no evaluation; were all shards built with the same counts and thresholds?" }
        return classify(popular, names, evaluations, metadata)
    }

    private fun classify(
        popular: Map<Long, Pair<String, List<Candidate>>>,
        names: Map<Long, String>,
        evaluations: Map<Long, Evaluation>?,
        metadata: Map<String, String>,
    ): OpeningBook {
        val positions = HashMap<Long, List<BookMove>>()
        for ((key, entry) in popular) {
            val (fen, candidates) = entry
            val eval = evaluations?.get(key)
            val position = Position.fromFen(fen)
            val moves = candidates.mapNotNull { c ->
                val loss = eval?.loss(c.uci)
                val userBook = c.popularForUser && (loss == null || loss <= params.userMaxLoss)
                val botCandidate = c.popularForBot && (loss == null || loss <= params.botMaxLoss)
                if (!userBook && !botCandidate) return@mapNotNull null
                val move = Uci.parse(position, c.uci)
                    ?: error("Counted move ${c.uci} is illegal in $fen")
                BookMove(
                    from = move.from,
                    to = move.to,
                    promotion = move.promotion,
                    strongGames = c.strong,
                    clubGames = c.club,
                    lossCp = loss,
                    userBook = userBook,
                    botCandidate = botCandidate,
                )
            }
            if (moves.isNotEmpty()) positions[key] = moves
        }
        log("book positions: ${positions.size}, moves: ${positions.values.sumOf { it.size }}")

        return OpeningBook(
            metadata = metadata + params.describe() + mapOf("evaluated" to "${evaluations != null}"),
            positions = positions,
            names = names,
        )
    }
}

/**
 * Opening names from the lichess-org/chess-openings TSV files (eco, name, pgn), keyed by
 * the position each line ends in. Where several lines end in the same position, the
 * shortest line's name wins.
 */
object OpeningNames {
    fun read(tsvLines: Sequence<String>): Map<Long, String> {
        val best = HashMap<Long, Pair<Int, String>>()
        for (line in tsvLines) {
            val f = line.split('\t')
            if (f.size < 3 || f[0] == "eco") continue
            val sans = PgnReader.parseMovetext(f[2])
            var p = Position.start
            for (san in sans) p = p.play(San.parse(p, san) ?: error("Bad move $san in opening line: $line"))
            val current = best[p.key]
            if (current == null || sans.size < current.first) best[p.key] = sans.size to f[1]
        }
        return best.mapValues { it.value.second }
    }
}

