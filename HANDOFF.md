# Authoritative checkpoint — v0.9.1 Local Study Drafts

Use **this** ZIP, not v0.8 or v0.9. Read `docs/V091_BRAIN_RELEASE_AND_GATES_AR.md`.
This source descends from Work v0.6.1 through merged v0.8.1 and v0.9.
Do not erase user data or replace installed APK without testing; signing keystore preserved.
No model weights are bundled. Don't confuse FakeLlama tests with real on-device inference.

**Current integration:** v0.9.0 Local Brain experiment on the merged v0.6.1 base. Before calling functional, compile Android against the pinned AAR and test local GGUF inference on Xiaomi Pad 5. Details: `docs/LOCAL_AI_INTEGRATION_2026-10-23_AR.md`. Do not overwrite existing app data.

# LATEST MERGED SOURCE CHECKPOINT 2026-10-23

Read `docs/MERGE_REPORT_2026-10-23_AR.md` before any build or code changes. Work v0.6.1 is the base; Brain v0.8 is integrated. Full Gradle/Android tests remain pending. Preserve the signing certificate, backup safety, and non-destructive schema.

# MedMate Personal — v0.6.1-alpha handoff

## Source of truth

- Package: `com.medmate.personal`; versionCode 8; versionName `0.6.1-alpha`.
- SQLite schema v3; portable ZIP backup v2 with v1 restore support.
- Kotlin 2.3.21, AGP 8.13.2, Gradle 8.13, JDK 17, Compose BOM 2026.04.01, compileSdk 36, targetSdk 35, minSdk 26.
- Android `PdfRenderer` displays pages; PDFBox-Android extracts embedded text. No OCR yet.
- Offline-first, no accounts, no INTERNET permission, no automatic upload of PDFs or notes.

## Verified in Work

A clean `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` run completed successfully and generated a signed Debug APK. All 28 JUnit/Robolectric tests passed. Lint reported 0 errors and 8 warnings. APK package/version/SDK inspection, signature verification, ZIP integrity and 16 KiB zip alignment passed.

Robolectric covers the app SQLite helper migrations, notes/position persistence, review locking, PDFBox page text/search and backup v1/v2 round trips. Its `PdfRenderer` is a four-page test shadow, so native page rendering is still a device/emulator responsibility.

Standalone Kotlin smoke scripts and SQLite contract/migration scripts also pass. Details and artifact hashes are in `docs/V061_VERIFICATION.md`.

## Data safety rules

- Never drop/recreate the database or add destructive migrations.
- Keep PDF originals immutable and addressed by SHA-256.
- Keep internal page indexes zero-based and UI labels one-based.
- Backup restore validates the whole archive before insertion, merges with conflicts ignored and preserves local notes.
- Preserve the debug signing key for development updates. Do not use it for a public release.
- The explicit backup ZIP is unencrypted; do not represent it as secure encrypted storage.

## Next work

1. Test installation/update and the full Arabic/English workflow on an emulator and a Xiaomi Pad 5 using `docs/PAD5_FIRST_RUN_AR.md`.
2. Improve PDF rendering with bounded memory and high-resolution/tiled zoom while retaining exact page anchors.
3. Replace the custom scheduler with a tested FSRS implementation and a non-destructive schedule migration.
4. Add the offline Exam Planner, then an opt-in grounded AI assistant that distinguishes PDF evidence from external/generated additions.
5. Do not upload source to GitHub or connect online AI without explicit user approval.
