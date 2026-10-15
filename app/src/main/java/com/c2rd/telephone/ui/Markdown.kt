package com.c2rd.telephone.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em

/**
 * Small Markdown subset tuned for reading off a prompter, not a full CommonMark parser.
 *
 * Blocks:  # / ## / ### headings, > quotes, - * + bullets, 1. lists, --- rules
 * Inline:  **bold** __bold__ *italic* _italic_ ~~strike~~ ==highlight== `code`
 *          [link](url) shows just the link text, [anything else] renders as a stage cue.
 *          Backslash escapes the next character.
 */
object Markdown {
    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val RULE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
    private val BULLET = Regex("""^[-*+]\s+(.*)$""")
    private val ORDERED = Regex("""^(\d+)[.)]\s+(.*)$""")
    private val QUOTE = Regex("""^>\s?(.*)$""")

    private val bold = SpanStyle(fontWeight = FontWeight.ExtraBold)
    private val italic = SpanStyle(fontStyle = FontStyle.Italic)
    private val strike = SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color.White.copy(alpha = 0.55f))
    private val highlight = SpanStyle(color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
    private val code = SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x33FFFFFF))
    private val link = SpanStyle(textDecoration = TextDecoration.Underline)
    private val cue = SpanStyle(color = Color(0xFF4DD0E1), fontStyle = FontStyle.Italic, fontSize = 0.8.em)
    private val quote = SpanStyle(color = Color(0xFFB0BEC5), fontStyle = FontStyle.Italic)
    private val muted = SpanStyle(color = Color.White.copy(alpha = 0.4f))

    private val MARKERS = listOf(
        "**" to bold, "__" to bold, "~~" to strike, "==" to highlight,
        "*" to italic, "_" to italic, "`" to code,
    )

    fun render(src: String): AnnotatedString = buildAnnotatedString {
        val lines = src.lines()
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            HEADING.matchEntire(line)?.let { m ->
                val size = when (m.groupValues[1].length) {
                    1 -> 1.6.em
                    2 -> 1.35.em
                    3 -> 1.15.em
                    else -> 1.0.em
                }
                withStyle(SpanStyle(fontSize = size, fontWeight = FontWeight.Bold)) { inline(m.groupValues[2]) }
            } ?: RULE.matchEntire(line)?.let {
                withStyle(muted) { append("— — —") }
            } ?: BULLET.matchEntire(line)?.let { m ->
                append("•  ")
                inline(m.groupValues[1])
            } ?: ORDERED.matchEntire(line)?.let { m ->
                append(m.groupValues[1] + ".  ")
                inline(m.groupValues[2])
            } ?: QUOTE.matchEntire(line)?.let { m ->
                withStyle(quote) { inline(m.groupValues[1]) }
            } ?: inline(line)

            if (index < lines.lastIndex) append('\n')
        }
    }

    private fun AnnotatedString.Builder.inline(s: String) {
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) {
                append(plain.toString())
                plain.clear()
            }
        }

        var i = 0
        while (i < s.length) {
            val c = s[i]

            if (c == '\\' && i + 1 < s.length) {
                plain.append(s[i + 1])
                i += 2
                continue
            }

            if (c == '[') {
                val close = s.indexOf(']', i + 1)
                if (close > i + 1) {
                    flush()
                    val inner = s.substring(i + 1, close)
                    val paren = if (close + 1 < s.length && s[close + 1] == '(') s.indexOf(')', close + 2) else -1
                    if (paren > 0) {
                        withStyle(link) { inline(inner) }
                        i = paren + 1
                    } else {
                        withStyle(cue) { append("[$inner]") }
                        i = close + 1
                    }
                    continue
                }
            }

            val marker = MARKERS.firstOrNull { s.startsWith(it.first, i) }
            if (marker != null) {
                val (token, style) = marker
                val start = i + token.length
                val close = findClose(s, token, i)
                if (close > start) {
                    flush()
                    withStyle(style) {
                        val body = s.substring(start, close)
                        if (token == "`") append(body) else inline(body)
                    }
                    i = close + token.length
                } else {
                    plain.append(token)
                    i = start
                }
                continue
            }

            plain.append(c)
            i++
        }
        flush()
    }

    /** Index of the token that closes the one opened at [open], or -1. */
    private fun findClose(s: String, token: String, open: Int): Int {
        val start = open + token.length
        // "2 * 3" and snake_case shouldn't turn into italics.
        if (start >= s.length || s[start].isWhitespace()) return -1
        val wordBound = token[0] == '_'
        if (wordBound && open > 0 && s[open - 1].isLetterOrDigit()) return -1

        var j = start
        while (j < s.length) {
            if (s[j] == '\\') {
                j += 2
                continue
            }
            if (s.startsWith(token, j)) {
                // A single * or _ must not close on half of a ** / __ pair.
                if (token.length == 1 && j + 1 < s.length && s[j + 1] == token[0]) {
                    j += 2
                    continue
                }
                val boundaryOk = !wordBound || j + 1 >= s.length || !s[j + 1].isLetterOrDigit()
                if (!s[j - 1].isWhitespace() && boundaryOk) return j
            }
            j++
        }
        return -1
    }
}
