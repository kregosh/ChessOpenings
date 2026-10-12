package com.kregosh.chessopenings.bookgen

import java.io.BufferedReader

/** A game from a PGN file: its header tags and the SAN moves of its movetext. */
data class PgnGame(val tags: Map<String, String>, val sans: List<String>)

/**
 * Streams games from PGN text, as in the Lichess database dumps. Comments (including
 * `[%clk]` and `[%eval]`), variations, NAGs, move numbers and results are skipped.
 * Only the first [maxPlies] moves of each game are kept.
 */
class PgnReader(private val input: BufferedReader, private val maxPlies: Int = Int.MAX_VALUE) {
    fun games(): Sequence<PgnGame> = sequence {
        var tags = LinkedHashMap<String, String>()
        val movetext = StringBuilder()
        var inMoves = false

        suspend fun SequenceScope<PgnGame>.flush() {
            if (tags.isNotEmpty() || movetext.isNotBlank()) yield(PgnGame(tags, parseMovetext(movetext, maxPlies)))
            tags = LinkedHashMap()
            movetext.clear()
            inMoves = false
        }

        while (true) {
            val raw = input.readLine() ?: break
            val line = raw.trim()
            when {
                line.startsWith("[") && !inMoves -> TAG.matchEntire(line)?.let { tags[it.groupValues[1]] = it.groupValues[2] }
                line.startsWith("[") && inMoves -> {
                    // A new game started without a blank line after the previous movetext.
                    flush()
                    TAG.matchEntire(line)?.let { tags[it.groupValues[1]] = it.groupValues[2] }
                }
                line.isEmpty() -> if (inMoves) flush()
                else -> {
                    inMoves = true
                    movetext.append(line).append(' ')
                }
            }
        }
        flush()
    }

    companion object {
        private val TAG = Regex("""\[(\w+)\s+"((?:[^"\\]|\\.)*)"\]""")
        private val MOVE_NUMBER = Regex("""^\d+\.+""")
        private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")

        fun parseMovetext(text: CharSequence, maxPlies: Int = Int.MAX_VALUE): List<String> {
            val sans = ArrayList<String>()
            var i = 0
            var commentDepth = 0
            var variationDepth = 0
            val token = StringBuilder()

            fun endToken() {
                if (token.isEmpty()) return
                var t = token.toString()
                token.clear()
                if (commentDepth > 0 || variationDepth > 0) return
                t = MOVE_NUMBER.replace(t, "")
                if (t.isEmpty() || t.startsWith("$") || t in RESULTS) return
                if (sans.size < maxPlies) sans += t
            }

            while (i < text.length && sans.size < maxPlies) {
                val c = text[i]
                when {
                    c == '{' -> { endToken(); commentDepth++ }
                    c == '}' -> { endToken(); if (commentDepth > 0) commentDepth-- }
                    commentDepth > 0 -> Unit
                    c == ';' -> { endToken(); while (i < text.length && text[i] != '\n') i++ }
                    c == '(' -> { endToken(); variationDepth++ }
                    c == ')' -> { endToken(); if (variationDepth > 0) variationDepth-- }
                    c.isWhitespace() -> endToken()
                    else -> token.append(c)
                }
                i++
            }
            endToken()
            return sans
        }
    }
}
