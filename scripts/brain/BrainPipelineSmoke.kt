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
    verified(CitationValidator.check("المعلومة مش كافية بالنص الموجود", prompt.presentedEvidence.size).accepted,
        "source-free abstention allowed when excerpts do not answer")
    verified(CitationValidator.check("«المعلومة مش كافية بالنص الموجود.»", prompt.presentedEvidence.size).accepted,
        "punctuated exact abstention accepted")
    verified(CitationValidator.check("المعلومة مش كافية بالنص الموجود", 0).accepted,
        "no-evidence abstention still accepted")

    verified(!CitationValidator.check("المعلومة مش كافية بالنص الموجود [S1]", prompt.presentedEvidence.size).accepted,
        "abstention cannot imply a PDF citation supports missing evidence")
    verified(!CitationValidator.check("المعلومة مش كافية بالنص الموجود [S1]", prompt.presentedEvidence.size, false).accepted,
        "abstention remains source-free even when citations are optional")
    verified(!CitationValidator.check("إجابة غير موثقة. المعلومة مش كافية بالنص الموجود", prompt.presentedEvidence.size).accepted,
        "fabricated claim cannot piggyback on abstention")
    verified(!CitationValidator.check("المعلومة مش كافية بالنص الموجود [S999]", prompt.presentedEvidence.size).accepted,
        "unknown source marker cannot piggyback on abstention")
    // A plausible-looking marker must match the exact supplied [S1] syntax.
    // Reject malformed/forged references even if another valid [S1] is present.
    listOf("[S01]", "[s1]", "[S 1]", "[S1 extra]", "[S1x]", "[S]", "[S1").forEach { bad ->
        val checked = CitationValidator.check("شرح طبي من النص [S1] $bad", prompt.presentedEvidence.size)
        verified(!checked.accepted && checked.invalid.isNotEmpty(), "malformed marker $bad")
    }
    verified(!CitationValidator.check("المعلومة مش كافية بالنص الموجود [s1]", prompt.presentedEvidence.size).accepted,
        "malformed citation cannot piggyback on abstention")

    verified(GatewayPolicy.endpoint("https://study.example.org/v1/chat/completions") == "https://study.example.org/v1/chat/completions", "https endpoint")
    listOf("http://study.example.org/v1/chat/completions", "https://u:p@host/v1/chat/completions", "https://host/path?key=x", "https://host/other").forEach { bad ->
        verified(runCatching { GatewayPolicy.endpoint(bad) }.isFailure, "invalid gateway: $bad")
    }
    println("PASS: $checks Brain pipeline and HTTPS security assertions")
}
