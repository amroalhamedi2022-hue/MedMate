package com.medmate.personal.brain

/**
 * On-device GGUF diagnostic using a synthetic, non-medical passage.
 *
 * Calling this on the actual LlamaAndroidPort runs native inference. A unit
 * test with a fake port verifies only the contract, never real GGUF execution.
 * No personal PDF text, generated answer, model weights or credentials are
 * saved or transmitted by this diagnostic.
 */
/** Two optional system/process memory snapshots; not peak RSS or model-only RAM. */
data class NativeProbeMemory(
    val appPssKb: Int,
    val systemAvailableBytes: Long,
    val systemLowMemory: Boolean
) {
    init {
        require(appPssKb >= 0 && systemAvailableBytes >= 0) { "Invalid memory snapshot" }
    }
}

data class NativeProbeResult(
    val modelName: String,
    val elapsedMs: Long,
    val answerChars: Int,
    val calibrationPhrasePresent: Boolean,
    val citationSyntaxValid: Boolean,
    val memoryBefore: NativeProbeMemory? = null,
    val memoryAfter: NativeProbeMemory? = null
)

object NativeInferenceProbe {
    // Fresh per-run code rejects stale/hard-coded responses. Not JNI attestation.
    private fun freshChallenge(): String {
        val bytes = ByteArray(6)
        java.security.SecureRandom().nextBytes(bytes)
        return "ORBIT-" + bytes.joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }.uppercase(java.util.Locale.ROOT)
    }
    private const val CHAPTER = "synthetic-native-probe"

    suspend fun run(
        port: OnDeviceLlmPort,
        challengeCode: String = freshChallenge(),
        sampleMemory: () -> NativeProbeMemory? = { null }
    ): NativeProbeResult {
        require(Regex("^ORBIT-[0-9A-F]{12}$").matches(challengeCode)) {
            "Invalid synthetic calibration challenge"
        }
        val name = port.loadedModelName?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Load a GGUF model before running the native probe")
        val evidence = Evidence(
            id = "$CHAPTER:0:0",
            chapterId = CHAPTER,
            pageIndex = 0,
            text = "This is a synthetic calibration passage, not a medical textbook or patient PDF. " +
                "The calibration code written in this passage is $challengeCode. " +
                "Only report this code and cite the passage; do not invent a different code."
        )
        val prepared = MedicalPromptBuilder.prepare(
            GroundedRequest(
                chapterId = CHAPTER,
                query = "What is the calibration code in the supplied synthetic passage? Cite [S1].",
                evidence = listOf(evidence),
                maxEvidenceChars = 1000
            )
        )
        // Sample immediately around the actual generate call; do not claim a
        // peak or a GGUF-specific allocation from process/system snapshots.
        val memoryBefore = sampleMemory()
        val start = System.nanoTime()
        val answer = port.generate(prepared) { }
        val elapsed = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(0L)
        val memoryAfter = sampleMemory()
        require(answer.isNotBlank()) { "The native model returned an empty diagnostic answer" }
        return NativeProbeResult(
            modelName = name,
            elapsedMs = elapsed,
            answerChars = answer.length,
            calibrationPhrasePresent = Regex(
                "(?<![\\p{L}\\p{N}])" + Regex.escape(challengeCode) + "(?![\\p{L}\\p{N}])",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(answer),
            citationSyntaxValid = CitationValidator.check(answer, 1).accepted,
            memoryBefore = memoryBefore,
            memoryAfter = memoryAfter
        )
    }
}
