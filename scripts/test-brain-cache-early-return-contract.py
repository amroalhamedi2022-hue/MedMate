#!/usr/bin/env python3
"""Ensure invalid citation anchors purge the shared private-PDF index before returning."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
brain = (root / "app/src/main/java/com/medmate/personal/brain/StudyBrain.kt").read_text(encoding="utf-8")
cache = (root / "app/src/main/java/com/medmate/personal/brain/BoundedEvidenceIndexCache.kt").read_text(encoding="utf-8")

guard_start = brain.index("if (identityScope.any { it.pageIndex < 0 }")
guard_end = brain.index("// Distinguish extraction damage", guard_start)
guard = brain[guard_start:guard_end]
assert "indexCache.invalidate()" in guard, (
    "ambiguous/negative page identities must invalidate the previous PDF cache before early return"
)
assert guard.index("indexCache.invalidate()") < guard.index("return BrainSearchResult"), (
    "cache invalidation must occur before the AMBIGUOUS_PAGE_IDENTITIES result returns"
)
invalidate_start = cache.index("fun invalidate()")
invalidate_end = cache.index("/** Compatibility helper", invalidate_start)
invalidate = cache[invalidate_start:invalidate_end]
assert "requestEpoch += 1L" in invalidate and "cached = null" in invalidate, (
    "invalidation must clear retained text and supersede older in-flight requests atomically"
)
search_start = brain.index("fun search(")
search_end = brain.index("fun prepareSourceOnly", search_start)
search_body = brain[search_start:search_end]
epoch_start = search_body.index("val cacheRequestEpoch = indexCache.beginRequest(chapterId)")
identity_check = search_body.index("val identityScope")
prepare_call = search_body.index("indexCache.prepareSearch(chapterId, chapterPages, cacheRequestEpoch)")
assert epoch_start < identity_check < prepare_call, (
    "allocate the epoch before page validation, then reconcile filtered content with that same token"
)

prepare_start = cache.index("fun prepareSearch(")
prepare_end = cache.index("/** Compatibility helper", prepare_start)
prepare = cache[prepare_start:prepare_end]
assert "if (this.requestEpoch != requestEpoch) return@synchronized" in prepare, (
    "late preparation from an older request must not evict a newer cache entry"
)

print("PASS PDF cache contract: invalid anchors purge private text; request epochs are allocated at entry and stale preparation cannot mutate newer cache state.")
