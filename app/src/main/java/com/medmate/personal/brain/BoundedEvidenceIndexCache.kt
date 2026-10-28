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
    private var requestEpoch: Long = 0L // guarded by synchronized(this)

    /** Metadata only; never exposes the retained page text. Useful for bounded-cache diagnostics. */
    internal val retainedChapterId: String? get() = cached?.chapterId
    internal val retainedTextChars: Long get() = cached?.textChars ?: 0L

    /**
     * Allocate the request epoch at search entry, before PDF validation or other
     * work can suspend/delay this request behind a newer one.
     */
    fun beginRequest(chapterId: String): Long {
        require(chapterId.isNotBlank()) { "Invalid chapter identity" }
        return synchronized(this) {
            requestEpoch += 1L
            if (cached?.chapterId != chapterId) cached = null
            requestEpoch
        }
    }

    /**
     * Reconcile page content after corruption filtering. A superseded request
     * must never evict or replace the cache owned by a newer request.
     */
    fun prepareSearch(chapterId: String, pages: List<PageSource>, requestEpoch: Long) {
        require(chapterId.isNotBlank()) { "Invalid chapter identity" }
        val textChars = pages.sumOf { it.text.length.toLong() }
        synchronized(this) {
            if (this.requestEpoch != requestEpoch) return@synchronized
            val current = cached
            if (current != null && (textChars > maxCachedChars ||
                    current.chapterId != chapterId || current.pages != pages)) {
                cached = null
            }
        }
    }

    /** Compatibility helper for cache-focused tests; production captures the epoch at entry. */
    fun beginSearch(chapterId: String, pages: List<PageSource>): Long {
        val epoch = beginRequest(chapterId)
        prepareSearch(chapterId, pages, epoch)
        return epoch
    }

    /**
     * Fail-closed purge for requests whose source/page identity is not trustworthy.
     * Advancing the epoch also prevents an older in-flight search from repopulating it.
     */
    fun invalidate() {
        synchronized(this) {
            requestEpoch += 1L
            cached = null
        }
    }

    /** Compatibility helper for cache-focused tests; production captures the returned epoch. */
    fun evictIfStale(chapterId: String, pages: List<PageSource>) {
        beginSearch(chapterId, pages)
    }

    private fun currentEpoch(): Long = synchronized(this) { requestEpoch }

    fun reusableIndex(
        chapterId: String,
        pages: List<PageSource>,
        requestEpoch: Long = currentEpoch()
    ): IndexedEvidenceRetriever? {
        require(chapterId.isNotBlank()) { "Invalid chapter identity" }
        val textChars = pages.sumOf { it.text.length.toLong() }
        if (textChars > maxCachedChars) {
            // Oversized input uses uncached retrieval and cannot evict a newer request.
            synchronized(this) {
                if (this.requestEpoch == requestEpoch) cached = null
            }
            return null
        }

        return synchronized(this) {
            val current = cached
            if (current != null && current.chapterId == chapterId && current.pages == pages) {
                current.index
            } else if (this.requestEpoch != requestEpoch) {
                // A superseded request may finish locally but cannot repopulate shared state.
                null
            } else {
                IndexedEvidenceRetriever(PageSegmenter(850).split(pages)).also {
                    cached = Entry(chapterId, pages.toList(), it, textChars)
                }
            }
        }
    }
}
