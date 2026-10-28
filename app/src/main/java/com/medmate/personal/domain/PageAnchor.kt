package com.medmate.personal.domain

/** PDF coordinates remain stable even if displayed page labels change. */
data class PageAnchor(
    val chapterSha256: String,
    val pdfPageIndex: Int, // always 0-based internally
    val startChar: Int? = null,
    val endChar: Int? = null,
    val extractionVersion: Int = 1,
) {
    init {
        require(chapterSha256.matches(Regex("[a-fA-F0-9]{64}")))
        require(pdfPageIndex >= 0)
        require(startChar == null || startChar >= 0)
        require(endChar == null || (startChar != null && endChar >= startChar))
    }
    val displayedPdfPage: Int get() = pdfPageIndex + 1
}
