package com.medmate.personal.brain

/**
 * Diagnostic record for an explicit, real on-device llama.cpp explanation call.
 * The UI constructs this ONLY after LlamaAndroidPort.generate returns; no fake
 * adapter can count as device inference. This class does not itself run GGUF.
 * No PDF text, model weights, secrets, or answer content are persisted here.
 */
data class LocalInferenceAudit(
    val modelName: String,
    val elapsedMs: Long,
    val answerChars: Int,
    val sourcePages: List<Int>,
    val citationCheck: CitationCheck
) {
    val syntaxPass: Boolean get() = citationCheck.accepted

    companion object {
        fun fromNativeResponse(
            modelName: String?,
            elapsedMs: Long,
            prepared: PreparedPrompt,
            answer: String
        ): LocalInferenceAudit {
            require(!modelName.isNullOrBlank()) { "No native model session" }
            require(elapsedMs >= 0) { "Invalid elapsed time" }
            require(prepared.task == BrainTask.EXPLAIN) { "Explanation diagnostics only" }
            val sources = prepared.presentedEvidence
            require(sources.isNotEmpty()) { "No PDF evidence" }
            require(sources.all { it.chapterId == sources.first().chapterId && it.pageIndex >= 0 }) {
                "Cross-chapter or invalid PDF evidence"
            }
            require(answer.isNotBlank()) { "No model response" }
            return LocalInferenceAudit(
                modelName = modelName,
                elapsedMs = elapsedMs,
                answerChars = answer.length,
                sourcePages = sources.map { it.pageNumber }.distinct(),
                citationCheck = CitationValidator.check(answer, sources.size)
            )
        }
    }
}


/**
 * Fail-closed display gate for native local explanations. A response is exposed
 * to the student only when every citation marker is syntactically valid and
 * points inside the exact prepared PDF evidence list, or it is the explicit
 * source-insufficient abstention accepted by CitationValidator.
 *
 * This validates citation syntax/provenance only; it does not prove medical
 * truth. Rejected model text is deliberately not returned for UI display.
 */
data class LocalAnswerGateResult(
    val displayableAnswer: String?,
    val citationCheck: CitationCheck
) {
    val accepted: Boolean get() = displayableAnswer != null
}

object LocalAnswerGate {
    fun evaluate(answer: String, prepared: PreparedPrompt): LocalAnswerGateResult {
        require(prepared.task == BrainTask.EXPLAIN) { "Local answer gate accepts explanation tasks only" }
        require(prepared.presentedEvidence.isNotEmpty()) { "No PDF evidence for local explanation" }
        val check = CitationValidator.check(answer, prepared.presentedEvidence.size)
        val safeAnswer = if (check.accepted) answer.trim().takeIf { it.isNotEmpty() } else null
        return LocalAnswerGateResult(safeAnswer, check)
    }
}
