import com.medmate.personal.study.*
fun main() {
  val now = 1_000_000_000L
  val s = ReviewState(now)
  val good = ReviewPlanner.next(s, 2, now)
  check(good.dueAt == now + ReviewPlanner.DAY_MS && good.intervalDays == 1)
  val review = ReviewPlanner.next(good, 2, now + ReviewPlanner.DAY_MS)
  check(review.intervalDays == 3)
  val again = ReviewPlanner.next(review, 0, now + 4 * ReviewPlanner.DAY_MS)
  check(again.intervalDays == 0 && again.lapses == 1)
  val easy = ReviewPlanner.next(s, 3, now)
  check(easy.intervalDays == 4)
  StudyValidation.check(StudyKind.MCQ, "What is the answer?", "", listOf("One", "Two", "Three", "Four"), 1, "")
  StudyValidation.check(StudyKind.MCQ, "Five-choice question?", "", listOf("One", "Two", "Three", "Four", "Five"), 4, "")
  var outOfBounds = false
  try { StudyValidation.check(StudyKind.MCQ, "Five-choice question?", "", listOf("One", "Two", "Three", "Four", "Five"), 5, "") } catch (e: IllegalArgumentException) { outOfBounds = true }
  check(outOfBounds)
  var duplicate = false
  try { StudyValidation.check(StudyKind.MCQ, "Example", "", listOf("A", "A", "C", "D"), 0, "") } catch (e: IllegalArgumentException) { duplicate = true }
  check(duplicate)
  println("PASS: scheduling (Good x2, Again, Easy), MCQ validation and duplicate rejection")
}
