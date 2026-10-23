# MedMate v0.8 — Brain & Study Engine implementation

**Scope:** based on v0.7 source archive (which itself branched from last available v0.6). The Work-built v0.6.1 code/signing key were not provided. This is SOURCE ONLY, not an installable APK.

## Implemented source changes

1. `brain/OfflineStudyDrafts.kt`: local extraction of (a) literal definitions as flashcards, (b) original medical sentences as candidate notes, (c) MCQs with four or five options and an explicit single-letter answer key. No invented answer keys. Missing, multiple, malformed, or ambiguous keys are skipped. Strict per-chapter and page references, stable fingerprints, length and count bounds.
2. `ui/BrainPanel.kt`: self-contained extraction button; candidates shown with page jumps and original book context; approve/reject flow; manual save via `StudyStore` with duplicate check. Existing read/search features retained.
3. `brain/GeneratedStudyDrafts.kt`: validate optional AI-generated High-Yield, Flashcards and MCQs. Reject nonexistent source IDs, invalid keys, malformed options and duplicate prompts. Map pages from indexed sources, not model-supplied page numbers. **Does not** verify medical truth; all items remain drafts until user approves.
4. `brain/CloudBrainGateway.kt`: second method to request structured study drafts via a user-chosen HTTPS OpenAI-compatible Chat Completions endpoint; strict JSON; bounded request/response, timeouts, redirects disabled. Default response sampling parameter removed for compatibility. Existing grounded explanation retained. Direct OpenAI API can be selected manually, with a session-only user-supplied API key; no credentials bundled or stored.
5. Study data flow: MCQ option count extended from **4 to 4–5** in `StudyValidation`, `StudyStore.review`, `StudyDeskPanel` editor and quiz, and `BackupManager` review validation. Legacy 4-option cards still valid, no schema migration. Multi-answer (e.g., `Answer: B,E`) is explicitly NOT auto-imported because the current schema represents exactly one correct index.
6. UI wording adjusted to distinguish automatic suggestions from reviewed, saved knowledge. Cloud consent resets when endpoint/model changes.
7. Added JVM smoke tests and Android-JUnit tests for 5-choice MCQ extraction and source mapping; modified domain tests; versionCode 10 / versionName 0.8-brain-source.

## Local test evidence (2026-10-23)

- `bash scripts/test-drafts.sh`: PASS — offline MCQ/definition extraction, four/five-choice key handling, no-key/multikey rejection, source page and deterministic ID; AI structured draft validator source mapping, invalid-key and duplicate rejection.
- `bash scripts/test-brain.sh`: PASS — 18 source search/gateway policy assertions.
- `bash scripts/test-domain.sh`: PASS — existing scheduler validation, four/five-choice MCQ validation, out-of-bounds and duplicate rejection.
- `bash scripts/test-study-filter.sh`: PASS — filtering and immutable-reviewed-answer policy.
- `bash scripts/test-export.sh`: PASS — export tests.
- `bash scripts/test-backup-lines.sh`: PASS — bounded backup parsing tests.
- `python3 scripts/test-database-contract.py`: PASS — original SQLite schema and migration contract tests.
- `python3 scripts/test-migrations.py`: PASS — simulated v1/v2 -> v3 migration contract tests.
- `CloudBrainGateway.kt` was Kotlin type-checked with lightweight `org.json` stubs and the available Kotlin coroutines jar. **This is not a live HTTPS or Android integration test**.

### NOT tested (hard blockers)

- **No Android compile, APK, Gradle, Android SDK or Pad 5 runtime verification** in this environment. The source ZIP has no Gradle wrapper jar and Internet DNS access to Android repositories is blocked. Android UI code is unverified.
- No live AI request or provider response was tested. JSON schema may vary per model; nonconforming output should be rejected.
- No full in-the-wild medical textbook evaluated. Tests use representative page text resembling the user's orthopaedics screenshot; actual PDFBox text extraction order could differ.
- No native local generative LLM installed or run. Local fallback is indexed text retrieval and extractive study drafts. **Do not call that an on-device generative model.**
- No automatic verification of medical factual entailment. Checking citations and answer structure only limits some classes of errors.
- No FSRS integration; existing scheduler is still heuristic.

## Mandatory next steps

1. Obtain exact v0.6.1 **source and signing continuity details**, rebase safely; no uninstalling or data loss.
2. Run Android Gradle build + JUnit + Lint and fix compilation issues. Test new UI in emulator and actual Pad 5. No release claim until that passes.
3. Test first complete flow with a real PDF: import -> index -> extract -> verify source -> approve a 5-option MCQ -> review -> backup -> restore. Include malformed/scanned PDFs, RTL, rotated pages, tables, duplicate imports, interruptions.
4. Implement local generative provider using maintained **official** llama.cpp Android binding / pinned revision with GGUF model checksum and life-cycle management. Test Qwen3.5-2B Q4_K_M (Apache-2.0 upstream) or another verified model on actual 6GB Pad 5; measure latency/throughput/RAM/heat before making performance claims. Consider LiteRT-LM only if verified better on this target.
5. End-to-end optional HTTPS cloud with a real authorized endpoint, cost and privacy review, structured JSON failure tests, safe retries and cancellation.
6. Human-referenced medical accuracy evaluation; support multi-select questions with a new schema and backup migration rather than flattening multiple answers.
7. Integrate proper FSRS and Exam Planner, optimize UI and PDF reader. Continue to preserve offline use and past study data.

## Safety and storage

- Data schema unchanged, so 4-option cards are compatible with 5-option-aware code.
- The UI requires explicit cloud consent and manual approvals; network permission is already in v0.7.
- DO NOT overwrite the Work-signed v0.6.1 APK with a newly signed/debug build. Export, verify, and preserve backups and the original signing key first.
