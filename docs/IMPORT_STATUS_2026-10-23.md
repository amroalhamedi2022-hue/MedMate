# MedMate — Safe Public Source Import Checkpoint (2026-10-23)

## Verified repository state
- The owner created `amroalhamedi2022-hue/MedMate` as a **public** repository. It was initially empty; a first commit was created on **this isolated staging branch only** (`automation/medmate-safe-source-import-20261023`). `main` has not been created or modified in this round.
- The full existing Android source is **NOT yet in GitHub**. This is an import checkpoint, **not** a rebuilt project, working APK, or a validated GitHub CI result. Do not start from scratch.
- Authoritative local source is MedMate Personal **v0.9.1**, 113 sanitized source/build/test/docs files in the checked local export. The local export's SHA-256 is `cef67359d78d350d9c609d5401ed33ef6e7b184d302be271cdb1db9a8ab0a64a` (194,842 bytes), available as a conversation artifact to the owner. GitHub connector in this run could write UTF-8 text files but could not transfer this local ZIP as an unpacked tree.
- Never import the private `signing/medmate-debug.keystore` into this public repository, including historical commits. The ZIP excludes it and all `.gguf`, `.jks`, `.apk`, `.aab` files. The single `four-pages.pdf` in `app/src/test/resources/` is a synthetic four-page test fixture (cardiac, renal, pulmonary, neurology) rather than a personal textbook.

## Actual local production/test work (not yet committed to GitHub)
- `BrainCore.kt`: CitationValidator rejects responses containing only valid source marker(s) or punctuation. This is syntactic quality control, **not clinical correctness verification**.
- `scripts/brain/BrainPipelineSmoke.kt`: new negative tests; `bash scripts/test-brain.sh` PASS (21 checks).
- `app/build.gradle.kts`: optional user-owned continuity debug key; public source builds fall back to Android Gradle Plugin's default debug key if missing. **Default debug APK cannot upgrade an app signed with the user's original private key.**
- `scripts/test-public-source.py`: checks signing fallback, gitignore, and missing private keys/model weights in sanitized export; PASS.
- Separate PASS: LocalBrain 6+12, fake Llama adapter, memory preflight 6, study drafts, scheduling, export, study evidence, study filter, bounded backup parsing, database migration contracts and merged static guards. Aggregate test-merged script timed out after partial success.
- **NOT TESTED:** Gradle Android APK build, real llama.cpp/GGUF model on Xiaomi Pad 5, clinical grounding accuracy, actual user-device backup/restore and upgrade.

## Next safe action
Import the sanitized **entire source tree** to a dedicated MedMate branch using a file-upload-capable path, then run source tests and Android CI on that exact commit. Create a reviewable PR once a proper base branch exists. Never upload user PDFs, signing keys, private study data or proprietary model weights; no paid AI APIs, auto-merge or public APK releases. Only after device verification and independent recheck can the app be called complete.
