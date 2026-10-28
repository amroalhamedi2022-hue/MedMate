package com.medmate.personal.brain

/** The OFFLINE path never calls a model or network. It finds verifiable original excerpts only. */
data class BrainSearchResult(
    val evidence: List<Evidence>,
    val status: BrainSearchStatus,
    val shownPages: Int,
    val excludedCorruptedPages: Int = 0,
    val firstCorruptedPage: Int? = null
)

enum class BrainSearchStatus { FOUND, NO_MATCH, SCANNED_PDF_OR_MISSING_TEXT, CORRUPTED_PDF_TEXT, AMBIGUOUS_PAGE_IDENTITIES, TOO_SHORT, SUPERSEDED }

object StudyBrain {
    // Bound retained PDF text and token maps: one active chapter at a time.
    // Oversized documents evict the previous chapter before taking the uncached path.
    private const val MAX_CACHED_CHARS = 350_000L
    private val indexCache = BoundedEvidenceIndexCache(MAX_CACHED_CHARS)

    fun search(chapterId: String, pages: List<PageSource>, question: String, onlyPage: Int? = null): BrainSearchResult {
        // Capture ordering at entry. A newer question/document owns the shared cache
        // from this point, so older work must stop before scanning the rest of a PDF.
        val cacheRequestEpoch = indexCache.beginRequest(chapterId)
        fun requestIsCurrent() = indexCache.isCurrentRequest(cacheRequestEpoch)
        fun supersededResult() = BrainSearchResult(emptyList(), BrainSearchStatus.SUPERSEDED, 0)
        if (!requestIsCurrent()) return supersededResult()

        val cleaned = question.trim()
        // Do not use eager filter/grouping operations here: they walk every page
        // before giving us a chance to notice that a newer search superseded this one.
        val scoped = ArrayList<PageSource>()
        for (page in pages) {
            if (!requestIsCurrent()) return supersededResult()
            if (page.chapterId == chapterId) scoped.add(page)
        }

        // Citation identity must be unambiguous in the requested scope. Validate
        // incrementally so a stale request cannot keep traversing an old page list.
        val identityScope = ArrayList<PageSource>()
        for (page in scoped) {
            if (!requestIsCurrent()) return supersededResult()
            if (onlyPage == null || page.pageIndex == onlyPage) identityScope.add(page)
        }
        val seenPageIndices = HashSet<Int>()
        var ambiguousIdentity = false
        for (page in identityScope) {
            if (!requestIsCurrent()) return supersededResult()
            if (page.pageIndex < 0 || !seenPageIndices.add(page.pageIndex)) {
                ambiguousIdentity = true
                break
            }
        }
        if (ambiguousIdentity) {
            // An older malformed result may not purge a newer request's cache.
            if (!indexCache.invalidateIfCurrent(cacheRequestEpoch)) return supersededResult()
            return BrainSearchResult(emptyList(), BrainSearchStatus.AMBIGUOUS_PAGE_IDENTITIES, 0)
        }

        // Exclude damaged extraction from retrieval while keeping a bounded count
        // for the UI. No extracted PDF text is copied into the coverage report.
        val chapterPages = ArrayList<PageSource>()
        val damagedPageIndices = ArrayList<Int>()
        for (page in scoped) {
            if (!requestIsCurrent()) return supersededResult()
            val corrupted = PdfCoverageAudit.likelyCorruptedText(page.text)
            if ((onlyPage == null || page.pageIndex == onlyPage) && corrupted) {
                damagedPageIndices.add(page.pageIndex)
            }
            if (!corrupted) chapterPages.add(page)
        }
        val corruptInScope = damagedPageIndices.size
        val firstCorruptedPage = damagedPageIndices.minOrNull()

        indexCache.prepareSearch(chapterId, chapterPages, cacheRequestEpoch)
        if (!requestIsCurrent()) return supersededResult()

        val valid = ArrayList<PageSource>()
        var readablePages = 0
        for (page in chapterPages) {
            if (!requestIsCurrent()) return supersededResult()
            if (onlyPage == null || page.pageIndex == onlyPage) {
                valid.add(page)
                if (page.text.isNotBlank()) readablePages++
            }
        }

        if (MedicalText.queryTokens(cleaned).isEmpty()) {
            if (!requestIsCurrent()) return supersededResult()
            return BrainSearchResult(emptyList(), BrainSearchStatus.TOO_SHORT, readablePages,
                corruptInScope, firstCorruptedPage)
        }
        if (readablePages == 0) {
            if (!requestIsCurrent()) return supersededResult()
            return BrainSearchResult(emptyList(), if (corruptInScope > 0)
                BrainSearchStatus.CORRUPTED_PDF_TEXT else BrainSearchStatus.SCANNED_PDF_OR_MISSING_TEXT,
                0, corruptInScope, firstCorruptedPage)
        }

        val index = indexCache.reusableIndex(chapterId, chapterPages, cacheRequestEpoch)
        if (!requestIsCurrent()) return supersededResult()
        val results = if (index != null) {
            index.search(chapterId, cleaned, pageIndex = onlyPage, limit = 5)
        } else {
            // An oversized current PDF uses uncached retrieval. Recheck immediately
            // before segmentation so an already superseded request does not split it.
            if (!requestIsCurrent()) return supersededResult()
            EvidenceRetriever(PageSegmenter(850).split(valid)).search(chapterId, cleaned, limit = 5)
        }
        if (!requestIsCurrent()) return supersededResult()
        return BrainSearchResult(results,
            if (results.isEmpty()) BrainSearchStatus.NO_MATCH else BrainSearchStatus.FOUND,
            readablePages, corruptInScope, firstCorruptedPage)
    }

    fun prepareSourceOnly(chapterId: String, question: String, found: BrainSearchResult,
                          maxEvidenceChars: Int = 5200): PreparedPrompt? =
        if (found.status != BrainSearchStatus.FOUND) null else
            MedicalPromptBuilder.prepare(GroundedRequest(chapterId, question, found.evidence, maxEvidenceChars = maxEvidenceChars))
}
