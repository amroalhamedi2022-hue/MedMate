package com.medmate.personal

import com.medmate.personal.study.StudyKind
import com.medmate.personal.study.StudyValidation
import org.junit.Test

class StudyValidationTest {
    @Test fun acceptsManualMcq() {
        StudyValidation.check(StudyKind.MCQ, "Which bone?", "", listOf("Femur", "Tibia", "Fibula", "Humerus"), 1, "Study reference")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateOptions() {
        StudyValidation.check(StudyKind.MCQ, "Which bone?", "", listOf("Femur", "Femur", "Fibula", "Humerus"), 0, "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWhitespaceEquivalentOptions() {
        StudyValidation.check(StudyKind.MCQ, "Which phrase?", "", 
            listOf("Femur fracture", "Femur   fracture", "Fibula", "Humerus"), 0, "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCanonicallyEquivalentUnicodeOptions() {
        StudyValidation.check(StudyKind.MCQ, "Which word?", "",
            listOf("Café", "Cafe\\u0301", "Fibula", "Humerus"), 0, "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun flashcardNeedsAnswer() {
        StudyValidation.check(StudyKind.FLASHCARD, "What is OA?", "", emptyList(), -1, "")
    }
}
