package com.medmate.personal.domain

/**
 * Shared fail-closed boundary for any code that opens or indexes a PDF.
 * Keep the count in one domain-level place so the reader and brain cannot drift.
 */
object PdfPageLimits {
    const val MAX_PAGES = 100_000

    fun requireSafeCount(totalPages: Int) {
        require(totalPages in 1..MAX_PAGES) {
            "PDF page count is outside the safe local audit range"
        }
    }
}
