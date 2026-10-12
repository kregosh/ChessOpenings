package com.kregosh.chessopenings.bookgen

import com.kregosh.chessopenings.chess.Position
import com.kregosh.chessopenings.chess.San
import com.kregosh.chessopenings.core.book.BookCodec
import com.kregosh.chessopenings.core.book.OpeningBook
import java.io.File
import java.time.LocalDate
import kotlin.system.exitProcess

private const val USAGE = """bookgen: builds the opening book (book.bin) from game data.

  bookgen count --out counts.txt [options] [games.pgn ...]
      Counts moves per position. Reads PGN files, or standard input if none are
      given, e.g.: curl … | zstd -dc | bookgen count --out counts.txt
      --max-plies N        plies read per game (24)
      --max-games N        stop after N games read (no limit)
      --max-positions N    memory bound for lossy counting (2000000)
      --min-write N        only write positions seen N+ times (50)
      --strong-min ELO     both players at least this for the strong band (2000)
      --club-min ELO, --club-max ELO   average rating range of the club band (1200, 1800)
      --min-base SECONDS   skip faster time controls (180)

  bookgen build --counts counts.txt[,more.txt] --openings data/openings --out book.bin [options]
      Applies the thresholds, evaluates with Stockfish and writes the book.
      --stockfish CMD      engine command; without it, moves aren't evaluated
      --depth N            search depth (16)
      --threads N          engine processes in parallel (number of CPUs)
      --source TEXT        description of the game data, stored in the book
      --min-strong-games, --min-club-games, --user-share, --bot-share,
      --user-max-loss, --bot-max-loss    see BookParams

  bookgen dump --book book.bin [--line "e4 e5 Nf3"]
      Prints the book's metadata and the moves after the given line."""

fun main(args: Array<String>) {
    if (args.isEmpty() || args[0] in setOf("-h", "--help")) {
        println(USAGE)
        return
    }
    val (options, files) = parseArgs(args.drop(1))
    try {
        when (args[0]) {
            "count" -> count(options, files)
            "build" -> build(options)
            "dump" -> dump(options)
            else -> fail("Unknown command '${args[0]}'")
        }
    } catch (e: IllegalArgumentException) {
        fail(e.message ?: "Bad arguments")
    } catch (e: java.io.IOException) {
        System.err.println("bookgen: ${e.message}")
        exitProcess(1)
    }
}

private fun log(message: String) = System.err.println(message)

private fun fail(message: String): Nothing {
    System.err.println("bookgen: $message\n\n$USAGE")
    exitProcess(2)
}

internal fun parseArgs(args: List<String>): Pair<Map<String, String>, List<String>> {
    val options = HashMap<String, String>()
    val rest = ArrayList<String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("--")) {
            require(i + 1 < args.size) { "Missing value for $a" }
            options[a.removePrefix("--")] = args[i + 1]
            i += 2
        } else {
            rest += a
            i++
        }
    }
    return options to rest
}

private fun Map<String, String>.int(name: String, default: Int): Int =
    this[name]?.let { it.toIntOrNull() ?: throw IllegalArgumentException("--$name needs a number") } ?: default

private fun Map<String, String>.double(name: String, default: Double): Double =
    this[name]?.let { it.toDoubleOrNull() ?: throw IllegalArgumentException("--$name needs a number") } ?: default

private fun Map<String, String>.required(name: String): String =
    this[name] ?: throw IllegalArgumentException("--$name is required")

private fun count(o: Map<String, String>, files: List<String>) {
    val out = File(o.required("out"))
    val filter = GameFilter(
        strongMin = o.int("strong-min", 2000),
        clubMinAverage = o.int("club-min", 1200),
        clubMaxAverage = o.int("club-max", 1800),
        minBaseSeconds = o.int("min-base", 180),
    )
    val maxGames = o["max-games"]?.toLongOrNull() ?: Long.MAX_VALUE
    val counter = MoveCounter(filter, o.int("max-plies", 24), o.int("max-positions", 2_000_000))
    val inputs = if (files.isEmpty()) listOf(System.`in`.bufferedReader()) else files.map { File(it).bufferedReader() }
    val started = System.currentTimeMillis()
    run {
        for (input in inputs) input.use { reader ->
            for (game in PgnReader(reader, o.int("max-plies", 24)).games()) {
                counter.add(game)
                if (counter.gamesRead % 100_000 == 0L) {
                    log("${counter.stats()}, ${(System.currentTimeMillis() - started) / 1000}s")
                }
                if (counter.gamesRead >= maxGames) return@run
            }
        }
    }
    log("done: ${counter.stats()}")
    out.bufferedWriter().use {
        CountsFile.write(
            counter, it, o.int("min-write", 50),
            header = listOf("bookgen counts", counter.stats(), filter.toString()),
        )
    }
    log("wrote ${out.path}")
}

private fun build(o: Map<String, String>) {
    val counts = HashMap<Long, PositionCounts>()
    for (path in o.required("counts").split(',')) File(path).bufferedReader().use { CountsFile.read(it, counts) }
    log("positions in counts: ${counts.size}")

    val namesDir = File(o.required("openings"))
    val names = OpeningNames.read(
        (namesDir.listFiles { f -> f.name.endsWith(".tsv") } ?: emptyArray()).sorted().asSequence().flatMap { it.readLines() },
    )
    log("opening names: ${names.size}")

    val params = BookParams(
        minStrongGames = o.int("min-strong-games", 100),
        minClubGames = o.int("min-club-games", 200),
        userShare = o.double("user-share", 0.05),
        botShare = o.double("bot-share", 0.03),
        userMaxLoss = o.int("user-max-loss", 40),
        botMaxLoss = o.int("bot-max-loss", 80),
    )
    val depth = o.int("depth", 16)
    val evaluate = o["stockfish"]?.let { cmd ->
        val threads = o.int("threads", Runtime.getRuntime().availableProcessors());
        { jobs: List<Pair<String, List<String>>> ->
            log("evaluating ${jobs.size} positions at depth $depth with $threads engines")
            evaluateAll(jobs, threads, { UciEngine(cmd.split(' '), depth) }, ::log)
        }
    }
    val metadata = buildMap {
        put("built", LocalDate.now().toString())
        o["source"]?.let { put("source", it) }
        if (evaluate != null) put("depth", "$depth")
    }
    val book = BookBuilder(params, ::log).build(counts, names, evaluate, metadata)
    val out = File(o.required("out"))
    out.writeBytes(BookCodec.encode(book))
    log("wrote ${out.path}: ${out.length() / 1024} KB")
}

private fun dump(o: Map<String, String>) {
    val book = OpeningBook.decode(File(o.required("book")).readBytes())
    println("metadata:")
    book.metadata.toSortedMap().forEach { (k, v) -> println("  $k = $v") }
    println("positions: ${book.positionCount}, names: ${book.names.size}")

    var position = Position.start
    for (san in o["line"]?.split(' ')?.filter { it.isNotBlank() }.orEmpty()) {
        position = position.play(San.parse(position, san) ?: throw IllegalArgumentException("Illegal move $san"))
    }
    println("\n${position.toFen()}" + (book.name(position)?.let { "  ($it)" } ?: ""))
    val moves = book.moves(position)
    if (moves.isEmpty()) println("  not in book")
    for (m in moves) {
        val move = m.toMove(position)
        val san = move?.let { San.write(position, it) } ?: m.uci
        val flags = listOfNotNull("user".takeIf { m.userBook }, "bot".takeIf { m.botCandidate }).joinToString("+")
        println("  %-8s strong %7d  club %7d  loss %5s  %s".format(san, m.strongGames, m.clubGames, m.lossCp ?: "-", flags))
    }
}
