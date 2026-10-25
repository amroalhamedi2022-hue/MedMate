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

    fun inspect(chapterId: String, totalPages: Int, pages: List<PageSource>,
                maxListed: Int = 8): PdfTextCoverage {
        require(chapterId.isNotBlank() && totalPages > 0) { "Invalid PDF identity/page count" }
        require(maxListed in 0..32) { "Invalid coverage preview limit" }
        require(pages.size <= totalPages) { "Too many indexed PDF pages" }
        val states = ByteArray(totalPages) // 0 missing, 1 empty, 2 suspiciously short, 3 readable
        for (page in pages) {
            require(page.chapterId == chapterId && page.pageIndex in 0 until totalPages) {
                "Mixed chapter or invalid page anchor"
            }
            require(states[page.pageIndex] == 0.toByte()) { "Duplicate page anchor" }
            val chars = page.text.count { it.isLetterOrDigit() }
            states[page.pageIndex] = when {
                page.text.isBlank() -> 1
                chars < MIN_TEXT_CHARS -> 2
                else -> 3
            }.toByte()
        }
        val missing = states.count { it == 0.toByte() }
        val empty = states.count { it == 1.toByte() }
        val lowText = states.count { it == 2.toByte() }
        val readable = states.count { it == 3.toByte() }
        return PdfTextCoverage(
            totalPages = totalPages,
            indexedPages = totalPages - missing,
            readablePages = readable,
            emptyPages = empty,
            lowTextPages = lowText,
            missingPages = missing,
            firstUnusablePages = states.indices.asSequence()
                .filter { states[it] != 3.toByte() }.take(maxListed).toList()
        )
    }
}
