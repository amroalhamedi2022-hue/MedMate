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

    @Test fun identityDiscardsOldQuestionAndPage() {
        val captured = BrainRequestIdentity("pdf-sha", "fracture", true, 1)
        assertTrue(captured.matches("pdf-sha", " fracture ", true, 1))
        assertFalse(captured.matches("pdf-sha", "dislocation", true, 1))
        assertFalse(captured.matches("pdf-sha", "fracture", true, 2))
        assertFalse(captured.matches("other-pdf", "fracture", true, 1))
    }
}
