package com.medmate.personal.brain

/**
 * Chapter-scoped, offline visibility into PDF text extraction. This reports
 * *coverage*, not OCR or the clinical reliability of extracted text.
 *
 * Only page indices/counts are retained. PDF excerpts never enter the report.
 * An unindexed page is distinct from an indexed page with no usable text.
 */
data class PdfTextCoverage(
    val totalPages: Int,
    val indexedPages: Int,
    val readablePages: Int,
    val emptyPages: Int,
    val lowTextPages: Int,
    val garbledPages: Int,
    val missingPages: Int,
    val firstUnusablePages: List<Int>
) {
    init {
        require(totalPages in 1..PdfCoverageAudit.MAX_AUDIT_PAGES) {
            "PDF page count is outside the safe local audit range"
        }
        require(indexedPages in 0..totalPages && readablePages in 0..totalPages &&
            emptyPages in 0..totalPages && lowTextPages in 0..totalPages &&
            garbledPages in 0..totalPages && missingPages in 0..totalPages) {
            "Invalid PDF coverage counts"
        }
        require(indexedPages.toLong() == readablePages.toLong() + emptyPages +
            lowTextPages + garbledPages && missingPages == totalPages - indexedPages) {
            "Inconsistent PDF coverage counts"
        }
        require(firstUnusablePages.size <= 32 &&
            firstUnusablePages.all { it in 0 until totalPages } &&
            firstUnusablePages.distinct().size == firstUnusablePages.size) {
            "Invalid PDF coverage navigation preview"
        }
    }

    val complete: Boolean get() = indexedPages == totalPages
    val readyForFullChapterSearch: Boolean get() = complete && readablePages == totalPages
    val hasSearchableEvidence: Boolean get() = readablePages > 0
    val percentReadable: Int get() = (100L * readablePages / totalPages).toInt()
}

/** Pure Kotlin; no filesystem/network or Android dependencies. */
object PdfCoverageAudit {
    // The audit allocates one byte per PDF page. Bound hostile or pathological
    // page counts before allocation so a malformed document cannot trigger an OOM.
    const val MAX_AUDIT_PAGES = 100_000
    private const val MIN_TEXT_CHARS = 16
    /**
     * Conservative warning gate for clearly damaged PDF text extraction.
     * A few replacement glyphs are common in otherwise valid textbooks;
     * only dense U+FFFD sequences or repeated unsafe C0 controls trigger it.
     * This is NOT OCR quality scoring or a medical factuality check.
     */
    fun likelyCorruptedText(text: String): Boolean {
        if (text.isEmpty()) return false
        var replacements = 0
        var unsafeControls = 0
        for (char in text) {
            if (char == '\uFFFD') replacements++
            // PDF extraction may return invisible C0 controls other than NUL.
            // Preserve legitimate line breaks, tabs and page/form separators.
            if (char.code < 0x20 && char != '\n' && char != '\r' &&
                char != '\t' && char != '\u000C') unsafeControls++
        }
        return unsafeControls >= 2 ||
            (replacements >= 4 && replacements.toLong() * 12 >= text.length)
    }

    fun inspect(chapterId: String, totalPages: Int, pages: List<PageSource>,
                maxListed: Int = 8): PdfTextCoverage {
        require(chapterId.isNotBlank() && totalPages in 1..MAX_AUDIT_PAGES) {
            "Invalid PDF identity or page count exceeds the safe local audit limit"
        }
        require(maxListed in 0..32) { "Invalid coverage preview limit" }
        require(pages.size <= totalPages) { "Too many indexed PDF pages" }
        val states = ByteArray(totalPages) // 0 missing, 1 empty, 2 suspiciously short, 3 readable, 4 garbled
        for (page in pages) {
            require(page.chapterId == chapterId && page.pageIndex in 0 until totalPages) {
                "Mixed chapter or invalid page anchor"
            }
            require(states[page.pageIndex] == 0.toByte()) { "Duplicate page anchor" }
            val chars = page.text.count { it.isLetterOrDigit() }
            states[page.pageIndex] = when {
                page.text.isBlank() -> 1
                likelyCorruptedText(page.text) -> 4
                chars < MIN_TEXT_CHARS -> 2
                else -> 3
            }.toByte()
        }
        val missing = states.count { it == 0.toByte() }
        val empty = states.count { it == 1.toByte() }
        val lowText = states.count { it == 2.toByte() }
        val readable = states.count { it == 3.toByte() }
        val garbled = states.count { it == 4.toByte() }
        return PdfTextCoverage(
            totalPages = totalPages,
            indexedPages = totalPages - missing,
            readablePages = readable,
            emptyPages = empty,
            lowTextPages = lowText,
            garbledPages = garbled,
            missingPages = missing,
            firstUnusablePages = states.indices.asSequence()
                .filter { states[it] != 3.toByte() }.take(maxListed).toList()
        )
    }
}
