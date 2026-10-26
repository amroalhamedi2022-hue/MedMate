#!/usr/bin/env python3
"""Static regression guard for the shared PDF page-count boundary.

This complements JVM tests: it verifies PDF import and re-indexing invoke the
same pre-allocation/page-work limit before a document is committed or scanned.
It does not claim a physical-device PDF import was run.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
coverage = (root / "app/src/main/java/com/medmate/personal/brain/PdfTextCoverage.kt").read_text(encoding="utf-8")
store = (root / "app/src/main/java/com/medmate/personal/data/ChapterStore.kt").read_text(encoding="utf-8")

assert "const val MAX_AUDIT_PAGES = 100_000" in coverage
assert "fun requireSafePageCount(totalPages: Int)" in coverage
assert "firstUnusablePages.zipWithNext().all { (first, next) -> first < next }" in coverage

import_region = store[store.index("suspend fun importPdf"):store.index("suspend fun searchPages")]
page_count = import_region.index("val pageCount =")
guard_index = import_region.index("PdfCoverageAudit.requireSafePageCount(pageCount)")
move_index = import_region.index('val target = File(documentDir, "$sha.pdf")')
assert page_count < guard_index < move_index, "page-count limit must run before the imported PDF is moved into permanent storage"

index_region = store[store.index("suspend fun indexText"): ]
assert index_region.index("PdfCoverageAudit.requireSafePageCount(chapter.pageCount)") < index_region.index("PdfTextExtractor(app, fileFor(chapter))"), "indexing must reject unsupported page counts before PDFBox opens the document"
print("PASS PDF page-limit contract: import and indexing share the 100,000-page guard; navigation preview is ascending.")
