package com.lembar.journal.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lembar.journal.R

/**
 * Fonts a user can pick per entry. All are bundled (SIL Open Font License),
 * so they work offline and look identical on every phone.
 * [scale] evens out optical size differences (Caveat runs small).
 */
data class JournalFont(
    val key: String,
    val label: String,
    val mood: String,
    val family: FontFamily,
    val scale: Float = 1f,
) {
    fun bodyStyle(color: androidx.compose.ui.graphics.Color, base: TextUnit = 17.sp) = TextStyle(
        fontFamily = family,
        fontSize = base * scale,
        lineHeight = base * scale * 1.55f,
        color = color,
    )

    fun titleStyle(color: androidx.compose.ui.graphics.Color) = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp * scale,
        lineHeight = 32.sp * scale,
        color = color,
    )
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variableFamily(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

object JournalFonts {
    val all: List<JournalFont> = listOf(
        JournalFont("lora", "Lora", "Hangat, seperti buku", variableFamily(R.font.lora, 400, 600)),
        JournalFont("grotesk", "Space Grotesk", "Rapi dan modern", variableFamily(R.font.space_grotesk, 400, 600)),
        JournalFont("nunito", "Nunito", "Bulat dan ramah", variableFamily(R.font.nunito, 400, 700)),
        JournalFont("caveat", "Caveat", "Tulisan tangan", variableFamily(R.font.caveat, 400, 700), scale = 1.3f),
        JournalFont("courier", "Courier Prime", "Mesin ketik", FontFamily(Font(R.font.courier_prime)), scale = 0.95f),
        JournalFont("serif", "DM Serif", "Elegan, untuk cerita", FontFamily(Font(R.font.dm_serif_display)), scale = 1.05f),
        JournalFont("system", "Bawaan HP", "Font sistem Samsung", FontFamily.Default),
    )

    fun of(key: String?): JournalFont = all.firstOrNull { it.key == key } ?: all.first()
}
