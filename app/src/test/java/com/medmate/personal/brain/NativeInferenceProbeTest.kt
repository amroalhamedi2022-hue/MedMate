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
    private val CHALLENGE = "ORBIT-001122334455"

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
        val port = RecordingFakePort("The calibration code is ORBIT-001122334455 [S1].")
        val report = NativeInferenceProbe.run(port, challengeCode = CHALLENGE)
        assertEquals(1, port.calls)
        assertEquals("fake-for-unit-test.gguf", report.modelName)
        assertTrue(report.elapsedMs >= 0)
        assertEquals("The calibration code is ORBIT-001122334455 [S1].".length, report.answerChars)
        assertTrue(report.calibrationPhrasePresent)
        assertTrue(report.citationSyntaxValid)
        assertEquals(null, report.memoryBefore)
        assertEquals(null, report.memoryAfter)
        val prompt = requireNotNull(port.captured)
        assertEquals(BrainTask.EXPLAIN, prompt.task)
        assertEquals(1, prompt.presentedEvidence.size)
        assertEquals("synthetic-native-probe", prompt.presentedEvidence.single().chapterId)
        assertTrue(prompt.prompt.contains(CHALLENGE))
        assertTrue(prompt.prompt.contains("[S1]"))
    }

    @Test fun diagnosticMemorySamplesAreLabeledAndNeverCalledPeak() = runBlocking {
        val port = RecordingFakePort("ORBIT-001122334455 [S1] is the synthetic code.")
        var reads = 0
        val result = NativeInferenceProbe.run(port, challengeCode = CHALLENGE) {
            reads++
            if (reads == 1) NativeProbeMemory(190_000, 2_000_000_000L, false)
            else NativeProbeMemory(230_000, 1_900_000_000L, true)
        }
        assertEquals(2, reads)
        assertEquals(190_000, result.memoryBefore?.appPssKb)
        assertEquals(230_000, result.memoryAfter?.appPssKb)
        assertTrue(result.memoryAfter?.systemLowMemory == true)
        assertTrue(result.citationSyntaxValid)
        assertTrue(runCatching { NativeProbeMemory(-1, 100, false) }.isFailure)
        assertTrue(runCatching { NativeProbeMemory(1, -1, false) }.isFailure)
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
    @Test fun aStaleFixedCalibrationAnswerCannotPassAChangedChallenge() = runBlocking {
        val stale = RecordingFakePort("The calibration code is ORBIT-27 [S1].")
        val result = NativeInferenceProbe.run(stale, challengeCode = CHALLENGE)
        assertFalse(result.calibrationPhrasePresent)
        assertTrue(result.citationSyntaxValid)
        assertTrue(stale.captured!!.prompt.contains(CHALLENGE))
        assertFalse(stale.captured!!.prompt.contains("ORBIT-27."))
    }

    @Test fun calibrationMustBeAnEntireTokenNotAQuotedSubstring() = runBlocking {
        val port = RecordingFakePort("The code is ORBIT-00112233445599 [S1].")
        val result = NativeInferenceProbe.run(port, challengeCode = CHALLENGE)
        assertFalse(result.calibrationPhrasePresent)
    }

    @Test fun invalidCalibrationInputIsRejectedBeforeCallingThePort() = runBlocking {
        val port = RecordingFakePort("ORBIT-001122334455 [S1]")
        val result = runCatching { NativeInferenceProbe.run(port, challengeCode = "ORBIT-27") }
        assertTrue(result.isFailure)
        assertEquals(0, port.calls)
    }

}
