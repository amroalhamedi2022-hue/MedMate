import com.medmate.personal.study.*

private fun item(kind: StudyKind, page: Int, prompt: String, answer: String = "",
                 options: List<String> = emptyList(), correct: Int = -1, explanation: String = "") =
    StudyItem("id-$page-$kind", "digest", page, kind, prompt, answer, options, correct,
        explanation, 1_700_000_000_000L, ReviewState(1_700_000_000_000L))

fun main() {
    val material = listOf(
        item(StudyKind.FLASHCARD, 8, "What is OA?\nCause?", "Degeneration\tof cartilage", explanation = "Read  page 9"),
        item(StudyKind.HIGH_YIELD, 0, "Important *point*", explanation = "Second line\nArabic: مفصل"),
        item(StudyKind.MCQ, 1, "Best test?", options = listOf("X-ray", "MRI", "CT", "None"), correct = 0),
        item(StudyKind.FLASHCARD, 9, "=HYPERLINK(\"bad\")", "+Not executed")
    )
    val tsv = StudyExport.ankiBasicTsv("Orthopedics", material)
    val rows = tsv.trimEnd('\n').split('\n')
    check(rows.size == 3)
    check(rows.all { it.count { ch -> ch == '\t' } == 1 })
    check(rows.none { it.contains('\r') })
    check(rows[0].contains("PDF page 2") && rows[1].contains("PDF page 9"))
    check(rows[1].contains("What is OA? Cause?"))
    check(rows[2].startsWith("'=HYPERLINK"))
    val notes = StudyExport.highYieldMarkdown("Ortho*pedics", material)
    check(notes.contains("Ortho\\*pedics"))
    check(notes.contains("## PDF page 1"))
    check(notes.contains("Important \\*point\\*"))
    check(notes.contains("Arabic: مفصل"))
    check(!tsv.contains("Important *point*"))
    check(runCatching { StudyExport.ankiBasicTsv("Empty", material.filter { it.kind == StudyKind.HIGH_YIELD }) }.isFailure)
    check(runCatching { StudyExport.highYieldMarkdown("Empty", emptyList()) }.isFailure)
    println("PASS: source page numbers, TSV rows/columns, Unicode, input sanitation, injection guard, Markdown exports, empty states")
}
