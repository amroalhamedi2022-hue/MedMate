package com.medmate.personal.study

/**
 * Pure offline filtering: source page, kind and independent search words.
 * Does not change the order of the input (the store orders by PDF page/time).
 */
object StudyFilter {
    fun select(
        items: List<StudyItem>,
        kind: StudyKind,
        pageIndex: Int? = null,
        query: String = "",
    ): List<StudyItem> {
        require(pageIndex == null || pageIndex >= 0) { "Invalid PDF page" }
        val words = query.trim().take(120).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return items.filter { item ->
            if (item.kind != kind || (pageIndex != null && item.pageIndex != pageIndex)) return@filter false
            if (words.isEmpty()) return@filter true
            val searchable = buildString {
                append(item.prompt); append(' '); append(item.answer); append(' ')
                append(item.explanation); append(' ')
                item.options.forEach { append(it); append(' ') }
            }
            words.all { searchable.contains(it, ignoreCase = true) }
        }
    }
}

/**
 * Protects correctness of historical multiple-choice attempts: after reviews exist,
 * changing options/correct-answer would make older answer indices misleading.
 * Wording and explanations may always be corrected without destroying history.
 */
object StudyRevisionPolicy {
    fun changesGradedAnswer(
        old: StudyItem,
        answer: String,
        options: List<String>,
        correctIndex: Int,
    ): Boolean = when (old.kind) {
        StudyKind.HIGH_YIELD -> false
        StudyKind.FLASHCARD -> old.answer != answer.trim()
        StudyKind.MCQ -> old.options != options.map { it.trim() } || old.correctIndex != correctIndex
    }

    fun allowRevision(
        old: StudyItem,
        answer: String,
        options: List<String>,
        correctIndex: Int,
        hasReviews: Boolean,
    ): Boolean = !hasReviews || !changesGradedAnswer(old, answer, options, correctIndex)
}
