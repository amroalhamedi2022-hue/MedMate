package com.medmate.personal.brain

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-only tests with an explicitly fake port.
 * They DO NOT count as a real GGUF / llama.cpp inference test.
 */
class NativeInferenceProbeTest {
    private class RecordingFakePort(private val response: String) : OnDeviceLlmPort {
        override val loadedModelName: String? = "fake-for-unit-test.gguf"
        var calls = 0
        var captured: PreparedPrompt? = null
        override suspend fun loadModel(file: File) = Unit
        override suspend fun generate(prepared: PreparedPrompt, onToken: (String) -> Unit): String {
            calls++
            captured = prepared
            return response
        }
        override suspend fun unload() = Unit
    }

    @Test fun probeUsesOnlySyntheticEvidenceAndReportsSeparateGroundingFlags() = runBlocking {
        val port = RecordingFakePort("The calibration code is ORBIT-27 [S1].")
        val report = NativeInferenceProbe.run(port)
        assertEquals(1, port.calls)
        assertEquals("fake-for-unit-test.gguf", report.modelName)
        assertTrue(report.elapsedMs >= 0)
        assertEquals("The calibration code is ORBIT-27 [S1].".length, report.answerChars)
        assertTrue(report.calibrationPhrasePresent)
        assertTrue(report.citationSyntaxValid)
        val prompt = requireNotNull(port.captured)
        assertEquals(BrainTask.EXPLAIN, prompt.task)
        assertEquals(1, prompt.presentedEvidence.size)
        assertEquals("synthetic-native-probe", prompt.presentedEvidence.single().chapterId)
        assertTrue(prompt.prompt.contains("ORBIT-27"))
        assertTrue(prompt.prompt.contains("[S1]"))
    }

    @Test fun aNonGroundedFakeResponseDoesNotBecomeGroundedPass() = runBlocking {
        val report = NativeInferenceProbe.run(RecordingFakePort("Some unrelated answer."))
        assertFalse(report.calibrationPhrasePresent)
        assertFalse(report.citationSyntaxValid)
    }

    @Test fun anUnloadedPortCannotProduceAFalseNativeSuccess() = runBlocking {
        val unloaded = object : OnDeviceLlmPort {
            override val loadedModelName: String? = null
            override suspend fun loadModel(file: File) = Unit
            override suspend fun generate(prepared: PreparedPrompt, onToken: (String) -> Unit): String =
                error("Must not call fake generation")
            override suspend fun unload() = Unit
        }
        val failure = runCatching { NativeInferenceProbe.run(unloaded) }
        assertTrue(failure.isFailure)
    }
}
