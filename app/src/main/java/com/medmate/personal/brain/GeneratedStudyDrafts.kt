package com.medmate.personal.brain

/**
 * Model outputs are untrusted DRAFTS, not medical facts. Pages are derived from real evidence,
 * never from a language-model-supplied page number. Strict schema and human approval required.
 */
enum class GeneratedKind { HIGH_YIELD, FLASHCARD, MCQ }

data class GeneratedCandidate(
    val kind: GeneratedKind,
    val prompt: String,
    val answer: String,
    val options: List<String> = emptyList(),
    val correctIndex: Int = -1,
    val explanation: String = "",
    val sourceId: String,
    val supportingQuote: String = ""
)

data class GeneratedDraft(
    val candidate: GeneratedCandidate,
    val chapterId: String,
    val pageIndex: Int,
    val sourceQuote: String
)

data class GeneratedValidationResult(val accepted: List<GeneratedDraft>, val rejected: Int)

object GeneratedStudyDrafts {
    fun validate(candidates: List<GeneratedCandidate>, sources: List<Evidence>): GeneratedValidationResult {
        val validSources = sources.mapIndexed { idx, e -> "S${idx+1}" to e }.toMap()
        val results = mutableListOf<GeneratedDraft>()
        var rejected = 0
        val seen = mutableSetOf<String>()
        // An exact source excerpt is a stronger anchor than a model-supplied marker alone.
        fun normalizedQuote(text: String) = text.replace(Regex("\\s+"), " ").trim().lowercase(java.util.Locale.ROOT)
        for (c in candidates.take(30)) {
            val s = validSources[c.sourceId.removeSurrounding("[", "]")]
            val prompt = c.prompt.trim()
            val answer = c.answer.trim()
            val explanation = c.explanation.trim()
            val options = c.options.map { it.trim() }
            val quoted = c.supportingQuote.trim()
            val quoteVerifiable = s != null && quoted.length in 16..500 &&
                normalizedQuote(s.text).contains(normalizedQuote(quoted))
            val validBase = quoteVerifiable && prompt.length in 8..1500 && answer.length <= 3000 &&
                explanation.length <= 3000 && !s!!.text.isBlank()
            val validType = when (c.kind) {
                GeneratedKind.HIGH_YIELD -> answer.isEmpty() && options.isEmpty() && c.correctIndex == -1
                GeneratedKind.FLASHCARD -> answer.length in 3..1500 && options.isEmpty() && c.correctIndex == -1
                GeneratedKind.MCQ -> answer.isEmpty() && options.size in 4..5 &&
                    options.all { it.length in 2..500 } && options.map { it.lowercase() }.distinct().size == options.size &&
                    c.correctIndex in options.indices && explanation.isNotBlank()
            }
            val key = "${s?.pageIndex}|${c.kind}|${prompt.lowercase()}"
            if (!validBase || !validType || !seen.add(key)) { rejected++; continue }
            results.add(GeneratedDraft(c.copy(prompt = prompt, answer = answer,
                options = options, explanation = explanation, supportingQuote = quoted), s!!.chapterId, s.pageIndex, quoted))
        }
        rejected += (candidates.size - 30).coerceAtLeast(0)
        return GeneratedValidationResult(results, rejected)
    }
}
