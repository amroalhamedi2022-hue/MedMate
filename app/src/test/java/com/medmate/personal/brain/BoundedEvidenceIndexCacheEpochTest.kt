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

    @Test fun supersededRequestSkipsTraversalOfItsStalePageList() {
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

        // Any attempt to size or compare this old page list throws. Once a newer
        // request owns the epoch, neither preparation nor cache reuse should walk it.
        val stalePages = object : AbstractList<PageSource>() {
            override val size: Int
                get() = error("superseded request traversed stale PDF pages")
            override fun get(index: Int): PageSource =
                error("superseded request traversed stale PDF pages")
        }
        cache.prepareSearch("older-pdf", stalePages, staleEpoch)
        assertNull(cache.reusableIndex("older-pdf", stalePages, staleEpoch))
        assertSame(currentIndex, cache.reusableIndex("current-pdf", currentPages, currentEpoch))
        assertEquals("current-pdf", cache.retainedChapterId)
    }


    @Test fun requestSupersededDuringPageSizingCannotPublishIndex() {
        val cache = BoundedEvidenceIndexCache(1_024)
        val racingEpoch = cache.beginRequest("racing-pdf")
        val racingPages = object : AbstractList<PageSource>() {
            override val size: Int get() = 1
            override fun get(index: Int): PageSource {
                cache.beginRequest("newer-pdf")
                return PageSource("racing-pdf", 0, "This list supersedes itself while being sized.")
            }
        }

        // The request becomes stale during O(n) sizing. The final locked epoch
        // check must refuse to publish an index built from the older snapshot.
        assertNull(cache.reusableIndex("racing-pdf", racingPages, racingEpoch))
        assertNull(cache.retainedChapterId)
        assertEquals(0L, cache.retainedTextChars)
    }


    @Test fun staleAmbiguousRequestCannotPurgeNewerIndexForSameChapter() {
        val cache = BoundedEvidenceIndexCache(1_024)
        val chapterId = "same-pdf-chapter"
        val stalePages = listOf(PageSource(
            chapterId, 0, "Older extracted page text from the same PDF chapter."
        ))
        val staleEpoch = cache.beginRequest(chapterId)
        cache.prepareSearch(chapterId, stalePages, staleEpoch)
        assertNotNull(cache.reusableIndex(chapterId, stalePages, staleEpoch))

        // A newer question can target the same chapter but carry a fresh
        // extracted-page snapshot. The old request must not invalidate it.
        val currentPages = listOf(PageSource(
            chapterId, 0, "Current page snapshot for the newer question."
        ))
        val currentEpoch = cache.beginRequest(chapterId)
        cache.prepareSearch(chapterId, currentPages, currentEpoch)
        val currentIndex = requireNotNull(
            cache.reusableIndex(chapterId, currentPages, currentEpoch)
        )

        assertFalse(
            "an old ambiguous result must not purge a newer request for the same chapter",
            cache.invalidateIfCurrent(staleEpoch)
        )
        assertSame(currentIndex, cache.reusableIndex(chapterId, currentPages, currentEpoch))
        assertEquals(chapterId, cache.retainedChapterId)
        assertEquals(currentPages.sumOf { it.text.length.toLong() }, cache.retainedTextChars)
    }
}
