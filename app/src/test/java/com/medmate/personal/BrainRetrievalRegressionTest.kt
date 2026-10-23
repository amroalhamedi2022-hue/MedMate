package com.medmate.personal

import com.medmate.personal.brain.*
import org.junit.Assert.*
import org.junit.Test

/** Source-only regression tests: no Android device or network required. */
class BrainRetrievalRegressionTest {
    @Test fun rankingIsStableAndNeverMixesChapters() {
        val evidence = listOf(
            Evidence("single", "ortho", 3, "Fracture of the radius causes pain."),
            Evidence("frequent", "ortho", 4, "Fracture fracture fracture of the radius."),
            Evidence("foreign", "surgery", 0, "Fracture fracture fracture of the radius.")
        )
        val search = EvidenceRetriever(evidence)
        assertEquals(listOf("frequent", "single"),
            search.search("ortho", "fracture radius").map { it.id })
        assertEquals(listOf("single"),
            search.search("ortho", "fracture radius", pageIndex = 3).map { it.id })
        assertTrue(search.search("ortho", "hypothyroidism").isEmpty())
        assertEquals(listOf("foreign"),
            search.search("surgery", "fracture radius").map { it.id })
    }

    @Test fun shortQuestionCountsOnlySelectedPdfPages() {
        val pages = listOf(
            PageSource("ortho", 0, "A fracture is a break in bone continuity."),
            PageSource("ortho", 1, ""),
            PageSource("surgery", 0, "A second book has its own content.")
        )
        val filtered = StudyBrain.search("ortho", pages, "?", onlyPage = 99)
        assertEquals(BrainSearchStatus.TOO_SHORT, filtered.status)
        assertEquals(0, filtered.shownPages)
        assertTrue(filtered.evidence.isEmpty())
        assertEquals(1, StudyBrain.search("ortho", pages, "?").shownPages)
        assertEquals(1, StudyBrain.search("surgery", pages, "?").shownPages)
    }
}
