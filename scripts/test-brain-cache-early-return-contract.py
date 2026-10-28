#!/usr/bin/env python3
"""Ensure PDF retrieval stops stale work and ambiguous citation anchors fail closed."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
brain = (root / "app/src/main/java/com/medmate/personal/brain/StudyBrain.kt").read_text(encoding="utf-8")
cache = (root / "app/src/main/java/com/medmate/personal/brain/BoundedEvidenceIndexCache.kt").read_text(encoding="utf-8")

search_start = brain.index("fun search(")
search_end = brain.index("fun prepareSourceOnly", search_start)
search = brain[search_start:search_end]
epoch = search.index("val cacheRequestEpoch = indexCache.beginRequest(chapterId)")
first_page_loop = search.index("for (page in pages)")
assert epoch < first_page_loop, "allocate request ownership before touching PDF page lists"
assert "fun requestIsCurrent() = indexCache.isCurrentRequest(cacheRequestEpoch)" in search
assert "if (!requestIsCurrent()) return supersededResult()" in search, (
    "superseded PDF requests must return without continuing page traversal"
)
assert "enum class BrainSearchStatus" in brain and "SUPERSEDED" in brain, (
    "the UI must distinguish an obsolete request from a genuine no-match result"
)
assert "BrainSearchStatus.SUPERSEDED ->" in (
    root / "app/src/main/java/com/medmate/personal/ui/BrainPanel.kt"
).read_text(encoding="utf-8"), "the Compose UI must report superseded searches honestly"

identity_start = search.index("if (ambiguousIdentity)")
identity_end = search.index("val chapterPages", identity_start)
identity_guard = search[identity_start:identity_end]
assert "indexCache.invalidateIfCurrent(cacheRequestEpoch)" in identity_guard
assert identity_guard.index("invalidateIfCurrent") < identity_guard.index("return BrainSearchResult"), (
    "ambiguous/negative page identities must purge the old cache before returning"
)
assert "fun isCurrentRequest(requestEpoch: Long)" in cache
checker_start = cache.index("fun isCurrentRequest(requestEpoch: Long)")
checker_end = cache.index("\n\n", checker_start)
assert "synchronized(this)" in cache[checker_start:checker_end + 1], (
    "request ownership must be read under the cache lock"
)

prepare_start = cache.index("fun prepareSearch(")
prepare_end = cache.index("/** Compatibility helper", prepare_start)
prepare = cache[prepare_start:prepare_end]
assert "if (this.requestEpoch != requestEpoch) return@synchronized" in prepare, (
    "late preparation from an older request must not evict a newer cache entry"
)
invalidate_start = cache.index("fun invalidateIfCurrent(requestEpoch: Long)")
invalidate_end = cache.index("/** Compatibility helper", invalidate_start)
invalidate = cache[invalidate_start:invalidate_end]
assert "this.requestEpoch != requestEpoch" in invalidate and "cached = null" in invalidate, (
    "a stale ambiguous request must not purge a newer chapter's cache entry"
)

print("PASS PDF cache contract: stale searches stop traversing old pages; ambiguous anchors purge only the cache owned by that request.")
