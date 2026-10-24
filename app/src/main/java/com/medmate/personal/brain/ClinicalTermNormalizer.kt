package com.medmate.personal.brain

/**
 * Small, explicit morphology bridge for English medical textbook nouns.
 * No general stemming: "diagnoses", "analysis" and drug names must not be
 * silently rewritten. Both the PDF tokens and the query aliases use this map.
 */
object ClinicalTermNormalizer {
    private val forms = mapOf(
        "fractures" to "fracture",
        "dislocations" to "dislocation",
        "joints" to "joint",
        "nerves" to "nerve",
        "muscles" to "muscle",
        "vessels" to "vessel",
        "syndromes" to "syndrome",
        "contractures" to "contracture",
        "arteries" to "artery",
        "injuries" to "injury",
        "tendons" to "tendon",
        "ligaments" to "ligament",
        "bones" to "bone"
    )
    fun canonical(token: String): String = forms[token] ?: token
}
