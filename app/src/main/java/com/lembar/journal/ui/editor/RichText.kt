package com.lembar.journal.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em
import com.lembar.journal.domain.Markup

enum class BodyAlign(val key: String, val label: String, val align: TextAlign) {
    Start("start", "Rata kiri", TextAlign.Start),
    Center("center", "Rata tengah", TextAlign.Center),
    End("end", "Rata kanan", TextAlign.End),
    Justify("justify", "Rata kiri-kanan", TextAlign.Justify);

    fun next(): BodyAlign = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Start
    }
}

/**
 * Renders the plain-text markup: hides "# " / "## ", draws "- " as "•",
 * enlarges headings and gives list items a hanging indent.
 * The same transformation is used by the editor and the read-only view, so a
 * page looks (and lines up with its handwriting) identically in both.
 */
class MarkupTransformation(
    private val base: TextStyle,
    private val accent: Color,
) : VisualTransformation {

    private val baseSize = base.fontSize
    private val baseLine = base.lineHeight

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val lines = Markup.lines(raw)
        val origStarts = IntArray(lines.size)
        val transStarts = IntArray(lines.size)
        val hidden = IntArray(lines.size)
        val origEnds = IntArray(lines.size)

        /*
         * Every line is its own ParagraphStyle and adjacent paragraphs already break the line,
         * so a real '\n' at a paragraph edge would add an empty line. Each '\n' is therefore
         * drawn as a zero-width space that ends the paragraph (same length, so offsets map 1:1).
         * An empty last line gets one extra zero-width space so the cursor has a line to sit on.
         */
        val out = buildAnnotatedString {
            lines.forEachIndexed { i, l ->
                origStarts[i] = l.start
                origEnds[i] = l.end
                transStarts[i] = length
                val line = raw.substring(l.start, l.end)
                val last = i == lines.lastIndex
                val start = length
                var lineHeight = baseLine
                var indent: TextIndent? = null
                when (l.kind) {
                    Markup.Kind.H1, Markup.Kind.H2 -> {
                        hidden[i] = l.markerLen
                        val h1 = l.kind == Markup.Kind.H1
                        val size = baseSize * (if (h1) 1.45f else 1.2f)
                        append(line.substring(l.markerLen))
                        addStyle(SpanStyle(fontSize = size, fontWeight = if (h1) FontWeight.Bold else FontWeight.SemiBold), start, length)
                        lineHeight = size * 1.3f
                    }
                    Markup.Kind.Bullet -> {
                        append('•')
                        addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), start, start + 1)
                        append(line.substring(1))
                        indent = TextIndent(restLine = 0.9.em)
                    }
                    Markup.Kind.Number -> {
                        append(line)
                        addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, start + l.markerLen)
                        indent = TextIndent(restLine = (0.55 * l.markerLen).em)
                    }
                    Markup.Kind.Plain -> append(line)
                }
                if (!last) append(ZWSP) else if (line.isEmpty() && i > 0) append(ZWSP)
                if (length > start) addStyle(ParagraphStyle(lineHeight = lineHeight, textIndent = indent), start, length)
            }
        }

        val outLen = out.length
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val i = lineFor(origStarts, offset)
                val k = offset - origStarts[i]
                val t = if (k < hidden[i]) transStarts[i] else transStarts[i] + k - hidden[i]
                return t.coerceIn(0, outLen)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val i = lineFor(transStarts, offset)
                val o = origStarts[i] + hidden[i] + (offset - transStarts[i])
                return o.coerceIn(origStarts[i], origEnds[i]).coerceIn(0, raw.length)
            }
        }
        return TransformedText(out, mapping)
    }

    private fun lineFor(starts: IntArray, offset: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    private companion object {
        const val ZWSP = '\u200B'
    }

    override fun equals(other: Any?) = other is MarkupTransformation && other.base == base && other.accent == accent
    override fun hashCode() = base.hashCode() * 31 + accent.hashCode()
}

/** Read-only version of the formatted text. */
fun formatted(text: String, base: TextStyle, accent: Color): AnnotatedString =
    MarkupTransformation(base, accent).filter(AnnotatedString(text)).text
