package com.medmate.personal.brain

fun main() {
    check(ClinicalTermNormalizer.canonical("fractures") == "fracture")
    check(ClinicalTermNormalizer.canonical("arteries") == "artery")
    check(ClinicalTermNormalizer.canonical("injuries") == "injury")
    check(ClinicalTermNormalizer.canonical("diagnoses") == "diagnoses")
    check(ClinicalTermNormalizer.canonical("analysis") == "analysis")
    check(MedicalText.tokens("Multiple fractures and injuries") == listOf("multiple", "fracture", "injury"))
    val pages = listOf(
        Evidence("c:0:0", "c", 0, "Multiple fractures of the radius require evaluation."),
        Evidence("c:1:0", "c", 1, "The patient has a nerve injury.")
    )
    val search = EvidenceRetriever(pages)
    check(search.search("c", "fracture").first().id == "c:0:0")
    check(search.search("c", "fractures").first().id == "c:0:0")
    check(search.search("c", "كسور").first().id == "c:0:0")
    check(search.search("c", "injuries").first().id == "c:1:0")
    check(search.search("c", "fracture", pageIndex = 1).isEmpty())
    println("PASS: 11 clinical morphology and page-grounding checks")
}
