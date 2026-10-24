# MedMate Personal — autonomous source patch 2026-10-23

## 2026-10-24 — Untrusted PDF source-ID prompt-header hardening (source-only)
- **Production SHA:** `60cc106695495afba8396a32f2b1b5ce4587fc7e`; **regression commits:** `5b725f5a0d2f3d12f6e41ac7603aaa79cb211c4c`, `8a0661879eec7eb02dadf9305b5e012b9bc971c4`; corrected Kotlin newline escapes `32ecdf5541bfd3db29f94f58561932c66073e2e6`, `1e3c9fc1c8f7537d81e8c838c4e7b2ec02c973c9` on PR #1. No base/main writes.
- **Fix:** The prompt uses a bounded, single-line ASCII-safe display form of `Evidence.id`, so malicious chapter names cannot inject new prompt header lines or fake `[S999]` source labels. The original ID remains intact in `presentedEvidence` for PDF page navigation. Added Kotlin smoke/JUnit regressions and corrected existing bearer-token smoke cases to use actual CR/LF/TAB/NUL escape sequences instead of literal backslash letters.
- **PASS:** Six isolated Kotlin/JVM checks of the exact new source-ID normalization logic; static source/test integration checks and escape-sequence inspection. **NOT TESTED:** Compilation/execution of the full committed `BrainCore.kt` and new smoke/JUnit tests, Gradle/lint, APK, native llama.cpp/GGUF, Pad 5, clinical correctness, cloud gateway and real device security tests.
- **Build gate:** all writes `[skip ci]`; no APK or Actions dispatch. Final one-time APK build remains blocked on all remaining source/unit/static acceptance and actual device verification availability.

## 2026-10-24 — PDF prompt excerpts end at word boundaries (source-only)
- **Production commit:** `832c4b98f61ea0d5c23e1896738e867961f75c85`; **regression commit:** `3b6d52261695fc4e83be903fbb1b8227213efce9` on draft PR #1; no `main` writes.
- **Fix:** `MedicalPromptBuilder.prepare` now truncates oversized PDF excerpts at whitespace, not in the middle of a medical term or negation. If no >=40-character safe excerpt fits, that excerpt is skipped and later evidence may still be cited as `[S1]`. This improves citation clarity but is **not** medical factual verification or AI inference.
- **PASS (isolated only):** Kotlin/JVM compilation and execution of two cases using an extracted equivalent of the new prompt-boundary logic: normal truncation at whitespace, and skipping a long unbreakable excerpt while keeping later evidence. New regressions added to `scripts/brain/BrainPipelineSmoke.kt`.
- **NOT TESTED:** Running the committed full Brain smoke script against the repository, Android Gradle compile/lint, APK, real llama.cpp/GGUF, device RAM/thermal, clinical grounding and physical Pad 5. No GitHub Actions build dispatched.
- **Outstanding:** Real GGUF integration/device acceptance and independent recheck remain IN_PROGRESS.


## 2026-10-24 — Source-only evidence continuity and Kotlin smoke repair
- **Source + tests + scripts commit:** `80ca366bffdd6e6457d88bd38eec15d105916241` on draft PR #1. No base/default branch writes.
- **Fix:** `MedicalPromptBuilder.prepare` now skips short/empty excerpts instead of aborting the entire evidence loop. Subsequent valid PDF pages retain contiguous `[S1]`, `[S2]` markers; the excerpt character budget includes the two trailing newlines. This fixes a potential false no-evidence response when a short retrieved fragment precedes a usable one.
- **Regression added:** Kotlin smoke assertions and Android JUnit source tests cover later valid excerpts, marker numbering and source mapping.
- **Smoke scripts repaired:** `test-brain.sh`, both `test-drafts.sh` compiler calls, `test-local-brain-v09.sh`, and `test-llama-adapter-contract.sh` now include `ClinicalTermNormalizer.kt`, which `BrainCore.kt` requires. Static inspection of all five compiler blocks: **PASS**.
- **PASS:** Isolated Kotlin/JVM compilation and execution of the equivalent excerpt selection loop, including short-fragment skip, contiguous markers and bounded excerpt length. **NOT TESTED:** full production `BrainCore.kt` compilation, newly committed Kotlin smoke/JUnit tests, complete Android build, device runtime, medical accuracy, offline field tests and real GGUF inference. Isolated test is not proof of a working local generative model.
- **CI policy:** `[skip ci]` commit; 0 PR workflow runs reported for this SHA when checked. No APK created or Actions build dispatched.
- **Open:** real llama.cpp/GGUF integration, source-grounded study flow, final UI and independent device verification remain incomplete. No Library/Handoff access.


AUTOMATION_COMPLETE: IN_PROGRESS

## 2026-10-24 — Citation marker hardening (source-only)
- **Production commit** `cf5d591b1a55bf1455efa9de435a0bd41e50c82e`; **test commit** `dd77392ea92c0e685b7b456bc2345b3ad49baa10` (draft PR #1, no main write).
- **Fix:** `CitationValidator` now identifies all bracketed source-like markers (including malformed alphabetic `[Sfoo]`, negative `[S-1]`, colon `[S:2]`, whitespace `[ S1]` and incomplete `[S1`), so a valid `[S1]` elsewhere cannot conceal a forged citation. This is **syntax verification only**, not clinical claim verification.
- **PASS:** 7 V8 regex-parity cases; additionally compiled and ran an **isolated Kotlin/JVM regex harness** with 12 malformed and 3 valid markers (15 checks, PASS). This verifies the regex on Kotlin/JVM but does not compile the Android project. New Kotlin project smoke assertions committed in `scripts/brain/BrainPipelineSmoke.kt`.
- **NOT TESTED:** actual Kotlin compilation/smoke script, Gradle unit suite, GitHub CI, Android APK, real GGUF inference, PDF grounding/medical factual correctness on device and independent recheck. Do not treat JS regex parity as Kotlin test execution.
- **Build policy:** `[skip ci]` on new commits; no build or workflow dispatch requested. No PR workflow run reported at test checkpoint `dd77392e`. Source-only development continues.


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

- **Exact production/test commit:** `e6d6a219b9dae73189a814bba60e78067542b3b6` ([PR #1](https://github.com/amroalhamedi2022-hue/MedMate/pull/1)). Source-order static gate **PASS**; Kotlin smoke, Android CI, real GGUF and device **NOT TESTED**. No APK build requested.

## 2026-10-23 — Safe no-evidence abstention in PDF-grounded Brain (source-only)
- **Production changed: YES.** `CitationValidator.check` now accepts the exact Arabic insufficient-evidence response even when retrieved PDF excerpts exist but cannot answer the question. It allows harmless surrounding punctuation/spacing, while rejecting fabricated claims that merely append the abstention phrase and rejecting unknown citation markers. This prevents pressure to hallucinate a source marker; it is **not** clinical factual validation.
- **Production SHA:** `22ff100f871f9590ad27c2145a3c7712bf805e51`; **regression SHA:** `7f38d409124b617543097b751db85fa83eb528fc` on existing draft PR #1.
- **PASS (isolated Kotlin logic smoke):** `kotlinc /tmp/MedMateCitationSmoke.kt -include-runtime -d /tmp/medmate-citation-smoke.jar && java -jar /tmp/medmate-citation-smoke.jar` — 8 checks on a standalone copy of the changed validator logic (abstention, punctuation, fabricated claim, unknown citation, source-only, citation-only). `scripts/brain/BrainPipelineSmoke.kt` was extended with the corresponding source-level regressions.
- **NOT TESTED:** full repository `bash scripts/test-brain.sh`, Gradle unit tests, Android compile, CI, real GGUF inference, medical PDF clinical accuracy, physical Pad 5 and independent recheck. **No APK or model was built**. Existing manual-only Android workflow was not dispatched.
- **Next:** run complete source-only Kotlin smoke from a checkout when available; continue native GGUF integration, model/PDF citation QA, SRS/backup and security without an interim APK.


## 2026-10-23 — Strict PDF source marker syntax / anti-citation-laundering (source-only)
- **Production changed: YES.** `CitationValidator` now accepts only exact `[S1]`, `[S2]`, ... syntax and rejects malformed citation-like tokens (`[S01]`, `[s1]`, `[S 1]`, `[S1 extra]`, `[S1x]`, `[S]`, unclosed `[S1`) even if the same response also includes a legitimate source marker. The explicit insufficient-evidence abstention remains valid without fabricated citations. This is syntactic grounding protection, **not** clinical truth verification.
- **Production + regression commit:** `8556bb11c80dbf3f7108bff4428a228a965554f2` on draft PR #1. `scripts/brain/BrainPipelineSmoke.kt` gains eight source-marker negative tests.
- **PASS:** `kotlinc /tmp/CitationCheck.kt /tmp/CitationSmoke.kt -include-runtime -d /tmp/citation-smoke.jar && java -jar /tmp/citation-smoke.jar` — 14 checks of the identical extracted Kotlin validator implementation, including positive citations, malformed markers, overflow, abstention and citation-only rejection. **NOT TESTED:** complete `bash scripts/test-brain.sh` from the repository, full Android Gradle unit/lint/build, GitHub CI on this SHA, genuine GGUF model inference, real PDF clinical grounding, Xiaomi Pad 5, independent device recheck. The new repository smoke cases were authored but not executed here.
- **CI:** existing workflow is manual-only; no build dispatched. **APK:** none for this SHA. No paid API, private PDF, signing key, model weights, main write, merge or public release. `AUTOMATION_COMPLETE: IN_PROGRESS`.
- **Next:** full source-only tests, finish verified native GGUF lifecycle and PDF safety, SRS/backup QA, then one final APK build after requirements are complete.

## 2026-10-24 — Citation-free evidence abstention (source-only)
- **Production commit:** `b1d4518ace87114fc8eb394843ce46f7db3c7d0d` updates `CitationValidator`: the exact insufficient-evidence response is accepted only **without** source markers. Previously `المعلومة مش كافية بالنص الموجود [S1]` could pass, falsely suggesting a PDF citation supports missing evidence. Normal sourced explanations still require valid markers; citation-optional mode cannot attach a marker to abstention either.
- **Regression commit:** `3266f65b0e608ff8517b14c61ecff71abc5186e9` adds two cases to `scripts/brain/BrainPipelineSmoke.kt`, covering normal and citation-optional modes.
- **PASS (isolated Kotlin):** compiled an isolated copy of the changed validator logic with `kotlinc`, executed eight checks via `java -jar`: source-free abstention with/without excerpts, rejection of cited abstention in both modes, valid sourced explanation, unknown source, marker-only and malformed marker. This is **not** full-repository test execution.
- **NOT TESTED:** full `bash scripts/test-brain.sh` from current checkout, Android Gradle/CI/APK, actual llama.cpp/GGUF, medical accuracy, physical Pad 5, clinical review and independent recheck. No new APK, private PDF, GGUF, secret, workflow or main change. `AUTOMATION_COMPLETE: IN_PROGRESS`.

## 2026-10-24 — PDF retrieval performance and page-scope correction (source-only)
- **Production commits:** `bb00169f6f0baebf50c909394f58b54f1dded258` replaces repeated full-token-list scans with a query-term-only frequency index in `EvidenceRetriever` while preserving the existing scoring/ranking formula; `f677106a62d685e11bcc280adc5daedba28b77a0` corrects `StudyBrain.search` so short questions count readable pages only from the selected chapter and optional page.
- **Regression commits:** `3702dd380535ab70094db136e51ea07ba82d3efd` adds focused pipeline smoke checks; `226c22d95df7fff56278bed566f04803be54a7e5` adds `BrainRetrievalRegressionTest.kt` JUnit coverage for frequency ordering, page/chapter isolation and short queries.
- **PASS:** source-level inspection of exact changes and preservation of scoring formula. **NOT TESTED:** execution of the new Kotlin smoke/JUnit tests, full Kotlin/Android build, GitHub CI, real GGUF inference, actual speed/RAM measurement, medical accuracy and physical device recheck. Optimization is not a measured performance improvement until benchmarked. No APK, paid API, personal PDFs, model weights, main write or auto-merge.
- **Next:** run source-only Kotlin smoke/JUnit in an authorized environment, complete native GGUF model lifecycle and evidence-grounded study features, then single final APK and independent device recheck.


## 2026-10-24 — source-only clinical English plural bridge
- **Production:** new `ClinicalTermNormalizer.kt` maps 13 explicitly enumerated medical English plural forms to their singular canonical tokens (fractures/fracture, arteries/artery, injuries/injury etc.). `MedicalText.tokens` now canonicalizes PDF text, and `MedicalText.queryTokens` canonicalizes Arabic->English aliases as well. No general-purpose stemming or clinical claim generation; unknown terms like diagnoses/analysis remain unchanged. Existing chapter/page filtering and ranking formula are unchanged.
- **Source commits:** normalizer `7028cf736033fe3ef463ef2dcd831b7050440dd3`, smoke source `d2a2a99f6b46b80a2fccebd725e25adb172e5460`, runner `6c77e3a8f6819f9e03f8dec50b98b9a2674a0557`, BrainCore integration `510d1ca1e0decf579105757000fe1bfd9414d5bd`.
- **PASS:** compiled and executed the exact isolated `ClinicalTermNormalizer.kt` implementation using local `kotlinc`; 5 standalone canonicalization assertions passed. **NOT TESTED:** the new full 11-check `scripts/test-clinical-terms.sh` against integrated `BrainCore.kt`, Android Gradle/CI, true GGUF generation, Pad 5 and clinical correctness. Do not equate lexical plural matching with local AI inference.
- **Privacy/build:** no keys, user PDFs, GGUF weights or APK added; no GitHub Actions run or interim APK requested. PR remains review-only, no main write or merge.

## 2026-10-24 — Explicit Arabic surgical-term PDF retrieval bridge (source-only)
- **Production SHA:** `eab579fb68904fe321cfa806ad607933dcdfc7fa`; **regression SHA:** `72c3858ecd66d9fae00a42569dd038c115fb3229` on draft PR #1.
- **Production:** eight small, explicitly audited Arabic-to-English retrieval aliases for appendix/appendicitis, pancreas/pancreatitis, gallbladder/cholecystitis, obstruction, intestine/intestinal, peritoneum/peritonitis, ulcer and perforation. Existing Arabic normalization and chapter/page restrictions remain in force. These are search hints, **not diagnosis, clinical translation validation, or a generative model**.
- **Tests authored:** ten new source smoke assertions in `scripts/brain/ClinicalTermsSmoke.kt` covering six Arabic surgical queries and four chapter/page isolation cases; total asserted checks becomes 21.
- **PASS:** static review of production alias map and regression test source. **NOT TESTED:** actual Kotlin smoke execution, full Gradle/JUnit/Android CI, real GGUF model inference, PDF medical accuracy, Pad 5 performance and independent recheck. No APK build requested; commits use `[skip ci]`.
- **Status:** IN_PROGRESS; native llama-android path is present but not yet validated with a real GGUF on a device. No private PDFs, keys, model weights or paid API.


## 2026-10-24 — PDF extracted-term dehyphenation (source-only)
- **Production commits:** `a2bdd79b78b83dcda26de3c4c8143a6282ab6184` and Kotlin escape correction `5044a6e6bf780a941d104eb7346cb03524b0da4c`. `MedicalText.normalize` now rejoins Latin medical words split by an end-of-line hyphen and removes discretionary U+00AD soft hyphens before tokenization. Normal hyphenated compounds and paragraph breaks remain distinct; no generative AI capability is implied.
- **Regression commit:** `bcbcb899f1ea16977230d736ba04ad78c87bb227` adds six cases to `scripts/brain/ClinicalTermsSmoke.kt` including English PDF hyphenation and retrieval.
- **PASS:** compiled and executed five **isolated Kotlin/JVM checks** of the same normalization expressions with `kotlinc` and `java -jar` (LF/CRLF wrapped words, soft hyphen, ordinary compound, paragraph boundary).
- **NOT TESTED:** full repository `scripts/test-clinical-terms.sh`, Gradle tests, native GGUF inference, clinical correctness, Android build, offline/performance/Pad 5 and independent device recheck. No APK, workflow dispatch, paid API, private PDFs or model weights used. Status: **IN_PROGRESS**.


## 2026-10-24 — Surgical plurals in PDF retrieval (source-only)
- **Production commit:** `9ec00bc88db7ed39bc2aa8874226ab1ec2ea6645`; **smoke test:** `deb8b948dc7f9541ea84b1d45b12223b4f07bc97`; **JUnit regression:** `6565e5fe27c4115fedc87424e95eb7a79a7cea6a` on draft PR #1.
- **Fix:** explicit English plural normalization for ulcers, perforations, obstructions, intestines, gallbladders, abscesses and adhesions. Query and PDF tokens already share the same canonicalizer; no general stemming, change to rank formula, page scope, or AI-generated clinical claims.
- **PASS:** compiled and ran 9 isolated Kotlin/JVM assertions with `kotlinc` and `java -jar` against an equivalent standalone copy of the canonicalizer (7 surgical plurals plus two intentionally unchanged irregular words).
- **NOT TESTED:** newly committed integrated ClinicalTermsSmoke/JUnit execution, full Android Gradle tests/lint, actual PDF corpus clinical relevance, GGUF/native inference, Pad 5 offline/RAM/performance and independent recheck. Isolated smoke is not proof of integration.
- **Build:** no APK, no workflow dispatch, no merge, no paid API, no private medical data. Status: **IN_PROGRESS**.

## 2026-10-24 — GGUF import/load UI state consistency (source-only)
- **Production commit:** `cac30204d1548f4cdd6901e7b4eb0e68638b8811` updates `BrainPanel.kt`: after a successful local GGUF import, refresh the saved-model list **before** the RAM preflight/native activation; failed activation no longer hides an already-loaded native session. Saved-model activation failure also mirrors `onDevice.loadedModelName` instead of unconditionally clearing the UI label. No native backend/API/model weights changed.
- **Regression commit:** `dbd09c710a0de1c28c8a5e5266290ec8ed787503` adds `scripts/test-model-load-ui-contract.py`, a source-only regression checking ordering and both error paths.
- **PASS (static source checks):** four assertions against the exact current GitHub production/test blob verified list-refresh ordering, two failure paths, and presence of regression guards.
- **NOT TESTED:** running the Python regression script from a repository checkout, Android Kotlin/Compose compilation, RAM failure on a physical device, actual llama.cpp/GGUF loading, medical accuracy, offline performance and independent device QA. No APK/Actions run, paid service, main write or merge. **IN_PROGRESS.**

## 2026-10-24 — Ephemeral cloud bearer-header validation (source-only)
- **Production:** `GatewayPolicy.kt` adds `validSessionToken` to enforce <=2048 printable ASCII characters (empty allowed for an unauthenticated personal relay), rejecting CR/LF/TAB/NUL and ambiguous Unicode before constructing the Authorization header. `CloudBrainGateway.kt` uses this shared policy instead of checking LF alone. No paid API, stored secret, cloud auto-fallback or gateway activation was introduced.
- **Regression commit:** `009712764921b9b2f2469f1bc084183314242302` adds checks to `scripts/brain/BrainPipelineSmoke.kt`. Production integration commit: `380f5d60b1b5f8f3cbb7fb754e09c6bbaae8396e`; policy blob SHA `f5bf3ac09e1985774d4d7ce5a8b0ee21dd3ef09d`.
- **PASS:** isolated `kotlinc` + `java -jar` compiled the current GatewayPolicy source in a temporary JVM harness and passed 10 checks (blank/normal/max-length accepted; CR/LF/TAB/NUL/Unicode/oversize rejected; HTTPS endpoint accepted). Ten source-contract assertions checked the exact committed gateway/policy/regression code. **This is not a full integrated smoke test.**
- **NOT TESTED:** complete `scripts/test-brain.sh`, Gradle JUnit/lint, Android compile, real HTTPS relay, real GGUF/llama.cpp inference, PDF medical accuracy, Pad 5 RAM/thermal/offline/install tests.
- **CI/APK:** no workflow dispatched or APK created. PR remains open/unmerged, `main` untouched. **Status:** IN_PROGRESS.
