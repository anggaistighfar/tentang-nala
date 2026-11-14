package com.lembar.journal.domain

/**
 * Lightweight line-based formatting stored as plain text, so entries stay
 * small, searchable and readable even outside the app:
 *
 *   "# "  heading        "## " sub-heading
 *   "1. " numbered item  "- "  bullet item
 *
 * The editor hides heading markers and draws "- " as "•".
 */
object Markup {

    enum class Kind { Plain, H1, H2, Number, Bullet }

    data class Line(val start: Int, val end: Int, val kind: Kind, val markerLen: Int, val number: Int)

    /** Text plus selection, independent of Compose so it can be unit-tested. */
    data class Doc(val text: String, val selStart: Int, val selEnd: Int)

    private val numberRe = Regex("^(\\d{1,3})\\. ")

    fun kindOf(line: String): Triple<Kind, Int, Int> = when {
        line.startsWith("## ") -> Triple(Kind.H2, 3, 0)
        line.startsWith("# ") -> Triple(Kind.H1, 2, 0)
        line.startsWith("- ") || line.startsWith("• ") -> Triple(Kind.Bullet, 2, 0)
        else -> numberRe.find(line)?.let { Triple(Kind.Number, it.value.length, it.groupValues[1].toInt()) }
            ?: Triple(Kind.Plain, 0, 0)
    }

    fun lines(text: String): List<Line> {
        val out = ArrayList<Line>()
        var start = 0
        while (true) {
            val nl = text.indexOf('\n', start)
            val end = if (nl < 0) text.length else nl
            val (k, m, n) = kindOf(text.substring(start, end))
            out += Line(start, end, k, m, n)
            if (nl < 0) break
            start = nl + 1
        }
        return out
    }

    fun marker(kind: Kind, number: Int = 1): String = when (kind) {
        Kind.Plain -> ""
        Kind.H1 -> "# "
        Kind.H2 -> "## "
        Kind.Number -> "$number. "
        Kind.Bullet -> "- "
    }

    /** Text without formatting markers (for previews, search snippets and word counts). */
    fun plain(text: String): String = lines(text).joinToString("\n") { l ->
        val s = text.substring(l.start, l.end)
        when (l.kind) {
            Kind.H1, Kind.H2 -> s.substring(l.markerLen)
            Kind.Bullet -> "• " + s.substring(2)
            else -> s
        }
    }

    private fun lineIndexAt(ls: List<Line>, offset: Int): Int =
        ls.indexOfLast { it.start <= offset }.coerceAtLeast(0)

    /** Kind of the line holding the cursor, for highlighting toolbar buttons. */
    fun kindAt(doc: Doc): Kind {
        val ls = lines(doc.text)
        return ls[lineIndexAt(ls, doc.selStart.coerceIn(0, doc.text.length))].kind
    }

    private fun selectedLines(ls: List<Line>, doc: Doc): Set<Int> {
        val lo = minOf(doc.selStart, doc.selEnd)
        val hi = maxOf(doc.selStart, doc.selEnd)
        return ls.indices.filter { ls[it].start <= hi && ls[it].end >= lo }.toSet()
    }

    /**
     * Rewrites line markers only (content untouched) and maps the selection so the
     * cursor stays on the same character. [newMarker] returns null to keep a line as is.
     */
    private fun remark(doc: Doc, newMarker: (Int, Line) -> String?): Doc {
        val ls = lines(doc.text)
        val sb = StringBuilder(doc.text.length + 8)
        val starts = IntArray(ls.size)
        val markerLens = IntArray(ls.size)
        ls.forEachIndexed { i, l ->
            if (i > 0) sb.append('\n')
            starts[i] = sb.length
            val m = newMarker(i, l) ?: doc.text.substring(l.start, l.start + l.markerLen)
            markerLens[i] = m.length
            sb.append(m).append(doc.text, l.start + l.markerLen, l.end)
        }
        fun map(o: Int): Int {
            val i = lineIndexAt(ls, o)
            val k = o - ls[i].start
            val nk = if (k <= ls[i].markerLen) markerLens[i] else k - ls[i].markerLen + markerLens[i]
            return (starts[i] + nk).coerceAtMost(sb.length)
        }
        return Doc(sb.toString(), map(doc.selStart), map(doc.selEnd))
    }

    /** Toolbar action: apply [kind] to the selected lines, or remove it if they already have it. */
    fun toggle(doc: Doc, kind: Kind): Doc {
        val ls = lines(doc.text)
        val sel = selectedLines(ls, doc)
        val allSame = sel.all { ls[it].kind == kind }
        return renumber(remark(doc) { i, _ -> if (i in sel) (if (allSame) "" else marker(kind, 1)) else null })
    }

    /** Consecutive numbered lines count up from the first line's number. */
    fun renumber(doc: Doc): Doc {
        val ls = lines(doc.text)
        val want = arrayOfNulls<String>(ls.size)
        var prev = 0
        var changed = false
        ls.forEachIndexed { i, l ->
            if (l.kind != Kind.Number) return@forEachIndexed
            val n = if (i > 0 && ls[i - 1].kind == Kind.Number) prev + 1 else l.number
            prev = n
            if (n != l.number) {
                want[i] = marker(Kind.Number, n)
                changed = true
            }
        }
        return if (changed) remark(doc) { i, _ -> want[i] } else doc
    }

    /**
     * Runs after every keystroke:
     *  - Enter on a list item starts the next item ("2. ", "- "),
     *  - Enter on an empty list item ends the list,
     *  - Backspace into a hidden heading marker turns the line back into a paragraph,
     *  - numbered lists are kept in order,
     *  - the cursor never lands inside a hidden marker.
     */
    fun afterEdit(old: Doc, new: Doc): Doc {
        var d = new
        val p = new.selStart
        val collapsed = new.selStart == new.selEnd && old.selStart == old.selEnd
        val insertedNewline = collapsed &&
            new.text.length == old.text.length + 1 && p > 0 && p <= new.text.length && new.text[p - 1] == '\n' &&
            new.text.regionMatches(0, old.text, 0, p - 1) &&
            new.text.regionMatches(p, old.text, p - 1, old.text.length - (p - 1))

        if (insertedNewline) {
            val prevStart = new.text.lastIndexOf('\n', p - 2) + 1
            val prevLine = new.text.substring(prevStart, p - 1)
            val (k, m, n) = kindOf(prevLine)
            if (k == Kind.Number || k == Kind.Bullet) {
                d = if (prevLine.length == m) {
                    Doc(new.text.removeRange(prevStart, p), prevStart, prevStart)
                } else {
                    val mk = if (k == Kind.Number) marker(Kind.Number, n + 1) else marker(Kind.Bullet)
                    Doc(new.text.substring(0, p) + mk + new.text.substring(p), p + mk.length, p + mk.length)
                }
            }
        } else if (collapsed && old.text.length - new.text.length == 1) {
            val ls = lines(new.text)
            val l = ls[lineIndexAt(ls, p)]
            val line = new.text.substring(l.start, l.end)
            val oldLines = lines(old.text)
            val oldKind = oldLines[lineIndexAt(oldLines, old.selStart)].kind
            val brokenHeading = (oldKind == Kind.H1 || oldKind == Kind.H2) && line.startsWith("#") && l.kind == Kind.Plain
            val brokenBullet = oldKind == Kind.Bullet && (line.startsWith("-") || line.startsWith("•")) && l.kind == Kind.Plain
            if (brokenHeading || brokenBullet) {
                val cut = if (brokenHeading) line.takeWhile { it == '#' }.length else 1
                d = Doc(new.text.removeRange(l.start, l.start + cut), l.start, l.start)
            }
        }
        return snap(renumber(d))
    }

    /** Keeps a collapsed cursor out of hidden heading markers and the bullet glyph. */
    fun snap(doc: Doc): Doc {
        if (doc.selStart != doc.selEnd) return doc
        val ls = lines(doc.text)
        val l = ls[lineIndexAt(ls, doc.selStart)]
        val hidden = l.kind == Kind.H1 || l.kind == Kind.H2 || l.kind == Kind.Bullet
        return if (hidden && doc.selStart < l.start + l.markerLen) {
            val o = l.start + l.markerLen
            Doc(doc.text, o, o)
        } else doc
    }

    // ---- "Rapikan" ------------------------------------------------------------------

    /**
     * Tidies the text without changing its meaning: single spaces, no space before
     * punctuation, a space after it, capital letters at sentence starts, and at most
     * one blank line between paragraphs. Formatting markers are kept.
     */
    fun tidy(text: String): String {
        val lines = text.replace("\r", "").split('\n').map { raw ->
            val (k, m, _) = kindOf(raw)
            val mk = if (k == Kind.Bullet) "- " else raw.substring(0, m)
            var c = raw.substring(m)
            c = c.replace(Regex("[ \\t\\u00A0]+"), " ").trim()
            c = c.replace(Regex(" +([,.!?;:])"), "$1")
            c = c.replace(Regex("([,!?;:])(?=\\p{L})"), "$1 ")
            c = c.replace(Regex("(?<=\\p{Ll})\\.(?=\\p{L}{2})"), ". ")
            c = capitalizeSentences(c)
            if (c.isEmpty() && k == Kind.Plain) "" else mk + c
        }
        val out = ArrayList<String>()
        for (l in lines) {
            if (l.isBlank()) {
                if (out.isNotEmpty() && out.last().isNotEmpty()) out += ""
            } else {
                out += l
            }
        }
        while (out.isNotEmpty() && out.last().isEmpty()) out.removeAt(out.lastIndex)
        return out.joinToString("\n")
    }

    private fun capitalizeSentences(s: String): String {
        val sb = StringBuilder(s)
        var cap = true
        for (i in sb.indices) {
            val ch = sb[i]
            when {
                ch == '.' || ch == '!' || ch == '?' -> cap = i + 1 < sb.length && sb[i + 1] == ' '
                cap && ch.isLetter() -> {
                    sb.setCharAt(i, ch.uppercaseChar())
                    cap = false
                }
                ch.isLetterOrDigit() -> cap = false
            }
        }
        return sb.toString()
    }
}
