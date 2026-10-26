# MedMate — real GGUF device acceptance

Use the official [Qwen2.5-1.5B-Instruct-GGUF model repository](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF) and review its Apache-2.0 model license. A Q4_K_M file is a reasonable first ARM64 smoke candidate, but this general model is **not medically validated**. Do not commit model weights or upload them to GitHub.

On Xiaomi Pad 5 or another ARM64 Android device, download the GGUF locally and enable USB debugging. Connect exactly one device, then choose one of these paths.

**Use the exact CI-built APK pair (no local Gradle build):** download and unzip the `MedMate-device-test-bundle` artifact from a successful Android debug workflow run, then point both variables at the extracted APKs:

```bash
APP_APK=/absolute/path/app-debug.apk \
TEST_APK=/absolute/path/app-debug-androidTest.apk \
GGUF_FILE=/absolute/path/to/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf \
bash scripts/run-native-gguf-device-test.sh
```

**Or build from a source checkout:** omit `APP_APK` and `TEST_APK`; the script assembles and installs both debug APKs itself.

Set `ANDROID_SERIAL` if needed. The script installs both APKs before pushing the model, then pushes the GGUF only to app-specific external storage and invokes AndroidX instrumentation directly (so package installation cannot erase the model). It verifies host/device SHA-256 and byte count before inference and removes the temporary device copy on exit. The instrumented test checks free RAM, imports through the production content-addressed importer, invokes the real `LlamaAndroidPort`/llama.cpp JNI path, and runs a fresh random calibration challenge with a `[S1]` citation and no full source/prompt echo. It logs the GGUF SHA-256, size, elapsed time, app PSS snapshots and available system RAM without logging the answer. Newly imported test-only model files are removed after unload; pre-existing imported models are preserved.

A **PASS** means only that this GGUF loaded and passed the synthetic native inference smoke on that device. It does not establish clinical correctness, PDF-grounded accuracy, cloud-consent behavior or final performance. Missing model/device means **NOT TESTED**, never a fake-port PASS. GitHub CI compiles this test but does not run it or contain a model.


## Cleanup assertions (source change; device execution still pending)

The instrumented test now asserts that loadedModelName, loadedModelSha256, and loadedModelBytes all clear after unload(). It also asserts that a model newly imported only for the test is deleted afterward, while a previously imported/reused model is preserved. CI compiles the AndroidTest APK; it does not execute this test without a real device and user-supplied GGUF.
