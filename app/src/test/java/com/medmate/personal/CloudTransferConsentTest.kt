package com.medmate.personal

import com.medmate.personal.brain.BrainRequestIdentity
import com.medmate.personal.brain.CloudTransferConsent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Production consent gate; no fake model and no network requests. */
class CloudTransferConsentTest {
    private val source = BrainRequestIdentity("chapter", "Explain the fracture", true, 4)
    private val endpoint = "https://example.org/v1/chat/completions"

    @Test fun exactApprovalIsAccepted() {
        val consent = CloudTransferConsent.grant(endpoint, "local-model", source)
        assertTrue(consent.permits(endpoint, "local-model", source))
    }

    @Test fun changingDestinationOrModelIsRejected() {
        val consent = CloudTransferConsent.grant(endpoint, "local-model", source)
        assertFalse(consent.permits("https://other.example/v1/chat/completions", "local-model", source))
        assertFalse(consent.permits(endpoint, "other-model", source))
        assertFalse(consent.permits("http://example.org/v1/chat/completions", "local-model", source))
    }

    @Test fun changingQuestionChapterOrPageIsRejected() {
        val consent = CloudTransferConsent.grant(endpoint, "local-model", source)
        assertFalse(consent.permits(endpoint, "local-model", source.copy(question = "changed")))
        assertFalse(consent.permits(endpoint, "local-model", source.copy(chapterSha256 = "changed")))
        assertFalse(consent.permits(endpoint, "local-model", source.copy(sourcePage = 9)))
    }

    @Test fun invalidGrantCannotBeIssued() {
        assertTrue(runCatching { CloudTransferConsent.grant(endpoint, "", source) }.isFailure)
        assertTrue(runCatching { CloudTransferConsent.grant(endpoint + "?key=leak", "local-model", source) }.isFailure)
        assertTrue(runCatching { CloudTransferConsent.grant(endpoint, "local-model", source.copy(question = "")) }.isFailure)
    }
}
