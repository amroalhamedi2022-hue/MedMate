package com.medmate.personal.brain

import com.medmate.personal.study.StudyOptionIdentity

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
        // Every generated batch must be grounded in exactly one PDF chapter. The
        // prompt builder already enforces this; validate again at the untrusted
        // model-output boundary so callers cannot mix page references across books.
        // The prompt builder validates evidence before inference, but both
        // local and cloud output paths call this public validator independently.
        // A malformed source list must fail closed as a whole: never persist
        // an invented page (-1), an empty chapter, or an oversized excerpt.
        // Match StudyDraftPrompt's 20-source / 20k-character input budget.
        val invalidSources = sources.isEmpty() || sources.size > 20 ||
            sources.first().chapterId.isBlank() ||
            sources.any { it.chapterId != sources.first().chapterId ||
                it.pageIndex < 0 || it.text.isBlank() } ||
            sources.sumOf { it.text.length.toLong() } > 20_000L
        if (invalidSources) {
            return GeneratedValidationResult(emptyList(), candidates.size)
        }
        val validSources = sources.mapIndexed { idx, e -> "S${idx+1}" to e }.toMap()
        val results = mutableListOf<GeneratedDraft>()
        var rejected = 0
        val seen = mutableSetOf<String>()
        // PDF extraction often splits medical terms across a printed line, or
        // yields Unicode ligatures (e.g. "ﬁ"). Normalize these *only for matching*;
        // the stored citation always retains the original exact PDF page and text.
        fun normalizedQuote(text: String): String = java.text.Normalizer
            .normalize(text, java.text.Normalizer.Form.NFKC)
            .replace(Regex("(?<=\\p{L})-\\s*\\r?\\n\\s*(?=\\p{L})"), "")
            .replace("\u00AD", "")
            // Arabic vowel marks/tatweel and common alef/ya spellings differ
            // between PDF extraction and a model's quotation. Fold them only
            // for quote comparison, never rewrite the original stored source.
            .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
            .replace('ى', 'ي')
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(java.util.Locale.ROOT)
        fun quoteOnWordBoundaries(source: String, quote: String): Boolean {
            if (quote.isBlank()) return false
            var at = source.indexOf(quote)
            while (at >= 0) {
                val end = at + quote.length
                val before = at == 0 || !source[at - 1].isLetterOrDigit()
                val after = end == source.length || !source[end].isLetterOrDigit()
                // Do not allow a model to quote an affirmative-looking
                // fragment from a negated medical statement. "No evidence of
                // infection" -> "evidence of infection" AND "infection is
                // present" are both unsafe crops. Restrict the lookback to
                // the current clause and six words; do not reject a separate
                // sentence after punctuation. This is a conservative lexical
                // guard, NOT clinical entailment or a diagnosis validator.
                val negations = setOf("no", "not", "without", "absence", "absent",
                    "negative", "cannot", "unable", "لا", "لم", "لن", "ليس",
                    "ليست", "غير", "عدم", "دون", "بدون", "غياب")
                // A quote that omits uncertainty can falsely turn a suspected
                // finding into a confirmed one. Preserve nearby epistemic
                // qualifiers as carefully as explicit negation. This is a
                // conservative lexical check, NOT medical entailment.
                val uncertainty = setOf("may", "might", "could", "possible",
                    "possibly", "suspected", "suspicious", "suggestive",
                    "probable", "probably", "potential", "potentially",
                    "uncertain", "unclear", "unlikely", "ربما", "يحتمل",
                    "محتمل", "احتمال", "احتمالية", "اشتباه", "مشتبه",
                    "يشتبه", "مرجح", "يبدو")
                val firstQuotedWord = Regex("""[\p{L}]+""").find(quote)?.value
                val prefix = source.substring(0, at)
                val hardStart = prefix.indexOfLast { it in ".!?;:؛\n\r" } + 1
                // A comma alone does not end the scope of "no" in a list
                // ("no fever, cough or pain"). A contrastive "but/لكن" does.
                val comma = prefix.indexOfLast { it == ',' || it == '،' }
                val afterComma = if (comma >= hardStart) prefix.substring(comma + 1).trimStart() else ""
                val contrast = Regex("""^(?:but|however|yet|لكن|ولكن)(?:\s|$)""")
                    .containsMatchIn(afterComma)
                val clauseStart = if (comma >= hardStart && contrast) comma + 1 else hardStart
                val precedingWords = Regex("""[\p{L}]+""")
                    .findAll(prefix.substring(clauseStart)).map { it.value }
                    .toList().takeLast(6)
                val omittedNegation = firstQuotedWord !in negations &&
                    precedingWords.any { it in negations }
                val omittedUncertainty = firstQuotedWord !in uncertainty &&
                    precedingWords.any { it in uncertainty }
                if (before && after && !omittedNegation && !omittedUncertainty) return true
                at = source.indexOf(quote, at + 1)
            }
            return false
        }
        for (c in candidates.take(30)) {
            val s = validSources[c.sourceId.removeSurrounding("[", "]")]
            val prompt = c.prompt.trim()
            val answer = c.answer.trim()
            val explanation = c.explanation.trim()
            val options = c.options.map { it.trim() }
            val optionKeys = options.map(StudyOptionIdentity::key)
            val quoted = c.supportingQuote.trim()
            val quoteVerifiable = s != null && quoted.length in 16..500 &&
                quoteOnWordBoundaries(normalizedQuote(s.text), normalizedQuote(quoted))
            val validBase = quoteVerifiable && prompt.length in 8..1500 && answer.length <= 3000 &&
                explanation.length <= 3000 && !s!!.text.isBlank()
            // An exact supporting quote alone does not prove that an AI-picked MCQ key
            // or explanation follows from that quote. Require both the selected answer
            // option and the explanation to be source-verbatim, word-bounded excerpts.
            // This intentionally rejects many paraphrased drafts rather than accepting
            // unsupported clinical claims; accepted items remain human-reviewed drafts.
            val mcqEvidenceGrounded = s != null && c.correctIndex in options.indices &&
                quoteOnWordBoundaries(normalizedQuote(s.text), normalizedQuote(options[c.correctIndex])) &&
                explanation.length in 16..500 &&
                quoteOnWordBoundaries(normalizedQuote(s.text), normalizedQuote(explanation))
            val validType = when (c.kind) {
                GeneratedKind.HIGH_YIELD -> answer.isEmpty() && options.isEmpty() && c.correctIndex == -1
                GeneratedKind.FLASHCARD -> answer.length in 3..1500 && options.isEmpty() && c.correctIndex == -1
                GeneratedKind.MCQ -> answer.isEmpty() && options.size in 4..5 &&
                    options.all { it.length in 2..500 } && optionKeys.all { it.isNotBlank() } && optionKeys.distinct().size == options.size &&
                    c.correctIndex in options.indices && mcqEvidenceGrounded
            }
            val key = "${s?.chapterId}|${s?.pageIndex}|${c.kind}|${prompt.lowercase(java.util.Locale.ROOT)}"
            if (!validBase || !validType || !seen.add(key)) { rejected++; continue }
            results.add(GeneratedDraft(c.copy(prompt = prompt, answer = answer,
                options = options, explanation = explanation, supportingQuote = quoted), s!!.chapterId, s.pageIndex, quoted))
        }
        rejected += (candidates.size - 30).coerceAtLeast(0)
        return GeneratedValidationResult(results, rejected)
    }
}
