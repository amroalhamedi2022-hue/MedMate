package com.medmate.personal.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Syntax and citation-source gate only, not medical factual review. */
class CloudAnswerGateTest {
    @Test fun acceptsGroundedExplanation() {
        val answer = "الشرح حسب الفصل [S1]"
        assertEquals(answer, CitationValidator.requireGroundedCloudAnswer(answer, 1))
    }

    @Test fun acceptsExplicitAbstentionWithoutFakeCitation() {
        val answer = "المعلومة مش كافية بالنص الموجود"
        assertEquals(answer, CitationValidator.requireGroundedCloudAnswer(answer, 1))
    }

    @Test fun rejectsUncitedAndForgedSources() {
        listOf("هذا شرح بلا استشهاد", "شرح غير موثق [S99]",
            "شرح [S1] [S-1]", "المعلومة مش كافية بالنص الموجود [S1]").forEach { answer ->
            assertTrue("Must reject: $answer", runCatching {
                CitationValidator.requireGroundedCloudAnswer(answer, 1)
            }.isFailure)
        }
    }
}
