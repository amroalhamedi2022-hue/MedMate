package com.medmate.personal.reader

import com.medmate.personal.brain.Evidence
import com.medmate.personal.brain.GroundedRequest
import com.medmate.personal.brain.MedicalPromptBuilder
import com.medmate.personal.brain.SourceCitationLinks
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
        // Robolectric's RuntimeEnvironment API returns Application directly; it is not generic.
        val context: Application = RuntimeEnvironment.getApplication()
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
                    stream.showText("PAGE1-EXCLUDE-SYNTHETIC. This first page must not be selected for second-page citation routing.")
                    stream.endText()
                }
                val second = PDPage()
                document.addPage(second)
                PDPageContentStream(document, second).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(40f, 700f)
                    stream.showText("PAGE2-CALIBRATION-SYNTHETIC. This second page contains the unique calibration marker for citation routing.")
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

                // Exercise the production chain from PDFBox page extraction to
                // the grounded prompt's page label and the tappable citation projection.
                // This is real PDFBox text extraction, but deliberately not model inference.
                val evidence = Evidence(
                    id = "synthetic-pdf-two-page:1:0",
                    chapterId = "synthetic-pdf-two-page",
                    pageIndex = 1,
                    text = page2
                )
                val prepared = MedicalPromptBuilder.prepare(
                    GroundedRequest(
                        chapterId = "synthetic-pdf-two-page",
                        query = "Which calibration marker appears on page two?",
                        evidence = listOf(evidence),
                        maxEvidenceChars = 1000
                    )
                )
                assertTrue(prepared.prompt.contains("[S1] Page 2"))
                assertFalse(prepared.prompt.contains("PAGE1-EXCLUDE-SYNTHETIC"))
                val links = SourceCitationLinks.resolve(
                    "Page two contains PAGE2-CALIBRATION-SYNTHETIC [S1].",
                    prepared.presentedEvidence
                )
                assertEquals(listOf(1), links.map { it.pageIndex })
                assertEquals(listOf(2), links.map { it.pageNumber })
                assertThrows(IllegalArgumentException::class.java) { extractor.page(2) }
            }
        } finally {
            pdf.delete()
        }
    }
}
