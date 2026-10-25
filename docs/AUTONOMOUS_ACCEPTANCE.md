## 2026-10-25 — Medical draft quote negation-scope safety milestone

- **Production:** `GeneratedStudyDrafts` now conservatively rejects excerpts that drop a negation within the preceding six words of the same clause, not only the immediately adjacent word. This blocks misleading crops like `No radiographic evidence of infection` -> `infection is present`. English/Arabic hard sentence breaks are respected; commas preserve negation in symptom lists, but a contrastive `but/لكن` permits an independent positive clause. Quotes beginning with their own explicit negation remain eligible.
- **Regression:** added eight bilingual positive/negative JUnit cases, plus comma-list cases. This is a **lexical safety filter**, not medical truth/clinical entailment verification; legitimate excerpts may require a longer faithful quote.
- **PASS (isolated Kotlin/JVM):** compiled and executed eight algorithm checks with `kotlinc` and `java`; corrected comma-clause behavior after a failed local test. This isolated harness does **not** compile the complete Android project.
- **NOT TESTED:** full JUnit/Android CI on current SHA, genuine llama.cpp/GGUF inference, clinical accuracy, HTTPS allow/deny on device, Pad 5 RAM/thermals and final signed release.
- **Targeted build question:** do the real production Kotlin changes and committed JUnit tests compile and pass together with Compose, SQLite, lint and Android packaging? Isolated algorithm checks cannot answer this; one CI validation build is justified, not a routine per-line build.
- **Status:** IN_PROGRESS; PR #1 Draft, no main merge, no PDFs, GGUF, private keys, paid API, or unrelated Library/Handoff changes.

## 2026-10-25 — Cloud payload-bound consent + exact PDF preview
- **Production:** `CloudEvidenceSeal.of` now fingerprints the entire reviewed explanation prompt in addition to question, chapter, page and exact excerpts; altered prompt text with unchanged excerpts cannot reuse a grant. Cloud study-draft generation validates the **original reviewed PreparedPrompt** and derives its JSON-only prompt deterministically from the same source excerpts, avoiding a mismatched consent seal.
- **UI:** Cloud consent is disabled until the user opens a preview showing the exact bounded PDF excerpts (with [S#] page markers) and question. Preview-review state resets when the prepared question/PDF context changes; no PDF bytes, tokens or models are persisted/uploaded to GitHub.
- **Tests committed:** JUnit regressions for changed prompt with unchanged source and deterministic draft-source consent, alongside prior one-shot/replay/mismatched chapter checks.
- **PASS (source):** verified changed Kotlin source and UI integration paths against the same PR branch; previous cloud-citation CI run 40173779293 completed SUCCESS on older SHA `bb917c12`, **not** evidence for this batch.
- **PASS (JUnit/Compose/Android CI):** [run 40177851929](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40177851929) on functional SHA `17c2434fd67531f02a1b1c6fe63217a9c1e84820`: SQLite contracts, `testDebugUnitTest`, `lintDebug`, `assembleDebug` all succeeded. Debug Artifact **12592627832**, validation reports **12592463544**.
- **PASS (independent artifact rehash):** downloaded Debug Artifact and extracted `app-debug.apk` (33,536,769 bytes); SHA-256 `a4bbb9b347230c6e90870fc0f69531a3ffe014121295228a0378f73652b081c2` independently matches CI checksum. ARM64 `libllama.so` and `libllama_jni.so` packaged; no `.gguf` model included. This is NOT proof of native inference.
- **NOT TESTED:** actual HTTPS allow/deny behavior on a device, real GGUF inference, Xiaomi Pad 5, clinical correctness and final signed release.
- **Build question:** do new consent/JUnit contracts compile with the Compose preview and remain packaged without breaking the existing native llama binding? Source inspection cannot prove Kotlin/Compose compilation; one targeted CI run is justified.
- **No final release:** PR #1 Draft; no `main` writes, no paid API, no Handoff/Library changes.

## 2026-10-25 — Reject cloud explanations with missing/forged PDF citations
- Production: `CitationValidator.requireGroundedCloudAnswer` rejects ungrounded or forged cloud explanation output; `CloudBrainGateway.explain` invokes it before returning any answer to the UI. An explicit evidence-insufficient abstention without a fake citation remains valid. Study-draft JSON uses its separate quote/source validation, unchanged.
- Regression: four checks added to `scripts/brain/BrainPipelineSmoke.kt` and a new Android JUnit `CloudAnswerGateTest.kt` for valid source markers, unsupported claims, forged markers and abstention.
- **PASS (source inspection):** production gateway calls the new guard; both source and test paths are committed. **NOT TESTED:** actual Kotlin/JUnit/Android CI on this SHA (pending), HTTPS request behavior, device cloud privacy, GGUF inference, medical correctness, and Pad 5 performance.
- **Build question:** will the new Kotlin gateway wiring and JUnit test compile and pass in the real Android project? Static inspection alone cannot answer. A targeted CI build is justified; debug APK is not a final release.
- **Status:** IN_PROGRESS; PR #1 Draft, no main write, no personal PDF/model/token upload or Handoff/Library access.

# MedMate Personal — autonomous source patch 2026-10-23

## 2026-10-24 — Harden source-grounded explanation question boundaries (source-only)
- **Production commit:** `1305975a87163850546596638a49684fe57e8c76`; **JUnit regression:** `018bc4220c3704a232ecd115840f0fa96e5210bf`; **Kotlin smoke regression:** `5dd4997fd521c355b1df388f322e8a4afc6e0bd9` (draft PR #1 only).
- **Fix:** `MedicalPromptBuilder.prepare` now flattens control/format/newline characters and caps the untrusted student question at 1,200 characters before inserting it into EXPLAIN prompts. The original question remains in `PreparedPrompt.question` for async request identity; citation and PDF page anchors remain unchanged. Invisible-only questions fail before model invocation. This reduces prompt-header spoofing, **not** semantic prompt-injection or clinical risk.
- **PASS:** 4 isolated Kotlin/JVM checks of equivalent prompt-sanitization logic (forged headers, length bound, invisible-only input, ordinary question); inspected actual PR production source and regression tests for matching expressions and call sites. **FAIL:** 0 in the isolated checks.
- **NOT TESTED:** Full committed `scripts/test-brain.sh`, actual production `BrainCore.kt` compilation, Gradle/JUnit/lint, cloud gateway, real GGUF/llama.cpp, clinical correctness, Pad 5/offline/performance, APK and SHA-256.
- **Build policy:** all commits `[skip ci]`; no APK or Actions build requested, no `main` modification, no merge. Final build deferred.

## 2026-10-24 — Bounded, single-line student topics in generated study prompts (source-only)
- **Production commit:** `28da61ab8d244a7515c6be7afda12aabf4722a93`; **smoke commit:** `0cfdfc542654f6aa3458aa03a38e219f7ffdbec6`; **script commit:** `13964a2f1f87c0903f19b7228cb6d8c929976e81`; **JUnit commit:** `efacdd894ff5ad9a3e13bd4b5247dc06f5537bc9` (draft PR #1).
- **Fix:** `StudyDraftPrompt.prepare` collapses control/format/newline characters from student questions into a single line and caps the inserted topic at 1,200 characters; retains the original question in `PreparedPrompt.question` for identity checking. Direct callers cannot pass more than 20,000 source text characters. This reduces prompt-section spoofing/context overflow but does **not** prove semantic prompt-injection resistance or clinical correctness.
- **PASS:** Kotlin compiler + JVM executed 7 isolated checks using the production `StudyDraftPrompt.kt` copied from GitHub and minimal matching model stubs; checked forged newline section, source marker continuity, length cap and oversized evidence rejection. Added matching repository smoke/JUnit regressions; `scripts/test-drafts.sh` now compiles the actual prompt class.
- **FAIL:** 0 in the 7 isolated checks. **NOT TESTED:** full committed `scripts/test-drafts.sh` and JUnit suite, Android Gradle/lint, cloud HTTPS gateway, actual llama.cpp/GGUF, clinical accuracy, Xiaomi Pad 5 RAM/thermal/performance, offline device testing, final APK and SHA-256.
- **Build policy:** all commits `[skip ci]`, no APK/Actions build dispatched, `main` untouched. Full source and physical-device gates remain open.


## 2026-10-24 — Preserve medical words at PDF segment boundaries (source-only)
- **Production commit:** `7051c6b5cc3b1674b921f7348c7296d56c7aff0d`; **smoke regression:** `590d4c739a030a4aa00ffb9a01fa2b21ab8144d1`; **JUnit regression:** `21db0e2d417e57b77a8f9579bf7c4231814339ec` (MedMate PR #1 only).
- **Fix:** PDF paragraph segmentation now prefers a whitespace boundary before the character cap so common medical terms are not silently split into unsearchable fragments. For very long unbroken tokens, it retains bounded, lossless hard splits; chapter/page anchors and 128–6000 character limits are unchanged.
- **PASS:** Compiled and executed a standalone Kotlin/JVM reproduction of the modified production `PageSegmenter` algorithm with 3 assertions: multi-chunk bounds, exact paragraph reconstruction without invented mid-word spaces, and lossless hard-split fallback (5 ordinary chunks, 3 fallback chunks).
- **NOT TESTED:** Running the newly committed smoke/JUnit tests against the complete source checkout, Android Gradle compilation, GGUF real model inference, clinical accuracy, device RAM/thermal/performance, offline device, APK and SHA-256. Standalone reproduction is not a full-project test.
- **Build policy:** All commits `[skip ci]`; no Actions build dispatched; `main` unchanged. Single final APK build deferred.


## 2026-10-24 — Unicode confusable citation spoof rejection (source-only)
- **Production SHA:** `41edadce2621421118527211ab7250b7e4284861`; **test SHA:** `7a8a8ee8e263f33ebd442b4c4e39e390d5a053e3` (draft PR #1; no main write).
- **Fix:** `CitationValidator` now rejects fullwidth and mathematical Unicode source-marker lookalikes (e.g. `[Ｓ1]`, `［Ｓ１］`, `[𝐒1]`) even when a genuine `[S1]` is present. NFKC is used only to **detect** spoofed markers; it does not convert them into accepted citations. Existing exact ASCII marker behavior and source-count validation remain.
- **PASS:** Compiled and executed a standalone Kotlin/JVM smoke of the exact new regular-expression and NFKC detection logic using `kotlinc` + `java`: 10 malformed-marker cases rejected and 3 valid cases preserved. Fetched both updated repository files and statically verified the guard and regression cases are present.
- **NOT TESTED:** Executing the committed `scripts/test-brain.sh` / JUnit suite in the complete project, Android Gradle compile/lint, APK, actual native GGUF inference, clinical grounding accuracy and Pad 5 memory/performance/offline behavior. A regex smoke is not proof of medical correctness.
- **Build policy:** Both commits use `[skip ci]`; no Actions build or APK created. Continue until all source and device acceptance gates are complete.


## 2026-10-24 — Unicode-invisible citation spoofing guard (source-only)
- **Production SHA:** `9d5157388cbd3203d52d3ff86f1c33d6e0495b32`; **regression SHA:** `2de0bb4c17b8d0f56f5687051b73fb47919a9a9e`, draft PR #1; no base/default-branch changes.
- **Fix:** `CitationValidator` now catches source-like markers with Unicode separators, zero-width spaces and word-joiner characters before `S`, e.g. `[NBSP S1]` and `[ZWSP S1]`. A separate valid `[S1]` no longer masks these visually misleading malformed markers. This validates citation syntax, **not medical truth**.
- **PASS:** Compiled and executed an isolated Kotlin/JVM harness for the exact updated regex: 6 malformed-marker cases and 1 valid-marker case passed. Committed matching regression cases to `scripts/brain/BrainPipelineSmoke.kt`.
- **NOT TESTED:** Compiling/running the committed full BrainCore and smoke suite, Android Gradle/lint, APK, genuine GGUF/llama.cpp on Xiaomi Pad 5, clinical grounding, offline device behavior and performance.
- **Build policy:** `[skip ci]` commits; 0 PR-triggered workflow runs on regression SHA when checked. Final APK deferred until source/unit/static gates and device-readiness criteria are met.


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


## 2026-10-24 — Strict UTF-8 JSONL restore and CRLF boundary (source-only)
- **Production commit:** `c23247b0a56041eec5d815a6b82c1fd4fc428715` in `BoundedLineReader.kt`. **JUnit regression commit:** `cd422cc2a0f53b7933dda24ebc2fd143f0a59f3c` on draft PR #1 (not main).
- **Fix:** restore scanner now uses an explicit UTF-8 decoder with `CodingErrorAction.REPORT`; malformed byte sequences can no longer be silently replaced with U+FFFD in medical notes/study-card JSONL. CRLF's trailing CR is excluded from the record length bound so an exactly `maxChars`-long line remains valid; a bare CR still counts toward the limit. Existing line-size limits and JSONL schema are unchanged.
- **PASS:** compiled the **same production Kotlin source** with local `kotlinc` and executed four JVM checks: Arabic/CRLF exact-limit reading, bare-CR length rejection, malformed UTF-8 rejection and oversized-line rejection. **FAIL:** none observed in these isolated checks.
- **NOT TESTED:** committed JUnit tests in the full Gradle project, Android build/lint, full restore transaction on device, GGUF/llama.cpp inference, medical accuracy, Pad 5 offline/RAM/performance/install and final APK. A source-only Kotlin smoke is not proof of Android restore success.
- **Build/merge:** no APK, no Actions dispatch, no main write, merge, paid service, private PDF/model weight or Library/Handoff. Status **IN_PROGRESS**; final APK build remains gated on full feature/source-test completion.


### Independent Kotlin source/test replay (same batch)
- **PASS:** additionally compiled the exact committed production blob `46818a192c56fe13e0b8e85050d2a5ebfbcb0737` and the exact committed JUnit test-source blob `b00d4758763a398f12e1e58de3c57f998aaa954d` with local `kotlinc`. Executed all five test methods with lightweight local JUnit assertion shims: **5/5 PASS**. The local `git hash-object` values matched both repository blobs byte-for-byte.
- **NOT TESTED:** genuine JUnit/Gradle test runner and Android instrumentation/device. The assertion-shim replay validates test logic only; it is not a substitute for the full project test suite.

## 2026-10-24 — Pre-inference study-draft evidence isolation and real LF test coverage
- **Production SHA:** `481bcdaa987c4c8255f3ec7784ca5c2d960d439d`; **test SHAs:** `58d57c02d54c3e0b2d09e5db429f896c99c3db4a`, `ed56a583c13919cb494a9377390a557b400bb318`, `d45818f6ddceda171f8e7fac71cce748f7c5b7db`, `10ae693361be06c420b13845eb49b9a149bd455a`. `StudyDraftPrompt.prepare` now rejects mixed-chapter, blank, negative-page, and >20-source evidence **before** local inference or optional cloud transmission, rather than relying only on post-generation validation. Existing 20,000-character limit remains. Replaced test inputs/assertions using literal `\\n` with actual Kotlin newline escapes to test prompt-header spoofing properly.
- **PASS (actual Kotlin source):** Git blob SHA `5934c0853d27c0927c137301c95c1e7bd3b4a6d0` matched the copied production `StudyDraftPrompt.kt` byte-for-byte; compiled with `kotlinc` and minimal model stubs and passed 12 JVM checks (including 20-source allowed, 21-source rejected, mixed chapters rejected, malformed source rejection and actual LF flattening). **FAIL:** first manual copy had incorrect regex escaping and did not compile; corrected copy matched the Git blob and compiled. This was a local transcription error, not a GitHub source failure.
- **NOT TESTED:** repository Gradle/JUnit suite, complete Brain pipeline, cloud endpoint, Android APK, Pad 5, clinical correctness and real GGUF inference. Zero PR workflow runs reported for the latest test SHA. No APK created. The source gate does not prove a working on-device model.

AUTOMATION_COMPLETE: IN_PROGRESS

## 2026-10-24 — Generated study-draft source trust boundary (source milestone)
- **Production:** `GeneratedStudyDrafts.validate` now rejects an entire generated draft batch when evidence is empty, exceeds 20 sources/20,000 characters, has an empty chapter, mixes chapters, contains negative page indices or blank excerpts. This independent output boundary matches `StudyDraftPrompt` input limits, preventing malformed PDF page anchors from being saved even if a caller bypasses prompt preparation. This does **not** establish medical correctness.
- **PASS (actual production Kotlin):** Git blob `12e4c0add2d036a1006e9acf9730d00c428561e3` was reproduced byte-for-byte in an isolated Kotlin/JVM harness; compiled with `kotlinc` and ran **8 assertions**, including valid evidence and 7 invalid source batches. All passed. Added regression cases to `scripts/brain/GeneratedDraftSmoke.kt` and Android `GeneratedDraftsTest.kt`.
- **NOT TESTED:** full committed `scripts/test-drafts.sh`, Gradle/JUnit, Android compilation/APK, real GGUF inference, device RAM/thermal/offline, cloud HTTPS consent path, medical claim accuracy and PDF-to-draft UI.
- **Gate:** malformed-evidence rejection **PASS (isolated production source)** / **NOT TESTED (full suite/device)**. No fake backend test is claimed as real GGUF inference.
- **Commit checkpoint:** `281514c49275026f25743afc0cd42d784494e6da` (production + smoke + JUnit, draft PR #1 only). No APK/Actions build, no merge/main write.


## 2026-10-24 — Consent-bound cloud study requests (source milestone)
- **Source checkpoint:** `ebdcfb437a31554f11d47b56141a200d8b9e7716` (before this ledger entry), draft PR #1 only; base/default branches untouched.
- **Production:** `CloudTransferConsent` binds a one-use in-memory non-copyable approval to the canonical HTTPS gateway, model and exact indexed chapter/question/page identity. Both Cloud Explain and generated High-Yield/MCQ/Flashcard requests capture immutable endpoint/model/token at click, consume approval before dispatch, and enforce the same approval again inside `CloudBrainGateway.complete` before `openConnection`. Editing endpoint/model or starting a new search revokes approval. The gateway still disables redirects, has no default paid provider and never stores session tokens. This reduces accidental disclosure; it does not prove a live endpoint safe.
- **PASS:** `kotlinc` + `java` executed **11 pure Kotlin checks** against the exact new production `CloudTransferConsent.kt` and `CloudConsentSmoke.kt` bytes (Git blob SHA matches `1d8f064b2be7f9e7466557c17589ca9b2c5eea5e` and `e50b9d2cf3ffd2347f16dd699adeb878a487dd1a`), with locally reproduced dependency logic. **13 additional source integration checks** fetched and inspected the real PR files: both UI paths, one-use reset, gateway enforcement, immutable request arguments, redirect disabled, and test script integration. New `scripts/test-cloud-consent.sh` included in aggregate test suite. Four Android JUnit regression methods added in `CloudTransferConsentTest.kt` (**NOT TESTED** under Gradle).
- **NOT TESTED:** full Android Kotlin/Compose/Gradle compilation, actual HTTPS transmission/denial and TLS on device, full project smoke, native llama.cpp/GGUF generation, clinical citation correctness, RAM/thermal/offline Pad 5 testing, final APK. Fake Llama tests are not model inference.
- **Build question:** Android compilation and actual consent/no-send behavior would be useful to verify, but MedMate's workflow is dispatch-only on a non-default PR branch and cannot be run from default without a separate authorized CI path; no default/main write or routine APK build was attempted.

## 2026-10-24 — Cloud PDF-transfer consent is atomic and one-shot (source milestone)
- **Production commits:** `b160ee81c1fa7a3265b7f44312523922e9582a8d` (grant) and `8bed2c6ee5f126e1649c59e9eff96234d64d353c` (outbound gateway); **JUnit regression:** `c365b4578863f7ffdb14c9bb4560110f114bf7b6`; **smoke regression:** `a127b1a2d56fcbc6ad21162c77e315404705a9d6`. Draft MedMate PR #1 only.
- **Bug fixed:** The previous in-memory grant could be reused by a retained reference even though the Compose UI cleared its own pointer. `CloudTransferConsent.consume()` now claims approval atomically, at most once, and only for the same HTTPS endpoint/model/question/chapter/page. `CloudBrainGateway` claims it **after local validation but before opening the connection**; even an unsuccessful attempt requires fresh user approval. The UI still previews with the non-consuming `permits()` check.
- **PASS (isolated Kotlin/JVM):** Compiled a local mirror of the changed consent logic with `kotlinc` and executed 8 assertions including mismatch rejection, successful first claim, replay refusal, and a **20-thread simultaneous claim race** with exactly one winner. **PASS (repository source inspection):** Seven assertions checked the committed production gate, its placement before `openConnection()`, and the presence of the JUnit and smoke regressions. Actual Git blob SHAs were inspected.
- **FAIL:** None in executed checks. **NOT TESTED:** Native repository `scripts/test-cloud-consent.sh`, Gradle/JUnit test execution, compilation of the complete Android/CloudBrainGateway source, real HTTPS request or failure handling, actual PDF transfer refusal, Pad 5, llama.cpp/GGUF model inference, clinical accuracy, APK/SHA-256.
- **CI:** Zero PR workflow runs reported at smoke SHA when checked; commits use `[skip ci]`. **Build decision:** no APK built in this round; atomic capability behavior was answerable with source/JVM checks, whereas real network, device permission and native model integration remain separate purposeful build/device gates. No private PDF, key or GGUF model uploaded. **Status:** IN_PROGRESS.


## 2026-10-24 — GGUF saved-model preflight before native llama.cpp
- **Production:** `LocalModelImport.validateForLoad` now checks a persisted GGUF file's app-local extension, 128-byte to 8-GiB size range, 24-byte magic/version/tensor/metadata header immediately before `LlamaAndroidPort` invokes native loading. This rejects obvious damaged/replaced models **before** evicting an already loaded native session. The real `Llama.loadModel` and `Llama.complete` path remains unchanged and requires real-device verification.
- **Tests committed:** additional synthetic valid/damaged/truncated/version-invalid file cases in `ModelAndTutorSmoke.kt` and `LocalBrainV09Test.kt`. No private GGUF weights, medical PDF, secrets or signing keys committed.
- **PASS (isolated Kotlin/JVM equivalent):** compiled and ran **9 synthetic preflight checks** in a local `kotlinc` harness using the same validation logic; valid header accepted, damaged magic/version/counts, truncated/incorrect-extension/small files rejected, previously valid file preserved. This is an **isolated reproduction**, not execution of the committed full test suite or actual llama.cpp.
- **Research:** existing pinned `dev.ffmpegkit-maintained:llama-android:0.1.1` advertises MIT license, arm64-v8a and the exact `Llama.loadModel/complete/releaseModel` API. Sources: https://github.com/ffmpegkit-maintained/llama-android and https://github.com/ggml-org/ggml/blob/master/docs/gguf.md . This verifies documented compatibility only, not an Android integration PASS.
- **FAIL:** none in the isolated 9 checks. **NOT TESTED:** full Kotlin smoke/JUnit, Android Compose/Gradle/lint, AAR resolution/ABI packaging, actual GGUF inference, PDF clinical grounding, physical Pad 5 RAM/thermal/offline, final APK and SHA-256.
- **Purposeful build question (authorized 2026-10-24):** does the real Android Gradle project compile with the pinned native llama AAR and run its unit/lint gates? Isolated Kotlin cannot verify Android dependency resolution or Compose/ABI packaging. One targeted PR-branch CI build is justified, **not proof of GGUF inference or a final release**. Do not claim CI/artifact before observing it.
- **Scope:** draft MedMate PR #1 only; no `main`, no auto-merge, no paid API, no Library/Handoff. **IN_PROGRESS.**


### Targeted Android CI bootstrap failure and corrective run
- **FAIL (CI environment, before source compilation):** run **40126814109**, source SHA `f394467fbfd0fb4273a4c9745ff61bd13429ae26`, https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40126814109 . The `android-actions/setup-android@v3` step invoked `sdkmanager tools`, but the removed legacy Android SDK `tools` package was unavailable. **No Kotlin/Gradle tests ran; no APK artifact.** This is not a model failure.
- **Fix:** PR-branch workflow now uses the GitHub-hosted runner's existing Android SDK manager to install `platforms;android-36` and `build-tools;35.0.0`, matching the proven Storylens runner approach. Fix source SHA `07ad6f88355cdff22375d6fc0c779e832739265d`.
- **Second purposeful CI:** run **40126898764**, https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40126898764 . The SDK step completed and Gradle `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` began; at the time of this checkpoint it was **IN_PROGRESS**, so unit/lint/compile/APK are **NOT TESTED / no result yet**. No private model, user PDF or key uploaded.
- **Still NOT TESTED:** genuine GGUF inference, clinical PDF citations, real HTTPS consent/no-send, physical Pad 5, model RAM/thermal/offline and final release.


## 2026-10-24 — Real CI closure and content-addressed GGUF integrity milestone
- **Resolved prior CI checkpoint:** targeted Android run **40126898764** at source SHA `07ad6f88355cdff22375d6fc0c779e832739265d` finished **PASS** on 2026-10-24. Actual job completed SDK installation, SQLite migration checks, `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` and artifact upload. Real artifacts: `MedMate-validation-reports` ID **12573563298** (archive SHA-256 `9d5b5bc330a18412f2bdaea3b0f9c61a5418c22d2324aae7da8d48756d50d453`), `MedMate-debug-apk` ID **12573313606** (ZIP archive SHA-256 `d3f1136adb2c7f24166127a6c21afda3e6cc476e8a4cff913b569cdb7b48db0c`). **APK inner-file SHA-256 NOT TESTED**; archive hash must not be presented as APK hash. No Android device install, native GGUF inference or clinical accuracy test.
- **New production milestone:** imported GGUF models are named after their SHA-256 at import, but the old native-load preflight only checked the first 24 bytes. `LocalModelImport.validateForLoad` now checks the complete saved file against its lowercase content-addressed filename before calling llama.cpp. It rejects a modified tensor body even when the GGUF header remains valid, and rejects unverified filenames that must be reimported. Streaming checksum callback permits cancellation; `LlamaAndroidPort` runs the scan on Dispatchers.IO **before** releasing a previously loaded native session. This improves integrity, not proof that the model is valid or clinically accurate.
- **New regression source:** `LocalBrainV09Test.kt` and `ModelAndTutorSmoke.kt` cover body tampering, valid recheck, unverified filenames and cancellation with tiny synthetic GGUF headers. No model weights, personal PDFs or keys committed.
- **PASS (source inspection):** production and tests are committed in draft PR #1. **NOT TESTED (new SHA):** Kotlin/JUnit execution and full Android compilation after this change, physical device performance and model inference.
- **Purposeful build question:** Does the *modified* coroutine/IO cancellation path compile with the actual Android Kotlin dependencies, and do the new JUnit regression cases pass with the production import code? Static review and an isolated fake GGUF cannot prove the integrated Kotlin/Gradle result. **One targeted branch CI run is justified**; it is a Debug validation build, not a final APK or real GGUF inference.
- **Scope:** only MedMate PR #1; no main/auto-merge/paid API or Library/Handoff. IN_PROGRESS.

## 2026-10-25 — Printed PDF MCQ answer-key ambiguity guard
- **Production SHA:** `fb2945e87f362d7faba090a73b36f37158933aef`; **smoke SHA:** `8e6c38016a889a9a5949ed0efb8de47a3e9c8ec8`; **JUnit SHA:** `2d29bbebeaedb4bed6322094a5a7a25eb99098dd`, PR #1 only.
- The offline extractive PDF MCQ parser now rejects **the entire question** when multiple answer labels are printed (even duplicate same-letter keys), or when a malformed/ambiguous answer label follows a valid one. It never silently promotes the first key as certain. Added three negative cases to source smoke and JUnit.
- **PASS:** Five **isolated Kotlin/JVM** answer-label checks (one valid and four invalid). This is a reproduction of the production parser's key-label logic, **not** the full compiled project or committed smoke script.
- **New CI status:** **NOT TESTED** on the latest SHA. **Older CI baseline PASS:** GitHub Actions run `40126898764`, Android SDK, SQLite contracts, Gradle unit tests, lint, assembleDebug, artifact upload all successful; Debug APK artifact `12573313606` and validation report `12573563298` exist. These artifacts predate the new parser change; neither proves genuine GGUF inference or a device install.
- **NOT TESTED:** full current JUnit/Gradle, actual PDF user flow, real llama.cpp/GGUF, clinical correctness, Pad 5 thermal/memory, cloud HTTPS, final APK/signature continuity.
- No new APK built for this parser-only change; the prior successful Android CI already establishes a build baseline, while the new regression must still pass in full CI before acceptance.

## 2026-10-25 — Bounded reusable PDF evidence index (source + targeted Android CI)
- **Production SHA:** `e7d493680983d8c0ac0aed9ae3eb61c6340fe1ab` on MedMate draft PR #1, not main. Added `IndexedEvidenceRetriever.kt` with immutable per-evidence token frequencies and normalized phrase text, plus a one-chapter, **350,000-character bounded** reuse cache in `StudyBrain.kt`. Repeated questions can reuse lexical parsing; changing a chapter's indexed PDF text invalidates the cached index. Oversized chapters use the original uncached retrieval path. Exact BM25-like frequency scoring, page citations, chapter isolation and source-only behavior are retained. **This is not semantic AI or GGUF inference.**
- **Regression:** new `IndexedSearchTest.kt` compares actual `StudyBrain.search` with the original `EvidenceRetriever` for multiple English/Arabic questions and page scopes, including PDF text replacement, cross-chapter isolation, unknown queries and scanned/empty pages. `scripts/test-brain.sh` now compiles the new production source. No user PDFs or model weights added.
- **PASS local syntax/logic:** `kotlinc` compiled the new indexed retriever and StudyBrain source with minimal Kotlin stubs for existing BrainCore dependencies; four source-behavior checks passed. This is a **stubbed local smoke**, not the committed full BrainCore/JUnit or Android build.
- **Purposeful build question:** Do the new production retriever and JUnit parity tests compile and pass against the actual Android/Kotlin/PDF dependency graph, and does a Debug APK still assemble? Local stub tests cannot answer this. GitHub Actions [run 40139831599](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40139831599) was triggered for that exact production SHA and was **IN_PROGRESS** during this checkpoint. Mark Android/JUnit/lint/artifact as **NOT TESTED pending completion**, not PASS.
- **NOT TESTED:** measured PDF-search speed/RAM on Pad 5, real PDF citation truth, OCR for scanned books, native llama.cpp GGUF loading/inference, cloud endpoint and consent on device, clinical QA, upgrade-signature continuity and independent final acceptance. **IN_PROGRESS**, no final APK or release.
- **Scope:** MedMate PR #1 only; no main, auto-merge, paid API, private PDF/keys/models, or ChatGPT Library/Handoff. Documentation-only `[skip ci]` commit avoids a redundant APK build; the source SHA above is the CI target.

### CI completion for the indexed PDF milestone (supersedes pending status above)
- **PASS:** [Android Actions run 40139831599](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40139831599) completed successfully on exact production SHA `e7d493680983d8c0ac0aed9ae3eb61c6340fe1ab`. The job reports PASS for SQLite contracts/migrations, Gradle `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, and writing `APK_SHA256.txt`.
- **Real artifacts:** Debug APK ZIP artifact **12578776127** (`MedMate-debug-apk`), validation report artifact **12578396535** (`MedMate-validation-reports`). Inner APK SHA-256 is present in the uploaded archive but was **not independently extracted or verified** in this run.
- **NOT TESTED remains:** Xiaomi Pad 5 installation/performance, real PDF/clinical correctness, true GGUF/llama.cpp model inference, real cloud endpoint consent behavior, backup/restore on device, and final release-signature continuity. A successful Debug build does not satisfy these gates. No final APK delivery.

### Independent Debug APK integrity/native-library inspection
- Downloaded actual Actions artifact **12578776127**, extracted `app-debug.apk` (**33,504,001 bytes**), and independently computed SHA-256 **`4ea45ae9d9b8aa93a00445c1eba8202bfd7f0615121829203f0b79be4bc29bed`**, exactly matching its bundled `APK_SHA256.txt`. **PASS artifact integrity.**
- The APK contains **`lib/arm64-v8a/libllama.so`** and **`lib/arm64-v8a/libllama_jni.so`** plus ARM64 GGML libraries. No `.gguf`, keystore/private-key file or PDF is packaged. **PASS native binary packaging inspection**, but **NOT TESTED real GGUF loading or token inference**, JNI runtime compatibility, Pad 5 memory/thermal or medical correctness. Debug build is not a final release.


## 2026-10-25 — Native GGUF inference audit and stale PDF response isolation
- **Production SHA:** `d85d122fdb1ed25f1cb80adc8b215dc8a8c239d7`; **JUnit test SHA:** `f6daba33807e1873b38028ff725e7cae184b60d7`, MedMate draft PR #1 only.
- Added `LocalInferenceAudit.kt` to produce an in-memory, source-bound diagnostic **only after the actual `LlamaAndroidPort.generate` call returns**. It records the native loaded model name, measured wall-clock inference milliseconds, response length, PDF source page numbers and citation-syntax result. It explicitly does **not** prove clinical accuracy, GGUF inference in CI, or any device performance. No model weights, answer text, PDF excerpts or secrets are persisted in this audit.
- `BrainPanel.kt` now shows the audit next to local PDF-grounded explanations and guards both local explanation and generated MCQ/flashcard draft results with the captured `BrainRequestIdentity` (chapter SHA, question, page-only flag and source page). Results from a changed source are discarded rather than presented as belonging to another PDF. Added four `LocalInferenceAuditTest` JUnit cases for valid/forged citations, missing native session, cross-chapter/negative clock rejection and stale question/page identity.
- **Purposeful build question:** do the new Kotlin source and JUnit compile and pass against the actual Android/Compose/llama AAR graph, and can a Debug APK still assemble? Source-only checks cannot prove Compose Kotlin compilation or native dependency packaging. Target [Actions run 40147310046](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40147310046) was **IN_PROGRESS at documentation time**; mark latest CI/Gradle **NOT TESTED pending completion**.
- **NOT TESTED:** genuine GGUF model import/load/generation on Xiaomi Pad 5, latency/RAM/thermal, clinical correctness, physical PDF citations, cloud HTTPS consent, and final device/signature acceptance. Existing tests cannot replace a real model. No main merge, release, paid API, personal PDFs or keys.


## 2026-10-25 — Source-grounded PDF citation navigation for local/cloud answers
- **Functional/CI SHA:** `b8e8c2ee2c8c240538d4ff7a7c11b8d5992ec7b5`, draft PR #1 only.
- **Production:** new `SourceCitationLinks.kt` resolves exact `[S1]`/`[S2]` markers to the **specific `PreparedPrompt.presentedEvidence` page indices** used for that local llama.cpp or explicitly consented Cloud AI request. The Compose `BrainPanel` displays tappable PDF-page links alongside both answers. Never trusts model-supplied page numbers; never makes any citation tappable when the full `CitationValidator` rejects the response, even if one valid marker appears next to a forged one. Rejects mixed chapters, negative pages, empty evidence and citation-only answers. The existing stale-question/page/chapter response guard remains in front of link assignment.
- **Tests:** committed `SourceCitationLinksTest.kt` covers exact/repeated markers, page mapping, malformed/Unicode/forged citations, cross-chapter and abstention. **PASS isolated Kotlin/JVM:** exact production `SourceCitationLinks.kt` Git blob `400cb3be516e709e0cf7d6eee00bf3bde2d9c64d` compiled with minimal stub `Evidence/CitationValidator`; three resolver checks PASS. **Important:** stubbed validator is not the full committed production validator or Gradle/JUnit.
- **Purposeful Android CI question:** does the real Compose panel compile with new state/links and do JUnit tests pass against the full production `CitationValidator`, and can the native ARM64 APK still assemble? Isolated Kotlin cannot answer this. [Run 40151230089](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40151230089) was **IN_PROGRESS** when checked; **CI/Artifact: NOT TESTED pending completion**.
- **NOT TESTED:** real GGUF inference, physical PDF tap navigation on Pad 5, medical truth of any answer, cloud HTTPS consent on device, model RAM/thermal, final signed APK. These are navigation improvements, **not evidence of model success**. No user PDFs/keys/models uploaded; no main merge, paid API or Library/Handoff edits. **IN_PROGRESS**.


### Targeted CI completion for PDF citation navigation
- **PASS** [GitHub Actions 40151230089](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40151230089) on exact functional source SHA `b8e8c2ee2c8c240538d4ff7a7c11b8d5992ec7b5`: SQLite schema/migration checks, full Android Gradle `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` **BUILD SUCCESSFUL in 4m 40s**. The newly committed `SourceCitationLinksTest.kt` is included in the project test suite; full JUnit/Compose compilation succeeded. This is not a real GGUF model test.
- **Actual artifacts:** `MedMate-debug-apk` ID **12583085993**, `MedMate-validation-reports` ID **12582931184**. CI-calculated inner `app-debug.apk` SHA-256: **`469646c7def026f4d2198395903f3f1c972376572fef40e1ed6dc2870e11e1c8`**. **Independent artifact re-download/re-hash NOT TESTED**.
- **NOT TESTED:** physically tapping cited PDF pages in the Android UI, real GGUF/llama.cpp inference, cloud HTTPS transfer/consent on device, medically verified source grounding, model memory/thermal, final release signing/upgrade. **IN_PROGRESS**; debug APK is not a final deliverable.


## 2026-10-25 — Offline PDF extraction coverage + navigable OCR gaps

- **Functional source SHA before evidence note:** `a9ebd0ab32672df14f1ec3c7a96eafce8341e745` on MedMate draft PR #1; `main` untouched.
- **Production:** `PdfTextCoverage.kt` audits **the currently selected chapter only** and distinguishes pages not yet indexed, indexed-but-empty pages, suspiciously short extracted text, and sufficiently readable text. Keeps only bounded page indices/counts, never PDF excerpts, model weights, tokens or personal data. Rejects mixed-chapter, duplicate or out-of-range page anchors. Coverage is a diagnostic, **not OCR**, a clinical source-quality certification or a generative model.
- **Integrated Compose UX:** `BrainPanel.kt` shows chapter-wide coverage without re-running PDFBox on each recomposition; offers an **explicit offline full-chapter indexing action**, progress and direct navigation to up to five pages needing review. Existing question search refreshes the report after indexing. Scanned PDFs are not falsely presented as searchable.
- **Tests committed:** `PdfTextCoverageTest.kt` covers partial/empty/low-text/full coverage, page counts, 20,000-page bounded previews, source isolation and invalid anchors. Tests target actual production Kotlin through Gradle/JUnit, not FakeLlama. No new network, OCR dependency or personal PDF fixture.
- **Purposeful Android build question:** Do the new Compose UI and coverage data types compile together with the existing Android/llama AAR, and do the new JUnit tests pass? Isolated Kotlin/source inspection cannot prove Android Compose compilation. [Actions 40155275698](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40155275698) **IN_PROGRESS at this checkpoint**; latest full Gradle/JUnit/lint/APK **NOT TESTED pending CI completion**.
- **NOT TESTED:** physical PDF scan coverage and navigation on Xiaomi Pad 5, real OCR, real GGUF/llama.cpp model inference, clinical citation truth, cloud consent/HTTPS on device, memory/thermal/performance, final APK. **IN_PROGRESS**.
- **CI economy:** development workflow cancels superseded intermediate builds; no `main` write, auto-merge, paid API, personal PDFs/GGUF/secrets or Library/Handoff access.


### PDF coverage purposeful CI completion (supersedes pending status above)
- **PASS:** [Actions 40155275698](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40155275698) on exact functional/workflow SHA `a9ebd0ab32672df14f1ec3c7a96eafce8341e745`: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` **BUILD SUCCESSFUL in 3m 59s**. Newly committed `PdfTextCoverageTest.kt` is part of the JUnit source suite. Android Compose source compiles with the coverage panel.
- **Real artifacts:** Debug APK ZIP **12583933773** and validation report ZIP **12584422909**. CI-calculated SHA-256 of inner `app-debug.apk`: **`cfebef7f8b3baf5e66e70c3acc090c03f83c64828bd87556ab89d6637074b1eb`**. Independent artifact re-download/re-hash **NOT TESTED**.
- **NOT TESTED:** actual scanned medical PDFs on Xiaomi Pad 5, OCR, real GGUF inference, PDF clinical grounding, device performance, cloud HTTPS/consent, final signed upgrade and independent second-pass acceptance. Debug APK is not a final release; MedMate **IN_PROGRESS**.


## 2026-10-25 — Corrupt PDF extraction quarantine (source-grounding safety)

- **Production:** `PdfCoverageAudit.likelyCorruptedText` conservatively detects dense Unicode replacement glyphs or embedded NULs. The chapter coverage panel now counts these as **garbled** rather than readable and points users to the affected pages. `StudyBrain.search` excludes clearly damaged extracted pages from evidence retrieval before either local or cloud study explanations, preserving original PDFs and cross-chapter isolation. A single replacement glyph in otherwise good textbook text does **not** trigger quarantine. This is not OCR, medical fact-checking or model inference.
- **Tests committed:** `PdfTextCoverageTest.kt` covers dense replacements, NULs, mixed good/garbled pages and conservative single-glyph behavior; `BrainRetrievalRegressionTest.kt` checks that corrupted pages never enter cited evidence, while valid pages remain searchable and selected corrupted-only pages report missing usable text.
- **PASS source inspection:** verified production and test files are present on the MedMate PR branch with expected cross-file references; **NOT TESTED:** actual committed JUnit execution, Android Compose compilation/lint/APK for this source, real GGUF inference, OCR quality, actual PDF data, Pad 5, cloud HTTPS and clinical accuracy.
- **Purposeful Android build question:** do the new Kotlin data field, Compose coverage warning, retrieval filter and committed JUnit tests compile and run together, and does an ARM64 Android APK assemble? Source inspection cannot establish this. Non-skip checkpoint requests branch CI; **CI/Artifact NOT TESTED until exact-SHA completed run is checked**. No paid API, personal PDF, GGUF, secrets, main write or merge.
- **Acceptance:** local Brain is not certified as running a real model; MedMate remains **IN_PROGRESS** pending GGUF and independent device/medical recheck.


### Corrupted-PDF grounding quarantine — targeted Android CI completion

- **PASS:** [Actions 40159165080](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40159165080) completed successfully on exact functional/test SHA `1a54bc918c8e5d82619b009c9c166ac6dff8bc6c`. Android `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` **BUILD SUCCESSFUL in 4m 21s**, with the new PDF damage/grounding regression tests included in the Gradle JUnit suite. Compose source compiled and Debug APK assembled.
- **Artifacts:** `MedMate-debug-apk` ID **12585684613** and `MedMate-validation-reports` ID **12586100304**. CI-calculated **inner** `app-debug.apk` SHA-256: `0f050337e013235331caf5eb71f5707cac8f08bdac46a4500e9dd9a74d3b9ae0`. Artifact ZIP checksum is different; **independent download/re-hash NOT TESTED**.
- **NOT TESTED:** physical scanned PDF and tap-through review on Pad 5, real GGUF/llama.cpp inference, medical factuality, OCR, cloud HTTPS/consent on device, thermal/RAM, signing/upgrade and final independent acceptance. A successful Android build is not evidence of a working model. **IN_PROGRESS**.

## 2026-10-25 — Corrupt-PDF vs scanned-PDF search status (source safety)
- Production `StudyBrain.kt` SHA `ba1de65520f2cef820e0aa83a980cb12becee459` adds `CORRUPTED_PDF_TEXT` status and a chapter/page-scoped `excludedCorruptedPages` count. Dense damaged extraction still cannot be passed as LLM evidence or citation source; scanned/image-only PDFs keep their separate OCR guidance.
- `BrainPanel.kt` SHA `d7eddf12e5478f91b63d2100faa539629e0adae8` displays a distinct corrupted-text warning and warns when only clean pages were searched, directing readers to the existing PDF coverage and page-navigation panel.
- JUnit `BrainRetrievalRegressionTest.kt` SHA `59225162eb74502d8d21d53c62c12b11385cdac3` extends regression coverage: damaged-only page, blank scanned page, mixed clean/corrupt chapter, chapter isolation, source citation suppression and short queries. No personal PDFs/models/keys used.
- **PASS (source inspection):** production and test paths updated on the existing draft PR branch, with scope/enum/UI/test consistency checked. **JUnit/Compose/Android CI: NOT TESTED** until a successful run on exact SHA is confirmed. A purposeful build is warranted: Compose's exhaustive `when` and JUnit need actual Kotlin/Gradle compilation; static source inspection cannot establish success. No new APK artifact/SHA claimed.
- **GGUF on Pad 5 / PDF accuracy / real cloud HTTPS consent / independent device tests:** NOT TESTED. No fake llama claims, no paid API, no main merge, no final APK.
- **2026-10-25 follow-up:** added `firstCorruptedPage` (bounded page index) and one-tap Compose jump from both damaged-only and mixed-source results. Latest functional JUnit SHA `fba2b9136664e48d4d6e3511eddcfbac0551f40d`. **PASS:** 12 static cross-file checks for enum, scope, source exclusion, navigation and regression assertions. **NOT TESTED:** Kotlin/JUnit/Compose CI on exact SHA, physical device and real GGUF; no APK claimed.


## 2026-10-25 — PDF invisible-control quarantine and real JUnit regression repair
- **Production:** `PdfCoverageAudit.likelyCorruptedText` now quarantines repeated unsafe C0 control characters (not only NUL or dense U+FFFD). Legitimate CR/LF/TAB/form-feed separators remain allowed. `StudyBrain` already excludes quarantined pages, so those damaged pages cannot be presented as PDF-grounded medical evidence or cited.
- **Tests:** expanded `PdfTextCoverageTest.kt`, `BrainRetrievalRegressionTest.kt` and the source smoke with mixed clean/corrupt pages and page-only isolation. Fixed an older JUnit fixture that accidentally used the literal characters `\\uFFFD` rather than the actual replacement glyph; that caused the earlier [CI 40162925233](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40162925233) to fail at `BrainRetrievalRegressionTest.kt:144`. Repaired `scripts/test-brain.sh` to compile the `PdfTextCoverage.kt` dependency of `StudyBrain`.
- **PASS:** static inspection of committed production/test wiring and exact fixture correction. **Full Kotlin/JUnit/Android:** [targeted run 40166527606](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40166527606) **IN_PROGRESS** on functional SHA `797249033d7e4df8fd40c68829186bbf1786a400`; no result, APK checksum or artifact claimed until it finishes. This build is justified to exercise Kotlin/JUnit, Compose, lint and actual Android packaging, which source inspection cannot prove.
- **NOT TESTED:** medical truth, scanned/OCR accuracy, genuine GGUF model execution, HTTPS cloud consent on device, Xiaomi Pad 5 RAM/thermal/performance, signed final APK and independent acceptance. A PDF quarantine test is **not** an inference test. PR #1 stays Draft; no main merge, private PDF/model/key or Library/Handoff changes.


### Invisible-control PDF quarantine — Android CI completed
- **PASS:** [Actions 40166527606](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40166527606) on exact functional/test SHA `797249033d7e4df8fd40c68829186bbf1786a400`. SQLite contract/migrations, Android Gradle JUnit (including corrected damaged-text fixture and new C0-control tests), lint and Debug APK assembly all succeeded; **BUILD SUCCESSFUL in 2m 59s**.
- **Artifacts:** `MedMate-debug-apk` ID **12587768948**; `MedMate-validation-reports` ID **12587918724**. CI-calculated **inner APK SHA-256** `36dd6daaab4ca05ce9aa966befbebc280d54127ec2366cd2fcabb148dfeb8113`; independent artifact re-hash **NOT TESTED**. Debug APK is **not** final.
- **NOT TESTED:** real GGUF/llama.cpp inference, clinical correctness of PDF citations, real user PDFs, physical Pad 5, live HTTPS consent and transfer blocking, performance/thermal and final signed release. These tests do not certify the model.


## 2026-10-25 — One-shot Cloud AI consent sealed to exact PDF excerpts
- **Production + regression SHA:** `895708bfc97479450306506a16bb211690f56c96` on existing MedMate draft PR #1; no `main` write or merge.
- **Production security:** `CloudEvidenceSeal.kt` validates the selected chapter SHA, exact question, optional selected PDF page, bounded source count/size, and presence of every excerpt in the outgoing prompt **before any network attempt**. It calculates a length-delimited SHA-256 fingerprint of the precise approved excerpt texts and source page IDs, without persisting excerpts or the digest. `CloudTransferConsent` now offers a sealed one-shot grant; `CloudBrainGateway` refuses unsealed grants, mismatched excerpts, replay, stale question/page or changed endpoint/model at the outbound boundary. Existing pure Kotlin legacy consent tests remain, but their unsealed grants cannot authorize the actual cloud gateway. Both explanation and generated-draft prompts share the same approved source fingerprint.
- **UI integration:** the opt-in checkbox seals the currently displayed `PreparedPrompt`; both cloud action buttons recheck the seal. Changing evidence/question/page after consent requires a fresh explicit approval. No default provider, paid API, uploaded PDFs, user secrets or model weights added.
- **New JUnit:** `CloudEvidenceSealTest.kt` covers exact one-shot approval, changed excerpt/question, cross-chapter/page mismatch, prompt omission and unsealed legacy grants. These are deterministic local source/consent tests; **not** a live HTTPS transfer.
- **PASS** [GitHub Actions run 40169789382](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40169789382) on exact SHA: SQLite schema/migrations, Android Gradle `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` **BUILD SUCCESSFUL in 3m 21s**. Compose, new Kotlin guard and JUnit test source compiled; test task passed.
- **Real artifacts:** `MedMate-debug-apk` ID **12589252899**, `MedMate-validation-reports` ID **12588904115**. CI-calculated inner `app-debug.apk` SHA-256 **`2751073121ea622008b2a0bb6cb280aa81b442229505a4ee70edb82e169ab1a1`**; independent re-download/re-hash **NOT TESTED**.
- **NOT TESTED:** actual optional HTTPS endpoint and denied-transfer packet inspection on Pad 5, real GGUF/llama.cpp model loading and inference, medical correctness of generated claims, physical PDF citation taps, memory/thermal, final signing/upgrade and independent device QA. **IN_PROGRESS**; Debug APK is not a final release.


## 2026-10-25 — Explicit synthetic native GGUF probe (real-device readiness)
- Added `NativeInferenceProbe`: a user-initiated, **synthetic non-medical source** prompt through the *loaded* `OnDeviceLlmPort`, with model name, inference duration, answer size, separate calibration-code and citation-syntax results. No private PDF, token, model weights or generated answer is persisted or transmitted. Added a button in BrainPanel after model load.
- **PASS (source inspection):** production UI invokes `NativeInferenceProbe.run(onDevice)`, where `onDevice` is `LlamaAndroidPort`, not `FakeLlama`; a model must be loaded before the probe. Three JUnit tests explicitly use a fake port and **do not count as native inference**.
- **NOT TESTED:** execution of the new JUnit tests, Android Compose compile/lint/APK on latest head, actual llama.cpp/GGUF inference, device memory/thermal, clinical PDF grounding, cloud HTTPS and physical Pad 5. A successful diagnostic will only prove the synthetic inference request, not medical accuracy.
- **Purposeful build question:** Does the new probe compile with the real Compose UI and pass the Android unit tests? Source inspection cannot establish Kotlin/Compose integration or packaging. One targeted CI build is warranted, not one build per edit.


### Validation closure for synthetic native probe (2026-10-25)
- **PASS:** [Android CI 40181545922](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40181545922), functional SHA `e341aac08de92f46c91113984064d9ee06ad23ef`: Android JUnit (fake probe contract only), SQLite, lint, Compose and APK.
- **PASS:** independently extracted 33,536,769-byte `app-debug.apk` from GitHub Artifact **12594915191**, SHA-256 `b4046e53f73a7087513bf548ba94670397fe8d619e1c8b59db5eb836de941920` matches CI. Dex includes `NativeInferenceProbe`, `ORBIT-27`, and the synthetic source ID; ARM64 `libllama.so`/`libllama_jni.so` packaged. No GGUF/PDF/keystore packaged.
- **NOT TESTED:** actual GGUF inference on Xiaomi Pad 5, device privacy/thermal, medical citation accuracy. A fake unit test or packaged library is not a real model execution. Debug APK is not final.


## 2026-10-25 — Native GGUF probe memory snapshots milestone
- Production: optional diagnostic now samples Android own-process PSS and system available RAM immediately before and after a synthetic **real LlamaAndroidPort.generate** call. UI labels the readings as two snapshots, **not peak RAM or model-only memory**, and warns when Android reports low memory. The model and synthetic prompt remain local; no user PDF, GGUF or secrets enter GitHub.
- Contract-only JUnit: `NativeInferenceProbeTest` verifies exactly two samples, values and negative-input rejection using a clearly **fake** port. **NOT TESTED:** genuine model inference, on-device readings, medical correctness or cloud HTTPS.
- **Purposeful Android build question:** Does the new Compose diagnostics panel compile against Android ActivityManager APIs, and do JUnit, lint and APK packaging pass? Source-only tests cannot validate Compose/Android API linkage. Build only after the milestone source/tests, not after every edit.
- **Acceptance:** Kotlin/JUnit/CI PENDING; Xiaomi Pad 5 real GGUF, RAM/thermal and PDF citations NOT TESTED. Debug is not final. No main merge.

### GGUF probe RAM snapshot CI and artifact evidence — 2026-10-25
- **PASS Android CI:** [run 40185615010](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40185615010) on source SHA `fc649f9e36524f146be600253770a4c30c57d436`: JUnit including fake-port memory snapshot contract, SQLite, Android lint, Compose and Debug APK assembly.
- **PASS independent artifact rehash:** artifact `12595688311`, `app-debug.apk` (33,536,769 bytes), SHA-256 `b86cad5bb4fc25d4e221f18b7ea8d3bcc50720dfb4f9b2f7c447a1dcbe49b6e4` independently extracted and matched `APK_SHA256.txt`. ARM64 `libllama.so` and `libllama_jni.so` present; no GGUF/PDF/keystore packaged.
- **NOT TESTED:** physical Pad 5 actual GGUF inference, device PSS/available RAM snapshots, clinical correctness, HTTPS cloud consent/denial and final upgrade-signing compatibility. Fake-port JUnit and native libraries inside APK do **not** prove a model has run.


## 2026-10-25 — Arabic PDF quote provenance and negation safety checkpoint
- **Production/tests SHA:** `92ec45800dcf0e8faf6df2a73ba80e1a782bc667` on draft PR #1. No main changes.
- **Production:** source-quote verification for generated High-yield/Flashcards/MCQs now folds Arabic vowel marks, tatweel and conservative alef/ya spelling variants for comparison only, preserving the original indexed PDF text/page. Rejects a model's partial quote when it omits an immediately preceding `no/not/without/لا/لم/لن/ليس` negation. This lexical guard is NOT semantic entailment or clinical correctness verification.
- **Tests committed:** `GeneratedDraftsTest.kt` covers Arabic vocalized PDF text, correct page anchors, forged source IDs, unrelated PDF text, and adjacent negation omission in English/Arabic.
- **NOT TESTED:** execution of new JUnit suite, Android CI/build on this SHA, actual PDF medical editorial validation, real GGUF/llama.cpp inference, cloud HTTPS allow/deny and Xiaomi Pad 5. No new verified APK or SHA-256.
- **Next:** run JUnit/CI and investigate any failures; device/native-model and clinical review gates remain open.
