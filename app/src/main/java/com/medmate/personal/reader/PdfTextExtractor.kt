package com.medmate.personal.reader

import android.content.Context
import com.medmate.personal.domain.PdfPageLimits
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.Closeable
import java.io.File

/** PDFBox extracts a page's embedded text; it cannot OCR an image-only page. */
class PdfTextExtractor(context: Context, file: File) : Closeable {
    private val document: PDDocument

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
        val opened = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir))
        try {
            // Defense in depth: import and re-indexing already validate page counts,
            // but every direct extractor caller must enforce the same bound too.
            PdfPageLimits.requireSafeCount(opened.numberOfPages)
        } catch (failure: Throwable) {
            // PDFBox owns temporary resources after load; close even when the
            // document is rejected before the extractor instance is published.
            try { opened.close() } catch (_: Throwable) { /* preserve the rejection */ }
            throw failure
        }
        document = opened
    }

    val pageCount: Int get() = document.numberOfPages

    fun page(index: Int): String {
        require(index in 0 until pageCount)
        return PDFTextStripper().apply {
            setStartPage(index + 1)
            setEndPage(index + 1)
            setSortByPosition(true)
        }.getText(document).trim().replace("\u0000", "")
    }

    override fun close() = document.close()
}
