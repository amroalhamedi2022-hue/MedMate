# MedMate Brain — Correction & Development Plan

Baseline used for this patch: last available **v0.6 source archive**, because Work's v0.6.1 source is **not present** in this conversation or the MedMate Library. v0.6.1 APK was reportedly built, but its sources are needed for a clean rebase before another release. Never install a differently signed APK over v0.6.1 in an attempt to preserve data; Android rejects different signatures, and uninstalling deletes private study data.

## Non-negotiable product gates

- There must be a meaningful, traceable path: chapter PDF -> extraction -> local retrieval -> grounded answer -> student verification -> saved flashcard/MCQ -> scheduled review.
- Offline-first includes reading/searching/cards and **retrieving original excerpts**, not magically providing a generative LLM without bundled weights/runtime.
- All cloud traffic requires an explicit button press and consent; no automatic fallback to network.
- Do not send entire books, backups or personal notes by default; send only selected question + retrieved excerpts when permitted.
- Separate source facts, model interpretations, citations and student's own notes visually.
- No new SQLite migration for ephemeral results. No destructive migration at all.

## Phases / exit criteria

### P0. Source integrity and practical UX
- [x] Audit available v0.6 source; preserve existing schema and study data.
- [x] Fix obvious black-on-dark header colors in v0.6 source.
- [ ] Compare and rebase against **actual** v0.6.1 Work source to avoid losing fixes.
- [ ] Build debug APK, install or update with SAME signing key, smoke test on Pad 5.

### P1. Offline understanding / retrieval
- [x] Integrate page-aware chunker and lexical retriever into Android source.
- [x] Add Arabic-to-English lexical aliases for common medical terms.
- [x] First-use resumable PDF indexing on request; show progress and scanned-file message.
- [x] Add source-preview question panel and tap-to-open evidence pages.
- [x] Add Kotlin smoke/security tests.
- [ ] Assess 85-page real orthopedic PDF, multi-column extraction, answer-key tables.
- [ ] FTS5 or SQLite chunk persistence, multilingual embeddings + hybrid re-ranking, OCR for scans.

### P2. Local *generative* Brain
- [x] Define native inference interface; never fake a model response.
- [ ] Integrate official llama.cpp Android library/NDK binding, pin trusted commit and license, review binary reproducibility.
- [ ] Test a small Arabic-capable Apache-2.0 GGUF model such as Qwen3 1.7B with Q4 quantization; store model in app-specific storage, checksum on import, no bundled weights required.
- [ ] Record first-token latency, tokens/second, RAM peak, battery/thermal on Xiaomi Pad 5; reject if unacceptably slow.
- [ ] Streaming, cancellation, model memory lifecycle, source validation and prompt injection checks.

### P3. Optional Cloud Brain
- [x] Add explicit consent, source-only prompt, configurable HTTPS private gateway adapter.
- [x] Prevent redirects/HTTP and bound timeouts and responses.
- [x] Gateway access token not stored; never embed provider secret in the APK.
- [ ] Actual HTTPS end-to-end test against an authorized gateway, provider errors/quotas, optional authentication.
- [ ] Ensure cloud enhancement is optional and does not fire on local failure.

### P4. Make studying useful
- [ ] High-Yield drafts from cited sources, preview/reject/edit/save.
- [ ] MCQ single/multiple select with medical validation, explanation, source evidence, confirmation before saving.
- [ ] Flashcard authoring from validated notes; FSRS Kotlin scheduling, data migration and restore tests.
- [ ] Error notebook -> personalized remediation -> exam planner priorities.

### P5. Product finish
- [ ] Redesign home, reader split view and study journey around content, not empty buttons.
- [ ] Colors accessibility, RTL/LTR mixing, PDF zoom and annotations, typography.
- [ ] Automated tests + real Pad 5 field trials before calling it production-ready.

## Architecture

`PDF -> page_text -> StudyBrain.search -> evidence cards (local)`

Optional only after consent:

`evidence cards -> MedicalPromptBuilder -> HTTPS private gateway -> model -> CitationValidator -> student verification`

Later on-device generation:

`evidence cards -> OnDeviceLlmPort(llama.cpp Android) -> model -> CitationValidator`

## API safety

The gateway endpoint must be a user-specified HTTPS URL ending in `/v1/chat/completions`; it is intended as a **personal relay keeping the AI provider's key server-side**, never a provider key compiled into an APK. A session-only relay token can be provided. `CloudBrainGateway` is a transport implementation, not yet verified against an actual server.

## Compatibility caveat

New source versionCode=9, but **do not attempt to reinstall/upgrade a Work-signed v0.6.1 with a new debug signing key.** Obtain the original signing key, or export a backup, verify it in a separate installation before any uninstall. A different signer cannot update a user data preserving Android package install.


## 2026-10-27 — Async result context freshness gate

- BrainPanel now snapshots the latest Compose values for chapter SHA-256, question, page-only mode and current PDF page with rememberUpdatedState. The four long-running publication paths (local explanation, local study drafts, cloud explanation, cloud study drafts) compare their captured BrainRequestIdentity to those latest values before publishing results.
- This closes a stale-closure hazard: the coroutine launched by an earlier composition must not compare only against the click-time chapter/page parameter after the reader has moved to another chapter/page.
- The source-contract gate in scripts/test-model-load-ui-contract.py asserts all four latest-state snapshots and exactly four latest-context comparisons. This is a source/UI wiring guard, not a substitute for a device timing test.
- **PASS — Android CI run 40533697210**, source/test head bdcc6855a11207fc49197f095e9ebcd63d778a46. 179 JVM/Robolectric tests passed, 0 failures, 0 ignored; Lint, Debug + AndroidTest APK assembly, package/SDK/permission verification and device-bundle packaging passed.
- **PASS — independently re-hashed APK pair:** app-debug.apk 30,264,727 bytes, SHA-256 f0229958978dc3d46d2635a007533c7c0eceaf1f7f931e65173e893260e5eace; app-debug-androidTest.apk 374,045 bytes, SHA-256 1fa5e1773a47ccf5205449f2868e5e946cbac78efe160c77958694fd68aae297. Both match SHA256SUMS in device-test bundle Artifact **12748740398**.
- **NOT TESTED:** Xiaomi Pad 5 installation, real GGUF/llama.cpp/JNI inference, model-backed PDF page-two answer, live HTTPS consent/denial, RAM/thermal/latency and independent clinical review. No GGUF, PDF, token or key is included. Debug is not final.

## 2026-10-27 — MCQ option identity hardening (CI PASS)

- Manual `StudyValidation` and untrusted model-generated `GeneratedStudyDrafts` share `StudyOptionIdentity`: Unicode NFKC compatibility normalization, removal of invisible Unicode format controls (`Cf`), Unicode separator/whitespace collapse, trim and `Locale.ROOT` lowercase.
- Both paths reject duplicate-looking options that differ only by zero-width controls or compatibility ligatures; blank-looking Unicode-only choices remain rejected. Tests cover the manual and generated-output boundaries.
- **PASS — Android CI:** [run 40590272471](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40590272471), exact source/test head `f3f7c24e8e9c264529eb63530903c8254fe30469`. **197 JVM/Robolectric tests PASS, 0 failures, 0 ignored**; contract scripts, wrapper validation, dependency alignment, SQLite/migration gates, Lint, Debug + AndroidTest APK assembly, package/SDK/permission verification and device-bundle packaging passed.
- The first attempt [run 40589354326](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40589354326) exposed a test-fixture typo (the hidden-character variant accidentally omitted the letter “i”); the fixture was corrected and the full CI rerun passed. No production validator was weakened.
- **PASS — independently re-hashed APK pair:** device-test bundle Artifact [12774371704](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40590272471/artifacts/12774371704). `app-debug.apk` 30,264,727 bytes, SHA-256 `935532e6eac0da5ae9d305046d8b27618f31071fd73387dd2aafe4ec9c11d942`; `app-debug-androidTest.apk` 374,045 bytes, SHA-256 `8fa87955e2cc20124f813cf1d44a1b0e6e7b3329d54bcf3f2b4403bdd0e1b60a`. Both were independently recalculated after download and match `SHA256SUMS`.
- **NOT TESTED:** Xiaomi Pad 5, genuine GGUF/llama.cpp/JNI inference, model-backed PDF page-two answer, live HTTPS consent/denial, RAM/thermal/latency and independent clinical review. This validator prevents duplicate-choice ambiguity; it does not establish medical correctness or entailment. No GGUF, personal PDF, credential or signing key is included. Debug is not final.


## 2026-10-28 — Native completion publication guard

- The synchronous JNI completion path now refuses to publish a result after coroutine cancellation has been observed and rejects blank/oversized output before callback. This guards stale UI output; it does not make llama.cpp inference itself interruptible.
- `NativeCompletionPublicationTest` covers cancelled publication, successful one-shot publication and invalid output. These are JVM tests, not model inference.
- **PASS:** Android CI [40596545073](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40596545073), source/test SHA `41442ef3bf58361a011651d3ce39dff81603f75d`: 200 tests, 0 failures/ignored; Lint, Debug + AndroidTest APK assembly and device-bundle checks passed. Both APKs were independently re-hashed against `SHA256SUMS` in Artifact [12775889349](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40596545073/artifacts/12775889349).
- **NOT TESTED:** physical Pad 5, real GGUF/JNI inference, real PDF page-2 citation, live cloud consent/denial and RAM/thermal/latency. Debug is not final; no model or personal data was uploaded.


## Implementation reality correction — native adapter status (2026-10-28)

- The current production `LlamaAndroidPort` is wired to the pinned MIT-licensed `dev.ffmpegkit.llama` Android AAR and calls its native `Llama.loadModel`, `Llama.complete`, and `Llama.releaseModel` APIs with an imported local GGUF. This is a real native-backend integration path, not `FakeLlama`; the AndroidTest acceptance class references the production port directly and binds the loaded model SHA/byte count.
- Do **not** mark the full P2 device gate complete yet. The latest run [40596545073](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40596545073) compiled the real-model instrumentation and passed 200 JVM/Robolectric tests, but did not execute the instrumentation or load any GGUF. Upstream provenance/reproducibility audit, physical ARM64 model load, PDF page-2 round-trip, and Pad 5 RAM/thermal/latency remain open.


## 2026-10-28 — Native backend provenance gate integrated

- Pinned dependency and documented upstream provenance: `dev.ffmpegkit-maintained:llama-android:0.1.1`, public upstream `ffmpegkit-maintained/llama-android`, MIT declaration, documented llama.cpp build `b9878`.
- CI provenance regression is wired and **PASS** on `aa2b301f203214409be7ddcc5b2774f7872a7f9a` ([run 40611008395](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40611008395)); 200 JVM/Robolectric tests, lint, Android/AndroidTest packaging and independently rehashed APK pair all passed.
- This is a supply-chain/source wiring gate, **not** independent AAR binary attestation or real inference. Next P2 gate remains the user-supplied real GGUF on ARM64, followed by the synthetic two-page PDF citation path and explicit cloud allow/deny on Xiaomi Pad 5. Record model SHA/bytes and device memory/latency; never upload the model or personal PDFs.


## Current source baseline correction — 2026-10-28

The historical plan above originally described an available v0.6 snapshot because v0.6.1 source was missing at that checkpoint. It must **not** be read as the current import state: PR #1 now contains the sanitized **MedMate Personal v0.9.1 round-3 Android source** (116 files, original sanitized archive SHA-256 `07b5b5d11de5f0b5c8948a9a84c1e43017870615e84cb6ea0356765a10ac8b0d`) on `automation/medmate-v091-source-tree-20261023`.

Current source includes the production `LlamaAndroidPort` path through the pinned llama-android AAR, content-addressed local GGUF import, page-grounded PDF retrieval/citations, explicit evidence-bound cloud consent, study-draft validation, backups, and a device-only real-model/PDF acceptance test. This is a **source capability statement**, not proof that any GGUF was loaded or that clinical output is correct.

The 2026-10-28 bounded-index milestone evicts the previous chapter's cached text/token index when the active document exceeds the cache ceiling, and evicts stale chapter/content before early returns for empty queries or unreadable pages. Its Kotlin/JUnit integration is **pending exact-head Android CI**. Real ARM64 GGUF inference, page-two citation round-trip, live HTTPS consent/denial, device RAM/thermal/latency and independent clinical review remain **NOT TESTED**. Do not upload GGUF weights, personal PDFs, signing keys or credentials; do not substitute `FakeLlama` for the device acceptance test.
