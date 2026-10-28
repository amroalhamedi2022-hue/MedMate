package com.medmate.personal.study

/**
 * Portable, completely offline study exports. Does not copy any source PDF.
 * Anki output is a plain UTF-8, tab-separated Basic-note import (Front, Back).
 * No HTML, formula execution or external dependencies are involved.
 */
object StudyExport {
    private val quizKinds = setOf(StudyKind.FLASHCARD, StudyKind.MCQ)

    fun ankiBasicTsv(chapterTitle: String, items: List<StudyItem>): String {
        val questions = items.filter { it.kind in quizKinds }.sortedWith(
            compareBy<StudyItem> { it.pageIndex }.thenBy { it.createdAt }.thenBy { it.id }
        )
        require(questions.isNotEmpty()) { "ما في بطاقات أو MCQ للتصدير بهذا الشبتر" }
        return buildString {
            questions.forEach { item ->
                val source = "MedMate | ${clean(chapterTitle)} | PDF page ${item.pageIndex + 1}"
                val front = if (item.kind == StudyKind.MCQ) {
                    "${clean(item.prompt)} | " + item.options.mapIndexed { i, option ->
                        "${'A' + i}) ${clean(option)}"
                    }.joinToString(" | ")
                } else clean(item.prompt)
                val answer = if (item.kind == StudyKind.MCQ) {
                    val index = item.correctIndex
                    require(index in item.options.indices) { "إجابة MCQ غير صالحة" }
                    "Correct: ${'A' + index}) ${clean(item.options[index])}"
                } else clean(item.answer)
                val back = listOfNotNull(
                    answer,
                    item.explanation.takeIf { it.isNotBlank() }?.let { "Explanation: ${clean(it)}" },
                    source
                ).joinToString(" | ")
                append(cell(front)).append('\t').append(cell(back)).append('\n')
            }
        }
    }

    fun highYieldMarkdown(chapterTitle: String, items: List<StudyItem>): String {
        val points = items.filter { it.kind == StudyKind.HIGH_YIELD }
            .sortedWith(compareBy<StudyItem> { it.pageIndex }.thenBy { it.createdAt }.thenBy { it.id })
        require(points.isNotEmpty()) { "ما في نقاط High-Yield للتصدير بهذا الشبتر" }
        return buildString {
            append("# ").append(markdown(chapterTitle)).append(" — High-Yield\n\n")
            append("Personal study notes · Linked to original PDF page numbers.\n\n")
            points.groupBy { it.pageIndex }.forEach { (page, entries) ->
                append("## PDF page ").append(page + 1).append("\n\n")
                entries.forEach { item ->
                    append("- **").append(markdown(item.prompt)).append("**\n")
                    if (item.explanation.isNotBlank()) {
                        append("  - ").append(markdown(item.explanation)).append("\n")
                    }
                }
                append('\n')
            }
        }
    }

    /** Keep one line/field for Anki; control bytes must not invent TSV records. */
    private fun clean(value: String): String = value
        .replace(Regex("[\\p{Cc}\\p{Zl}\\p{Zp}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Also safe to open exports with spreadsheet apps (formula injection guard). */
    private fun cell(value: String): String {
        val v = clean(value)
        return if (v.startsWith('=') || v.startsWith('+') || v.startsWith('-') || v.startsWith('@')) "'$v" else v
    }

    private fun markdown(value: String): String = value.trim()
        .replace("\\", "\\\\")
        .replace(Regex("([*`_\\[\\]#])"), "\\\\$1")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\n", "  \n")
}
