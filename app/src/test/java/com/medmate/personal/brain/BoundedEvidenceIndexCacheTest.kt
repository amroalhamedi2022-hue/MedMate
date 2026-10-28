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

    @Test fun olderSearchCannotPopulateCacheAfterANewerChapterStarts() {
        val cache = BoundedEvidenceIndexCache(1_000)
        val oldPages = listOf(PageSource("older-pdf", 0, "Older private fracture notes."))
        val newPages = listOf(PageSource("current-pdf", 0, "Current cardiac examination notes."))

        val oldEpoch = cache.beginSearch("older-pdf", oldPages)
        val newEpoch = cache.beginSearch("current-pdf", newPages)

        assertNull(cache.reusableIndex("older-pdf", oldPages, oldEpoch))
        assertNull("superseded search must not populate the shared cache", cache.retainedChapterId)
        assertNotNull(cache.reusableIndex("current-pdf", newPages, newEpoch))
        assertEquals("current-pdf", cache.retainedChapterId)
    }

    @Test fun supersededSearchCannotReplaceAnAlreadyCachedNewerChapter() {
        val cache = BoundedEvidenceIndexCache(1_000)
        val oldPages = listOf(PageSource("older-pdf", 0, "Older private fracture notes."))
        val newPages = listOf(PageSource("current-pdf", 0, "Current cardiac examination notes."))

        val oldEpoch = cache.beginSearch("older-pdf", oldPages)
        assertNotNull(cache.reusableIndex("older-pdf", oldPages, oldEpoch))
        val newEpoch = cache.beginSearch("current-pdf", newPages)
        val currentIndex = requireNotNull(cache.reusableIndex("current-pdf", newPages, newEpoch))

        assertNull(cache.reusableIndex("older-pdf", oldPages, oldEpoch))
        assertSame(currentIndex, cache.reusableIndex("current-pdf", newPages, newEpoch))
        assertEquals("current-pdf", cache.retainedChapterId)
    }

    @Test fun failClosedInvalidationClearsPrivateTextAndSupersedesInflightSearches() {
        val cache = BoundedEvidenceIndexCache(1_000)
        val privatePages = listOf(PageSource("private-pdf", 0, "Private fracture notes that must not remain cached."))
        val requestEpoch = cache.beginSearch("private-pdf", privatePages)
        assertNotNull(cache.reusableIndex("private-pdf", privatePages, requestEpoch))
        assertEquals("private-pdf", cache.retainedChapterId)

        // StudyBrain invokes this when duplicate/negative page identities make
        // [S#] citation anchors ambiguous and the request must fail closed.
        cache.invalidate()

        assertNull("untrusted citation identity must purge retained PDF text", cache.retainedChapterId)
        assertEquals(0L, cache.retainedTextChars)
        assertNull("an older in-flight request cannot repopulate after invalidation",
            cache.reusableIndex("private-pdf", privatePages, requestEpoch))
    }
    @Test fun staleOversizedSearchCannotEvictANewerChapter() {
        val cache = BoundedEvidenceIndexCache(64)
        val oldPages = listOf(PageSource("older-pdf", 0, "x".repeat(65)))
        val oldEpoch = cache.beginSearch("older-pdf", oldPages)
        val currentPages = listOf(PageSource("current-pdf", 0,
            "Current chapter text that should remain indexed."))
        val currentEpoch = cache.beginSearch("current-pdf", currentPages)
        val currentIndex = requireNotNull(cache.reusableIndex("current-pdf", currentPages, currentEpoch))

        // The older oversized request completes late. It must not evict the newer cache entry.
        assertNull(cache.reusableIndex("older-pdf", oldPages, oldEpoch))
        assertSame(currentIndex, cache.reusableIndex("current-pdf", currentPages, currentEpoch))
        assertEquals("current-pdf", cache.retainedChapterId)
    }

    @Test fun olderRequestDelayedBeforePreparationCannotEvictNewerChapter() {
        val cache = BoundedEvidenceIndexCache(1_000)
        val olderEpoch = cache.beginRequest("older-pdf")
        val currentEpoch = cache.beginRequest("current-pdf")
        val currentPages = listOf(PageSource("current-pdf", 0,
            "Current chapter text that should remain indexed."))
        cache.prepareSearch("current-pdf", currentPages, currentEpoch)
        val currentIndex = requireNotNull(cache.reusableIndex("current-pdf", currentPages, currentEpoch))

        // Simulate an older call reaching content preparation only after the newer
        // request has already built its index.
        val olderPages = listOf(PageSource("older-pdf", 0, "Older private fracture notes."))
        cache.prepareSearch("older-pdf", olderPages, olderEpoch)

        assertNull(cache.reusableIndex("older-pdf", olderPages, olderEpoch))
        assertSame(currentIndex, cache.reusableIndex("current-pdf", currentPages, currentEpoch))
        assertEquals("current-pdf", cache.retainedChapterId)
    }

}
