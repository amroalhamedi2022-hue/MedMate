import com.medmate.personal.brain.*

fun main() {
    val sources = listOf(Evidence("a", "chapter", 8, "The original source text."), Evidence("b", "chapter", 9, "Different source text."))
    val good = GeneratedCandidate(GeneratedKind.FLASHCARD, "What is the definition?", "A textual answer", sourceId = "S2", supportingQuote = "Different source text.")
    val mcq = GeneratedCandidate(GeneratedKind.MCQ, "What choice best applies?", "", listOf("First answer", "Second answer", "Third answer", "Fourth answer"), 1, "Reason to review", "[S1]", "The original source text.")
    val bad = good.copy(sourceId = "S99")
    val wrong = mcq.copy(correctIndex = 4)
    val inject = mcq.copy(options = listOf("a", "a", "a", "a"))
    val output = GeneratedStudyDrafts.validate(listOf(good, mcq, bad, wrong, inject, good), sources)
    check(output.accepted.size == 2 && output.rejected == 4) { output.toString() }
    check(output.accepted[0].pageIndex == 9)
    check(output.accepted[1].pageIndex == 8)
    check(GeneratedStudyDrafts.validate(listOf(good), emptyList()).accepted.isEmpty())
    check(GeneratedStudyDrafts.validate(listOf(good.copy(supportingQuote = "Completely invented quote")), sources).accepted.isEmpty())
    check(GeneratedStudyDrafts.validate(listOf(good.copy(supportingQuote = "")), sources).accepted.isEmpty())
    // Model output must never combine sources from separate PDF chapters.
    val mixedChapter = sources + Evidence("c", "another-chapter", 0, "A third valid source excerpt.")
    val mixed = GeneratedStudyDrafts.validate(listOf(good, mcq), mixedChapter)
    check(mixed.accepted.isEmpty() && mixed.rejected == 2) { "Cross-chapter evidence accepted: $mixed" }
    // A single-chapter batch is allowed to reference multiple pages independently.
    check(GeneratedStudyDrafts.validate(listOf(good, mcq), sources).accepted.size == 2)
    val fifth = mcq.copy(options = mcq.options + "Fifth answer", correctIndex = 4)
    check(GeneratedStudyDrafts.validate(listOf(fifth), sources).accepted.single().candidate.correctIndex == 4)
    println("PASS generated draft validation: canonical page mapping, unknown sources, invalid keys, duplicate and option rejection")
}
