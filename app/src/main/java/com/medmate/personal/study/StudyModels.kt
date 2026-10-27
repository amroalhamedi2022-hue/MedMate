package com.medmate.personal.study

import java.text.Normalizer
import java.util.Locale

enum class StudyKind { HIGH_YIELD, FLASHCARD, MCQ }

/**
 * Canonical identity for answer choices. Collapse Unicode separators and
 * normalize canonical Unicode forms before case-folding so visually identical
 * options cannot slip through as distinct MCQ answers.
 */
object StudyOptionIdentity {
    private val whitespace = Regex("[\\p{Z}\\s]+")

    fun key(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
        .trim()
        .replace(whitespace, " ")
        .lowercase(Locale.ROOT)
}

data class StudyItem(
    val id: String,
    val chapterSha: String,
    val pageIndex: Int,
    val kind: StudyKind,
    val prompt: String,
    val answer: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val createdAt: Long,
    val review: ReviewState,
)

data class StudyCounts(
    val highYield: Int = 0,
    val flashcards: Int = 0,
    val mcqs: Int = 0,
    val due: Int = 0,
    val mistakes: Int = 0,
)

data class WrongAnswer(
    val item: StudyItem,
    val selectedIndex: Int,
    val timestamp: Long,
)

/** Guards against empty/wrong/mislabelled source-linked study content. */
object StudyValidation {
    fun check(
        kind: StudyKind,
        prompt: String,
        answer: String,
        options: List<String>,
        correctIndex: Int,
        explanation: String,
    ) {
        require(prompt.trim().length in 2..4000) { "اكتب نصًا واضحًا (2–4000 حرف)" }
        require(explanation.length <= 6000) { "الشرح طويل جدًا" }
        when (kind) {
            StudyKind.HIGH_YIELD -> Unit
            StudyKind.FLASHCARD -> require(answer.trim().length in 1..4000) { "البطاقة تحتاج إجابة" }
            StudyKind.MCQ -> {
                require(options.size in 4..5 && options.all { it.trim().isNotEmpty() && it.length <= 1000 }) {
                    "السؤال يحتاج أربعة أو خمسة اختيارات"
                }
                require(options.map { it.trim().lowercase() }.distinct().size == options.size) { "الاختيارات لازم تكون مختلفة" }
                require(correctIndex in options.indices) { "حدد الإجابة الصحيحة" }
            }
        }
    }
}
