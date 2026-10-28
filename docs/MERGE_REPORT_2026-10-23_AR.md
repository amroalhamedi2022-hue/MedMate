# MedMate Personal — v0.8.1 unified merge (2026-10-23)

## Verified inputs

- `MedMate_Personal_v0.6.1-alpha-source.zip` (provided by user): SHA-256 `96014616f387ba948ea9033c4a993dcf99e62814ab6754611b114e4a564ece79`, matching `SHA256SUMS-1.txt`.
- v0.8 Brain source from preceding Chat work.
- Important: the APK hash in `SHA256SUMS-1.txt` refers to an APK NOT supplied in this turn, so **the APK itself was not independently rehashed here**.

## Merge policy

The Android-build-verified **v0.6.1** is the master, not v0.8. Source changes were ported selectively. No database schema version change (still v3), no destructive migrations, no package ID change (`com.medmate.personal`). The existing debug signing keystore provided in v0.6.1 is retained in the private ZIP; its certificate SHA-256 is:
`3D214D4FA9CEC4C4EEC02AAA689F7E5E0879FDE8A495FF58EFA6B9C08DA87EE8`
Matching the signer SHA-256 recorded in Work's v0.6.1 verification ledger. This is certificate continuity evidence **only**, not proof an unbuilt future APK installs successfully.

## Features merged

- Offline source evidence retrieval and Arabic/English terminology normalization.
- Source-grounded prompt preparation, citation checks, and evidence-page navigation.
- User-initiated HTTPS Cloud AI explanation and draft generation, with session-only API key and explicit consent. The external endpoint remains untested; user must enter a provider account, model, and token.
- Offline PDF definition/MCQ quote extraction and user-reviewed saving.
- Four- or five-choice single-answer MCQ validation, saving, grading, and backup restore.
- Brain tab integrated into the existing PDF reader, plus selected dark theme text contrast corrections.
- Brain page-only query validity guard: after source-page switch, cloud buttons require a new search.
- Changed query clears generated drafts and cloud consent, avoiding stale source proposals.

## Protections preserved from v0.6.1

- Transaction-consistent backup export (v0.8 had accidentally removed it).
- Backup JSONL line cap: 128,000 bytes (v0.8 lowered it to 25,000, breaking valid longer records).
- Review rating consistency enforcement and corrupt-backup rejection.
- Page range and chapter existence checks before saving notes/last position.
- Error handling for PDF text/search loading.
- Proper Gradle Wrapper `.jar`, project signing continuity, Robolectric tests, fixture PDF, Android Lint and CI workflow.
- Android 12+ data-extraction privacy configuration; manifest now adds the INTERNET permission only for optional Cloud AI.

## Automated tests executed in this chat

- PASS `scripts/test-brain.sh` (18 assertions).
- PASS `scripts/test-drafts.sh` (offline and generated-draft structured smoke assertions).
- PASS `scripts/test-domain.sh` (including fifth-option index).
- PASS `scripts/test-study-filter.sh`.
- PASS `scripts/test-export.sh`.
- PASS `scripts/test-backup-lines.sh`.
- PASS `scripts/test-database-contract.py`.
- PASS `scripts/test-migrations.py` (v1/v2 -> v3 simulation).
- PASS `scripts/validate-merged-contracts.py` (12 anti-regression contracts).
- Added two Robolectric tests for a five-choice correct answer, invalid option 5, backup round trip and corrupted-review rejection. **New Robolectric tests were NOT executed here.**
- Gradle invocation attempted but FAILED BEFORE BUILD, because this container has no local Gradle distribution/Android SDK and network cannot fetch the pinned Gradle distribution. APK **not built**.

## Open work & ordering

1. Run full `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` with Android SDK and Gradle 8.13, using current wrapper. Fix any compilation issues without deleting v0.6.1 protections.
2. Verify APK signed certificate; install as in-place update on user device only AFTER exporting and verifying a personal backup. Test real SQLite data, existing cards, PDF navigation, native `PdfRenderer`, and both four/five-option reviews.
3. Test Brain UI with real medical source PDFs, scanned PDF empty-text detection, and source-only retrieval. Do not claim citation metadata proves medical answer correctness.
4. Test Cloud AI against an actual opted-in HTTPS gateway with safe non-sensitive sample text; API keys never commit or log.
5. Integrate actual on-device `llama.cpp`/GGUF inference; current "local" path is extract/search only, **not generative AI**. Benchmark model memory, latency, thermal load on Xiaomi Pad 5.
6. Improve semantic retrieval, page-bound citations, question/draft review workflows, FSRS, exam planner and liquid UI based on physical testing.

## Security note

The keystore is a *development/debug signing asset* shared by the user for upgrade continuity. Never commit it to a public GitHub repository. `.gitignore` now excludes `signing/*.keystore`, but that does not scrub a ZIP or existing git history. In production move signing to a secure secret store. Keep the ZIP private.
