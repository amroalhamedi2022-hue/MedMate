package com.medmate.personal.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure reporting tests; these DO NOT run a real GGUF model or Android hardware. */
class LocalInferenceAuditTest {
    private fun prompt(vararg sources: Evidence) = PreparedPrompt(
        prompt = "Explain from source", presentedEvidence = sources.toList(),
        task = BrainTask.EXPLAIN, question = "fracture")
    private val source = Evidence("orthopedics:1:0", "orthopedics", 1,
        "A fracture is a break in the continuity of bone tissue.")

    @Test fun validNativeResponseProducesPageBoundDiagnostic() {
        val report = LocalInferenceAudit.fromNativeResponse(
            "tiny-local.gguf", 1423L, prompt(source),
            "النص يعرّف الكسر بأنه انقطاع في استمرارية العظم [S1].")
        assertEquals("tiny-local.gguf", report.modelName)
        assertEquals(1423L, report.elapsedMs)
        assertEquals(listOf(2), report.sourcePages)
        assertTrue(report.syntaxPass)
        assertEquals(setOf("S1"), report.citationCheck.used)
    }

    @Test fun forgedCitationNeverCountsAsPassingNativeDiagnostic() {
        val report = LocalInferenceAudit.fromNativeResponse(
            "local.gguf", 10, prompt(source), "تعريف للكسر [S1]، ودليل مزيف [S9].")
        assertFalse(report.syntaxPass)
        assertTrue("S9" in report.citationCheck.invalid)
    }

    @Test fun missingNativeSessionOrMixedPdfEvidenceRejected() {
        val crossChapter = source.copy(id = "other:0:0", chapterId = "other", pageIndex = 0)
        for (badModel in listOf<String?>(null, "", "  ")) {
            try {
                LocalInferenceAudit.fromNativeResponse(badModel, 0, prompt(source), "شرح [S1]")
                throw AssertionError("Expected native session rejection")
            } catch (_: IllegalArgumentException) { }
        }
        try {
            LocalInferenceAudit.fromNativeResponse("local.gguf", 1,
                prompt(source, crossChapter), "شرح [S1]")
            throw AssertionError("Expected cross-chapter rejection")
        } catch (_: IllegalArgumentException) { }
        try {
            LocalInferenceAudit.fromNativeResponse("local.gguf", -1,
                prompt(source), "شرح [S1]")
            throw AssertionError("Expected invalid clock rejection")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun ambiguousOrEmptyPageIdentityCannotPassNativeDiagnostic() {
        val duplicateId = listOf(
            source.copy(id = "same-id"),
            source.copy(id = "same-id", pageIndex = 6, text = source.text + " Follow-up notes.")
        )
        val invisibleId = listOf(source.copy(id = "book\u200Bhidden"))
        val blankText = listOf(source.copy(text = ""))

        for (sources in listOf(duplicateId, invisibleId, blankText)) {
            val report = LocalInferenceAudit.fromNativeResponse(
                "local.gguf", 5L, prompt(*sources.toTypedArray()),
                "A fracture is a break in bone continuity [S1]."
            )
            assertFalse("unsafe source identity must fail the diagnostic gate", report.syntaxPass)
            assertEquals(emptyList<Int>(), report.sourcePages)
            assertEquals("Invalid or ambiguous PDF source identity", report.citationCheck.reason)
        }
    }

    @Test fun sourceFreeAbstentionDoesNotClaimUnsafePages() {
        val report = LocalInferenceAudit.fromNativeResponse(
            "local.gguf", 5L, prompt(source.copy(id = "bad\nid")),
            "المعلومة مش كافية بالنص الموجود"
        )
        assertTrue(report.syntaxPass)
        assertTrue(report.sourcePages.isEmpty())
        assertTrue(report.citationCheck.used.isEmpty())
    }

    @Test fun identityDiscardsOldQuestionAndPage() {
        val captured = BrainRequestIdentity("pdf-sha", "fracture", true, 1)
        assertTrue(captured.matches("pdf-sha", " fracture ", true, 1))
        assertFalse(captured.matches("pdf-sha", "dislocation", true, 1))
        assertFalse(captured.matches("pdf-sha", "fracture", true, 2))
        assertFalse(captured.matches("other-pdf", "fracture", true, 1))
    }

    @Test fun localAnswerGateDisplaysOnlyCitationAcceptedResponses() {
        val prepared = prompt(source)
        val valid = LocalAnswerGate.evaluate(
            "A fracture is a break in bone continuity [S1].", prepared)
        assertTrue(valid.accepted)
        assertEquals("A fracture is a break in bone continuity [S1].", valid.displayableAnswer)
        assertTrue(valid.citationCheck.accepted)

        val missing = LocalAnswerGate.evaluate(
            "A fracture is a break in bone continuity.", prepared)
        assertFalse(missing.accepted)
        assertEquals(null, missing.displayableAnswer)
        assertEquals("No source citation", missing.citationCheck.reason)

        val forged = LocalAnswerGate.evaluate(
            "A fracture is a break in bone continuity [S1], with a false extra claim [S9].", prepared)
        assertFalse(forged.accepted)
        assertEquals(null, forged.displayableAnswer)
        assertTrue("S9" in forged.citationCheck.invalid)

        // The approved explicit abstention is source-free and may be displayed.
        val abstention = LocalAnswerGate.evaluate(
            "المعلومة مش كافية بالنص الموجود.", prepared)
        assertTrue(abstention.accepted)
        assertEquals("المعلومة مش كافية بالنص الموجود.", abstention.displayableAnswer)
        assertTrue(abstention.citationCheck.used.isEmpty())
    }

}
