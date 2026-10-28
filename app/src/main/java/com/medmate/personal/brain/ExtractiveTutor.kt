package com.medmate.personal.brain

/** A useful LOCAL response without pretending that lexical extraction is generative AI.
 * Each sentence is an untouched quote from a retrieved PDF text segment.
 */
data class QuotedFinding(
    val marker: String,
    val pageIndex: Int,
    val quote: String,
    val matchedTerms: Int
) {
    val pageNumber: Int get() = pageIndex + 1
}

data class SourceBrief(
    val findings: List<QuotedFinding>,
    val pagesConsidered: Int,
    val isGenerative: Boolean = false
)

object ExtractiveTutor {
    private val sentenceBoundary = Regex("(?<=[.!?؟])\\s+|\\n+")

    /** Return only source quotes. Never paraphrase, infer causation, or invent medical answers. */
    fun brief(question: String, evidence: List<Evidence>, maxFindings: Int = 4): SourceBrief {
        require(maxFindings in 1..12)
        if (evidence.isEmpty()) return SourceBrief(emptyList(), 0)
        val tokens = MedicalText.queryTokens(question).toSet()
        if (tokens.isEmpty()) return SourceBrief(emptyList(), evidence.map { it.pageIndex }.distinct().size)
        val matches = evidence.take(20).flatMapIndexed { sourceIndex, source ->
            sentenceBoundary.split(source.text).mapNotNull { raw ->
                val quote = raw.trim()
                if (quote.length !in 35..850 || quote.count { it.isLetter() } < 20) return@mapNotNull null
                val normalized = MedicalText.tokens(quote).toSet()
                val hits = tokens.count { t -> t in normalized }
                if (hits <= 0) null else QuotedFinding("S${sourceIndex + 1}", source.pageIndex, quote, hits)
            }
        }
        val chosen = matches.sortedWith(compareByDescending<QuotedFinding> { it.matchedTerms }
            .thenBy { it.pageIndex }.thenBy { it.marker })
            .distinctBy { MedicalText.normalize(it.quote) }
            .take(maxFindings)
        return SourceBrief(chosen, evidence.map { it.pageIndex }.distinct().size)
    }
}
