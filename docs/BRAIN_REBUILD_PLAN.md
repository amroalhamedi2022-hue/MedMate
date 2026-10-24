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
