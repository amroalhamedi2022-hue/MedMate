## Source-version caveat for the latest verified pair

The APK pair from run `40366568877` is the last **verified CI baseline**, not proof of the newest PR source. It predates the Chapter/source checkpoint `dd5201e9dd056a4ffce58450f50f3db8cf7603cf` and later host-side guard changes. It can be used for a preliminary device smoke, but a pass with this pair must not close the latest exact-marker/cleanup or local-cloud regression gates. A fresh CI-built pair for the current PR head is required.

## Latest two-page PDF citation test APK pair — 2026-10-26

- CI run: [40366568877](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40366568877), source/test SHA `baa6b7cff1939fe24d1448806514bde9071a667b`.
- Device-test bundle: [artifact 12670667759](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40366568877/artifacts/12670667759).
- `app-debug.apk`: 33,553,165 bytes; SHA-256 `ee0b6682a44c12bda76e8cd9fcb8b17cde2fc8b8893efa42ff54e58ac434c715`.
- `app-debug-androidTest.apk`: 372,149 bytes; SHA-256 `48ab3f98f59ee83ccbeb2ab81c0c8d3056b6316352ff8bc76d24e8321f3b0652`.
- Both hashes independently recomputed and match `SHA256SUMS`. CI passed 142 JVM tests (0 failures/ignored), lint, APK + AndroidTest assembly, manifest verification and runner contract.
- The device-only instrumentation creates a synthetic two-page PDF, keeps a fresh identifier on page 2, invokes production PDFBox/retrieval and the real `LlamaAndroidPort`/llama.cpp JNI path, then requires `[S1]` to resolve to human-facing page 2. **The test is compiled, not executed.**
- **NOT TESTED:** Xiaomi Pad 5, actual GGUF inference, PDFBox-to-native citation round-trip, live HTTPS consent/denial, clinical review, RAM/thermal and final signing. Bundle includes no model weights, personal PDFs or credentials.

# MedMate — real GGUF device acceptance

Use the official [Qwen2.5-1.5B-Instruct-GGUF model repository](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF) and review its Apache-2.0 model license. A Q4_K_M file is a reasonable first ARM64 smoke candidate, but this general model is **not medically validated**. Do not commit model weights or upload them to GitHub.

On Xiaomi Pad 5 or another ARM64 Android device, download the GGUF locally and enable USB debugging. Connect exactly one device, then choose one of these paths.

**Use the exact CI-built APK pair (no local Gradle build):** download and unzip the `MedMate-device-test-bundle` artifact from a successful Android debug workflow run. From the extracted bundle directory, first run `sha256sum -c SHA256SUMS`; continue only if both checksums pass, then point both variables at the extracted APKs:

```bash
APP_APK=/absolute/path/app-debug.apk \
TEST_APK=/absolute/path/app-debug-androidTest.apk \
APP_APK_SHA256="$(awk '$2=="app-debug.apk"{print $1}' SHA256SUMS)" \
TEST_APK_SHA256="$(awk '$2=="app-debug-androidTest.apk"{print $1}' SHA256SUMS)" \
GGUF_FILE=/absolute/path/to/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf \
bash scripts/run-native-gguf-device-test.sh
```

**Or build from a source checkout:** omit `APP_APK` and `TEST_APK`; the script assembles and installs both debug APKs itself.

Set `ANDROID_SERIAL` if needed. The script installs both APKs before pushing the model, then pushes the GGUF only to app-specific external storage and invokes AndroidX instrumentation directly (so package installation cannot erase the model). It verifies host/device SHA-256 and byte count before inference and removes the temporary device copy on exit. The instrumented test checks free RAM, imports through the production content-addressed importer, invokes the real `LlamaAndroidPort`/llama.cpp JNI path, and runs a fresh random calibration challenge with a `[S1]` citation and no full source/prompt echo. It logs the GGUF SHA-256, size, elapsed time, app PSS snapshots and available system RAM without logging the answer. Newly imported test-only model files are removed after unload; pre-existing imported models are preserved.

A **PASS** means only that this GGUF loaded and passed the synthetic native inference smoke on that device. It does not establish clinical correctness, PDF-grounded accuracy, cloud-consent behavior or final performance. Missing model/device means **NOT TESTED**, never a fake-port PASS. GitHub CI compiles this test but does not run it or contain a model.


## Cleanup assertions (source change; device execution still pending)

The instrumented test now asserts that loadedModelName, loadedModelSha256, and loadedModelBytes all clear after unload(). It also asserts that a model newly imported only for the test is deleted afterward, while a previously imported/reused model is preserved. CI compiles the AndroidTest APK; it does not execute this test without a real device and user-supplied GGUF.


## Latest verified APK pair — 2026-10-26
- CI run: [40302580485](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40302580485), source/test head `d945c07db1e3f33601fe5f3c90f608aa0af71656`.
- Device-test bundle: [artifact 12641651670](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40302580485/artifacts/12641651670).
- `app-debug.apk`: 33,553,153 bytes; SHA-256 `af107fee084a17b6d6c0886d90020910c84575bc5c7fdb84f1a301967cda3550`.
- `app-debug-androidTest.apk`: 364,513 bytes; SHA-256 `5bd00633b3f7885f6179728495812ebb70aecdf02c56c85a7480371f650b9d2e`.
- Both APK hashes were independently recalculated and match the bundled `SHA256SUMS`. The model/device execution remains **NOT TESTED** until a real GGUF is supplied locally and instrumentation runs on an ARM64 device.


## Latest verified APK pair — 2026-10-26 (one-shot consent concurrency gate)
- CI run: [40305964124](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40305964124), test/source head `a1ecb7f7277e2ae75031a55f5276cac8a8a1aee5`.
- Device-test bundle: [artifact 12642516742](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40305964124/artifacts/12642516742).
- `app-debug.apk`: 33,553,153 bytes; SHA-256 `e3c80cbf8f3190605fc9611ddacb9a82cebca3ab7a34f782ab9b08edc455217b`.
- `app-debug-androidTest.apk`: 364,513 bytes; SHA-256 `434289f1baffbbaaaaebed3af6046e1982b948a70f3219e51e04d7f572015aac`.
- Both hashes were independently recalculated from the downloaded bundle and match `SHA256SUMS`. The bundle contains no model weights, private PDFs or credentials.
- The concurrency regression is a JVM test. Physical-device installation and genuine GGUF/llama.cpp inference remain **NOT TESTED** until a local model is supplied and the instrumentation script runs on an ARM64 device.


## Prebuilt APK integrity gate (2026-10-26)

When `APP_APK` and `TEST_APK` are supplied, the runner now requires `APP_APK_SHA256` and `TEST_APK_SHA256` and verifies both files before installing either APK. Missing or malformed hashes fail closed; a mismatch exits with status 4 before install, model push, or instrumentation. The script-contract regression exercises both the valid pair and a deliberately incorrect digest. The source-build path remains available without prebuilt hash variables. This is an artifact-integrity guard, not proof of real GGUF inference; device/model acceptance stays **NOT TESTED** until instrumentation runs on a real ARM64 device with a locally supplied model.


## Latest verified CI pair — 2026-10-26 (prebuilt APK hash guard)

- CI run: [40344711259](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40344711259), source/test commit `b6a70c15b548437e4a18d0ba744558c43498577f`.
- Device-test bundle: [artifact 12660049516](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40344711259/artifacts/12660049516).
- `app-debug.apk`: 33,553,165 bytes; SHA-256 `ef570a66a13045521fc63690cc4c759fc16cd29cc4d9d7f1b51ea33eb9a37b5c`.
- `app-debug-androidTest.apk`: 364,513 bytes; SHA-256 `100adcbe1dd6dba338a99bd60d3cccf127b7454d8079cfbd7fba578c5398ffbd`.
- Both hashes were recalculated from the downloaded artifact and match `SHA256SUMS`. The updated runner now requires both expected SHA-256 values for prebuilt APKs and refuses a mismatch before installation.
- The bundle contains no GGUF model, personal PDF, provider credential, or signing key. Physical-device native inference remains **NOT TESTED**.


## Latest CI-built APK pair — two-page citation fixture (2026-10-26)

- CI run: [40366568877](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40366568877), source/test SHA baa6b7cff1939fe24d1448806514bde9071a667b.
- Device-test bundle: [artifact 12670667759](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40366568877/artifacts/12670667759).
- app-debug.apk: 33,553,165 bytes; SHA-256 ee0b6682a44c12bda76e8cd9fcb8b17cde2fc8b8893efa42ff54e58ac434c715.
- app-debug-androidTest.apk: 372,149 bytes; SHA-256 48ab3f98f59ee83ccbeb2ab81c0c8d3056b6316352ff8bc76d24e8321f3b0652.
- Both hashes were independently recomputed from the downloaded bundle and match SHA256SUMS. JUnit 142/142 PASS; lint, AndroidTest compile, APK assembly, manifest checks and script contract PASS.
- The new device fixture places a fresh identifier only on PDF page 2 and requires a native answer's [S1] marker to map to PDF page 2. CI compilation does not execute instrumentation. Xiaomi Pad 5 plus a locally supplied GGUF is still required for a real inference PASS.
## 2026-10-26 — Current prebuilt pair for physical-device acceptance

The latest confirmed CI pair is from [run 40390430350](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40390430350), source commit `e4e6b4880b4854eb3c0ffde33810be4fd86b5209`, bundle artifact `12683001262`.

- App APK SHA-256: `9982973d7e55f3e0fa27c5a6b4848472bc925ec27bb426f957de82a8b4a651ef` (33,553,165 bytes).
- AndroidTest APK SHA-256: `f8edcd49e6c994bcf129cd7d95e6abfeac435f697793d4f20d65b3bc31145855` (373,377 bytes).
- The bundle's `SHA256SUMS` was independently checked against both extracted files.
- When using prebuilt files, set `APP_APK`, `TEST_APK`, `APP_APK_SHA256` and `TEST_APK_SHA256` before running `bash scripts/run-native-gguf-device-test.sh`. The runner refuses a hash mismatch before install, verifies the local GGUF hash/byte count on the device, requires a successful AndroidJUnitRunner/JUnit summary, and removes the temporary model on exit.
- Supply only a model file already stored locally and authorized for testing. The script does not download or upload weights; do not commit or share GGUF files, personal PDFs, credentials or signing keys.
- **NOT TESTED:** this pair has not yet been installed on Xiaomi Pad 5 and no actual GGUF inference result is claimed.

## 2026-10-26 — Explicit page-1 exclusion sentinel

The synthetic device fixture now includes a fresh `PAGE1-EXCLUDE-<random-id>` only on PDF page 1. The requested page-2 prompt must not contain this sentinel in either `PreparedPrompt.prompt` or `presentedEvidence`. This is a negative privacy/scope assertion in addition to requiring the unique calibration identifier on page 2, a single `[S1]` marker, and navigation to page 2. CI compilation and static wiring are still separate from actually running the test with a real local GGUF on Xiaomi Pad 5.

## 2026-10-26 — CI artifact for the page-isolation/HTTPS regression

- CI run: [40403719873](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40403719873), source SHA `2f9a0686f2b730c742e6a7015a9cb4ead6f6cf6f).
- Device-test bundle: [12691305315](https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40403719873/artifacts/12691305315).
- App SHA-256: `f5d57bcea4ea860fa592ede9b2c5fee3d79d5f0fc838666d586098f568229f2f` (33,553,165 bytes); AndroidTest SHA-256: `fc3aecddec322aaa4a8e61e331198e85e9ed689b2b72ec9111aadba641a3cf3c` (373,777 bytes). Both independently match the bundle's `SHA256SUMS`.
- The 143 JVM tests and the AndroidTest APK compilation passed. The instrumentation still needs to run on a physical device with a user-supplied local GGUF; the new page-1 exclusion assertion has **not** been executed against native inference.


## 2026-10-27 — PDF page-count guard (source contract)

- The same 100,000-page maximum now guards import and re-indexing, before permanent storage or PDFBox page work.
- The public navigation preview must be ascending and unique.
- Source contract is in `scripts/test-pdf-page-limit-contract.py`; its first CI attempt exposed a test-harness slice bug (not an application failure). That harness was fixed in `9bde03a6a30ad464911469aade6c94f7ae065b9c`.
- CI run `40415248660` completed successfully on 2026-10-26. JUnit: 147 PASS / 0 FAIL / 0 ignored; Lint and both APK assemblies PASS. Device-test bundle artifact `12695946600` contains Debug and AndroidTest APKs plus SHA256SUMS. Debug SHA-256 `e6c9eca71342bb40cb279c3d0e88374b306b15569405ae315e8ab80b7e5e82e6`; AndroidTest SHA-256 `45021669120c53c1be3883fdc63526f9d45c5e402aa2373be448ef40cd1fb297`.

- **Acceptance boundary remains open:** the build and JVM tests do not prove a physical PDF import on Xiaomi Pad 5, real GGUF/JNI inference, page-2 citation correctness, or actual network denial when cloud consent is refused. All remain NOT TESTED.


## 2026-10-27 — GGUF device-runner storage preflight (source/CI PASS; device NOT TESTED)

- Added a fail-closed free-space preflight to `scripts/run-native-gguf-device-test.sh`: before `adb push`, read available KiB with `toybox df -k` for the target device directory and require model bytes + 64 MiB reserve. If free space cannot be parsed or is insufficient, exit 6 before pushing the model; the cleanup trap remains active.
- Extended `scripts/test-native-gguf-device-script.sh` with Fake ADB `df` output and a low-storage case. The test asserts the preflight runs, no GGUF push/instrumentation occurs, and no model file remains on the fake device.
- CI Run 40418534168, commit `4816c228c881d2b0000918d03239f9e49e6d2e05`: PASS. Unit tests 147 passed, 0 failed, 0 ignored; Lint PASS; Debug and AndroidTest APK builds PASS; package/SDK/offline-permission verification PASS; device-test bundle packaged PASS.
- Debug APK SHA-256: `1be41039e845b3ddf620c93a304f50e09a0d983a54d4af3b2e308e1b9bbb711a` (33,553,165 bytes). AndroidTest APK SHA-256: `1d178f4091f6e7e6111026b41afd773d0e237d992e643450e17f76bf7fb8f725` (373,777 bytes). Independently recomputed from the downloaded Artifact 12697601058 and matched `SHA256SUMS`.
- CI: https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40418534168
- Device bundle: https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40418534168/artifacts/12697601058
- Validation reports: https://github.com/amroalhamedi2022-hue/MedMate/actions/runs/40418534168/artifacts/12697675874
- Physical Xiaomi Pad 5 execution, real GGUF/llama.cpp inference, real two-page PDF citation validation, cloud-consent network test, and medical review remain NOT TESTED. The artifact contains APKs/checksums only; it does not contain a GGUF model, personal PDF, or API key.


## 2026-10-27 — GGUF header preflight (source/CI gate)

Before any ADB interaction, APK installation or model transfer, `run-native-gguf-device-test.sh` now requires the local input to begin with the GGUF magic bytes (`47 47 55 46`, ASCII `GGUF`) and contain at least the 24-byte minimum header. A wrong-magic or truncated file exits with status 4 before ADB/Gradle. This is deliberately only a cheap format preflight: it does **not** establish that the file is a complete/compatible model, that llama.cpp can load it, or that the model is clinically valid. Genuine model acceptance still requires a user-supplied local GGUF and real ARM64 Android instrumentation. Synthetic headers in script tests are explicitly not models.


## 2026-10-27 — Bouncy Castle dependency alignment (CI pending)

The Gradle lint report for the prior successful build surfaced `TrustAllX509TrustManager` warnings inside the transitive Bouncy Castle 1.72 JARs brought by PDFBox-Android 2.0.27.0 (the report points to dependency-cache JARs, not MedMate source). Upstream PDFBox-Android issue #475 documents this long-standing dependency warning. To avoid staying on the 2022-era 1.72 cryptography modules, MedMate now pins `bcprov`, `bcpkix`, and `bcutil` together at the official Bouncy Castle Java 1.86 release. This is a dependency-alignment attempt, **not yet a claim that the lint warning is resolved**; CI must verify dependency resolution, PDF extraction tests, Android compilation and the new lint report. The real PDF/GGUF device acceptance remains separate.


## 2026-10-27 — Malformed free-space output coverage

The runner already rejects an unreadable/non-numeric device free-space result before transferring the GGUF. The host-side fake-ADB contract now includes an explicit `Available=unknown` case and asserts fail-closed exit 6 with no push/instrumentation or residual file. This is still a host contract test, not device evidence. The updated test is awaiting a CI result on the current PR branch; previous APK artifacts do not include this source/test checkpoint.
