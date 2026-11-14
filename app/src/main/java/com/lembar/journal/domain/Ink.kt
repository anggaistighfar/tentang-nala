package com.lembar.journal.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * Handwriting written straight onto the journal page. Coordinates and width are
 * divided by the page width, so the ink scales with the page instead of drifting.
 */
data class InkPoint(val x: Float, val y: Float, val w: Float)

data class InkStroke(val points: List<InkPoint>, val color: Int, val marker: Boolean) {
    val bottom: Float get() = points.maxOfOrNull { it.y + it.w } ?: 0f
}

/**
 * Compact binary format: x/y as 16-bit fixed point (1/2048 of the page width),
 * width as 16-bit (1/32768), then deflated and Base64'd. A full handwritten page
 * is typically 5–15 KB.
 */
object InkCodec {
    private const val VERSION = 1
    private const val XY = 2048f
    private const val W = 32768f
    const val MAX_POINTS = 80_000

    fun encode(strokes: List<InkStroke>): String {
        if (strokes.isEmpty()) return ""
        val bos = ByteArrayOutputStream()
        DataOutputStream(DeflaterOutputStream(bos, Deflater(Deflater.BEST_COMPRESSION))).use { out ->
            out.writeByte(VERSION)
            out.writeInt(strokes.size)
            for (s in strokes) {
                out.writeInt(s.color)
                out.writeByte(if (s.marker) 1 else 0)
                out.writeInt(s.points.size)
                for (p in s.points) {
                    out.writeShort((p.x * XY).toInt().coerceIn(0, Short.MAX_VALUE.toInt()))
                    out.writeShort((p.y * XY).toInt().coerceIn(0, Short.MAX_VALUE.toInt()))
                    out.writeShort((p.w * W).toInt().coerceIn(1, Short.MAX_VALUE.toInt()))
                }
            }
        }
        return Base64.getEncoder().encodeToString(bos.toByteArray())
    }

    fun decode(data: String): List<InkStroke> {
        if (data.isBlank()) return emptyList()
        return runCatching {
            DataInputStream(InflaterInputStream(ByteArrayInputStream(Base64.getDecoder().decode(data)))).use { inp ->
                require(inp.readUnsignedByte() == VERSION)
                List(inp.readInt()) {
                    val color = inp.readInt()
                    val marker = inp.readUnsignedByte() == 1
                    val pts = List(inp.readInt()) {
                        InkPoint(inp.readUnsignedShort() / XY, inp.readUnsignedShort() / XY, inp.readUnsignedShort() / W)
                    }
                    InkStroke(pts, color, marker)
                }
            }
        }.getOrDefault(emptyList())
    }
}
