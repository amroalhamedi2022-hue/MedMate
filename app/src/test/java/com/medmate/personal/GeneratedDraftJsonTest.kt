package com.medmate.personal

import com.medmate.personal.brain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeneratedDraftJsonTest {
    private val source = listOf(Evidence("chapter:3:0", "chapter", 3,
        "An original study sentence describing fractures in the source chapter."))

    @Test fun quoteGroundedLocalAndCloudDraftsUseTheSameValidation() {
        val raw = """{"items":[{"kind":"FLASHCARD","prompt":"What is described by the source?","answer":"A fracture concept","source_id":"S1","supporting_quote":"An original study sentence describing fractures","options":[],"correct_index":-1,"explanation":""}]}"""
        val result = GeneratedDraftJson.parseAndValidate(raw, source)
        assertEquals(1, result.accepted.size)
        assertEquals(3, result.accepted.single().pageIndex)
        assertEquals("An original study sentence describing fractures", result.accepted.single().sourceQuote)
    }
    @Test fun fabricatedOrMissingQuotesAreRejected() {
        val invented = """{"items":[{"kind":"HIGH_YIELD","prompt":"Clinical point","answer":"","source_id":"S1","supporting_quote":"fabricated evidence not present anywhere","options":[],"correct_index":-1}]}"""
        assertEquals(0, GeneratedDraftJson.parseAndValidate(invented, source).accepted.size)
        assertEquals(1, GeneratedDraftJson.parseAndValidate(invented, source).rejected)
    }
    @Test fun rejectsOversizedMalformedOutputAndInvalidKind() {
        val invalid = """{"items":[{"kind":"UNSUPPORTED","prompt":"Something"}]}"""
        assertEquals(1, GeneratedDraftJson.parseAndValidate(invalid, source).rejected)
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedDraftJson.parseAndValidate("not-json", source)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedDraftJson.parseAndValidate("x".repeat(120_001), source)
        }
    }
}
