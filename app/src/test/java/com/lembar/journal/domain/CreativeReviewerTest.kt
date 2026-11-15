package com.lembar.journal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreativeReviewerTest {

    private val classic = "Jalan-jalan ke kota Blitar\nJangan lupa beli sukun\nJika kamu ingin pintar\nBelajarlah dengan tekun"

    @Test fun syllables() {
        assertEquals(9, CreativeReviewer.lineSyllables("Jalan-jalan ke kota Blitar"))
        assertEquals(8, CreativeReviewer.lineSyllables("Belajarlah dengan tekun"))
        assertEquals(2, CreativeReviewer.syllables("pantai"))
        assertEquals(3, CreativeReviewer.syllables("bermain"))
    }

    @Test fun rhyme() {
        assertEquals("ar", CreativeReviewer.rhymeKey("Jalan-jalan ke kota Blitar"))
        assertEquals("ai", CreativeReviewer.rhymeKey("pergi ke pantai"))
        assertEquals("a-b-a-b", CreativeReviewer.rhymePattern(classic.split('\n')))
    }

    @Test fun classicPantunScoresFull() {
        val r = CreativeReviewer.review(EntryType.Pantun, "", classic)!!
        assertEquals(100, r.score)
        assertTrue(r.checks.none { it.status == CheckStatus.Warn })
    }

    @Test fun brokenRhymeIsFlagged() {
        val r = CreativeReviewer.review(EntryType.Pantun, "", "Pergi ke pasar membeli roti\nPulang lewat jalan raya\nKalau kamu baik hati\nTeman pasti banyak sekali")!!
        assertTrue(r.score!! < 100)
        assertTrue(r.checks.any { it.label == "Rima akhir" && it.status == CheckStatus.Warn })
    }

    @Test fun eightLinesSplitIntoTwoStanzas() {
        val r = CreativeReviewer.review(EntryType.Pantun, "", "$classic\n$classic")!!
        assertTrue(r.checks.any { it.label.startsWith("Bait 2") })
    }

    @Test fun ideaFindsParts() {
        val r = CreativeReviewer.review(EntryType.Idea, "Bank sampah", "Masalahnya sampah plastik di kompleks. Untuk warga RT 3. Langkah pertama: rapat minggu depan.")!!
        assertEquals(60, r.score)
        assertNotNull(r.tips.firstOrNull())
    }

    @Test fun storyArcAndDialogue() {
        val text = "Pagi itu Rina bahagia dan tersenyum. ".repeat(8) +
            "\n\nTiba-tiba ia kehilangan dompet, sedih dan takut, lalu menangis. ".repeat(8) +
            "\n\n\"Ini dompetmu,\" kata seorang anak. Rina lega dan bersyukur. ".repeat(8)
        val r = CreativeReviewer.review(EntryType.Story, "Dompet", text)!!
        assertTrue(r.checks.first { it.label == "Alur rasa" }.status == CheckStatus.Good)
        assertTrue(r.checks.first { it.label == "Dialog" }.status == CheckStatus.Good)
    }

    @Test fun markupIsIgnored() {
        val r = CreativeReviewer.review(EntryType.Pantun, "", "# Pantun\n\n$classic")!!
        assertTrue(r.checks.any { it.label.contains("Rima") })
    }
}
