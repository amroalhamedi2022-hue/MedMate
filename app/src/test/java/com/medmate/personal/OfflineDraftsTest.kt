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
}
