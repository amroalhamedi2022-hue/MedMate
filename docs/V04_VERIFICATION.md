# v0.4 verification and Android acceptance checklist

## Actually verified in this environment
- `sh scripts/test-domain.sh` — pure Kotlin spaced-review & MCQ validation smoke: PASS.
- `sh scripts/test-export.sh` — pure Kotlin Anki TSV / High-Yield Markdown formatter: PASS.
- SQLite v3 schema SQL executed against Python SQLite in-memory DB: PASS; no changes to schema from v0.3.
- AndroidManifest XML parse and absence of INTERNET permission: PASS.
- ZIP archive integrity: PASS (post-packaging).

## NOT verified — critical before trusting the app
- Gradle sync / Android Compose compilation: **NOT RUN** (Android SDK, Gradle wrapper jar and dependency cache unavailable).
- APK installation and UI behavior on Xiaomi Pad 5 / phone: **NOT RUN**.
- Android system document picker import/export and document-provider behavior: **NOT RUN**.
- SQLite migration with real v0.2/v0.3 databases and ZIP restore end-to-end on Android: **NOT RUN**.
- Anki Desktop actual import: **NOT RUN**; import mapping is documented, no guarantee given.

## Acceptance steps on a device
1. Back up your existing app data first; keep the original copy somewhere safe, and **do not uninstall** until successful upgrade is verified. Use the same Android signing key for in-place upgrades.
2. Open source folder in Android Studio with Android SDK 36 / JDK17+ / verified Gradle wrapper; build `:app:assembleDebug`.
3. Import PDF, add an Arabic flashcard, an English MCQ and a High-Yield point from different pages.
4. Verify two buttons: Anki TSV must contain exactly 2 columns per card, source page number, options/correct answer; MD groups by page.
5. Cancel each system picker and confirm nothing changes. Try an unavailable destination and confirm error dialog.
6. Verify MCQ review, progress after reopening app, and backups both before and after exports.
7. Import TSV to a temporary Anki Basic deck and verify Unicode render and correct mapping, then delete the temporary deck.
