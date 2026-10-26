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
