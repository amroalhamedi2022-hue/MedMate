package com.medmate.personal.brain

/**
 * Navigation-only projection of an AI answer onto the exact PDF excerpts that
 * were supplied to that request. A citation is never a medical fact check.
 * Fail closed: do not make ANY marker tappable when citation syntax is invalid.
 */
data class SourceCitationLink(val marker: String, val pageIndex: Int) {
    val pageNumber: Int get() = pageIndex + 1
}

object SourceCitationLinks {
    private val marker = Regex("""\\[S([1-9]\\d*)]""")

    fun resolve(answer: String, evidence: List<Evidence>): List<SourceCitationLink> {
        if (evidence.isEmpty()) return emptyList()
        val chapter = evidence.first().chapterId
        if (chapter.isBlank() || evidence.any { it.chapterId != chapter || it.pageIndex < 0 }) {
            return emptyList()
        }
        // Do not link a valid-looking [S1] when the same answer also contains
        // a forged [S99], [S-1], [S01], or a deceptive Unicode source marker.
        if (!CitationValidator.check(answer, evidence.size).accepted) return emptyList()
        return marker.findAll(answer).mapNotNull { match ->
            val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            evidence.getOrNull(number - 1)?.let { SourceCitationLink("S$number", it.pageIndex) }
        }.distinctBy { it.marker }.take(12).toList()
    }
}
