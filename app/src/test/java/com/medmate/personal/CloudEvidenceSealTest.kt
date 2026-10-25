package com.medmate.personal

import com.medmate.personal.brain.*
import org.junit.Assert.*
import org.junit.Test

class CloudEvidenceSealTest {
    private val excerpt = "Osteomyelitis is an infection of bone, and the selected source discusses diagnostic findings."
    private val source = Evidence("book:3:0", "sha-chapter", 3, excerpt)
    private val request = BrainRequestIdentity("sha-chapter", "Explain osteomyelitis", true, 3)
    private val prompt = PreparedPrompt("QUESTION: Explain osteomyelitis\n[S1] $excerpt",
        listOf(source), question = request.question)

    @Test fun identicalSourceAllowsExactlyOneCloudAttempt() {
        val seal = CloudEvidenceSeal.of(prompt, request)
        assertEquals(64, seal.length)
        val grant = CloudTransferConsent.grant("https://example.org/v1/chat/completions", "model", request, seal)
        assertTrue(grant.permits("https://example.org/v1/chat/completions", "model", request, seal))
        assertTrue(grant.consume("https://example.org/v1/chat/completions", "model", request, seal))
        assertFalse(grant.consume("https://example.org/v1/chat/completions", "model", request, seal))
    }

    @Test fun changedExcerptOrQuestionCannotReuseConsent() {
        val original = CloudEvidenceSeal.of(prompt, request)
        val changed = prompt.copy(prompt = prompt.prompt.replace("infection", "inflammation"),
            presentedEvidence = listOf(source.copy(text = excerpt.replace("infection", "inflammation"))))
        val seal2 = CloudEvidenceSeal.of(changed, request)
        assertNotEquals(original, seal2)
        val grant = CloudTransferConsent.grant("https://example.org/v1/chat/completions", "model", request, original)
        assertFalse(grant.consume("https://example.org/v1/chat/completions", "model", request, seal2))
        assertFalse(grant.permits("https://example.org/v1/chat/completions", "model",
            request.copy(question = "Different question"), original))
    }

    @Test fun mismatchedPdfPageAndPromptAreRejectedBeforeNetwork() {
        assertTrue(runCatching { CloudEvidenceSeal.of(prompt.copy(
            presentedEvidence = listOf(source.copy(chapterId = "other"))), request) }.isFailure)
        assertTrue(runCatching { CloudEvidenceSeal.of(prompt.copy(
            presentedEvidence = listOf(source.copy(pageIndex = 4))), request) }.isFailure)
        assertTrue(runCatching { CloudEvidenceSeal.of(prompt.copy(question = "Different"), request) }.isFailure)
        assertTrue(runCatching { CloudEvidenceSeal.of(prompt.copy(prompt = "No PDF excerpt"), request) }.isFailure)
    }

    @Test fun unsealedLegacyGrantCannotSendPdfExcerpts() {
        val legacy = CloudTransferConsent.grant("https://example.org/v1/chat/completions", "model", request)
        val seal = CloudEvidenceSeal.of(prompt, request)
        assertFalse(legacy.consume("https://example.org/v1/chat/completions", "model", request, seal))
    }
}
