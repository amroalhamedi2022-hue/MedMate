package com.medmate.personal

import com.medmate.personal.brain.*
import org.junit.Assert.*
import org.junit.Test

class OfflineDraftsTest {
    @Test fun fiveOptionBookMcqRemainsIntact() {
        val text = """Q179. What is the most characteristic finding?
            a) Wasting of the forearm.
            b) Flexion deformity of wrist.
            c) Extension of MCP joints.
            d) Weakness of the hand.
            e) Abnormal finger posture.
            Answer: E
            Explanation: Provided textbook key.
        """.trimIndent()
        val result = OfflineStudyDrafts.extract(listOf(PageSource("book", 9, text)))
        val item = result.single { it.kind == DraftKind.SOURCE_MCQ }
        assertEquals(5, item.options.size)
        assertEquals(4, item.correctIndex)
        assertEquals(9, item.pageIndex)
    }

    @Test fun duplicateOrConflictingPrintedAnswerKeysFailClosed() {
        val text = """Q10. Which finding is correct?
            a) Severe pain.
            b) Mild pain.
            c) No symptoms.
            d) Swelling.
            Answer: A
            Answer: B
        """.trimIndent()
        for (candidate in listOf(text, text.replace("Answer: B", "Answer: A"),
                text.replace("Answer: B", "Answer: A and C"))) {
            assertTrue(OfflineStudyDrafts.extract(listOf(PageSource("book", 3, candidate)))
                .none { it.kind == DraftKind.SOURCE_MCQ })
        }
    }

    @Test fun ambiguousKeyNeverBecomesQuestion() {
        val text = """Q1. Choose all applicable findings in this condition.
            a) First choice.
            b) Second choice.
            c) Third choice.
            d) Fourth choice.
            e) Fifth choice.
            Answer: B, E
        """.trimIndent()
        assertTrue(OfflineStudyDrafts.extract(listOf(PageSource("book", 2, text)))
            .none { it.kind == DraftKind.SOURCE_MCQ })
    }
    @Test fun arabicBookMcqWithArabicDigitsAndSourcePageIsExtracted() {
        val text = """سؤال ١٧. ما العلامة الأكثر ارتباطًا بنقص التروية في الطرف؟
            أ) ألم شديد مع برودة الطرف.
            ب) زيادة الشهية عند المريض.
            ج) تحسن الرؤية بعد النوم.
            د) زيادة كثافة الشعر.
            الإجابة الصحيحة: أ
            التفسير: مفتاح الإجابة مطبوع في المصدر.
        """.trimIndent()
        val quiz = OfflineStudyDrafts.extract(listOf(PageSource("arabic-book", 12, text)))
            .single { it.kind == DraftKind.SOURCE_MCQ }
        assertEquals(12, quiz.pageIndex)
        assertEquals(4, quiz.options.size)
        assertEquals(0, quiz.correctIndex)
        assertTrue(quiz.sourceQuote.contains("الإجابة الصحيحة: أ"))
        assertTrue(quiz.explanation.contains("مفتاح الإجابة"))
    }

    @Test fun arabicFiveChoiceMcqUsesFifthOptionWithoutInventingKey() {
        val text = """س ۲. أي الخيارات مذكور في المفتاح المطبوع للمصدر؟
            أ) الخيار الأول الوارد في السؤال.
            ب) الخيار الثاني الوارد في السؤال.
            ج) الخيار الثالث الوارد في السؤال.
            د) الخيار الرابع الوارد في السؤال.
            هـ) الخيار الخامس الوارد في السؤال.
            الجواب: هـ
        """.trimIndent()
        val quiz = OfflineStudyDrafts.extract(listOf(PageSource("arabic-book", 4, text)))
            .single { it.kind == DraftKind.SOURCE_MCQ }
        assertEquals(5, quiz.options.size)
        assertEquals(4, quiz.correctIndex)
    }

    @Test fun arabicConflictingOrMultipleKeysAreNeverApproved() {
        val base = """سؤال ٣. أي عبارة يحددها مفتاح الكتاب على أنها صحيحة؟
            أ) العبارة الأولى المطبوعة.
            ب) العبارة الثانية المطبوعة.
            ج) العبارة الثالثة المطبوعة.
            د) العبارة الرابعة المطبوعة.
            الإجابة: ب
        """.trimIndent()
        for (bad in listOf(base + "\nالإجابة: ج",
                base.replace("الإجابة: ب", "الإجابة: أ و ج"),
                base + "\nالجواب: ب")) {
            assertTrue(OfflineStudyDrafts.extract(listOf(PageSource("arabic-book", 2, bad)))
                .none { it.kind == DraftKind.SOURCE_MCQ })
        }
        assertEquals(1, OfflineStudyDrafts.extract(listOf(PageSource("arabic-book", 2, base)))
            .count { it.kind == DraftKind.SOURCE_MCQ })
    }

    @Test fun arabicDefinitionIsAQuotedDraftNotAnInferredFact() {
        val line = "نقص التروية هو انخفاض وصول الدم المحمل بالأكسجين إلى الأنسجة مما قد يؤدي إلى تلفها."
        val cards = OfflineStudyDrafts.extract(listOf(PageSource("arabic-book", 6, line)))
            .filter { it.kind == DraftKind.DEFINITION_CARD }
        assertEquals(1, cards.size)
        assertEquals(6, cards.single().pageIndex)
        assertTrue(cards.single().prompt.startsWith("ما معنى"))
        assertEquals(line, cards.single().sourceQuote)
        assertEquals(line, cards.single().answer)
    }

    @Test fun wrappedEnglishQuestionStemIsRecoveredWithoutChangingPrintedKey() {
        val text = """Q42. Which of
            the following findings is described by the printed source?
            a) First textbook choice.
            b) Second textbook choice.
            c) Third textbook choice.
            d) Fourth textbook choice.
            Answer: C
        """.trimIndent()
        val item = OfflineStudyDrafts.extract(listOf(PageSource("book", 10, text)))
            .single { it.kind == DraftKind.SOURCE_MCQ }
        assertEquals("Which of the following findings is described by the printed source?", item.prompt)
        assertEquals(2, item.correctIndex)
        assertEquals(10, item.pageIndex)
        assertTrue(item.sourceQuote.contains("the following findings"))
    }

    @Test fun wrappedArabicQuestionStemKeepsItsPageAndSourceKey() {
        val text = """سؤال ٤. أي من
            العلامات التالية وردت في النص الطبي المطبوع؟
            أ) العلامة الأولى المطبوعة.
            ب) العلامة الثانية المطبوعة.
            ج) العلامة الثالثة المطبوعة.
            د) العلامة الرابعة المطبوعة.
            الجواب: ب
        """.trimIndent()
        val item = OfflineStudyDrafts.extract(listOf(PageSource("chapter-ar", 7, text)))
            .single { it.kind == DraftKind.SOURCE_MCQ }
        assertEquals("أي من العلامات التالية وردت في النص الطبي المطبوع؟", item.prompt)
        assertEquals(1, item.correctIndex)
        assertEquals("chapter-ar", item.chapterId)
        assertEquals(7, item.pageIndex)
    }

    @Test fun excessiveStemContinuationCannotCreateQuestionFromUnrelatedPageText() {
        val text = """Q12. Which
            first stray line
            second stray line
            third stray line
            fourth stray line
            fifth stray line
            a) First printed option.
            b) Second printed option.
            c) Third printed option.
            d) Fourth printed option.
            Answer: A
        """.trimIndent()
        assertTrue(OfflineStudyDrafts.extract(listOf(PageSource("book", 3, text)))
            .none { it.kind == DraftKind.SOURCE_MCQ })
    }

    @Test fun wrappedQuestionWithConflictingPrintedKeysStillFailsClosed() {
        val text = """Q22. Which of
            these four choices is indicated in the printed source?
            a) First printed option.
            b) Second printed option.
            c) Third printed option.
            d) Fourth printed option.
            Answer: B
            Answer: B
        """.trimIndent()
        assertTrue(OfflineStudyDrafts.extract(listOf(PageSource("book", 2, text)))
            .none { it.kind == DraftKind.SOURCE_MCQ })
    }

}
