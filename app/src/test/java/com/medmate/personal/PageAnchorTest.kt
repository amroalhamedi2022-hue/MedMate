package com.medmate.personal

import com.medmate.personal.domain.PageAnchor
import org.junit.Assert.assertEquals
import org.junit.Test

class PageAnchorTest {
    @Test fun zeroBasedIndexDisplaysHumanPage() {
        val anchor = PageAnchor("a".repeat(64), 8)
        assertEquals(9, anchor.displayedPdfPage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativePage() { PageAnchor("a".repeat(64), -1) }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadTextRange() { PageAnchor("a".repeat(64), 0, 40, 20) }
}
