## Source patch 2026-10-23 — GGUF import stall safety (not an APK)

- Bounded repeated zero-byte InputStream reads during local GGUF import, preserving cleanup and verified model reuse.
- Added functional smoke coverage for transient SAF stalls and permanently stalled streams.
- Kotlin smoke + fake-backend regression checks passed; Android build/device inference remain unverified.

## 0.9.1 — Brain draft pipeline and source integrity (source; unbuilt)
- Unified JSON draft validation for local model and consent-only cloud backend.
- Mandatory `supporting_quote` matched against indexed evidence before a candidate is offered.
- Store reviewed original quote with each user-approved study item in existing explanation field.
- Distinct local generation task, bounded tokens and preliminary RAM gate for GGUF.
- Added standalone verification and Robolectric tests; changed versionCode to 13.
- Android build, live cloud and real native inference NOT verified; see docs/V091_BRAIN_RELEASE_AND_GATES_AR.md.

## v0.9.0 Local Brain experimental — 2026-10-23

- **Added:** source-exact local quotation brief with page links and expandable full excerpts.
- **Added:** safe GGUF import (streaming SHA-256, atomic file, basic header validation, de-duplication, cleanup on cancellation/failure).
- **Added:** experimental real on-device LLM adapter using pinned MIT AAR `dev.ffmpegkit-maintained:llama-android:0.1.1` and an ARM64-only import/load/explain workflow; no bundled model and no automatic cloud fallback.
- **Added:** native-memory unload when the reader screen leaves composition, bounded local prompt, model mutex and validation of [S] markers.
- **Added:** Kotlin smoke tests and JUnit tests for extractive quotes and safe model import, mock-backend lifecycle tests, Android CI dependency compile gate.
- **Kept:** DB schema v3, backup/restore, same original debug keystore, 4–5 option MCQ, offline drafts, cloud opt-in gateway.
- **Unverified:** Gradle APK build, genuine AAR/native run, GGUF model compatibility, measurements on Pad 5, and new Compose UI interaction.

# Changelog

## 0.6.1-alpha — First verified APK and persistence hardening

- Built a real signed Debug APK with JDK 17, Gradle 8.13, AGP 8.13.2 and Android SDK 36; versionCode 8.
- Bundled the verified official Gradle wrapper JAR and distribution SHA-256, plus a manual-only private CI workflow that runs tests, Lint and APK assembly.
- Added 7 Robolectric persistence/integration tests; the complete Debug suite now contains 28 passing tests. Coverage includes real SQLiteOpenHelper v1/v2→v3 migration, page-note validation, PDFBox page indexing/search, ZIP backup v1/v2 restore, local-note conflict preservation and review consistency. Android PdfRenderer is shadowed in JVM tests.
- Export now reads a single SQLite transaction snapshot. Restore accepts valid escaped study records up to 128 KiB per line and rejects review ratings that contradict stored correctness.
- Added stable debug signing so future development APKs can update this build without uninstalling and erasing app-private data.
- Added Android 12+ data-extraction exclusions; automatic cloud/device backup remains disabled in favor of explicit ZIP export.
- Fixed Compose import/build errors, bounded search input, stale asynchronous search/page-text state, and duplicate study-action taps.
- Database schema remains v3 and backup schema remains v2; no destructive migration was added.
- Clean validation: `clean testDebugUnitTest lintDebug assembleDebug` PASS; 28/28 JUnit tests PASS; Lint has 0 errors and 8 warnings; APK signature, ZIP integrity, 16 KiB zip alignment, package/version/SDK and offline permission contract PASS.
- No emulator or physical Xiaomi Pad 5 test was performed.

## 0.6-alpha — Study filter and safe revisions

- Added page-aware and mixed Arabic/English word filtering for High-Yield notes, flashcards and four-choice MCQs; preserves PDF source anchors.
- Added editing dialog for existing study content; reviewed graded answers cannot be overwritten in-place, protecting historical MCQ option indexes and spaced-repetition data. Prompt and explanation corrections remain possible.
- Switched authored-item list to stable-key LazyColumn, reducing UI work when many items exist.
- Added pure Kotlin smoke checks and JUnit tests for filters and revision policy.
- Kept `medmate.db` schema version 3 and ZIP backup schema 2 unchanged. Android versionCode 7.
- Actual Android compile/build/install still **not performed**; no APK exists.

# MedMate Personal — Changelog

## 0.5.1-alpha — Restore safety & offline verification

- Added bounded-line JSONL reader and switched ZIP restore pre-validation and insertion from `BufferedReader.readLine`-style unbounded line allocations to incremental, capped parsing for notes, study items, and review history. The existing backup schema remains **v2** and v1 restores remain supported.
- Added Kotlin standalone smoke test and a JUnit test for Arabic + English, CRLF and last-line processing, and deliberately oversized records.
- Closed PDF descriptors if construction of Android `PdfRenderer` fails during page rendering or archive restore validation.
- Added coroutine cancellation checks while copying a PDF into the app-private original library.
- Added SQL-equivalent migration regression tests for v1->v3 and v2->v3, with original notes/page-index state retained. These **are not** Android SQLiteOpenHelper tests.
- Kept database schema **3**, backup format **2**, and package id unchanged. Existing app data should remain accessible; Android regression test is still outstanding.
- Updated manual-only GitHub Actions job to run database/migration contracts in addition to Android unit tests + Debug APK assembly.

## 0.5.0-alpha

- Restore integrity hardening, import hash checks, MCQ review validation, optional manual CI workflow. See previous archive for full history.

**Build state:** Android SDK/Gradle unavailable in current runtime, external Gradle/Maven hosts DNS unreachable. **No APK compiled.**
