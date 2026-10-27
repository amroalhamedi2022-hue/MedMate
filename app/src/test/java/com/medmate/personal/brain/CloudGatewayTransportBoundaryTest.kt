package com.medmate.personal.brain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.json.JSONObject

/** Exercises the production HTTPS outbound boundary using an in-memory
 * connection, NOT a live endpoint. No user PDF, token or external network.
 * Does not verify actual HTTPS/TLS on a physical device. */
@RunWith(RobolectricTestRunner::class)
class CloudGatewayTransportBoundaryTest {
    private val endpoint = "https://example.org/v1/chat/completions"
    private val model = "private-relay"
    private val request = BrainRequestIdentity("synthetic-chapter", "Explain this synthetic passage", true, 2)
    private val excerpt = "The synthetic sample describes a test-only bone illustration and contains no patient information."
    private val prepared = MedicalPromptBuilder.prepare(
        GroundedRequest("synthetic-chapter", request.question,
            listOf(Evidence("synthetic-chapter:2:0", "synthetic-chapter", 2, excerpt)))
    )

    private class FakeHttpsConnection(
        url: URL,
        private val body: String = """{"choices":[{"message":{"content":"هذا شرح من المصدر [S1]"}}]}""",
        private val responseStream: InputStream? = null,
        private val responseCode: Int = 200,
        private val failOutput: Boolean = false,
        private val failRequestProperty: Boolean = false,
        private val responseContentType: String? = "application/json; charset=utf-8"
    ) : HttpURLConnection(url) {
        val requestBytes = ByteArrayOutputStream()
        var disconnected = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun setRequestProperty(key: String, value: String) {
            if (failRequestProperty) throw java.io.IOException("synthetic request setup failure")
            super.setRequestProperty(key, value)
        }
        override fun getOutputStream(): ByteArrayOutputStream {
            if (failOutput) throw java.io.IOException("synthetic write failure")
            return requestBytes
        }
        override fun getResponseCode() = responseCode
        override fun getContentType(): String? = responseContentType
        override fun getInputStream(): InputStream = responseStream ?: ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    }

    @Test fun approvedRequestWritesOnlyReviewedSyntheticContextOnce() = runBlocking {
        val opened = mutableListOf<FakeHttpsConnection>()
        val gateway = CloudBrainGateway { url ->
            FakeHttpsConnection(url).also(opened::add)
        }
        val seal = CloudEvidenceSeal.of(prepared, request)
        val consent = CloudTransferConsent.grant(endpoint, model, request, seal)
        val answer = gateway.explain(endpoint, model, "SESSION-TOKEN", prepared, consent, request)
        assertTrue(answer.contains("[S1]"))
        assertEquals(1, opened.size)
        val connection = opened.single()
        assertTrue(connection.disconnected)
        assertEquals("POST", connection.requestMethod)
        assertFalse("cloud gateway must never follow redirects with a PDF prompt or bearer token", connection.instanceFollowRedirects)
        assertFalse("private PDF prompts must not use URLConnection caches", connection.useCaches)
        assertEquals("no-store, no-cache, max-age=0", connection.getRequestProperty("Cache-Control"))
        assertEquals("no-cache", connection.getRequestProperty("Pragma"))
        assertEquals("Bearer SESSION-TOKEN", connection.getRequestProperty("Authorization"))
        val json = JSONObject(connection.requestBytes.toString("UTF-8"))
        assertEquals(model, json.getString("model"))
        assertEquals(false, json.getBoolean("stream"))
        val messages = json.getJSONArray("messages")
        assertEquals(2, messages.length())
        val transmitted = messages.getJSONObject(1).getString("content")
        assertEquals(prepared.prompt, transmitted)
        assertTrue(transmitted.contains(excerpt))
        assertFalse(transmitted.contains("PRIVATE PATIENT RECORD"))
        assertTrue(runCatching {
            gateway.explain(endpoint, model, "SESSION-TOKEN", prepared, consent, request)
        }.isFailure)
        assertEquals(1, opened.size)
    }


    @Test fun nonHttpsAndCredentialBearingDestinationsCannotMintConsent() {
        val seal = CloudEvidenceSeal.of(prepared, request)
        val invalidEndpoints = listOf(
            "http://example.org/v1/chat/completions",
            "https://user:secret@example.org/v1/chat/completions",
            "https://example.org/v1/chat/completions?token=secret",
            "https://example.org/not-the-chat-endpoint"
        )
        invalidEndpoints.forEach { candidate ->
            assertTrue("unsafe endpoint must be rejected before any socket: $candidate", runCatching {
                CloudTransferConsent.grant(candidate, model, request, seal)
            }.isFailure)
        }
    }

    @Test fun changedPdfOrPromptAndWrongDestinationNeverOpenConnection() = runBlocking {
        var sockets = 0
        val gateway = CloudBrainGateway { url ->
            sockets++
            FakeHttpsConnection(url)
        }
        val seal = CloudEvidenceSeal.of(prepared, request)
        val consent = CloudTransferConsent.grant(endpoint, model, request, seal)
        val changedPrompt = prepared.copy(prompt = prepared.prompt + "\\nPRIVATE PATIENT RECORD")
        assertTrue(runCatching {
            gateway.explain(endpoint, model, "", changedPrompt, consent, request)
        }.isFailure)
        val wrongPage = request.copy(sourcePage = 3)
        assertTrue(runCatching {
            gateway.explain(endpoint, model, "", prepared, consent, wrongPage)
        }.isFailure)
        assertTrue(runCatching {
            gateway.explain("https://different.example/v1/chat/completions",
                model, "", prepared, consent, request)
        }.isFailure)
        assertTrue(runCatching {
            gateway.explain(endpoint, "another-model", "", prepared, consent, request)
        }.isFailure)
        assertEquals(0, sockets)
        // Rejected mismatches do not silently consume a valid explicit grant.
        assertTrue(consent.permits(endpoint, model, request, seal))
    }

    @Test fun malformedCredentialAndLegacyGrantFailBeforeTransport() = runBlocking {
        var sockets = 0
        val gateway = CloudBrainGateway { url ->
            sockets++
            FakeHttpsConnection(url)
        }
        val seal = CloudEvidenceSeal.of(prepared, request)
        val consent = CloudTransferConsent.grant(endpoint, model, request, seal)
        assertTrue(runCatching {
            gateway.explain(endpoint, model, "bad\\nAuthorization: leaked", prepared, consent, request)
        }.isFailure)
        val legacy = CloudTransferConsent.grant(endpoint, model, request)
        assertTrue(runCatching {
            gateway.explain(endpoint, model, "", prepared, legacy, request)
        }.isFailure)
        assertEquals(0, sockets)
        assertTrue(consent.permits(endpoint, model, request, seal))
    }

    @Test fun forgedResponseIsRejectedAndCannotReplayConsumedApproval() = runBlocking {
        var sockets = 0
        val gateway = CloudBrainGateway { url ->
            sockets++
            FakeHttpsConnection(url,
                """{"choices":[{"message":{"content":"شرح بلا استشهاد PDF"}}]}""")
        }
        val consent = CloudTransferConsent.grant(
            endpoint, model, request, CloudEvidenceSeal.of(prepared, request))
        assertTrue(runCatching {
            gateway.explain(endpoint, model, "", prepared, consent, request)
        }.isFailure)
        assertEquals(1, sockets)
        assertTrue(runCatching {
            gateway.explain(endpoint, model, "", prepared, consent, request)
        }.isFailure)
        assertEquals(1, sockets)
    }

    @Test fun stalledResponseStreamFailsClosedAndDisconnects() = runBlocking {
        var reads = 0
        val stalled = object : InputStream() {
            override fun read(): Int = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                reads++
                return 0
            }
        }
        val opened = mutableListOf<FakeHttpsConnection>()
        val gateway = CloudBrainGateway { url ->
            FakeHttpsConnection(url, responseStream = stalled).also(opened::add)
        }
        val consent = CloudTransferConsent.grant(
            endpoint, model, request, CloudEvidenceSeal.of(prepared, request))
        val failure = runCatching {
            gateway.explain(endpoint, model, "", prepared, consent, request)
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(9, reads)
        assertEquals(1, opened.size)
        assertTrue(opened.single().disconnected)
        assertFalse(consent.permits(endpoint, model, request, CloudEvidenceSeal.of(prepared, request)))
    }

    @Test fun temporaryEmptyReadsRecoverWithoutDroppingValidGroundedAnswer() = runBlocking {
        val valid = """{"choices":[{"message":{"content":"شرح اصطناعي من المصدر [S1]"}}]}"""
        val payload = ByteArrayInputStream(valid.toByteArray(Charsets.UTF_8))
        var reads = 0
        val paused = object : InputStream() {
            override fun read(): Int = payload.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                reads++
                return if (reads <= 3) 0 else payload.read(buffer, offset, length)
            }
        }
        val opened = mutableListOf<FakeHttpsConnection>()
        val gateway = CloudBrainGateway { url ->
            FakeHttpsConnection(url, responseStream = paused).also(opened::add)
        }
        val consent = CloudTransferConsent.grant(
            endpoint, model, request, CloudEvidenceSeal.of(prepared, request))
        val answer = gateway.explain(endpoint, model, "", prepared, consent, request)
        assertTrue(answer.contains("[S1]"))
        assertTrue(reads >= 4)
        assertTrue(opened.single().disconnected)
    }

    @Test fun controlHeavyJsonExpansionIsRejectedBeforeSocketAndKeepsConsentUnused() = runBlocking {
        var sockets = 0
        val gateway = CloudBrainGateway { url ->
            sockets++
            FakeHttpsConnection(url)
        }
        // The prompt is within the character limit, but JSON escapes each NUL
        // as six ASCII bytes. This guards the actual serialized request size.
        val largePrepared = prepared.copy(prompt = "\u0000".repeat(17_000) + "\n" + prepared.prompt)
        assertTrue(largePrepared.prompt.length in 1..32_000)
        assertTrue(MedicalPromptBuilder.promptMatchesQuestion(largePrepared.prompt, request.question))
        val seal = CloudEvidenceSeal.of(largePrepared, request)
        val consent = CloudTransferConsent.grant(endpoint, model, request, seal)
        val failure = runCatching {
            gateway.explain(endpoint, model, "", largePrepared, consent, request)
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure.message.orEmpty().contains("تجاوز الحد الآمن"))
        assertEquals(0, sockets)
        assertTrue(consent.permits(endpoint, model, request, seal))
    }

    @Test fun redirectResponseIsRejectedAndNeverFollowed() = runBlocking {
        val opened = mutableListOf<FakeHttpsConnection>()
        val gateway = CloudBrainGateway { url ->
            FakeHttpsConnection(url, responseCode = 302).also(opened::add)
        }
        val seal = CloudEvidenceSeal.of(prepared, request)
        val consent = CloudTransferConsent.grant(endpoint, model, request, seal)
        assertTrue(runCatching {
            gateway.explain(endpoint, model, "", prepared, consent, request)
        }.isFailure)
        assertEquals(1, opened.size)
        assertFalse(opened.single().instanceFollowRedirects)
        assertTrue(opened.single().disconnected)
        assertFalse(consent.permits(endpoint, model, request, seal))
    }


    @Test fun nonJsonResponseIsRejectedBeforeBodyParsingAndCannotReplayConsent() = runBlocking {
        val opened = mutableListOf<FakeHttpsConnection>()
        val gateway = CloudBrainGateway { url ->
            FakeHttpsConnection(url, responseContentType = "text/html; charset=utf-8").also(opened::add)
        }
        val seal = CloudEvidenceSeal.of(prepared, request)
        val consent = CloudTransferConsent.grant(endpoint, model, request, seal)
        val failure = runCatching {
            gateway.explain(endpoint, model, "", prepared, consent, request)
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("غير JSON"))
        assertEquals(1, opened.size)
        assertTrue(opened.single().disconnected)
        assertFalse(consent.permits(endpoint, model, request, seal))
        assertTrue(runCatching { gateway.explain(endpoint, model, "", prepared, consent, request) }.isFailure)
        assertEquals("a rejected response must not permit a replay", 1, opened.size)
    }

    @Test fun malformedUtf8ResponseIsRejectedWithoutSilentMedicalTextReplacement() = runBlocking {
        val opened = mutableListOf<FakeHttpsConnection>()
        val prefix = "{\"choices\":[{\"message\":{\"content\":\"source ".toByteArray(Charsets.UTF_8)
        val suffix = " [S1]\"}}]}".toByteArray(Charsets.UTF_8)
        // Invalid UTF-8 bytes inside otherwise JSON-shaped response text. A
        // permissive decoder would silently replace them and keep parsing.
        val malformed = prefix + byteArrayOf(0xC3.toByte(), 0x28.toByte()) + suffix
        val gateway = CloudBrainGateway { url ->
            FakeHttpsConnection(url, responseStream = ByteArrayInputStream(malformed)).also(opened::add)
        }
        val seal = CloudEvidenceSeal.of(prepared, request)
        val consent = CloudTransferConsent.grant(endpoint, model, request, seal)
        val failure = runCatching {
            gateway.explain(endpoint, model, "SESSION-TOKEN", prepared, consent, request)
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("UTF-8"))
        assertEquals(1, opened.size)
        assertTrue(opened.single().disconnected)
        assertFalse("a malformed response consumes the one-shot grant", consent.permits(endpoint, model, request, seal))
        assertTrue(runCatching {
            gateway.explain(endpoint, model, "SESSION-TOKEN", prepared, consent, request)
        }.isFailure)
        assertEquals(1, opened.size)
    }

    @Test fun vendorJsonMediaTypeRemainsCompatible() = runBlocking {
        val opened = mutableListOf<FakeHttpsConnection>()
        val gateway = CloudBrainGateway { url ->
            FakeHttpsConnection(url, responseContentType = "application/vnd.example+json; charset=utf-8").also(opened::add)
        }
        val seal = CloudEvidenceSeal.of(prepared, request)
        val consent = CloudTransferConsent.grant(endpoint, model, request, seal)
        val answer = gateway.explain(endpoint, model, "", prepared, consent, request)
        assertTrue(answer.contains("[S1]"))
        assertTrue(opened.single().disconnected)
        assertFalse(opened.single().useCaches)
    }

    @Test fun oversizedCloudResponseIsRejectedAndApprovalCannotBeReplayed() = runBlocking {
        val opened = mutableListOf<FakeHttpsConnection>()
        val gateway = CloudBrainGateway { url ->
            FakeHttpsConnection(url, body = "X".repeat(200_001)).also(opened::add)
        }
        val consent = CloudTransferConsent.grant(
            endpoint, model, request, CloudEvidenceSeal.of(prepared, request))
        assertTrue(runCatching {
            gateway.explain(endpoint, model, "", prepared, consent, request)
        }.isFailure)
        assertEquals(1, opened.size)
        assertTrue(opened.single().disconnected)
        assertTrue(runCatching {
            gateway.explain(endpoint, model, "", prepared, consent, request)
        }.isFailure)
        assertEquals(1, opened.size)
    }

    @Test fun oneShotConsentAllowsAtMostOneConcurrentConsumer() {
        val seal = CloudEvidenceSeal.of(prepared, request)
        val consent = CloudTransferConsent.grant(endpoint, model, request, seal)
        val ready = java.util.concurrent.CountDownLatch(16)
        val start = java.util.concurrent.CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        val workers = (0 until 16).map {
            Thread {
                ready.countDown()
                if (start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    results.add(consent.consume(endpoint, model, request, seal))
                }
            }.apply { start() }
        }
        assertTrue("workers did not reach the barrier", ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
        start.countDown()
        workers.forEach { worker ->
            worker.join(5_000)
            assertFalse("consent consumer thread stalled", worker.isAlive)
        }
        assertEquals(16, results.size)
        assertEquals("a single consent capability must authorize at most one concurrent transfer",
            1, results.count { it })
        assertFalse(consent.permits(endpoint, model, request, seal))
    }


    @Test fun connectionSetupFailureStillDisconnectsAndConsumesGrant() = runBlocking {
        val opened = mutableListOf<FakeHttpsConnection>()
        val gateway = CloudBrainGateway { url ->
            FakeHttpsConnection(url, failRequestProperty = true).also(opened::add)
        }
        val seal = CloudEvidenceSeal.of(prepared, request)
        val consent = CloudTransferConsent.grant(endpoint, model, request, seal)
        assertTrue(runCatching {
            gateway.explain(endpoint, model, "SESSION-TOKEN", prepared, consent, request)
        }.isFailure)
        assertEquals(1, opened.size)
        assertTrue("connection must be disconnected even when request configuration throws", opened.single().disconnected)
        assertFalse("a failed outbound attempt must not reuse the one-shot grant", consent.permits(endpoint, model, request, seal))
    }

    @Test fun concurrentGatewayCallsConsumeOneConsentAndOpenAtMostOneConnection() {
        val opened = java.util.Collections.synchronizedList(mutableListOf<FakeHttpsConnection>())
        val gateway = CloudBrainGateway { url ->
            FakeHttpsConnection(url).also(opened::add)
        }
        val seal = CloudEvidenceSeal.of(prepared, request)
        val consent = CloudTransferConsent.grant(endpoint, model, request, seal)
        val ready = java.util.concurrent.CountDownLatch(16)
        val start = java.util.concurrent.CountDownLatch(1)
        val outcomes = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        val workers = (0 until 16).map {
            Thread {
                ready.countDown()
                if (start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    outcomes.add(runCatching {
                        runBlocking {
                            gateway.explain(endpoint, model, "SESSION-TOKEN", prepared, consent, request)
                        }
                    }.isSuccess)
                }
            }.apply { start() }
        }
        assertTrue("gateway workers did not reach the barrier", ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
        start.countDown()
        workers.forEach { worker ->
            worker.join(10_000)
            assertFalse("gateway worker stalled", worker.isAlive)
        }
        assertEquals(16, outcomes.size)
        assertEquals("one reviewed consent must authorize at most one successful outbound call", 1, outcomes.count { it })
        assertEquals(15, outcomes.count { !it })
        assertEquals("concurrent replays must not create extra connections", 1, opened.size)
        assertTrue(opened.single().disconnected)
        assertFalse(consent.permits(endpoint, model, request, seal))
    }
}
