package com.medmate.personal.reader

import android.content.Context
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
        document = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir))
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
