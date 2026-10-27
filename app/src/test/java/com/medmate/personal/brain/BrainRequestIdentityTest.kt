package com.medmate.personal.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainRequestIdentityTest {
    @Test
    fun capturedAndCurrentQuestionUseTheSameWhitespaceNormalization() {
        val identity = BrainRequestIdentity(
            chapterSha256 = "chapter-sha",
            question = "  What causes edema?  ",
            pageOnly = false,
            sourcePage = -1
        )
        assertTrue(identity.matches("chapter-sha", "What causes edema?", false, 0))
        assertTrue(identity.matches("chapter-sha", "\nWhat causes edema?\t", false, 4))
        assertFalse(identity.matches("chapter-sha", "What causes edema today?", false, 0))
    }

    @Test
    fun changedChapterAlwaysInvalidatesTheCapturedRequest() {
        val identity = BrainRequestIdentity("chapter-a", "Explain edema", false, -1)
        assertFalse(identity.matches("chapter-b", "Explain edema", false, 0))
    }

    @Test
    fun pageScopedRequestRejectsModeOrPageChanges() {
        val identity = BrainRequestIdentity("chapter-a", "Explain edema", true, 1)
        assertTrue(identity.matches("chapter-a", "Explain edema", true, 1))
        assertFalse(identity.matches("chapter-a", "Explain edema", false, 1))
        assertFalse(identity.matches("chapter-a", "Explain edema", true, 2))
    }

    @Test
    fun fullChapterRequestDoesNotBecomeStaleWhenViewportPageChanges() {
        val identity = BrainRequestIdentity("chapter-a", "Explain edema", false, -1)
        assertTrue(identity.matches("chapter-a", "Explain edema", false, 1))
        assertTrue(identity.matches("chapter-a", "Explain edema", false, 25))
    }
}
