package com.medmate.personal

import com.medmate.personal.brain.*
import org.junit.Assert.*
import org.junit.Test

class GeneratedDraftsTest {
    @Test fun sourcePageComesFromIndexNotAI() {
        val source = Evidence("book:11:0", "book", 11, "An indexed medical source excerpt.")
        val c = GeneratedCandidate(GeneratedKind.MCQ, "Which answer matches the passage?", "",
            listOf("First choice", "Second choice", "Third choice", "Fourth choice", "Fifth choice"),
            4, "Review the source.", "[S1]", "An indexed medical source excerpt.")
        val result = GeneratedStudyDrafts.validate(listOf(c), listOf(source))
        assertEquals(1, result.accepted.size)
        assertEquals(11, result.accepted.single().pageIndex)
        assertEquals(4, result.accepted.single().candidate.correctIndex)
    }

    @Test fun unknownCitationsAndInvalidKeysAreRejected() {
        val source = Evidence("book:0:0", "book", 0, "Real PDF text")
        val invalid = GeneratedCandidate(GeneratedKind.MCQ, "Which option is correct here?", "",
            listOf("First choice", "Second choice", "Third choice", "Fourth choice"), 6,
            "Must be checked", "S1")
        val unknown = invalid.copy(correctIndex = 0, sourceId = "S40")
        val result = GeneratedStudyDrafts.validate(listOf(invalid, unknown), listOf(source))
        assertEquals(0, result.accepted.size)
        assertEquals(2, result.rejected)
    }
}
