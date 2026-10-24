> Historical v0.6 ledger. The later verified build is documented in `V061_VERIFICATION.md`.

# v0.6 verification ledger

## Tests actually executed in normal Chat environment

- `scripts/test-study-filter.sh`: PASS; preserves order and source page, English-case-insensitive search, Arabic+English keywords, fields including options/explanations; negative source-page rejection; edited answers allowed before review but blocked afterward.
- `scripts/test-domain.sh`: PASS; scheduling, validation and duplicate MCQ options.
- `scripts/test-export.sh`: PASS; TSV and Markdown formatting, Unicode, injection checks.
- `scripts/test-backup-lines.sh`: PASS; bounded JSONL, CRLF and oversized line rejection.
- `scripts/test-database-contract.py`: PASS; SQLite schema and backup column mappings.
- `scripts/test-migrations.py`: PASS; SQL-equivalent v1/v2 -> v3 preservation.

## NOT YET tested / not to claim

- Gradle Android compilation and JUnit execution in Android project.
- Android UI rendering or editor correctness on device.
- In-place update transaction persistence, corrupt/unusual imported historical records, ZIP round-trip on device, Arabic/RTL accessibility, PDF performance, battery usage and animations.
- Anki Desktop actual import of exported TSV. No APK created.

## Required device QA

1. Create sample High-Yield, flashcard, and MCQ on different PDF pages. Filter by one page and verify only true PDF-page-linked items show.
2. Search mixed Arabic and English strings across prompt, answer, options and explanation; verify empty-state message and 300-result overflow hint; scroll long results.
3. Edit an unreviewed MCQ (prompt/options/correct index), save, reopen and verify persistence and page anchor.
4. Review it incorrectly then correctly. Edit explanation and wording afterward and verify due date and mistake history still intact. Confirm answers/choices are locked; backend must reject attempts to change graded answers after reviews.
5. Export Anki TSV and High-Yield Markdown after edit; check UTF-8 Arabic content and correct page labels.
6. Backup, close app, restore on a clean profile, reopen items; verify edit contents and schedules are present. Test cancellation and corrupted backup on disposable data.
7. Regression-test opening a long PDF, page search/index cancellation, rotation, Pad 5 landscape and phone portrait.

Review safety rule is intentionally conservative: to change a graded answer after reviews, create a new corrected item; do not silently invalidate old review history.
