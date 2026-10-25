package com.medmate.personal.brain

/** The OFFLINE path never calls a model or network. It finds verifiable original excerpts only. */
data class BrainSearchResult(
    val evidence: List<Evidence>,
    val status: BrainSearchStatus,
    val shownPages: Int
)

enum class BrainSearchStatus { FOUND, NO_MATCH, SCANNED_PDF_OR_MISSING_TEXT, TOO_SHORT }

object StudyBrain {
    // Bound retained PDF text and token maps: one active chapter at a time.
    // Huge chapters still use the original query-only retriever, not an unbounded cache.
    private const val MAX_CACHED_CHARS = 350_000L
    private data class Cached(val chapterId: String, val pages: List<PageSource>,
                              val index: IndexedEvidenceRetriever)
    @Volatile private var cached: Cached? = null

    private fun reusableIndex(chapterId: String, pages: List<PageSource>): IndexedEvidenceRetriever? {
        if (pages.sumOf { it.text.length.toLong() } > MAX_CACHED_CHARS) return null
        val old = cached
        if (old != null && old.chapterId == chapterId && old.pages == pages) return old.index
        return synchronized(this) {
            val current = cached
            if (current != null && current.chapterId == chapterId && current.pages == pages) current.index
            else IndexedEvidenceRetriever(PageSegmenter(850).split(pages)).also {
                cached = Cached(chapterId, pages.toList(), it)
            }
        }
    }

    fun search(chapterId: String, pages: List<PageSource>, question: String, onlyPage: Int? = null): BrainSearchResult {
        val cleaned = question.trim()
        // Apply chapter/page isolation even for an empty or stopword-only
        // question. A short query must not report pages from another PDF.
        // A clearly corrupted PDF extraction must not become source evidence
        // for a generated medical explanation. Keep the original PDF intact;
        // the coverage panel identifies the excluded page for inspection.
        val chapterPages = pages.filter {
            it.chapterId == chapterId && !PdfCoverageAudit.likelyCorruptedText(it.text)
        }
        val valid = chapterPages.filter { onlyPage == null || it.pageIndex == onlyPage }
        val readablePages = valid.count { it.text.isNotBlank() }
        if (MedicalText.queryTokens(cleaned).isEmpty())
            return BrainSearchResult(emptyList(), BrainSearchStatus.TOO_SHORT, readablePages)
        if (readablePages == 0)
            return BrainSearchResult(emptyList(), BrainSearchStatus.SCANNED_PDF_OR_MISSING_TEXT, 0)
        val index = reusableIndex(chapterId, chapterPages)
        val results = if (index != null) index.search(chapterId, cleaned, pageIndex = onlyPage, limit = 5)
            else EvidenceRetriever(PageSegmenter(850).split(valid)).search(chapterId, cleaned, limit = 5)
        return BrainSearchResult(results, if (results.isEmpty()) BrainSearchStatus.NO_MATCH else BrainSearchStatus.FOUND,
            readablePages)
    }

    fun prepareSourceOnly(chapterId: String, question: String, found: BrainSearchResult,
                          maxEvidenceChars: Int = 5200): PreparedPrompt? =
        if (found.status != BrainSearchStatus.FOUND) null else
            MedicalPromptBuilder.prepare(GroundedRequest(chapterId, question, found.evidence, maxEvidenceChars = maxEvidenceChars))
}
