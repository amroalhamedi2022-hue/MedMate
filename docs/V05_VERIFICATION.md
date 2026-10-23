# v0.5 Verification — evidence and caveats

## Performed here
- `bash scripts/test-domain.sh` → PASS (pure Kotlin logic: intervals, validation).
- `bash scripts/test-export.sh` → PASS (UTF-8 Arabic/English text, TSV/Markdown formatting).
- `python scripts/test-database-contract.py` → PASS (SQLite schema constraints, cascade, idempotent DDL; textual check of 7-column backup mapper and merge safety guards).
- XML parsed: `AndroidManifest.xml`, `ic_medmate.xml` → PASS.
- Built the source ZIP and `zipfile.testzip` → PASS.
- Manifest contains no INTERNET permission.

## Not performed
- Android Gradle Plugin resolution or Gradle compile/test.
- APK build, emulator install, Pad 5 install.
- Android SQLiteOpenHelper migration path, actual ZIP backup/restore roundtrip.
- PDF renderer/text extraction with real scanned/RTL documents, responsiveness and battery profiling.

## Main blocker
- No Android SDK, no Gradle distribution or wrapper JAR, repository DNS inaccessible inside this runtime. The CI YAML is manually triggered **only after user explicitly selects a private repository**. It is not running here.

## Risk levels
- Static Kotlin checks can miss Compose or Android API issues. The GitHub Actions configuration needs its first run and possible dependency adjustments. Never use v0.5 on important notes without external backup and Android QA.
