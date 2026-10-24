package com.medmate.personal.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Compare the cached production search against the original exact PDF retriever. */
class IndexedSearchTest {
    private val pages = listOf(
        PageSource("orthopedics", 0,
            "Fracture of the radius is a break in bone continuity. Neurovascular examination is important."),
        PageSource("orthopedics", 1,
            "A fracture may be displaced. Fracture management requires careful assessment."),
        PageSource("orthopedics", 2,
            "Volkmann contracture can follow compartment syndrome and ischemic injury."),
        PageSource("other-pdf", 0,
            "Fracture fracture fracture of the radius: unrelated chapter.")
    )

    private fun baseline(chapter: String, query: String, page: Int? = null): List<Evidence> {
        val selected = pages.filter { it.chapterId == chapter && (page == null || it.pageIndex == page) }
        return EvidenceRetriever(PageSegmenter(850).split(selected)).search(chapter, query, limit = 5)
    }

    @Test fun cachedIndexKeepsExactLegacyRankingAndPageCitations() {
        for (query in listOf("fracture", "fracture radius", "Volkmann", "شو الكسر", "ischemic",
            "unknown condition", "radius", "Fracture management")) {
            for (page in listOf<Int?>(null, 0, 1, 2, 99)) {
                val result = StudyBrain.search("orthopedics", pages, query, page)
                assertEquals("query/page ranking must match source-only baseline", baseline("orthopedics", query, page),
                    result.evidence)
                assertTrue(result.evidence.all { it.chapterId == "orthopedics" &&
                    (page == null || it.pageIndex == page) })
            }
        }
    }

    @Test fun changingPdfContentInvalidatesCacheWithoutLeakingOtherChapter() {
        StudyBrain.search("orthopedics", pages, "fracture")
        val changed = pages.map { page ->
            if (page.chapterId == "orthopedics" && page.pageIndex == 0)
                page.copy(text = "Only an unrelated anatomy passage remains on this page.")
            else page
        }
        val result = StudyBrain.search("orthopedics", changed, "radius")
        val expected = EvidenceRetriever(PageSegmenter(850).split(changed.filter {
            it.chapterId == "orthopedics"
        })).search("orthopedics", "radius")
        assertEquals(expected, result.evidence)
        assertTrue(result.evidence.none { it.pageIndex == 0 })
        assertEquals(BrainSearchStatus.TOO_SHORT, StudyBrain.search("orthopedics", pages, "?", 99).status)
        assertEquals(0, StudyBrain.search("orthopedics", pages, "?", 99).shownPages)
        assertEquals(BrainSearchStatus.SCANNED_PDF_OR_MISSING_TEXT,
            StudyBrain.search("orthopedics", listOf(PageSource("orthopedics", 0, "")), "fracture").status)
    }
}
