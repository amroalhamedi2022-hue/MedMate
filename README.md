# MedMate Personal v0.9.1 — Local study drafts (SOURCE ONLY)

> Read `docs/V091_BRAIN_RELEASE_AND_GATES_AR.md` first. This ZIP is not an APK.
>
> Added local GGUF study-draft generation, quote-grounded review, conserved source quote
> on saved items, and a device-memory preflight. No database migration (schema v3).
> Runtime Android / native model / real medical accuracy testing remains pending.

**Latest source:** v0.9.0 Local Brain experimental. Read `docs/LOCAL_AI_INTEGRATION_2026-10-23_AR.md` for precise completed/unverified status.

# MERGED SOURCE v0.8.1 — read README_MERGED.md and docs/MERGE_REPORT_2026-10-23_AR.md first

# MedMate Personal — v0.6.1 Alpha

Offline-first Android medical study app for Xiaomi Pad 5 with a responsive phone layout and a Liquid Aurora interface.

## Implemented

- SHA-256 content-addressed PDF import into app-private storage, Android `PdfRenderer` display, page navigation, last position and page-linked notes.
- Per-page embedded-text extraction with PDFBox, exact page search and source navigation. Image-only PDFs still require future OCR.
- Manually authored High-Yield notes, four-option MCQs, flashcards, review scheduling, mistake history and source-page links.
- History-safe editing: reviewed answers/options stay locked while wording and explanation can be corrected.
- Anki Basic TSV and High-Yield Markdown export.
- Explicit ZIP backup/merge restore for PDFs, notes, study items and review history. v1 and v2 backups are readable; current backup format is v2. The ZIP is not encrypted.
- No accounts and no `INTERNET` or broad-storage permission.

## Build state

A real Debug APK was produced from a clean build for v0.6.1-alpha (`versionCode 8`, package `com.medmate.personal`). The project includes the official Gradle 8.13 wrapper JAR and its distribution checksum.

Requirements: JDK 17, Android SDK platform 36 and Build Tools 35.0.0.

```sh
./gradlew --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python3 scripts/test-database-contract.py
python3 scripts/test-migrations.py
```

The last clean Work run passed all 57 Gradle tasks, 28/28 JUnit tests and Android Lint with 0 errors. See `docs/V061_VERIFICATION.md` for the exact checks and remaining limits.

## Data compatibility

- SQLite schema: v3. Upgrades from v1 and v2 are additive; no destructive migration is present.
- Backup schema: v2, with v1 restore support.
- A stable debug signing key is kept in `signing/` so future development builds can install over this debug build without requiring an uninstall. It is not a production key.
- Android automatic backup and device transfer are excluded. Use MedMate's explicit ZIP export and keep that unencrypted archive private.

## Not yet verified

The APK has not been installed on an emulator or a physical Xiaomi Pad 5 in this environment. PDF rendering performance, long-session battery/thermal behavior, rotation and real-device update/data retention still require the checklist in `docs/PAD5_FIRST_RUN_AR.md`.

The next implementation priorities remain tiled/high-resolution PDF work, real FSRS, Exam Planner, then an optional source-grounded AI assistant. Existing storage contracts must be preserved while those features are added.
