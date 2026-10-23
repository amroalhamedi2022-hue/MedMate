import com.medmate.personal.study.*

private fun item(
    kind: StudyKind, page: Int, prompt: String,
    answer: String = "", options: List<String> = emptyList(), correctIndex: Int = -1,
): StudyItem = StudyItem(
    id = "${kind.name}-$page", chapterSha = "sha", pageIndex = page, kind = kind,
    prompt = prompt, answer = answer, options = options,
    correctIndex = correctIndex, explanation = "التفسير: articular cartilage",
    createdAt = 100, review = ReviewState(200)
)

fun main() {
    val a = item(StudyKind.HIGH_YIELD, 8, "التهاب مفصل Osteoarthritis")
    val b = item(StudyKind.FLASHCARD, 8, "What is OA?", "Degenerative joint disease")
    val c = item(StudyKind.MCQ, 9, "Which disease involves cartilage?",
        options = listOf("Osteoarthritis", "Septic", "Gout", "RA"), correctIndex = 0)
    val list = listOf(a, b, c)
    check(StudyFilter.select(list, StudyKind.HIGH_YIELD).size == 1)
    check(StudyFilter.select(list, StudyKind.FLASHCARD, pageIndex = 9).isEmpty())
    check(StudyFilter.select(list, StudyKind.MCQ, query = "CARTILAGE osteo").single() == c)
    check(StudyFilter.select(list, StudyKind.HIGH_YIELD, query = "التهاب osteo").single() == a)
    check(StudyFilter.select(list, StudyKind.FLASHCARD, query = "DEGENERATIVE").single() == b)
    check(StudyFilter.select(list, StudyKind.MCQ, query = "not found").isEmpty())
    check(StudyFilter.select(list, StudyKind.HIGH_YIELD, query = " ").single() == a)
    check(StudyRevisionPolicy.allowRevision(b, b.answer, emptyList(), -1, hasReviews = true))
    check(!StudyRevisionPolicy.allowRevision(b, "A changed answer", emptyList(), -1, hasReviews = true))
    check(StudyRevisionPolicy.allowRevision(b, "A changed answer", emptyList(), -1, hasReviews = false))
    check(!StudyRevisionPolicy.allowRevision(c, "", c.options, 1, hasReviews = true))
    check(!StudyRevisionPolicy.allowRevision(c, "", listOf("Modified", "Septic", "Gout", "RA"), 0, hasReviews = true))
    check(StudyRevisionPolicy.allowRevision(c, "", c.options, 0, hasReviews = true))
    check(StudyRevisionPolicy.allowRevision(a, "anything", emptyList(), -1, hasReviews = true))
    try {
        StudyFilter.select(list, StudyKind.MCQ, pageIndex = -1)
        error("negative source page accepted")
    } catch (_: IllegalArgumentException) { /* expected */ }
    println("Study filtering/revision smoke: PASS")
}
