package com.medmate.personal.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class BoundedEvidenceIndexCacheTest {
    @Test fun identicalChapterAndPagesReuseTheBuiltIndex() {
        val cache = BoundedEvidenceIndexCache(1_000)
        val pages = listOf(PageSource("chapter-a", 0,
            "Fracture management requires a careful neurovascular examination."))
        val first = cache.reusableIndex("chapter-a", pages)
        assertNotNull(first)
        assertSame(first, cache.reusableIndex("chapter-a", pages))
        assertEquals("chapter-a", cache.retainedChapterId)
        assertEquals(pages.sumOf { it.text.length.toLong() }, cache.retainedTextChars)
    }

    @Test fun switchingToAnotherBoundedChapterReleasesThePreviousIndex() {
        val cache = BoundedEvidenceIndexCache(1_000)
        val firstPages = listOf(PageSource("private-pdf-a", 0,
            "A first private document with fracture notes."))
        val secondPages = listOf(PageSource("private-pdf-b", 0,
            "A second private document with cardiac notes."))
        val first = cache.reusableIndex("private-pdf-a", firstPages)
        val second = cache.reusableIndex("private-pdf-b", secondPages)
        assertNotNull(first)
        assertNotNull(second)
        assertNotSame(first, second)
        assertEquals("private-pdf-b", cache.retainedChapterId)
        assertEquals(secondPages.sumOf { it.text.length.toLong() }, cache.retainedTextChars)
    }

    @Test fun oversizedChapterEvictsPreviousPdfBeforeUncachedFallback() {
        val cache = BoundedEvidenceIndexCache(64)
        val small = listOf(PageSource("previous-pdf", 0,
            "Private notes that would otherwise remain indexed."))
        assertNotNull(cache.reusableIndex("previous-pdf", small))
        assertEquals("previous-pdf", cache.retainedChapterId)

        val oversized = listOf(PageSource("large-pdf", 0, "x".repeat(65)))
        assertNull(cache.reusableIndex("large-pdf", oversized))
        assertNull("previous chapter text must not remain cached", cache.retainedChapterId)
        assertEquals(0L, cache.retainedTextChars)
    }

    @Test fun stalePdfIsEvictedBeforeAnEmptyOrInvalidQueryCanReturn() {
        val cache = BoundedEvidenceIndexCache(1_000)
        val first = listOf(PageSource("private-pdf-a", 0, "A prior private PDF chapter."))
        assertNotNull(cache.reusableIndex("private-pdf-a", first))

        // StudyBrain calls this before returning TOO_SHORT or MISSING_TEXT.
        cache.evictIfStale("private-pdf-b", listOf(PageSource("private-pdf-b", 0, "")))
        assertNull("new chapter must not retain the previous PDF", cache.retainedChapterId)
        assertEquals(0L, cache.retainedTextChars)
    }

    @Test fun unchangedActivePdfKeepsItsIndexButChangedTextEvictsIt() {
        val cache = BoundedEvidenceIndexCache(1_000)
        val original = listOf(PageSource("same-pdf", 0, "Original searchable text."))
        val index = requireNotNull(cache.reusableIndex("same-pdf", original))
        cache.evictIfStale("same-pdf", original)
        assertSame(index, cache.reusableIndex("same-pdf", original))

        cache.evictIfStale("same-pdf", listOf(PageSource("same-pdf", 0, "Replaced page text.")))
        assertNull("changed page content must invalidate old retained text", cache.retainedChapterId)
        assertEquals(0L, cache.retainedTextChars)
    }

    @Test fun exactCharacterLimitIsCacheableButOneCharacterOverClearsIt() {
        val cache = BoundedEvidenceIndexCache(32)
        val exact = listOf(PageSource("boundary", 0, "x".repeat(32)))
        assertNotNull(cache.reusableIndex("boundary", exact))
        assertEquals(32L, cache.retainedTextChars)

        val over = listOf(PageSource("over-boundary", 0, "x".repeat(33)))
        assertNull(cache.reusableIndex("over-boundary", over))
        assertNull(cache.retainedChapterId)
        assertEquals(0L, cache.retainedTextChars)
    }
}
