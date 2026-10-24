package com.medmate.personal.brain

fun main() {
    check(ClinicalTermNormalizer.canonical("fractures") == "fracture")
    check(ClinicalTermNormalizer.canonical("arteries") == "artery")
    check(ClinicalTermNormalizer.canonical("injuries") == "injury")
    check(ClinicalTermNormalizer.canonical("diagnoses") == "diagnoses")
    check(ClinicalTermNormalizer.canonical("analysis") == "analysis")
    check(ClinicalTermNormalizer.canonical("ulcers") == "ulcer")
    check(ClinicalTermNormalizer.canonical("perforations") == "perforation")
    check(ClinicalTermNormalizer.canonical("obstructions") == "obstruction")
    check(ClinicalTermNormalizer.canonical("intestines") == "intestine")
    check(ClinicalTermNormalizer.canonical("gallbladders") == "gallbladder")
    check(ClinicalTermNormalizer.canonical("abscesses") == "abscess")
    check(ClinicalTermNormalizer.canonical("adhesions") == "adhesion")

    check(MedicalText.tokens("Multiple fractures and injuries") == listOf("multiple", "fracture", "injury"))
    check(MedicalText.tokens("pancre-\natitis") == listOf("pancreatitis"))
    check(MedicalText.tokens("pancre-\r\natitis") == listOf("pancreatitis"))
    check(MedicalText.tokens("frac\u00ADtures") == listOf("fracture"))
    check(MedicalText.tokens("well-being") == listOf("well", "being"))
    check(MedicalText.tokens("pancre-\n\natitis") == listOf("pancre", "atitis"))
    check(EvidenceRetriever(listOf(Evidence("hyphen:0", "hyphen", 0,
        "Acute pancre-\natitis and frac\u00ADtures are discussed.")))
        .search("hyphen", "pancreatitis").first().id == "hyphen:0")

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
    // Palestinian Arabic surgical queries should retrieve matching English
    // textbook terms without crossing the selected chapter/page boundary.
    check("appendicitis" in MedicalText.queryTokens("التهاب الزائدة"))
    check("pancreatitis" in MedicalText.queryTokens("التهاب البنكرياس"))
    check("cholecystitis" in MedicalText.queryTokens("التهاب المرارة"))
    check("obstruction" in MedicalText.queryTokens("انسداد الأمعاء"))
    check("peritonitis" in MedicalText.queryTokens("التهاب الصفاق"))
    check("perforation" in MedicalText.queryTokens("انثقاب القرحة"))
    val surgery = EvidenceRetriever(listOf(
        Evidence("s:0", "surgery", 0, "Acute pancreatitis may cause abdominal pain."),
        Evidence("s:1", "surgery", 1, "Intestinal obstruction requires evaluation."),
        Evidence("x:0", "ortho", 0, "Acute pancreatitis is outside this chapter.")
    ))
    check(surgery.search("surgery", "التهاب البنكرياس").first().id == "s:0")
    check(surgery.search("surgery", "انسداد الأمعاء", pageIndex = 1).first().id == "s:1")
    check(surgery.search("surgery", "التهاب البنكرياس", pageIndex = 1).isEmpty())
    check(surgery.search("ortho", "انسداد الأمعاء").isEmpty())
    println("PASS: clinical morphology, PDF hyphenation, Arabic aliases and scope checks")
}
