package com.medmate.personal.brain

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCompletionPublicationTest {
    @Test
    fun cancellationAfterNativeCompletionPreventsUiPublication() {
        var checks = 0
        val emitted = mutableListOf<String>()
        val failure = runCatching {
            publishNativeCompletionIfActive(
                rawAnswer = "  Grounded explanation [S1]  ",
                ensureActive = {
                    checks += 1
                    if (checks == 2) throw CancellationException("cancelled before UI publication")
                },
                onToken = { emitted += it }
            )
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(2, checks)
        assertTrue("cancelled native output must never reach the callback", emitted.isEmpty())
    }

    @Test
    fun activeCompletionIsTrimmedAndPublishedExactlyOnce() {
        var checks = 0
        val emitted = mutableListOf<String>()
        val result = publishNativeCompletionIfActive(
            rawAnswer = " \nGrounded explanation [S1]\t ",
            ensureActive = { checks += 1 },
            onToken = { emitted += it }
        )

        assertEquals("Grounded explanation [S1]", result)
        assertEquals(listOf("Grounded explanation [S1]"), emitted)
        assertEquals(2, checks)
    }

    @Test
    fun blankOrOversizedCompletionIsRejectedBeforePublication() {
        for (invalid in listOf("   ", "x".repeat(40_001))) {
            var published = false
            val failure = runCatching {
                publishNativeCompletionIfActive(
                    rawAnswer = invalid,
                    ensureActive = {},
                    onToken = { published = true }
                )
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertFalse("invalid native output must not be published", published)
        }
    }
}
