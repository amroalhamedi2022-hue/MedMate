package com.medmate.personal.brain

/** The OFFLINE path never calls a model or network. It finds verifiable original excerpts only. */
data class BrainSearchResult(
    val evidence: List<Evidence>,
    val status: BrainSearchStatus,
    val shownPages: Int,
    val excludedCorruptedPages: Int = 0,
    val firstCorruptedPage: Int? = null
)

enum class BrainSearchStatus { FOUND, NO_MATCH, SCANNED_PDF_OR_MISSING_TEXT, CORRUPTED_PDF_TEXT, AMBIGUOUS_PAGE_IDENTITIES, TOO_SHORT }

object StudyBrain {
    // Bound retained PDF text and token maps: one active chapter at a time.
    // Oversized documents evict the previous chapter before taking the uncached path.
    private const val MAX_CACHED_CHARS = 350_000L
    private val indexCache = BoundedEvidenceIndexCache(MAX_CACHED_CHARS)

    fun search(chapterId: String, pages: List<PageSource>, question: String, onlyPage: Int? = null): BrainSearchResult {
        val cleaned = question.trim()
        // Apply chapter/page isolation even for an empty or stopword-only
        // question. A short query must not report pages from another PDF.
        // A clearly corrupted PDF extraction must not become source evidence
        // for a generated medical explanation. Keep the original PDF intact;
        // the coverage panel identifies the excluded page for inspection.
        val scoped = pages.filter { it.chapterId == chapterId }
        // Page identity is part of every visible [S#] citation. A duplicate or
        // negative page anchor can make different excerpts point at the same
        // human-facing page, so fail closed before retrieval or model prompting.
        // Page-only questions validate that selected page; full-chapter search
        // validates every page in the selected chapter.
        val identityScope = if (onlyPage == null) scoped else scoped.filter { it.pageIndex == onlyPage }
        if (identityScope.any { it.pageIndex < 0 } ||
            identityScope.groupingBy { it.pageIndex }.eachCount().any { it.value > 1 }) {
            return BrainSearchResult(
                emptyList(), BrainSearchStatus.AMBIGUOUS_PAGE_IDENTITIES, 0
            )
        }
        // Distinguish extraction damage from scanned/image-only pages. The
        // latter may need OCR; corrupt text must never become LLM evidence.
        val damagedPageIndices = scoped.asSequence().filter {
            (onlyPage == null || it.pageIndex == onlyPage) &&
                PdfCoverageAudit.likelyCorruptedText(it.text)
        }.map { it.pageIndex }.toList()
        val corruptInScope = damagedPageIndices.size
        val firstCorruptedPage = damagedPageIndices.minOrNull()
        val chapterPages = scoped.filterNot { PdfCoverageAudit.likelyCorruptedText(it.text) }
        val valid = chapterPages.filter { onlyPage == null || it.pageIndex == onlyPage }
        val readablePages = valid.count { it.text.isNotBlank() }
        if (MedicalText.queryTokens(cleaned).isEmpty())
            return BrainSearchResult(emptyList(), BrainSearchStatus.TOO_SHORT, readablePages, corruptInScope, firstCorruptedPage)
        if (readablePages == 0)
            return BrainSearchResult(emptyList(), if (corruptInScope > 0)
                BrainSearchStatus.CORRUPTED_PDF_TEXT else BrainSearchStatus.SCANNED_PDF_OR_MISSING_TEXT,
                0, corruptInScope, firstCorruptedPage)
        val index = indexCache.reusableIndex(chapterId, chapterPages)
        val results = if (index != null) index.search(chapterId, cleaned, pageIndex = onlyPage, limit = 5)
            else EvidenceRetriever(PageSegmenter(850).split(valid)).search(chapterId, cleaned, limit = 5)
        return BrainSearchResult(results, if (results.isEmpty()) BrainSearchStatus.NO_MATCH else BrainSearchStatus.FOUND,
            readablePages, corruptInScope, firstCorruptedPage)
    }

    fun prepareSourceOnly(chapterId: String, question: String, found: BrainSearchResult,
                          maxEvidenceChars: Int = 5200): PreparedPrompt? =
        if (found.status != BrainSearchStatus.FOUND) null else
            MedicalPromptBuilder.prepare(GroundedRequest(chapterId, question, found.evidence, maxEvidenceChars = maxEvidenceChars))
}
