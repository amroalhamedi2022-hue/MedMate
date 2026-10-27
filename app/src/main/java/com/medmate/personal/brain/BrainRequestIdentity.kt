package com.medmate.personal.brain

/** Captures the exact indexed PDF/question context when an async AI request starts.
 *  UI must not display or allow saving results from a different chapter/page/question.
 *  Pure Kotlin to keep the safety gate independently testable without Android.
 */
data class BrainRequestIdentity(
    val chapterSha256: String,
    val question: String,
    val pageOnly: Boolean,
    val sourcePage: Int
) {
    // Normalize both sides symmetrically: incidental outer whitespace must not
    // make the same captured question look stale after a long native request.
    private val normalizedQuestion = question.trim()

    fun matches(chapter: String, currentQuestion: String, currentPageOnly: Boolean, currentPage: Int): Boolean =
        chapterSha256 == chapter && normalizedQuestion == currentQuestion.trim() &&
            pageOnly == currentPageOnly &&
            (!pageOnly || (sourcePage >= 0 && currentPage >= 0 && sourcePage == currentPage))
}
