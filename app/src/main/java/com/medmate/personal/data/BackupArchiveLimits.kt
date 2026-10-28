package com.medmate.personal.data

/**
 * Symmetric size limits for portable backups. The manifest is untrusted input:
 * validate each declared PDF and the aggregate before writing any staged file.
 * These limits bound storage exhaustion; they do not prove a PDF is safe or valid.
 */
object BackupArchiveLimits {
    const val MAX_PDF_BYTES = 512L * 1024L * 1024L
    const val MAX_TOTAL_PDF_BYTES = 2L * 1024L * 1024L * 1024L
    private const val MIN_PDF_BYTES = 5L

    fun requireSafeDocumentBytes(documentBytes: Iterable<Long>) {
        var total = 0L
        for (bytes in documentBytes) {
            require(bytes in MIN_PDF_BYTES..MAX_PDF_BYTES) {
                "A PDF in the backup is outside the safe per-file size limit"
            }
            require(total <= MAX_TOTAL_PDF_BYTES - bytes) {
                "The backup's total PDF payload exceeds the safe size limit"
            }
            total += bytes
        }
    }
}
