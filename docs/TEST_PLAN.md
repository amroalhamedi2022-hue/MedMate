# MedMate v0.4 acceptance plan (do not mark Android checks passed without running them)

## Tested offline in the project-authoring environment
- Kotlin review logic: `sh scripts/test-domain.sh` — PASS.
- Kotlin TSV + Markdown output: `sh scripts/test-export.sh` — PASS.
- SQLite DDL and AndroidManifest XML static checks — PASS.
- Source zip integrity: PASS after packaging.

## Must test in Android Studio or emulator
- [ ] Build debug APK with required SDK, Gradle wrapper and downloaded dependencies.
- [ ] Unit tests: `:app:testDebugUnitTest` including StudyExportTest.
- [ ] Upgrade v0.3 app in place with same signing certificate: notes, cards, MCQs, and wrong answers survive.
- [ ] Try fresh PDF and large PDF, close/reopen at latest page, search and add source-anchored card.
- [ ] Export Anki TSV from several pages; verify Arabic and MCQ options, two columns, correct page indices.
- [ ] Export High-Yield MD; verify source pages, Unicode, escaping.
- [ ] Cancel export picker and confirm no data changes. Test unavailable provider and error handling.
- [ ] Restore backups v1/v2 on a test install; confirm duplicates are not added and all revisions survive.
- [ ] Import exported TSV into Anki Basic test deck on desktop. Confirm plain text and manual field mapping.
- [ ] Performance and UI scrolling on Xiaomi Pad 5 and Android phone.

Do **not** uninstall an app with actual study notes just to test upgrades. Export backups and verify separately first; the current backups are unencrypted.
