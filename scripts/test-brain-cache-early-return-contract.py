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
print("PASS invalid-page cache contract: stale PDF text is purged before citation-identity failure; old epochs cannot repopulate.")
