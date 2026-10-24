package com.medmate.personal.brain

import kotlin.math.ln

/** Reuses immutable PDF-token frequencies across questions in one chapter.
 * Ranking and citation anchors intentionally match EvidenceRetriever exactly.
 * This is lexical retrieval, not an embedding model or native GGUF inference. */
internal class IndexedEvidenceRetriever(chunks: List<Evidence>) {
    private data class Indexed(val source: Evidence, val counts: Map<String, Int>, val normalized: String)
    private val documents = chunks.map { evidence ->
        Indexed(evidence, MedicalText.tokens(evidence.text).groupingBy { it }.eachCount(),
            MedicalText.normalize(evidence.text))
    }

    fun search(chapterId: String, query: String, pageIndex: Int? = null, limit: Int = 5): List<Evidence> {
        require(limit in 1..20)
        val queryTokens = MedicalText.queryTokens(query)
        if (queryTokens.isEmpty()) return emptyList()
        val eligible = documents.filter { it.source.chapterId == chapterId &&
            (pageIndex == null || it.source.pageIndex == pageIndex) }
        if (eligible.isEmpty()) return emptyList()
        val df = HashMap<String, Int>()
        for (doc in eligible) for (term in queryTokens) {
            if ((doc.counts[term] ?: 0) > 0) df[term] = (df[term] ?: 0) + 1
        }
        val phrase = MedicalText.normalize(query).trim()
        return eligible.mapNotNull { doc ->
            val score = queryTokens.sumOf { term ->
                val tf = doc.counts[term] ?: 0
                if (tf == 0) 0.0 else (1.0 + ln(tf.toDouble())) *
                    ln(1.0 + (eligible.size + 1.0) / (1.0 + (df[term] ?: 0)))
            }
            val phraseBonus = if (phrase.length >= 5 && doc.normalized.contains(phrase)) 1.5 else 0.0
            if (score <= 0.0 && phraseBonus <= 0.0) null
            else doc.source.copy(score = score + phraseBonus)
        }.sortedWith(compareByDescending<Evidence> { it.score }.thenBy { it.pageIndex }.thenBy { it.id })
            .take(limit)
    }
}
