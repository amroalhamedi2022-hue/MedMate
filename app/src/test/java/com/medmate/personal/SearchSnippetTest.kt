package com.medmate.personal

import com.medmate.personal.domain.SearchSnippet
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSnippetTest {
    @Test fun returnsContextNearMatch() {
        val text = "intro ".repeat(60) + "Osteoarthritis is degenerative" + " tail".repeat(30)
        val snippet = SearchSnippet.make(text, "osteoarthritis")
        assertTrue(snippet.contains("Osteoarthritis"))
        assertTrue(snippet.length <= 172)
    }

    @Test fun normalizesPageWhitespace() {
        assertEquals("fracture healing", SearchSnippet.make("fracture\n\n healing", "healing"))
    }

    @Test fun handlesArabicAndEmptyText() {
        assertTrue(SearchSnippet.make("العظم والغضروف والمفاصل", "الغضروف").contains("الغضروف"))
        assertEquals("", SearchSnippet.make("", "joint"))
    }
}
