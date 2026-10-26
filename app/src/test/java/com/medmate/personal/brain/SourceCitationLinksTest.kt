package com.medmate.personal.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source navigation contract; this does not test a GGUF model or clinical truth. */
class SourceCitationLinksTest {
    private val first = Evidence("bone:0:0", "bone", 0, "A fracture is a break in bone.")
    private val second = Evidence("bone:4:0", "bone", 4, "The periosteum surrounds bone.")

    @Test fun resolvesExactMarkersToTheirOriginalPdfPagesInOrder() {
        val links = SourceCitationLinks.resolve(
            "The periosteum surrounds bone [S2], and fractures interrupt bone [S1]. See [S2].",
            listOf(first, second))
        assertEquals(listOf("S2", "S1"), links.map { it.marker })
        assertEquals(listOf(4, 0), links.map { it.pageIndex })
        assertEquals(listOf(5, 1), links.map { it.pageNumber })
    }

    @Test fun anyForgedMarkerMakesEveryLinkNonClickable() {
        val bad = listOf(
            "Explanation [S1] and invented [S99]",
            "Explanation [S1] [S-1]",
            "Explanation [S1] [S01]",
            "Explanation [S1] [Sfoo]",
            "Explanation [S1] [ S2]",
            "Explanation [S1] ［S2］",
            "Explanation [S1] [S2"
        )
        bad.forEach { assertTrue(it, SourceCitationLinks.resolve(it, listOf(first, second)).isEmpty()) }
    }

    @Test fun rejectsCrossChapterAndNegativePageEvenForValidCitation() {
        val answer = "Fracture means a break in bone [S1]."
        assertTrue(SourceCitationLinks.resolve(answer, listOf(first, second.copy(chapterId = "other"))).isEmpty())
        assertTrue(SourceCitationLinks.resolve(answer, listOf(first.copy(pageIndex = -1))).isEmpty())
        assertTrue(SourceCitationLinks.resolve(answer, emptyList()).isEmpty())
    }

    @Test fun rejectsAmbiguousOrEmptyEvidenceBeforeMakingNavigation() {
        val answer = "A fracture is a break in bone [S1]."
        assertTrue(SourceCitationLinks.resolve(answer, listOf(first, second.copy(id = first.id))).isEmpty())
        assertTrue(SourceCitationLinks.resolve(answer, listOf(first.copy(id = " "))).isEmpty())
        assertTrue(SourceCitationLinks.resolve(answer, listOf(first.copy(text = " "))).isEmpty())
    }

    @Test fun abstentionAndCitationOnlyAnswersCannotProduceNavigation() {
        assertTrue(SourceCitationLinks.resolve("المعلومة مش كافية بالنص الموجود", listOf(first)).isEmpty())
        assertTrue(SourceCitationLinks.resolve("[S1]", listOf(first)).isEmpty())
        assertTrue(SourceCitationLinks.resolve("Explanation without a source", listOf(first)).isEmpty())
    }
}
