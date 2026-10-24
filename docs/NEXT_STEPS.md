# Next development priorities

1. **Build Android and QA** — run Gradle sync/assembleDebug and fix any issues; test Pad 5 with PDFs containing real text, images and mixed RTL/LTR.
2. **Migration/backup verification** — upgrade from v0.2 with same signing key and no app data removal; test archive v1/v2 restore idempotency, corruption and data preservation; consider encrypted backup.
3. **Study Studio usability** — edit cards, reorder MCQs, filter by chapter/page, quick-create from selected PDF text, make mistake attempts groupable.
4. **FSRS** — evaluate verified FSRS-6 Kotlin/Java libraries; pin and license-audit; migrate scheduling state without breaking existing due dates.
5. **AI assistant** — only after grounding/citation testing; page-anchored explanation in Palestinian Arabic, checked High-Yield drafts, MCQ approval. User must explicitly enable sending source text online.
6. **OCR and quality PDF zoom** — image-only content; preserve layout and table interpretation as possible, with explicit errors and page cross-reference.

### Post-v0.4 requirements (do not skip)
- Get a **real successful Android Gradle build** and fix compiler errors; preserve v3 database and prior user data.
- Verify Android SAF export of TSV and Markdown using at least two document providers, including one canceled picker.
- Verify an actual Anki Desktop Basic import, including Arabic and multi-line medical terms, before calling it fully interoperable.
- Fix any import/export reliability issues found by device QA; then consider optional encrypted backup, FSRS integration, and page-grounded AI.
