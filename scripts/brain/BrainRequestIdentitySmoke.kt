import com.medmate.personal.brain.BrainRequestIdentity

fun main() {
    val request = BrainRequestIdentity("sha-A", "fracture", true, 7)
    check(request.matches("sha-A", " fracture ", true, 7))
    check(!request.matches("sha-B", "fracture", true, 7))
    check(!request.matches("sha-A", "dislocation", true, 7))
    check(!request.matches("sha-A", "fracture", false, 7))
    check(!request.matches("sha-A", "fracture", true, 8))
    val wholeChapter = BrainRequestIdentity("sha-A", "fracture", false, 7)
    check(wholeChapter.matches("sha-A", "fracture", false, 8))
    check(!wholeChapter.matches("sha-B", "fracture", false, 7))
    println("PASS: 7 local/cloud async result source-context safety checks")
}
