package com.medmate.personal.brain

/**
 * Stable source identity shared by local PDF navigation and optional cloud consent.
 *
 * IDs are internal keys, not display text. Restrict them to visible printable
 * ASCII so whitespace, bidi/zero-width formatting, and visually confusable
 * Unicode cannot make two source identities appear equivalent to a student.
 */
internal object EvidenceIdentity {
    fun isCanonicalId(id: String): Boolean =
        id.length in 1..160 && id.all { ch -> ch.code in 0x21..0x7E }

    fun hasUniqueCanonicalIds(evidence: List<Evidence>): Boolean =
        evidence.all { isCanonicalId(it.id) } &&
            evidence.map { it.id }.toSet().size == evidence.size

    /**
     * One source marker must resolve to one stable, non-empty page excerpt.
     * Shared by answer display, clickable citations and diagnostics so their
     * acceptance decisions cannot drift apart.
     */
    fun hasSafePageAnchors(evidence: List<Evidence>): Boolean {
        if (evidence.isEmpty()) return false
        val chapter = evidence.first().chapterId
        return chapter.isNotBlank() &&
            evidence.all {
                it.chapterId == chapter && it.pageIndex >= 0 && it.text.isNotBlank()
            } &&
            hasUniqueCanonicalIds(evidence)
    }
}
