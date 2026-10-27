package com.medmate.personal.brain

import org.junit.Assert.*
import org.junit.Test

/**
 * End-to-end source-provenance regression across the production prompt builder,
 * local answer display gate and clickable PDF citation mapper. No model is run.
 */
class GroundedAnswerPipelineTest {
    private val chapter = "orthopedics"

    private fun evidence(): List<Evidence> = listOf(
        Evidence(
            id = "book/a",
            chapterId = chapter,
            pageIndex = 1,
            text = "A distal radius fracture requires careful neurovascular assessment and follow-up."
        ),
        Evidence(
            id = "book_a",
            chapterId = chapter,
            pageIndex = 6,
            text = "The fracture line may involve the periosteum and should be described with imaging context."
        ),
        Evidence(
            id = "book_a_2",
            chapterId = chapter,
            pageIndex = 8,
            text = "A fracture assessment documents alignment, circulation, sensation, and the follow-up plan."
        )
    )

    private fun prepare(items: List<Evidence>): PreparedPrompt =
        MedicalPromptBuilder.prepare(
            GroundedRequest(
                chapterId = chapter,
                query = "fracture",
                evidence = items,
                maxEvidenceChars = 2400
            )
        )

    @Test fun sanitizedPromptLabelCollisionsStillResolveToTheOriginalPdfPages() {
        val prepared = prepare(evidence())

        assertEquals(listOf("book/a", "book_a", "book_a_2"), prepared.presentedEvidence.map { it.id })
        assertTrue(prepared.prompt.contains("[S1] Page 2 (book_a):"))
        assertTrue(prepared.prompt.contains("[S2] Page 7 (book_a_2):"))
        assertTrue(prepared.prompt.contains("[S3] Page 9 (book_a_2_2):"))

        val answer = "The distal radius fracture needs assessment [S1]. " +
            "The periosteum is described in the second excerpt [S2]. " +
            "The third excerpt describes follow-up planning [S3]."
        val gate = LocalAnswerGate.evaluate(answer, prepared)
        assertTrue(gate.citationCheck.reason, gate.accepted)
        assertEquals(answer, gate.displayableAnswer)

        val links = SourceCitationLinks.resolve(gate.displayableAnswer!!, prepared.presentedEvidence)
        assertEquals(listOf("S1", "S2", "S3"), links.map { it.marker })
        assertEquals(listOf(1, 6, 8), links.map { it.pageIndex })
        assertEquals(listOf(2, 7, 9), links.map { it.pageNumber })
    }

    @Test fun duplicateOrNonCanonicalSourceIdentityWithholdsFactualAnswerAndLinks() {
        val duplicate = listOf(
            Evidence("same-id", chapter, 1,
                "A distal radius fracture requires careful neurovascular assessment and follow-up."),
            Evidence("same-id", chapter, 6,
                "The fracture line may involve the periosteum and should be described with imaging context.")
        )
        val nonCanonical = listOf(
            Evidence("book\u200Bhidden", chapter, 1,
                "A distal radius fracture requires careful neurovascular assessment and follow-up.")
        )
        for (items in listOf(duplicate, nonCanonical)) {
            val prepared = prepare(items)
            val answer = if (prepared.presentedEvidence.size > 1) {
                "A distal radius fracture requires assessment [S1]. The periosteum is described [S2]."
            } else {
                "A distal radius fracture requires assessment [S1]."
            }
            val gate = LocalAnswerGate.evaluate(answer, prepared)
            assertFalse("ambiguous source identity must fail closed", gate.accepted)
            assertNull(gate.displayableAnswer)
            assertTrue(SourceCitationLinks.resolve(answer, prepared.presentedEvidence).isEmpty())
        }
    }

    @Test fun invalidCitationNeverLeaksAnswerOrCreatesPageNavigation() {
        val prepared = prepare(evidence())
        val answer = "The distal radius fracture needs assessment [S1], but this claim invents [S9]."
        val gate = LocalAnswerGate.evaluate(answer, prepared)
        assertFalse(gate.accepted)
        assertNull(gate.displayableAnswer)
        assertTrue(SourceCitationLinks.resolve(answer, prepared.presentedEvidence).isEmpty())
    }

    @Test fun explicitEvidenceInsufficientAbstentionDoesNotInventCitationLinks() {
        val prepared = prepare(listOf(
            Evidence("bad\nid", chapter, 1,
                "A distal radius fracture requires careful neurovascular assessment and follow-up.")
        ))
        val abstention = "المعلومة مش كافية بالنص الموجود"
        val gate = LocalAnswerGate.evaluate(abstention, prepared)
        assertTrue(gate.accepted)
        assertEquals(abstention, gate.displayableAnswer)
        assertTrue(SourceCitationLinks.resolve(abstention, prepared.presentedEvidence).isEmpty())
    }
}
