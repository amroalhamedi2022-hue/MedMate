package com.medmate.personal

import com.medmate.personal.brain.*
import org.junit.Assert.*
import org.junit.Test

/** Source-only regression tests: no Android device or network required. */
class BrainRetrievalRegressionTest {
    @Test fun shortEvidenceDoesNotHideLaterGroundedExcerpts() {
        val prepared = MedicalPromptBuilder.prepare(GroundedRequest("ortho", "fracture", listOf(
            Evidence("tiny", "ortho", 0, "brief"),
            Evidence("useful", "ortho", 4, "The fracture of the distal radius requires careful neurovascular assessment."),
            Evidence("also-useful", "ortho", 5, "Additional imaging can be useful in complex patterns of forearm injury.")
        ), maxEvidenceChars = 500))
        assertEquals(listOf("useful", "also-useful"), prepared.presentedEvidence.map { it.id })
        assertTrue(prepared.prompt.contains("[S1] Page 5 (useful)"))
        assertTrue(prepared.prompt.contains("[S2] Page 6 (also-useful)"))
        assertFalse(prepared.prompt.contains("[S3]"))
        assertTrue(CitationValidator.check("شرح من النص [S2]", prepared.presentedEvidence.size).accepted)
    }

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

    @Test fun surgicalPluralsAreSearchableWithoutCrossingPagesOrBooks() {
        val pages = listOf(
            Evidence("s:0", "surgery", 0, "Ulcers and perforations require assessment."),
            Evidence("s:1", "surgery", 1, "Intestinal obstructions and adhesions are discussed."),
            Evidence("s:2", "surgery", 2, "Gallbladders and abscesses are mentioned."),
            Evidence("o:0", "ortho", 0, "Ulcers are outside the selected surgical chapter.")
        )
        val search = EvidenceRetriever(pages)
        assertEquals("s:0", search.search("surgery", "ulcer").first().id)
        assertEquals("s:0", search.search("surgery", "perforation").first().id)
        assertEquals("s:1", search.search("surgery", "obstruction").first().id)
        assertEquals("s:1", search.search("surgery", "adhesion").first().id)
        assertEquals("s:2", search.search("surgery", "abscess").first().id)
        assertEquals("s:0", search.search("surgery", "قرحة", pageIndex = 0).first().id)
        assertTrue(search.search("surgery", "ulcer", pageIndex = 1).isEmpty())
        assertTrue(search.search("ortho", "adhesion").isEmpty())
        assertEquals("diagnoses", ClinicalTermNormalizer.canonical("diagnoses"))
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
