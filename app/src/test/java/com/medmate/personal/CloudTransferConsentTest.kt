package com.medmate.personal

import com.medmate.personal.brain.BrainRequestIdentity
import com.medmate.personal.brain.CloudTransferConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    @Test fun approvalIsOneShotAndCannotBeReplayed() {
        val consent = CloudTransferConsent.grant(endpoint, "local-model", source)
        assertTrue(consent.permits(endpoint, "local-model", source))
        assertTrue(consent.consume(endpoint, "local-model", source))
        assertFalse(consent.permits(endpoint, "local-model", source))
        assertFalse(consent.consume(endpoint, "local-model", source))
    }

    @Test fun mismatchedClaimDoesNotConsumeApproval() {
        val consent = CloudTransferConsent.grant(endpoint, "local-model", source)
        assertFalse(consent.consume(endpoint, "other-model", source))
        assertFalse(consent.consume(endpoint, "local-model", source.copy(sourcePage = 5)))
        assertFalse(consent.consume("https://other.example/v1/chat/completions", "local-model", source))
        assertTrue(consent.consume(endpoint, "local-model", source))
    }

    @Test fun concurrentEvidenceSealedClaimsCannotDuplicatePdfUploadPermission() {
        val seal = "a".repeat(64)
        val consent = CloudTransferConsent.grant(endpoint, "local-model", source, seal)
        val start = CountDownLatch(1)
        val finished = CountDownLatch(16)
        val claims = AtomicInteger(0)
        repeat(16) {
            Thread {
                try {
                    start.await()
                    if (consent.consume(endpoint, "local-model", source, seal)) claims.incrementAndGet()
                } finally { finished.countDown() }
            }.start()
        }
        start.countDown()
        assertTrue("workers timed out", finished.await(5, TimeUnit.SECONDS))
        assertEquals("exactly one evidence-sealed PDF upload permission", 1, claims.get())
    }

    @Test fun revokingRetainedApprovalPreventsLaterOutboundClaim() {
        val seal = "c".repeat(64)
        val consent = CloudTransferConsent.grant(endpoint, "local-model", source, seal)
        assertTrue(consent.permits(endpoint, "local-model", source, seal))
        consent.revoke()
        assertFalse("revoked retained reference cannot preview", consent.permits(endpoint, "local-model", source, seal))
        assertFalse("revoked retained reference cannot claim outbound transfer", consent.consume(endpoint, "local-model", source, seal))
    }

    @Test fun evidenceSealedApprovalExpiresAfterFiveMinutes() {
        var now = 1_000_000L
        val seal = "b".repeat(64)
        val consent = CloudTransferConsent.grantForTest(endpoint, "local-model", source, seal) { now }
        assertTrue(consent.permits(endpoint, "local-model", source, seal))
        now += (CloudTransferConsent.GRANT_TTL_MILLIS - 1L) * 1_000_000L
        assertTrue("grant remains valid just before expiry", consent.permits(endpoint, "local-model", source, seal))
        now += 1_000_000L
        assertFalse("grant expires at five minutes", consent.permits(endpoint, "local-model", source, seal))
        assertFalse("expired grant cannot reach the network boundary", consent.consume(endpoint, "local-model", source, seal))
    }

    @Test fun concurrentClaimsCannotDuplicatePdfUploadPermission() {
        val consent = CloudTransferConsent.grant(endpoint, "local-model", source)
        val start = CountDownLatch(1)
        val finished = CountDownLatch(16)
        val claims = AtomicInteger(0)
        repeat(16) {
            Thread {
                try {
                    start.await()
                    if (consent.consume(endpoint, "local-model", source)) claims.incrementAndGet()
                } finally { finished.countDown() }
            }.start()
        }
        start.countDown()
        assertTrue("workers timed out", finished.await(5, TimeUnit.SECONDS))
        assertEquals("exactly one upload permission", 1, claims.get())
    }
}
