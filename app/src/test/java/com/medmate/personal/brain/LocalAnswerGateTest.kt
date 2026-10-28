package com.medmate.personal.brain

import org.junit.Assert.*
import org.junit.Test

/** Exercises the production display gate; no fake model or native inference is used. */
class LocalAnswerGateTest {
    private fun prepared(): PreparedPrompt = MedicalPromptBuilder.prepare(
        GroundedRequest(
            chapterId = "orthopedics",
            query = "Explain fracture",
            evidence = listOf(
                Evidence(
                    id = "orthopedics-page-2",
                    chapterId = "orthopedics",
                    pageIndex = 1,
                    text = "A fracture is a break in bone continuity and requires clinical assessment."
                )
            ),
            maxEvidenceChars = 500
        )
    )

    @Test fun acceptsTrimmedAnswerWithAValidPresentedSource() {
        val answer = "  A fracture is a break in bone continuity. [S1]  "
        val result = LocalAnswerGate.evaluate(answer, prepared())
        assertTrue(result.accepted)
        assertEquals("A fracture is a break in bone continuity. [S1]", result.displayableAnswer)
        assertTrue(result.citationCheck.accepted)
    }

    @Test fun rejectsMissingUnknownAndUnicodeLookalikeCitationsWithoutEchoingAnswer() {
        val source = prepared()
        val missing = "A fracture is a break in bone continuity."
        val unknown = "A fracture is a break in bone continuity. [S2]"
        val lookalike = "A fracture is a break in bone continuity. [Ｓ1]"

        listOf(missing, unknown, lookalike).forEach { answer ->
            val result = LocalAnswerGate.evaluate(answer, source)
            assertFalse(result.accepted)
            assertNull("rejected model text must never be exposed for display", result.displayableAnswer)
            assertFalse(result.citationCheck.accepted)
        }
    }

    @Test fun rejectsCitationOnlyOutputAndAcceptsExplicitSourceInsufficientAbstention() {
        val source = prepared()
        val markerOnly = LocalAnswerGate.evaluate("[S1]", source)
        assertFalse(markerOnly.accepted)
        assertNull(markerOnly.displayableAnswer)

        val abstention = "المعلومة مش كافية بالنص الموجود"
        val result = LocalAnswerGate.evaluate(abstention, source)
        assertTrue(result.accepted)
        assertEquals(abstention, result.displayableAnswer)
        assertTrue(result.citationCheck.accepted)
    }

    @Test fun explanationGateRejectsStudyDraftPromptTask() {
        val draftTask = StudyDraftPrompt.prepare(prepared())
        assertEquals(BrainTask.STUDY_DRAFTS, draftTask.task)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            LocalAnswerGate.evaluate("A fracture is a break in bone continuity. [S1]", draftTask)
        }
    }
}
