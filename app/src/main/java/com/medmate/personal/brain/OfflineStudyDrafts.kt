package com.medmate.personal.brain

import java.security.MessageDigest
import java.util.Locale

/**
 * Strictly extractive study-item suggestions from indexed PDF text.
 * No generation, no medical inference, no automatically-approved answer keys.
 * A human must inspect the original PDF page before saving any suggestion.
 */
enum class DraftKind { QUOTED_NOTE, DEFINITION_CARD, SOURCE_MCQ }

data class OfflineDraft(
    val id: String,
    val kind: DraftKind,
    val chapterId: String,
    val pageIndex: Int,
    val prompt: String,
    val answer: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = -1,
    val explanation: String = "",
    val sourceQuote: String,
) {
    val pageNumber: Int get() = pageIndex + 1
}

object OfflineStudyDrafts {
    // Never infer a correct answer without a literal, well-formed answer key in the same page.
    private val questionHeader = Regex("^(?:(?:Q(?:uestion)?|س(?:ؤال)?)\\s*[-#.:]?\\s*)([0-9٠-٩۰-۹]{1,4})[.)\\s:：-]+(.{10,})$", RegexOption.IGNORE_CASE)
    // Printed Arabic medical MCQs often use أ/ب/ج/د/هـ rather than A-E.
    // Keep one canonical key space; never infer an answer from option order.
    private val optionLine = Regex("^([a-eA-Eأابجدهـ])[.)]\\s*(.{2,})$")
    private fun canonicalOption(raw: String): Char? = when (raw.lowercase(Locale.ROOT)) {
        "a", "أ", "ا" -> 'a'
        "b", "ب" -> 'b'
        "c", "ج" -> 'c'
        "d", "د" -> 'd'
        "e", "ه", "هـ" -> 'e'
        else -> null
    }
    private val answerLine = Regex("^(?:answer|correct answer|ans|الإجابة(?:\\s+الصحيحة)?|الاجابة(?:\\s+الصحيحة)?|الجواب|الحل)\\s*[:：-]\\s*([a-eA-Eأابجدهـ])\\s*[.)]?\\s*$", RegexOption.IGNORE_CASE)
    // Treat every printed answer label as a key attempt. A second key or an
    // unparseable key (e.g. "Answer: A and C") invalidates the whole MCQ.
    // Never silently keep an earlier valid key when the book is ambiguous.
    private val answerHeader = Regex("^(?:answer|correct answer|ans|الإجابة(?:\\s+الصحيحة)?|الاجابة(?:\\s+الصحيحة)?|الجواب|الحل)\\s*[:：-]", RegexOption.IGNORE_CASE)
    private val explanationLine = Regex("^(?:explanation|rationale|الشرح|التفسير|التعليل)\\s*[:：-]\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val definitionLine = Regex(
        "^([A-Z][A-Za-z0-9()/'’ -]{3,68}?)\\s+(?:is defined as|refers to|is characterized by|means)\\s+(.{18,360})$",
        RegexOption.IGNORE_CASE
    )
    // Arabic definition sentences are quoted verbatim; they are not inferred
    // clinical claims. Keep a deliberately narrow, line-anchored pattern.
    private val arabicDefinitionLine = Regex(
        "^([\\u0621-\\u064A][\\u0621-\\u064A\\u064B-\\u065F\\u0660-\\u0669\\s()/-]{2,76}?)\\s+(?:هو|هي|يعني|يُعرّف\\s+بأنه|تُعرّف\\s+بأنها)\\s+(.{18,360})$"
    )
    private val whitespace = Regex("\\s+")
    private val clinicalCues = Regex("\\b(?:diagnosis|symptoms?|complications?|risk|treatment|management|characterized|indicated|contraindicated|defined|caused|causes|results|involves|examined|ischemia|fracture)\\b|(?:التشخيص|الأعراض|المضاعفات|العلاج|يسبب|ينتج|يتميز)", RegexOption.IGNORE_CASE)

    fun extract(pages: List<PageSource>, limit: Int = 120): List<OfflineDraft> {
        require(limit in 1..500)
        val output = LinkedHashMap<String, OfflineDraft>()
        for (page in pages) {
            if (output.size >= limit) break
            val text = page.text.replace("\r\n", "\n").replace('\r', '\n')
            if (text.isBlank()) continue
            // Parse printed single-answer MCQs with four or five options. Skip multi-key and ambiguous questions.
            for (mcq in parseMcqs(page, text)) {
                output.putIfAbsent(mcq.id, mcq)
                if (output.size >= limit) break
            }
            if (output.size >= limit) break
            // Extract original candidate quotes, not AI summaries or guaranteed "high yield" facts.
            // Human selection is required before a quote becomes a saved HIGH_YIELD note.
            var quotesFromPage = 0
            for (paragraph in text.split(Regex("\\n\\s*\\n"))) {
                if (quotesFromPage >= 2 || output.size >= limit) break
                val unified = whitespace.replace(paragraph.trim(), " ")
                for (sentence in unified.split(Regex("(?<=[.!؟])\\s+"))) {
                    val quote = sentence.trim()
                    if (quote.length !in 65..450 || !clinicalCues.containsMatchIn(quote) || quote.count { it.isLetter() } < 45) continue
                    if (questionHeader.containsMatchIn(quote) || optionLine.containsMatchIn(quote)) continue
                    val draft = OfflineDraft(
                        id = fingerprint(page, DraftKind.QUOTED_NOTE, quote),
                        kind = DraftKind.QUOTED_NOTE,
                        chapterId = page.chapterId,
                        pageIndex = page.pageIndex,
                        prompt = quote,
                        sourceQuote = quote
                    )
                    if (output.putIfAbsent(draft.id, draft) == null) quotesFromPage++
                    break
                }
            }
            for (line in text.lineSequence().map { it.trim() }) {
                if (output.size >= limit) break
                val match = definitionLine.matchEntire(line.trimEnd('.', '۔', '،'))
                    ?: arabicDefinitionLine.matchEntire(line.trimEnd('.', '۔', '،')) ?: continue
                val term = match.groupValues[1].trim()
                val definition = match.groupValues[2].trim()
                if (term.split(' ').size > 7 || definition.length !in 18..360) continue
                val quote = line.take(650)
                val draft = OfflineDraft(
                    id = fingerprint(page, DraftKind.DEFINITION_CARD, quote),
                    kind = DraftKind.DEFINITION_CARD,
                    chapterId = page.chapterId,
                    pageIndex = page.pageIndex,
                    prompt = if (term.any { it in 'ء'..'ي' }) "ما معنى $term بحسب النص؟"
                        else "What does $term mean according to the source?",
                    answer = quote,
                    sourceQuote = quote
                )
                output.putIfAbsent(draft.id, draft)
            }
        }
        return output.values.take(limit)
    }

    private fun parseMcqs(page: PageSource, text: String): List<OfflineDraft> {
        data class State(
            val stem: String,
            val quoteLines: MutableList<String> = mutableListOf(),
            val options: LinkedHashMap<Char, String> = linkedMapOf(),
            var key: Char? = null,
            var invalidKey: Boolean = false,
            var explanation: String = "",
            var mode: String = "stem"
        )
        val drafts = mutableListOf<OfflineDraft>()
        var state: State? = null
        fun flush() {
            val s = state ?: return
            val letters = s.options.keys.toList()
            val key = s.key
            if ((letters == listOf('a', 'b', 'c', 'd') || letters == listOf('a', 'b', 'c', 'd', 'e')) && key != null && !s.invalidKey && key in letters &&
                s.stem.length in 12..1200 && s.options.values.all { it.length in 2..1000 } &&
                s.options.values.map { it.lowercase(Locale.ROOT).trim() }.distinct().size == letters.size
            ) {
                val quote = s.quoteLines.joinToString("\n").take(5500)
                drafts.add(OfflineDraft(
                    id = fingerprint(page, DraftKind.SOURCE_MCQ, s.stem + s.options.values.joinToString() + key),
                    kind = DraftKind.SOURCE_MCQ,
                    chapterId = page.chapterId,
                    pageIndex = page.pageIndex,
                    prompt = s.stem,
                    options = letters.map { s.options.getValue(it) },
                    correctIndex = letters.indexOf(key),
                    explanation = s.explanation.take(3000),
                    sourceQuote = quote
                ))
            }
            state = null
        }
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val header = questionHeader.matchEntire(line)
            if (header != null) {
                flush()
                state = State(header.groupValues[2].trim(), mutableListOf(line))
                continue
            }
            val s = state ?: continue
            if (s.quoteLines.size >= 40) { flush(); continue }
            s.quoteLines.add(line)
            val option = optionLine.matchEntire(line)
            val answer = answerLine.matchEntire(line)
            val explanation = explanationLine.matchEntire(line)
            when {
                option != null -> {
                    val letter = canonicalOption(option.groupValues[1]) ?: run { flush(); continue }
                    if (s.key != null || s.options.containsKey(letter)) { flush(); continue }
                    s.options[letter] = option.groupValues[2].trim()
                    s.mode = "option"
                }
                answer != null -> {
                    if (s.key != null) s.invalidKey = true
                    else s.key = canonicalOption(answer.groupValues[1])
                    s.mode = "answer"
                }
                answerHeader.containsMatchIn(line) -> {
                    s.invalidKey = true
                    s.mode = "answer"
                }
                explanation != null -> {
                    s.explanation = explanation.groupValues[1].trim()
                    s.mode = "explanation"
                }
                line.startsWith("Your answer", ignoreCase = true) -> s.mode = "ignore"
                s.mode == "option" && s.options.isNotEmpty() -> {
                    val k = s.options.keys.last()
                    s.options[k] = (s.options.getValue(k) + " " + line).take(1000)
                }
                s.mode == "explanation" -> s.explanation = (s.explanation + " " + line).take(3000).trim()
                s.mode == "stem" -> s.mode = "stem" // Don't append unknown standalone lines to a medical question.
            }
        }
        flush()
        return drafts
    }

    private fun fingerprint(page: PageSource, kind: DraftKind, text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("${page.chapterId}:${page.pageIndex}:${kind.name}:${whitespace.replace(text.trim(), " ")}".toByteArray())
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }
}
