# v0.6.1 Alpha verification ledger

Date: 2026-10-20 UTC. Commands ran in ChatGPT Work on Linux; results below come from generated files and command output.

## Clean Android build

```sh
./gradlew --version
# Gradle 8.13; JDK 17.0.17

gradle --no-daemon clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# BUILD SUCCESSFUL in 1m 32s
# 57 actionable tasks: 57 executed
```

The project wrapper downloaded and validated the Gradle 8.13 distribution using the pinned SHA-256. The final APK exists and is a valid Android package.

## Tests

- JUnit/Robolectric: **28 tests, 0 failures, 0 errors, 0 skipped**.
- Persistence tests: note/position reopen; invalid page rejection; review transaction and reviewed-answer lock; actual `SQLiteOpenHelper` v1→v3 and v2→v3 migrations retaining notes.
- Backup tests: empty v2, full PDF/notes/study/reviews v2 round trip, repeated merge preserving a newer local note, contradictory review rejection without data loss, escaped valid JSONL record over the former 25 KiB boundary, historical v1 PDF+notes restore.
- PDF tests: PDFBox extracts a real four-page fixture and search returns the exact zero-based pages. JVM tests shadow Android `PdfRenderer` only for the native page-count call; native rendering was not tested.
- Standalone smoke scripts: study filtering/revision, review scheduling/validation, study exports and bounded backup-line parsing all PASS.
- SQLite Python scripts: database contract and v1/v2 migration contracts PASS.

## Lint

Android Lint completed with **0 errors and 8 warnings**:

- targetSdk 35 while compileSdk is 36 (intentional compatibility decision pending Android 16 device regression testing);
- three KTX style suggestions;
- one `allowBackup`/data-extraction warning while the manifest intentionally keeps pre-Android-12 backup disabled and supplies Android 12+ exclusions;
- three trust-manager warnings inside the third-party BouncyCastle JAR bundled by PDFBox. The app manifest has no INTERNET permission.

## APK inspection

- Output: `MedMate_Personal_v0.6.1-alpha-debug.apk`
- Size: 27,769,108 bytes
- SHA-256: `752ed544865930e6c1a520311fbcc782e9be0033b5f0018901f9e363ef1aa1a4`
- Package: `com.medmate.personal`
- versionCode/versionName: `8` / `0.6.1-alpha`
- minSdk/targetSdk/compileSdk: `26` / `35` / `36`
- APK Signature Scheme v2: verified; one RSA-3072 signer
- Signer certificate SHA-256: `3d214d4fa9cec4c4eec02aaa689f7e5e0879fde8a495ff58efa6b9c08da87ee8`
- `zipalign -c -P 16 -v 4`: PASS
- `unzip -t`: PASS, 257 files
- Permissions: only AndroidX's package-scoped `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; no INTERNET, broad storage or manage-external-storage permission.

## Not tested

No emulator or physical Android device was available. Installation, update-over-v0.6 behavior, native `PdfRenderer`, Compose interaction, accessibility, rotation, large-PDF memory, battery/thermal behavior and Xiaomi Pad 5 performance remain unverified. Use `PAD5_FIRST_RUN_AR.md`; do not describe those checks as passed until recorded from a device.
