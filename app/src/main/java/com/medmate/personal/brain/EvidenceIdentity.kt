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
}
