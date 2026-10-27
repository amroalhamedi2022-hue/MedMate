package com.medmate.personal.reader

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Executes PDFBox-Android text extraction on a synthetic in-memory-generated PDF.
 * This proves page scoping on the JVM, not GGUF inference or physical-device performance. */
@RunWith(RobolectricTestRunner::class)
class PdfTextExtractorTest {
    @Test
    fun extractionIsPageScopedAndRejectsOutOfRangeNavigation() {
        val context = RuntimeEnvironment.getApplication<Application>()
        PDFBoxResourceLoader.init(context.applicationContext)
        val pdf = File.createTempFile("medmate-page-scope-", ".pdf", context.cacheDir)
        try {
            PDDocument().use { document ->
                val first = PDPage()
                document.addPage(first)
                PDPageContentStream(document, first).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(40f, 700f)
                    stream.showText("PAGE1-EXCLUDE-SYNTHETIC")
                    stream.endText()
                }
                val second = PDPage()
                document.addPage(second)
                PDPageContentStream(document, second).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(40f, 700f)
                    stream.showText("PAGE2-CALIBRATION-SYNTHETIC")
                    stream.endText()
                }
                document.save(pdf)
            }

            PdfTextExtractor(context, pdf).use { extractor ->
                assertEquals(2, extractor.pageCount)
                val page1 = extractor.page(0)
                val page2 = extractor.page(1)
                assertTrue(page1.contains("PAGE1-EXCLUDE-SYNTHETIC"))
                assertFalse(page1.contains("PAGE2-CALIBRATION-SYNTHETIC"))
                assertTrue(page2.contains("PAGE2-CALIBRATION-SYNTHETIC"))
                assertFalse(page2.contains("PAGE1-EXCLUDE-SYNTHETIC"))
                assertThrows(IllegalArgumentException::class.java) { extractor.page(2) }
            }
        } finally {
            pdf.delete()
        }
    }
}
