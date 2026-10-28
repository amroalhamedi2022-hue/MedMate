package com.medmate.personal.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedEvidenceIndexCacheEpochTest {
    @Test fun staleAmbiguousRequestCannotPurgeNewerChapterIndex() {
        val cache = BoundedEvidenceIndexCache(1_024)
        val staleEpoch = cache.beginRequest("older-pdf")
        val currentEpoch = cache.beginRequest("current-pdf")
        val currentPages = listOf(PageSource(
            "current-pdf", 0, "Current cardiac examination notes."
        ))
        cache.prepareSearch("current-pdf", currentPages, currentEpoch)
        val currentIndex = requireNotNull(
            cache.reusableIndex("current-pdf", currentPages, currentEpoch)
        )

        assertFalse("a superseded malformed request must not purge current evidence",
            cache.invalidateIfCurrent(staleEpoch))
        assertSame(currentIndex, cache.reusableIndex("current-pdf", currentPages, currentEpoch))
        assertEquals("current-pdf", cache.retainedChapterId)
        assertTrue(cache.retainedTextChars > 0L)
    }

    @Test fun currentAmbiguousRequestPurgesItsIndexAndAllowsAFreshRetry() {
        val cache = BoundedEvidenceIndexCache(1_024)
        val pages = listOf(PageSource(
            "ambiguous-pdf", 0, "Synthetic text used only for cache lifecycle tests."
        ))
        val firstEpoch = cache.beginRequest("ambiguous-pdf")
        cache.prepareSearch("ambiguous-pdf", pages, firstEpoch)
        assertNotNull(cache.reusableIndex("ambiguous-pdf", pages, firstEpoch))

        assertTrue(cache.invalidateIfCurrent(firstEpoch))
        assertNull(cache.retainedChapterId)
        assertEquals(0L, cache.retainedTextChars)
        assertNull("the invalidated epoch cannot repopulate the cache",
            cache.reusableIndex("ambiguous-pdf", pages, firstEpoch))

        val retryEpoch = cache.beginRequest("ambiguous-pdf")
        cache.prepareSearch("ambiguous-pdf", pages, retryEpoch)
        assertNotNull("a fresh valid request may build the index again",
            cache.reusableIndex("ambiguous-pdf", pages, retryEpoch))
        assertEquals("ambiguous-pdf", cache.retainedChapterId)
    }
}
