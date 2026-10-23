package com.medmate.personal

import com.medmate.personal.study.*
import org.junit.Assert.*
import org.junit.Test

class StudyFilterTest {
    private fun sample(kind: StudyKind = StudyKind.FLASHCARD) = StudyItem(
        "id", "sha", 8, kind, "Osteoarthritis symptoms", "Joint pain",
        if (kind == StudyKind.MCQ) listOf("pain", "fever", "rash", "shock") else emptyList(),
        if (kind == StudyKind.MCQ) 0 else -1, "crepitus", 10L, ReviewState(15L)
    )

    @Test fun filterMatchesSourcePageAndMedicalTerms() {
        val items = listOf(sample(), sample(StudyKind.MCQ))
        assertEquals(1, StudyFilter.select(items, StudyKind.FLASHCARD, 8, "joint PAIN").size)
        assertEquals(0, StudyFilter.select(items, StudyKind.FLASHCARD, 9).size)
        assertEquals(1, StudyFilter.select(items, StudyKind.MCQ, 8, "crepitus").size)
    }

    @Test fun gradedAnswerCannotChangeAfterReviews() {
        val reviewed = sample(StudyKind.MCQ)
        assertTrue(StudyRevisionPolicy.allowRevision(reviewed, "Joint pain", reviewed.options, 0, true))
        assertFalse(StudyRevisionPolicy.allowRevision(reviewed, "Joint pain", reviewed.options, 1, true))
        assertTrue(StudyRevisionPolicy.allowRevision(reviewed, "Joint pain", reviewed.options, 1, false))
    }
}
