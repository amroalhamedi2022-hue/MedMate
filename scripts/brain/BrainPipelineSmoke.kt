import com.medmate.personal.brain.*

fun main() {
    val pages = listOf(
        PageSource("ortho", 0, "Volkmann's contracture is an ischemic flexor muscle contracture after compartment syndrome."),
        PageSource("ortho", 1, "A fracture is a break in continuity of bone. A displaced fracture can be comminuted."),
        PageSource("ortho", 2, "Treatment of a dislocation requires proper clinical evaluation."),
        PageSource("surgery", 0, "Fracture of mandible, no relation to this orthopedics chapter."),
    )
    var checks = 0
    fun verified(cond: Boolean, label: String) { check(cond) { label }; checks++ }
    val english = StudyBrain.search("ortho", pages, "Explain Volkmann's contracture")
    verified(english.status == BrainSearchStatus.FOUND, "English evidence")
    verified(english.evidence.first().pageNumber == 1, "page anchor")
    verified(english.evidence.all { it.chapterId == "ortho" }, "chapter isolation")
    val arabic = StudyBrain.search("ortho", pages, "اشرحلي الكسر")
    verified(arabic.status == BrainSearchStatus.FOUND && arabic.evidence.first().pageNumber == 2, "Arabic synonym")
    val arabic2 = StudyBrain.search("ortho", pages, "شو الخلع؟")
    verified(arabic2.evidence.firstOrNull()?.pageNumber == 3, "dislocation bridge")
    verified(StudyBrain.search("ortho", pages, "Volkmann", onlyPage = 1).status == BrainSearchStatus.NO_MATCH, "page filter")
    verified(StudyBrain.search("ortho", listOf(PageSource("ortho", 0, "")), "fracture").status == BrainSearchStatus.SCANNED_PDF_OR_MISSING_TEXT, "scanned")
    verified(StudyBrain.search("ortho", pages, "hypothyroidism").status == BrainSearchStatus.NO_MATCH, "unknown question")
    verified(StudyBrain.search("ortho", pages, "? ?").status == BrainSearchStatus.TOO_SHORT, "uninformative question")
    val prompt = requireNotNull(StudyBrain.prepareSourceOnly("ortho", "Volkmann?", english))
    verified(prompt.presentedEvidence.isNotEmpty() && "[S1]" in prompt.prompt, "source prompt")
    verified(!CitationValidator.check("hallucination without source", prompt.presentedEvidence.size).accepted, "reject uncited")
    verified(!CitationValidator.check("[S999999999999999999]", prompt.presentedEvidence.size).accepted, "reject overflow citation safely")
    verified(CitationValidator.check("الشرح من النص الطبي [S1]", prompt.presentedEvidence.size).accepted, "recognized citation")
    verified(!CitationValidator.check("[S1]", prompt.presentedEvidence.size).accepted, "reject citation-only response")
    verified(!CitationValidator.check("!! [S1] ...", prompt.presentedEvidence.size).accepted, "reject punctuation-only response")
    verified(!CitationValidator.check("[S1]", prompt.presentedEvidence.size).reason.isBlank(), "clear rejection reason")
    verified(GatewayPolicy.endpoint("https://study.example.org/v1/chat/completions") == "https://study.example.org/v1/chat/completions", "https endpoint")
    listOf("http://study.example.org/v1/chat/completions", "https://u:p@host/v1/chat/completions", "https://host/path?key=x", "https://host/other").forEach { bad ->
        verified(runCatching { GatewayPolicy.endpoint(bad) }.isFailure, "invalid gateway: $bad")
    }
    println("PASS: $checks Brain pipeline and HTTPS security assertions")
}
