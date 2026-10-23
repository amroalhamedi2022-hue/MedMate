# MedMate Personal — autonomous source patch 2026-10-23

AUTOMATION_COMPLETE: IN_PROGRESS

## Scope and source
- Starting point: Library `MedMate_Personal_v0.9.1_local_study_source.zip` (not a GitHub repository).
- New source patch: `v0.9.1-local-import-stall-fix1`, **not an APK release**.
- Production change: `LocalModelImport.kt` bounds consecutive zero-byte reads to eight while importing GGUF. Short pauses (three zero reads) are tolerated; an indefinitely stalled Android SAF provider aborts and deletes the partial `.part` file, preserving any valid previously imported model.
- Test change: `scripts/brain/ModelAndTutorSmoke.kt` covers recovery, repeated-zero failure, and temporary-file cleanup.
- This is a file-import safety fix, **not evidence of on-device generative inference**.

## Reproducible tests (executed in this environment)
- PASS `bash scripts/test-local-brain-v09.sh`: ExtractiveTutor 6 checks; LocalModelImport 12 checks including new pause/stall tests.
- PASS `bash scripts/test-llama-adapter-contract.sh`: fake llama backend only, no actual GGUF model.
- PASS `bash scripts/test-brain.sh`: 18 retrieval/prompt/security checks.
- PASS `bash scripts/test-model-memory.sh`: 6 preflight memory checks.
- PASS `python scripts/test-migrations.py`: simulated SQLite v1/v2 → v3 migrations.
- PASS `python scripts/validate-merged-contracts.py`: static merged/security checks.
- NOT TESTED `./gradlew :app:testDebugUnitTest :app:assembleDebug`: Android SDK/Gradle dependencies not provisioned in this environment; no APK produced.
- NOT TESTED physical Xiaomi Pad 5, actual llama.cpp/third-party AAR GGUF loading, real medical PDF end-to-end correctness, source-citation clinical truth, model RAM/thermal/battery, backup restore on upgrade.

## Outstanding acceptance
- [ ] Import source into an independent **private** MedMate GitHub repo; keep signing keystore and personal PDF/model files private. Do not place it in StoryLens.
- [ ] Verify third-party native llama-android AAR implementation/license and build Android ARM64 APK.
- [ ] Run genuine GGUF inference on Xiaomi Pad 5 and inspect PDF-grounded explanations/High-Yield/MCQs with a medically reviewed test set.
- [ ] Verify cloud AI only with explicit user consent, with no automatic PDF transfer.
- [ ] Verify flashcards/SRS, Mistake Notebook, Exam Planner, backup/restore, and app UI on physical device.
- [ ] Perform independent complete recheck after all requirements are met.

## 2026-10-23 — MedMate public import readiness / citation safety
- Production: `BrainCore.kt` CitationValidator now rejects a model response made only of source markers or punctuation, even if `[S1]` is in range. This is a structural minimum, **not medical factual verification**.
- Regression: `scripts/brain/BrainPipelineSmoke.kt` now asserts citation-only and punctuation-only rejection; `bash scripts/test-brain.sh` **PASS (21 checks)**.
- Production: `app/build.gradle.kts` now uses the Android Gradle Plugin's normal debug keystore when the private upgrade-continuity key is absent, instead of making a public-source build depend on a private signing file. An APK built with the default debug key **cannot upgrade the existing signed installation**; never overwrite without backup/signing review.
- Public export guard: `scripts/test-public-source.py` checks signing fallback, `.gitignore` and absence of key/model files from a sanitized export.
- Additional PASS: `scripts/test-local-brain-v09.sh` (6 tutor + 12 import checks), `scripts/test-llama-adapter-contract.sh` (fake backend only), `scripts/test-model-memory.sh` (6), `scripts/test-domain.sh`, `scripts/test-export.sh`, `scripts/test-drafts.sh`, `scripts/test-study-evidence.sh`, `scripts/test-study-filter.sh`, `scripts/test-backup-lines.sh`, `scripts/test-migrations.py`, `scripts/validate-merged-contracts.py`.
- The aggregate `scripts/test-merged.sh` run exceeded the automation time limit after passing its first several subtests; individual scripts above were run separately. Do not claim aggregate PASS.
- GitHub: confirmed `amroalhamedi2022-hue/MedMate` is Public and initially empty; staging branch `automation/medmate-safe-source-import-20261023` created with an introductory README. **Full source has not been uploaded to GitHub**; no `main` commit or PR exists. A sanitized local ZIP is prepared for subsequent import. No user PDF or private signing key should be uploaded.
- NOT TESTED: Gradle Android APK, actual llama.cpp/GGUF inference on Xiaomi Pad 5, clinical truth of generated answers, physical device backup/upgrade, full UI.

## 2026-10-23 — Local-only cloud-context safety (not yet uploaded to GitHub)
- **Production:** `BrainPanel.kt` no longer promotes a paid OpenAI API shortcut; optional Cloud AI requires an explicitly supplied user-operated HTTPS endpoint and consent. Added `BrainRequestIdentity.kt` and guarded both delayed cloud explanation and generated-draft results against stale question/page/chapter context.
- **Tests:** `bash scripts/test-brain-request-context.sh` checks seven identity cases plus both UI branches and absence of paid API promotion.
- **NOT TESTED:** Android Gradle APK compilation, actual HTTPS endpoint, real Pad 5, live GGUF. This source change is **LOCAL ONLY**, not a GitHub commit or PR.
- **Privacy:** no private signing key, personal PDF, or GGUF included in public-safe source.


## 2026-10-23 — Full public-safe v0.9.1 source now in GitHub
**Supersedes the older “GitHub source not uploaded” statement above.** This section records an actual **production source import**, not just documentation. Full source is present on `automation/medmate-v091-source-tree-20261023` since import commit `18df4d0d768146cefce33cd1f6f5ac864111f357`, 116 sanitized files from round-3 ZIP SHA-256 `07b5b5d11de5f0b5c8948a9a84c1e43017870615e84cb6ea0356765a10ac8b0d`. Earlier local fixes to `BrainCore.kt`, `BrainPanel.kt`, `BrainRequestIdentity.kt`, and their real tests are now committed in the GitHub source tree. No code was rewritten from scratch.

| Gate | Status | Evidence |
|---|---|---|
| Import entire existing sanitized Android source, tests, Gradle wrapper | **PASS (source)** | 116 Git blobs; 6 sampled text/binary Git blob SHA-1 checks match local archive; import SHA above |
| Private keystore/personal PDFs/GGUF/secret exclusion | **PASS (local export audit)** | `python scripts/test-public-source.py` PASS; ZIP only contains synthetic `four-pages.pdf` |
| Cloud response source-context guard and opt-in self-hosted HTTPS | **PASS (local smoke/static)** | `bash scripts/test-brain-request-context.sh` 7 identity + UI checks; **NOT TESTED** live endpoint |
| Brain pipeline, offline drafts, memory preflight, migration, backup, export, study filters | **PASS (local tests)** | Commands and details in `docs/IMPORT_STATUS_2026-10-23.md` |
| Real native GGUF on-device | **NOT TESTED** | Fake Llama adapter tests are not model inference |
| Android Gradle compile/lint/APK and GitHub Actions | **NOT TESTED** | No Android SDK/Gradle available in local runner; workflow is manual dispatch |
| Xiaomi Pad 5 real PDF workflows, reading performance, clinical QA and upgrade | **NOT TESTED** | Needs actual device and independent recheck |

**Next:** get Android CI running on exact imported SHA, fix any build errors without private assets, verify real native model integration, then device testing and second-pass acceptance. No auto-merge or release.

- **GitHub Actions trigger caveat:** The imported `.github/workflows/android-debug.yml` has only `workflow_dispatch` and currently exists on the PR head, while the repository default/base branch is still docs-only. GitHub normally requires a manually dispatched workflow file to exist on the default branch. A PR alone will not run this workflow. An authorized, no-cost CI trigger/default-branch workflow setup is needed; do not bypass the no-main-write/no-workflow-edit rule silently.


## 2026-10-23 — Cross-chapter study-draft evidence isolation (production + smoke)
- **Production:** `GeneratedStudyDrafts.validate` rejects the entire candidate batch when supplied PDF evidence spans more than one chapter, even if a caller bypasses the prompt builder's chapter gate. Prevents accidental cross-book/page citation contamination at the untrusted model-output boundary. Deduplication now keys by **chapter ID + page index + kind + normalized prompt** rather than page alone.
- **Production source SHA:** `2e1c970c03ad13fa0f8e31ce81fa370fbb758741`; regression test SHA `e7f4633b4cff9dbb765f4dfd14b3beeb3a495584` on draft PR #1 `automation/medmate-v091-source-tree-20261023`.
- **PASS local** from a copy of the original sanitized v0.9.1 source with exact GitHub blob bytes (Git blob SHA verified for both files): `bash scripts/test-drafts.sh` (offline + generated), `bash scripts/test-brain.sh` (21 pipeline/security checks), `bash scripts/test-brain-request-context.sh` (7 context + UI guards). Kotlin source+smoke compiled with local `kotlinc` and executed successfully.
- **NOT TESTED:** GitHub Android CI (the existing workflow remains manual dispatch only on non-default branch; no workflow changes made), Gradle Android compile/APK, real native GGUF, PDF-grounded clinical accuracy, Pad 5 and independent second-pass device recheck. No paid API, signing key, private PDF, GGUF or release was added.
- **Next:** resolve authorized free CI trigger for the existing workflow and inspect build failures on its exact SHA; then real GGUF on-device and medical source QA. A Kotlin smoke pass is not an Android APK pass.


## 2026-10-23 — PDF evidence quote normalization and boundary safety (production + tests)
- **Production commit:** `0b6dfd9cadc58ffc3070074302270c37141a5f3f` changes `GeneratedStudyDrafts.kt`. Model-provided supporting quotes are still checked against the *actual selected Evidence text*, and the resulting page index/chapter ID still come only from Evidence, never from the model.
- PDF source extraction often introduces Unicode ligatures (e.g. `ﬁ`) or a line-break hyphen splitting medical terms. Citation matching now normalizes NFKC, soft hyphens, and true line-wrap hyphenation for comparison while retaining the original quote and source page in the draft. New boundary checks reject substrings clipped inside a larger word; this is stricter than a plain `contains()`.
- **Test commit:** `8c6215e788c9500c2f1699ffc556621a5b1e55d4` extends `GeneratedDraftSmoke.kt` with real-PDF-like ligature, split-word, correct page, and fabricated truncated-word cases.
- **PASS local** using Kotlin source with production blob SHA `7e13a3a950b2d83d4942f4733c26ffc351e8fc20`: `bash scripts/test-drafts.sh` (offline and generated), `bash scripts/test-brain.sh` (21), `bash scripts/test-brain-request-context.sh` (7 + UI). The locally executed smoke additions exercise the same four cases; **GitHub-hosted Android Gradle CI has not run**.
- **NOT TESTED:** Android compilation and APK, real on-device GGUF inference, clinical accuracy of quoted claims, scanned PDF OCR quality, physical Xiaomi Pad 5 and independent second-pass verification. This is citation-validation hardening, **not** a running generative Brain or proof of medical correctness.
- **CI blocker:** the existing `.github/workflows/android-debug.yml` remains `workflow_dispatch` only on the PR head, absent from default branch; no unauthorized workflow/default-branch edits were made. **No MedMate APK artifact exists for this source SHA.**
- **Next:** obtain explicitly authorized no-cost workflow trigger setup, compile the Android source, then verify real GGUF inference and grounded medical drafts on device.


## 2026-10-23 — GGUF header sanity gate (production + regression, not inference)
- **Production SHA:** `bc5fe551d39f3dcd30122355657ef868c76f8b57` updates `LocalModelImport.kt`: first 24 GGUF header bytes now include little-endian tensor and metadata counts, both required to be positive and within conservative sanity limits before the imported file is persisted. This rejects trivial fake headers and absurd counts without claiming full GGUF parsing or model compatibility.
- **Regression SHA:** `d4c0aa51a35176137a861479faa8cf90d2c49bb9` adds synthetic zero-tensor, zero-metadata, absurd count and short-header negative cases to `ModelAndTutorSmoke.kt`; `da10bf9c3f1490d264748d4b57ffe657e137e83a` updates `LocalBrainV09Test.kt` fixtures and rejection assertions. Fake Llama adapter remains only a fake backend.
- **Tests:** **NOT TESTED** Kotlin smoke execution and Android Gradle compile for these SHAs in this run (no repository checkout/Android SDK in execution environment; GitHub workflow remains `workflow_dispatch` on a non-default branch). Tests are added but not asserted PASS. **CI: NOT TESTED**, no MedMate Actions run/APK artifact.
- **Remaining:** run `bash scripts/test-local-brain-v09.sh` and `./gradlew :app:testDebugUnitTest :app:assembleDebug` in authorized free runner; inspect real GGUF header compatibility and actual ARM64 llama.cpp inference on Xiaomi Pad 5; medical PDF citations, RAM/thermal and independent device recheck still **NOT TESTED**.
- **Automation state:** `IN_PROGRESS`. No main write, paid API, private PDF, signing key, model weights, auto-merge or release.

## 2026-10-23 — GGUF early header rejection (no APK build)
- **Production changed: YES.** `LocalModelImport.import` now validates the first 24 GGUF bytes (magic, version and plausible tensor/metadata counts) immediately once available and **before writing the remainder of the stream**. Previously it copied up to 8 GiB and only rejected an invalid header at EOF. Supports short provider reads; keeps SHA-256 naming, temp cleanup, no overwrite and cancellation behavior.
- **Regression added:** `ModelAndTutorSmoke.kt` uses a malicious 24-byte-only provider, asserts rejection after the first read, zero progress callbacks and no orphan `.part` file. Existing synthetic import/reuse/invalid-count tests remain.
- **Verification:** static source inspection PASS; Kotlin smoke execution **NOT TESTED** in this run, Android Gradle/CI **NOT TESTED**. No APK or model was built, no paid API or personal PDF/GGUF/signing key uploaded. A valid synthetic header is not proof of real llama.cpp compatibility.
- **Next:** run `bash scripts/test-local-brain-v09.sh` in a source-test-only environment, then finish real native GGUF integration and independent device QA before the single final APK build.
