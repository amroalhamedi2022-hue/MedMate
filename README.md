# MedMate Personal — Android medical study app

**Current source baseline: v0.9.1, offline-first; IN PROGRESS.**
The complete sanitized Android source and tests are on draft PR #1 branch
`automation/medmate-v091-source-tree-20261023`. This is not a final APK.

## Study workflow

- Import private PDFs to app-private storage; extract embedded text, search
  per chapter/page, open original PDF citations and navigate to cited pages.
- The offline evidence path is **extractive**, not a generative model.
  Scanned/image-only pages need OCR; **OCR is not yet implemented**.
- The source includes an actual llama.cpp-compatible ARM64 integration via
  `LlamaAndroidPort` and a user-imported GGUF model path. Packaged native
  libraries or FakeLlama tests **do not prove actual model inference**.
- PDF-grounded explanations, high-yield study notes, MCQ/flashcards,
  spaced review (not FSRS yet), mistake history, study exports and explicit
  ZIP backup/restore are in development.
- Optional Cloud AI requires a user-provided HTTPS endpoint and **explicit,
  one-request, context-bound consent**. No paid API is required and no PDF
  should be sent automatically. Cloud transport and consent still need
  physical-device verification.
- Backups are **not encrypted**. Keep exports private; no personal PDFs,
  GGUF weights, signing keys, tokens or credentials are committed publicly.

## Latest validated milestone (2026-10-25)

An offline PDF text coverage report distinguishes missing/unindexed,
empty, low-information and readable pages, with explicit indexing and links
to suspect pages. It is a source-readiness diagnostic, not OCR or medical QA.

**PASS:** [Android CI 40155275698](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40155275698)
on functional SHA `a9ebd0ab32672df14f1ec3c7a96eafce8341e745`,
including JUnit, Android Lint and Debug APK assembly.

**NOT TESTED:** real GGUF loading/generation on Xiaomi Pad 5, clinical
correctness of AI answers and citations, cloud HTTPS on device, scanned
PDF OCR, thermal/battery behavior, signed upgrade continuity, and final
independent acceptance. Debug builds are not final releases.

## Build and authoritative records

JDK 17, Android SDK platform 36 and the checked-in Gradle wrapper:

```sh
bash ./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Read `docs/AUTONOMOUS_ACCEPTANCE.md` for evidence and remaining gates,
`docs/V091_BRAIN_RELEASE_AND_GATES_AR.md` for the native model plan,
`docs/IMPORT_STATUS_2026-10-23.md` for sanitized-source provenance, and
`docs/PAD5_FIRST_RUN_AR.md` for the physical-device checklist. Historical
v0.6/v0.8 details are preserved in the versioned docs and changelog.

No automatic merge, public release, paid service or direct `main` write.
