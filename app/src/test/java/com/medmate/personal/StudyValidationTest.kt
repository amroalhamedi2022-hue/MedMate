package com.medmate.personal

import com.medmate.personal.study.StudyKind
import com.medmate.personal.study.StudyValidation
import com.medmate.personal.study.StudyOptionIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyValidationTest {
    @Test fun optionIdentityNormalizesUnicodeWhitespaceAndCanonicalForms() {
        assertEquals(StudyOptionIdentity.key("Femur"), StudyOptionIdentity.key("\u00A0Femur\u00A0"))
        assertEquals(StudyOptionIdentity.key("Café"),
            StudyOptionIdentity.key(java.text.Normalizer.normalize("Café", java.text.Normalizer.Form.NFD)))
    }

    @Test fun optionIdentityFoldsCompatibilityFormsAndInvisibleControls() {
        assertEquals(StudyOptionIdentity.key("fibula"), StudyOptionIdentity.key("ﬁbula"))
        assertEquals(StudyOptionIdentity.key("Femur"), StudyOptionIdentity.key("Fe\u200Bmur"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroWidthDisguisedDuplicateOptions() {
        StudyValidation.check(StudyKind.MCQ, "Which bone?", "",
            listOf("Femur", "Fe\u200Bmur", "Fibula", "Humerus"), 0, "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCompatibilityLigatureDuplicateOptions() {
        StudyValidation.check(StudyKind.MCQ, "Which bone?", "",
            listOf("Fibula", "ﬁbula", "Femur", "Humerus"), 0, "")
    }

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
            listOf("Café", java.text.Normalizer.normalize("Café", java.text.Normalizer.Form.NFD), "Fibula", "Humerus"), 0, "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnicodeSeparatorOnlyOption() {
        assertEquals("", StudyOptionIdentity.key("\u00A0\u00A0"))
        StudyValidation.check(StudyKind.MCQ, "Which bone?", "",
            listOf("Femur", "\u00A0\u00A0", "Fibula", "Humerus"), 0, "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun flashcardNeedsAnswer() {
        StudyValidation.check(StudyKind.FLASHCARD, "What is OA?", "", emptyList(), -1, "")
    }
}
