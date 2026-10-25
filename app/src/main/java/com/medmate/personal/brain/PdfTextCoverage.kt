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
    val complete: Boolean get() = indexedPages == totalPages
    val readyForFullChapterSearch: Boolean get() = complete && readablePages == totalPages
    val hasSearchableEvidence: Boolean get() = readablePages > 0
    val percentReadable: Int get() = (100L * readablePages / totalPages).toInt()
}

/** Pure Kotlin; no filesystem/network or Android dependencies. */
object PdfCoverageAudit {
    private const val MIN_TEXT_CHARS = 16
    /**
     * Conservative warning gate for clearly damaged PDF text extraction.
     * A few replacement glyphs are common in otherwise valid textbooks;
     * only dense U+FFFD sequences or embedded NUL controls trigger it.
     * This is NOT OCR quality scoring or a medical factuality check.
     */
    fun likelyCorruptedText(text: String): Boolean {
        if (text.isEmpty()) return false
        var replacements = 0
        var nul = 0
        for (char in text) {
            if (char == '\uFFFD') replacements++
            if (char == '\u0000') nul++
        }
        return nul >= 2 || (replacements >= 4 && replacements.toLong() * 12 >= text.length)
    }

    fun inspect(chapterId: String, totalPages: Int, pages: List<PageSource>,
                maxListed: Int = 8): PdfTextCoverage {
        require(chapterId.isNotBlank() && totalPages > 0) { "Invalid PDF identity/page count" }
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
