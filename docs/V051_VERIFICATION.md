# 0.5.1 verified vs unverified

## Passed without Android SDK

- Kotlin scheduling and MCQ model/validation smoke test.
- Anki/Markdown export smoke test including Arabic and source page mapping.
- Bounded JSONL reader smoke test: Arabic text, CRLF, no final newline, oversized record rejection before dispatch, empty file.
- Python SQLite DDL constraints and static backup projection checks.
- SQL-equivalent migrations from v1 to v3 and v2 to v3 preserve chapter/page note; v2 page text remains.
- XML parse and ZIP integrity check.

## Not yet verified — must run on Android

- Gradle project compilation and Compose compatibility
- Android unit tests / JUnit newly added
- Actual SQLiteOpenHelper migrations with preserved real user data
- ZIP full backup and restore with malformed archives
- PDF and SAF behaviors, rotation, cancel/retry, lifecycle
- Xiaomi Pad 5 performance and notes across process death

No claimed APK in v0.5.1. Never treat source-only smoke tests as device validation.
