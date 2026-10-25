package com.medmate.personal.brain

import org.junit.Assert.*
import org.junit.Test

class PdfTextCoverageTest {
    private val chapter = "a".repeat(64)
    private fun page(i: Int, text: String) = PageSource(chapter, i, text)

    @Test fun distinguishesUnindexedFromEmptyAndShortPages() {
        val report = PdfCoverageAudit.inspect(chapter, 6, listOf(
            page(0, "Fracture and dislocation require distinct clinical evaluation."),
            page(1, ""), page(3, "12"), page(5, "Peripheral nerves and vessels are at risk.")
        ))
        assertEquals(6, report.totalPages)
        assertEquals(4, report.indexedPages)
        assertEquals(2, report.readablePages)
        assertEquals(1, report.emptyPages)
        assertEquals(1, report.lowTextPages)
        assertEquals(2, report.missingPages)
        assertEquals(listOf(1, 2, 3, 4), report.firstUnusablePages)
        assertEquals(33, report.percentReadable)
        assertFalse(report.complete)
        assertFalse(report.readyForFullChapterSearch)
        assertTrue(report.hasSearchableEvidence)
    }

    @Test fun fullyIndexedReadableDocumentPassesCoverageGate() {
        val report = PdfCoverageAudit.inspect(chapter, 2, listOf(
            page(0, "A meaningful clinical paragraph in the textbook."),
            page(1, "A second chapter paragraph with traceable evidence.")
        ))
        assertTrue(report.complete)
        assertTrue(report.readyForFullChapterSearch)
        assertEquals(100, report.percentReadable)
        assertTrue(report.firstUnusablePages.isEmpty())
    }

    @Test fun scannedPdfIsNotMistakenForIndexedReadableText() {
        val report = PdfCoverageAudit.inspect(chapter, 3, listOf(page(0, ""), page(1, "   "), page(2, "3")))
        assertTrue(report.complete)
        assertEquals(0, report.readablePages)
        assertFalse(report.hasSearchableEvidence)
        assertEquals(2, report.emptyPages)
        assertEquals(1, report.lowTextPages)
        assertEquals(listOf(0, 1), report.firstUnusablePages.take(2))
    }

    @Test fun mixedChapterDuplicateAndOutOfRangeEvidenceFailClosed() {
        val good = page(0, "Clinical evidence with enough readable letters.")
        assertThrows(IllegalArgumentException::class.java) {
            PdfCoverageAudit.inspect(chapter, 2, listOf(good, good))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfCoverageAudit.inspect(chapter, 2, listOf(PageSource("other", 0, "Another chapter")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfCoverageAudit.inspect(chapter, 2, listOf(page(2, "Outside range")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfCoverageAudit.inspect(chapter, 0, emptyList())
        }
    }

    @Test fun denseReplacementGlyphsAndNullsAreNotCountedAsSearchableEvidence() {
        val damaged = "Fracture fracture " + "\uFFFD".repeat(12)
        val report = PdfCoverageAudit.inspect(chapter, 4, listOf(
            page(0, damaged),
            page(1, "Neurovascular examination is required for fracture assessment."),
            page(2, "Fracture of the radius may affect the wrist. \uFFFD")
        ))
        assertEquals(1, report.garbledPages)
        assertEquals(2, report.readablePages)
        assertEquals(3, report.indexedPages)
        assertEquals(listOf(0, 3), report.firstUnusablePages)
        assertFalse(report.readyForFullChapterSearch)
        assertTrue(PdfCoverageAudit.likelyCorruptedText("Long sentence \u0000\u0000 with PDF text"))
        assertFalse(PdfCoverageAudit.likelyCorruptedText(
            "One replacement \uFFFD inside an otherwise useful medical textbook paragraph."
        ))
        assertFalse(report.toString().contains("Neurovascular"))
    }

    @Test fun largePageCountsBoundNavigationAndNeverRetainPdfText() {
        val report = PdfCoverageAudit.inspect(chapter, 20_000,
            listOf(page(10, "Enough clinical source material to index.")), maxListed = 5)
        assertEquals(5, report.firstUnusablePages.size)
        assertEquals(listOf(0, 1, 2, 3, 4), report.firstUnusablePages)
        assertEquals(1, report.indexedPages)
        assertEquals(19_999, report.missingPages)
        assertFalse(report.toString().contains("clinical source material"))
    }
}
