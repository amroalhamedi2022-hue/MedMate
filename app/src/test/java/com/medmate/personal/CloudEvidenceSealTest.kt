package com.medmate.personal

import com.medmate.personal.brain.*
import org.junit.Assert.*
import org.junit.Test

class CloudEvidenceSealTest {
    private val excerpt = "Osteomyelitis is an infection of bone, and the selected source discusses diagnostic findings."
    private val source = Evidence("book:3:0", "sha-chapter", 3, excerpt)
    private val request = BrainRequestIdentity("sha-chapter", "Explain osteomyelitis", true, 3)
    private val prompt = MedicalPromptBuilder.prepare(
        GroundedRequest(request.chapterSha256, request.question, listOf(source), maxEvidenceChars = 2400)
    )

    @Test fun identicalSourceAllowsExactlyOneCloudAttempt() {
        val seal = CloudEvidenceSeal.of(prompt, request)
        assertEquals(64, seal.length)
        val grant = CloudTransferConsent.grant("https://example.org/v1/chat/completions", "model", request, seal)
        assertTrue(grant.permits("https://example.org/v1/chat/completions", "model", request, seal))
        assertTrue(grant.consume("https://example.org/v1/chat/completions", "model", request, seal))
        assertFalse(grant.consume("https://example.org/v1/chat/completions", "model", request, seal))
    }

    @Test fun visiblePromptQuestionCannotDriftBehindMatchingMetadata() {
        val altered = prompt.copy(
            prompt = prompt.prompt.replace("Explain osteomyelitis", "Explain a different diagnosis")
        )
        assertEquals("metadata still carries the approved question", request.question, altered.question)
        assertTrue("the visible outbound question must be bound to the approved identity",
            runCatching { CloudEvidenceSeal.of(altered, request) }.isFailure)
    }

    @Test fun questionSanitizationIsSharedByPromptAndConsentValidation() {
        val noisy = request.copy(question = "Explain\tosteomyelitis\u200B")
        val prepared = MedicalPromptBuilder.prepare(
            GroundedRequest(noisy.chapterSha256, noisy.question, listOf(source), maxEvidenceChars = 2400)
        )
        assertEquals("Explain osteomyelitis", MedicalPromptBuilder.sanitizeQuestion(noisy.question))
        assertTrue(runCatching { CloudEvidenceSeal.of(prepared, noisy) }.isSuccess)
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

    @Test fun unreviewedPromptAdditionCannotBeSealedForCloudTransfer() {
        val approved = CloudEvidenceSeal.of(prompt, request)
        val altered = prompt.copy(prompt = prompt.prompt +
            "\nPRIVATE ADDITION: unrelated confidential notes")
        // The excerpt list, question and PDF page are intentionally identical,
        // but the added text is outside the reviewed source preview and must fail closed.
        assertEquals(prompt.presentedEvidence, altered.presentedEvidence)
        assertTrue(runCatching { CloudEvidenceSeal.of(altered, request) }.isFailure)
        val grant = CloudTransferConsent.grant(
            "https://example.org/v1/chat/completions", "model", request, approved)
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

    @Test fun reorderedOrRelabeledPromptSourcesCannotBeSealed() {
        val fullChapterRequest = request.copy(pageOnly = false, sourcePage = -1)
        val secondText = "The synthetic second excerpt describes a separate test-only page and its distinct evidence marker."
        val second = Evidence("book:4:0", "sha-chapter", 4, secondText)
        val multi = MedicalPromptBuilder.prepare(
            GroundedRequest(fullChapterRequest.chapterSha256, fullChapterRequest.question,
                listOf(source, second), maxEvidenceChars = 2400)
        )
        val firstBlock = "[S1] Page 4 (book:3:0):\n${source.text}"
        val secondBlock = "[S2] Page 5 (book:4:0):\n$secondText"
        val orderedBlocks = "$firstBlock\n\n$secondBlock"
        assertTrue("fixture must contain two consecutive source blocks", multi.prompt.contains(orderedBlocks))

        val reordered = multi.copy(prompt = multi.prompt.replace(orderedBlocks, "$secondBlock\n\n$firstBlock"))
        assertTrue("reordered prompt blocks must not remap S1/S2 to different pages",
            runCatching { CloudEvidenceSeal.of(reordered, fullChapterRequest) }.isFailure)

        val relabeled = multi.copy(prompt = multi.prompt.replace(
            "[S1] Page 4 (book:3:0):", "[S1] Page 5 (book:3:0):"))
        assertTrue("a source marker with a changed page label must fail closed",
            runCatching { CloudEvidenceSeal.of(relabeled, fullChapterRequest) }.isFailure)
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
            "${source.id}\n",
            "${source.id}\u200B",
            "book\uFF1A3\uFF1A0",
            "b\u043eok:3:0",
            "x".repeat(161)
        )
        invalidIds.forEach { id ->
            val malformed = prompt.copy(presentedEvidence = listOf(source.copy(id = id)))
            assertTrue("non-canonical source ID must not be sealed: " + id.take(24),
                runCatching { CloudEvidenceSeal.of(malformed, request) }.isFailure)
        }
    }

}
