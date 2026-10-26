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
    val memoryAfter: NativeProbeMemory? = null,
    // A model that merely echoes its source/prompt has not demonstrated retrieval.
    val promptEchoDetected: Boolean = false,
    // A clean fake-port response is useful for contract tests, but it is not
    // evidence that the production llama.cpp/JNI backend actually ran.
    val nativeBackendConfirmed: Boolean,
    /** Content identity of the GGUF whose native load succeeded; null for fake ports. */
    val nativeModelSha256: String? = null,
    /** File size of that same GGUF, not native memory consumption. */
    val nativeModelBytes: Long? = null
) {
    val responseChecksPassed: Boolean
        get() = calibrationPhrasePresent && citationSyntaxValid && !promptEchoDetected

    val diagnosticPassed: Boolean
        get() = nativeBackendConfirmed && responseChecksPassed
}

object NativeInferenceProbe {
    internal fun validNativeModelIdentity(sha256: String?, bytes: Long?): Boolean =
        sha256 != null && Regex("^[0-9a-f]{64}$").matches(sha256) &&
            bytes != null && bytes in LocalModelImport.MIN_BYTES..LocalModelImport.MAX_BYTES

    internal fun sameNativeModelIdentity(
        beforeName: String?,
        beforeSha256: String?,
        beforeBytes: Long?,
        afterName: String?,
        afterSha256: String?,
        afterBytes: Long?
    ): Boolean =
        beforeName != null && beforeName.isNotBlank() &&
            beforeName == afterName &&
            beforeSha256 == afterSha256 &&
            beforeBytes == afterBytes &&
            validNativeModelIdentity(beforeSha256, beforeBytes)

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
        val nativePort = port as? LlamaAndroidPort
        val name = port.loadedModelName?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Load a GGUF model before running the native probe")
        // Snapshot the exact loaded model identity before inference. A different
        // load/unload racing after generate() returns must not be attributed to
        // the answer that just completed.
        val nativeShaBefore = nativePort?.loadedModelSha256
        val nativeBytesBefore = nativePort?.loadedModelBytes
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
        val nativeShaAfter = nativePort?.loadedModelSha256
        val nativeBytesAfter = nativePort?.loadedModelBytes
        val nativeNameAfter = nativePort?.loadedModelName
        val nativeIdentityValid = sameNativeModelIdentity(
            name, nativeShaBefore, nativeBytesBefore,
            nativeNameAfter, nativeShaAfter, nativeBytesAfter
        )
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
            memoryAfter = memoryAfter,
            // This is an additional sanity gate, not proof of genuine JNI/GGUF.
            // Full prompt/source echoes can contain both the code and [S1].
            promptEchoDetected = answer.contains(prepared.prompt, ignoreCase = true) ||
                answer.contains(evidence.text, ignoreCase = true) ||
                answer.contains("This is a synthetic calibration passage", ignoreCase = true) ||
                answer.contains("Only report this code and cite the passage", ignoreCase = true),
            nativeBackendConfirmed = nativePort != null && nativeIdentityValid,
            nativeModelSha256 = nativeShaAfter?.takeIf { nativeIdentityValid },
            nativeModelBytes = nativeBytesAfter?.takeIf { nativeIdentityValid }
        )
    }
}
