package com.medmate.personal.brain

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Fingerprint the exact PDF excerpts and student question approved for one
 * optional cloud transfer. Never persist the digest or any excerpt.
 * Both explanation and draft prompts use the same presented evidence.
 */
object CloudEvidenceSeal {
    fun of(prepared: PreparedPrompt, request: BrainRequestIdentity): String {
        val excerpts = prepared.presentedEvidence
        require(request.chapterSha256.isNotBlank() && request.question.isNotBlank()) {
            "Cloud request needs a selected chapter and question"
        }
        require(prepared.question.trim() == request.question.trim()) {
            "Cloud question changed since source selection"
        }
        require(!request.pageOnly || request.sourcePage >= 0) { "Invalid selected PDF page" }
        require(excerpts.size in 1..20 && excerpts.sumOf { it.text.length.toLong() } <= 20_000L) {
            "Cloud excerpts exceed the reviewed source budget"
        }
        require(prepared.prompt.length in 1..32_000) { "Cloud prompt exceeds safe length" }
        // Citation markers are positional ([S1] maps to evidence[0]). Duplicate or
        // blank IDs make provenance ambiguous even when the text itself is present.
        // Reject before consent can be claimed or any bytes can leave the device.
        require(excerpts.all { hasCanonicalEvidenceId(it.id) } &&
            excerpts.map { it.id }.toSet().size == excerpts.size) {
            "Cloud evidence has invalid, blank, padded, or duplicate source identities"
        }
        require(excerpts.all { e ->
            e.chapterId == request.chapterSha256 && e.pageIndex >= 0 &&
                (!request.pageOnly || e.pageIndex == request.sourcePage) &&
                e.text.length >= 40 && prepared.prompt.contains(e.text)
        }) { "Cloud evidence does not match the approved PDF context" }
        val sha = MessageDigest.getInstance("SHA-256")
        fun add(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            sha.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            sha.update(bytes)
        }
        add(request.chapterSha256)
        add(request.question.trim())
        add(request.pageOnly.toString())
        add(if (request.pageOnly) request.sourcePage.toString() else "*")
        // The exact explanation payload is part of the one-shot approval.
        // Merely preserving the excerpt list must NOT authorize a later prompt
        // that silently adds different student/private text to the upload.
        add(prepared.prompt)
        excerpts.forEach { e ->
            add(e.id); add(e.chapterId); add(e.pageIndex.toString()); add(e.text)
        }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Source IDs are identifiers, not display text. Invisible format marks,
     * whitespace and control characters can make two citations look identical
     * while resolving to different evidence records. Reject them before sealing.
     */
    private fun hasCanonicalEvidenceId(id: String): Boolean =
        id.length in 1..160 &&
            id.all { ch ->
                !Character.isISOControl(ch) &&
                    !Character.isWhitespace(ch) &&
                    !Character.isSpaceChar(ch) &&
                    Character.getType(ch) != Character.FORMAT.toInt()
            }
}
