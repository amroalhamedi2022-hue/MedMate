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

    @Test fun changingOnlyPromptTextInvalidatesPreviouslyApprovedTransfer() {
        val approved = CloudEvidenceSeal.of(prompt, request)
        val altered = prompt.copy(prompt = prompt.prompt +
            "\nPRIVATE ADDITION: unrelated confidential notes")
        // The excerpt list, question and PDF page are intentionally identical.
        assertEquals(prompt.presentedEvidence, altered.presentedEvidence)
        assertNotEquals(approved, CloudEvidenceSeal.of(altered, request))
        val grant = CloudTransferConsent.grant(
            "https://example.org/v1/chat/completions", "model", request, approved)
        assertFalse(grant.consume("https://example.org/v1/chat/completions", "model",
            request, CloudEvidenceSeal.of(altered, request)))
        assertTrue(grant.consume("https://example.org/v1/chat/completions", "model",
            request, approved))
    }

    @Test fun draftMessageUsesSameReviewedSourcesWithoutReapproving() {
        val approved = CloudEvidenceSeal.of(prompt, request)
        val draft = StudyDraftPrompt.prepare(prompt)
        assertEquals(prompt.presentedEvidence, draft.presentedEvidence)
        assertTrue(draft.prompt.contains(excerpt))
        assertNotEquals(prompt.prompt, draft.prompt)
        // CloudBrainGateway seals original PreparedPrompt and transmits only
        // its deterministic study-draft rendering, never an unrelated excerpt.
        val grant = CloudTransferConsent.grant(
            "https://example.org/v1/chat/completions", "model", request, approved)
        assertTrue(grant.consume("https://example.org/v1/chat/completions", "model",
            request, CloudEvidenceSeal.of(prompt, request)))
        assertFalse(grant.permits("https://example.org/v1/chat/completions", "model",
            request, approved))
    }

    @Test fun mismatchedPdfPageAndPromptAreRejectedBeforeNetwork() {
        assertTrue(runCatching { CloudEvidenceSeal.of(prompt.copy(
            presentedEvidence = listOf(source.copy(chapterId = "other"))), request) }.isFailure)
        assertTrue(runCatching { CloudEvidenceSeal.of(prompt.copy(
            presentedEvidence = listOf(source.copy(pageIndex = 4))), request) }.isFailure)
        assertTrue(runCatching { CloudEvidenceSeal.of(prompt.copy(question = "Different"), request) }.isFailure)
        assertTrue(runCatching { CloudEvidenceSeal.of(prompt.copy(prompt = "No PDF excerpt"), request) }.isFailure)
    }

    @Test fun blankOrDuplicateEvidenceIdsCannotBeSealedForCloudTransfer() {
        val duplicateIdentity = prompt.copy(
            presentedEvidence = listOf(source, source.copy())
        )
        assertTrue("duplicate citation IDs must fail before a cloud grant is created",
            runCatching { CloudEvidenceSeal.of(duplicateIdentity, request) }.isFailure)

        val blankIdentity = prompt.copy(
            presentedEvidence = listOf(source.copy(id = ""))
        )
        assertTrue("blank citation IDs must fail before a cloud grant is created",
            runCatching { CloudEvidenceSeal.of(blankIdentity, request) }.isFailure)
    }

    @Test fun unsealedLegacyGrantCannotSendPdfExcerpts() {
        val legacy = CloudTransferConsent.grant("https://example.org/v1/chat/completions", "model", request)
        val seal = CloudEvidenceSeal.of(prompt, request)
        assertFalse(legacy.consume("https://example.org/v1/chat/completions", "model", request, seal))
    }

    @Test fun paddedInvisibleOrOverlongEvidenceIdsFailClosed() {
        val invalidIds = listOf(
            " ${source.id}",
            "${source.id} ",
            "${source.id}\\n",
            "${source.id}\\u200B",
            "x".repeat(161)
        )
        invalidIds.forEach { id ->
            val malformed = prompt.copy(presentedEvidence = listOf(source.copy(id = id)))
            assertTrue("non-canonical source ID must not be sealed: " + id.take(24),
                runCatching { CloudEvidenceSeal.of(malformed, request) }.isFailure)
        }
    }

}
