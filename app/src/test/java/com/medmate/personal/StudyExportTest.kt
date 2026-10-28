package com.medmate.personal

import com.medmate.personal.study.*
import org.junit.Assert.*
import org.junit.Test

class StudyExportTest {
    private fun item(kind: StudyKind, page: Int, question: String, answer: String = "",
                     options: List<String> = emptyList(), ci: Int = -1, explanation: String = "") =
        StudyItem("id-$page-$kind", "sha", page, kind, question, answer, options, ci,
            explanation, 1L, ReviewState(1L))

    @Test fun ankiTwoColumnsArabicAndActualPage() {
        val cards = listOf(
            item(StudyKind.FLASHCARD, 8, "ليش؟\nOA", "Cartilage\tتآكل"),
            item(StudyKind.MCQ, 2, "Most likely?", options = listOf("A", "B", "C", "D"), ci = 2,
                explanation = "Think clinically")
        )
        val text = StudyExport.ankiBasicTsv("العظام", cards)
        assertEquals(2, text.trimEnd('\n').lines().size)
        assertTrue(text.lines().first().contains("PDF page 3"))
        assertTrue(text.contains("PDF page 9"))
        assertTrue(text.contains("تآكل"))
        assertTrue(text.lines().filter { it.isNotEmpty() }.all { it.count { ch -> ch == '\t' } == 1 })
    }

    @Test fun highYieldCorrectPageAndMarkdownEscaping() {
        val body = StudyExport.highYieldMarkdown("Chapter *1*", listOf(item(StudyKind.HIGH_YIELD, 0, "Red *flag*")))
        assertTrue(body.contains("Chapter \\*1\\*"))
        assertTrue(body.contains("## PDF page 1"))
        assertTrue(body.contains("Red \\*flag\\*"))
    }

    @Test fun emptySectionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { StudyExport.ankiBasicTsv("x", emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { StudyExport.highYieldMarkdown("x", emptyList()) }
    }
}
