package com.medmate.personal.brain

/**
 * Keeps at most one bounded chapter index. If the active document exceeds the
 * cache ceiling, discard the previous chapter immediately and use the uncached
 * query-only retriever. This avoids retaining an unrelated/private PDF's text
 * and token maps while a large document is being searched.
 */
internal class BoundedEvidenceIndexCache(private val maxCachedChars: Long) {
    init { require(maxCachedChars >= 0L) }

    private data class Entry(
        val chapterId: String,
        val pages: List<PageSource>,
        val index: IndexedEvidenceRetriever,
        val textChars: Long
    )

    @Volatile private var cached: Entry? = null

    /** Metadata only; never exposes the retained page text. Useful for bounded-cache diagnostics. */
    internal val retainedChapterId: String? get() = cached?.chapterId
    internal val retainedTextChars: Long get() = cached?.textChars ?: 0L

    /**
     * Run before early returns in search. Even an empty query or unreadable new
     * chapter must not leave a different PDF's cached text resident.
     */
    fun evictIfStale(chapterId: String, pages: List<PageSource>) {
        require(chapterId.isNotBlank()) { "Invalid chapter identity" }
        val textChars = pages.sumOf { it.text.length.toLong() }
        synchronized(this) {
            val current = cached
            if (current != null && (textChars > maxCachedChars ||
                    current.chapterId != chapterId || current.pages != pages)) {
                cached = null
            }
        }
    }

    fun reusableIndex(chapterId: String, pages: List<PageSource>): IndexedEvidenceRetriever? {
        require(chapterId.isNotBlank()) { "Invalid chapter identity" }
        val textChars = pages.sumOf { it.text.length.toLong() }
        if (textChars > maxCachedChars) {
            // Do not keep the previous PDF's text alive merely because this
            // chapter is too large to cache. The caller will use its bounded
            // uncached retrieval path for this request.
            synchronized(this) { cached = null }
            return null
        }

        val old = cached
        if (old != null && old.chapterId == chapterId && old.pages == pages) return old.index
        return synchronized(this) {
            val current = cached
            if (current != null && current.chapterId == chapterId && current.pages == pages) {
                current.index
            } else {
                IndexedEvidenceRetriever(PageSegmenter(850).split(pages)).also {
                    cached = Entry(chapterId, pages.toList(), it, textChars)
                }
            }
        }
    }
}
