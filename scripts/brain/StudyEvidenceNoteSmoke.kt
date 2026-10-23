import com.medmate.personal.brain.StudyEvidenceNote

fun main() {
    val quote = "This sentence appeared verbatim in the original medical chapter."
    val saved = StudyEvidenceNote.withSource("شرح موجز", quote)
    check(saved.contains(quote))
    check(saved.contains("شرح موجز"))
    check(StudyEvidenceNote.withSource("", quote).contains(quote))
    check(runCatching { StudyEvidenceNote.withSource("", "short") }.isFailure)
    check(runCatching { StudyEvidenceNote.withSource("z".repeat(6000), quote) }.isFailure)
    println("PASS saved-source metadata: 5 checks")
}
