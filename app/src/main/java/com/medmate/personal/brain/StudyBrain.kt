package com.medmate.personal.brain

/** The OFFLINE path never calls a model or network. It finds verifiable original excerpts only. */
data class BrainSearchResult(
    val evidence: List<Evidence>,
    val status: BrainSearchStatus,
    val shownPages: Int
)

enum class BrainSearchStatus { FOUND, NO_MATCH, SCANNED_PDF_OR_MISSING_TEXT, TOO_SHORT }

object StudyBrain {
    fun search(chapterId: String, pages: List<PageSource>, question: String, onlyPage: Int? = null): BrainSearchResult {
        val cleaned = question.trim()
        // Apply chapter/page isolation even for an empty or stopword-only
        // question. A short query must not report pages from another PDF.
        val valid = pages.filter { it.chapterId == chapterId && (onlyPage == null || it.pageIndex == onlyPage) }
        val readablePages = valid.count { it.text.isNotBlank() }
        if (MedicalText.queryTokens(cleaned).isEmpty())
            return BrainSearchResult(emptyList(), BrainSearchStatus.TOO_SHORT, readablePages)
        if (readablePages == 0)
            return BrainSearchResult(emptyList(), BrainSearchStatus.SCANNED_PDF_OR_MISSING_TEXT, 0)
        val chunks = PageSegmenter(850).split(valid)
        val results = EvidenceRetriever(chunks).search(chapterId, cleaned, limit = 5)
        return BrainSearchResult(results, if (results.isEmpty()) BrainSearchStatus.NO_MATCH else BrainSearchStatus.FOUND,
            valid.count { it.text.isNotBlank() })
    }

    fun prepareSourceOnly(chapterId: String, question: String, found: BrainSearchResult,
                          maxEvidenceChars: Int = 5200): PreparedPrompt? =
        if (found.status != BrainSearchStatus.FOUND) null else
            MedicalPromptBuilder.prepare(GroundedRequest(chapterId, question, found.evidence, maxEvidenceChars = maxEvidenceChars))
}
