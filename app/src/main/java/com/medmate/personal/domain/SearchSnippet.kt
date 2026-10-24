package com.medmate.personal.domain

/** Pure Kotlin: doesn't depend on Android, so it can be unit tested. */
object SearchSnippet {
    fun make(text: String, query: String, maxLength: Int = 170): String {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        val needle = query.trim()
        if (clean.isEmpty()) return ""
        val index = clean.indexOf(needle, ignoreCase = true)
        val center = if (index >= 0) index else 0
        val start = (center - maxLength / 3).coerceAtLeast(0)
        val end = (start + maxLength).coerceAtMost(clean.length)
        return (if (start > 0) "…" else "") + clean.substring(start, end) +
            (if (end < clean.length) "…" else "")
    }
}
