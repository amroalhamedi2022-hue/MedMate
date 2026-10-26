# MedMate — real GGUF device acceptance

Use the official [Qwen2.5-1.5B-Instruct-GGUF model repository](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF) and review its Apache-2.0 model license. A Q4_K_M file is a reasonable first ARM64 smoke candidate, but this general model is **not medically validated**. Do not commit model weights or upload them to GitHub.

On Xiaomi Pad 5 or another ARM64 Android device, download the GGUF locally, enable USB debugging, connect exactly one device, and run:

```bash
GGUF_FILE=/absolute/path/to/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf bash scripts/run-native-gguf-device-test.sh
```

Set `ANDROID_SERIAL` if needed. The script pushes the file only to app-specific external storage, runs `:app:connectedDebugAndroidTest`, and removes the temporary device copy. The instrumented test checks free RAM, imports through the production content-addressed importer, invokes the real `LlamaAndroidPort`/llama.cpp JNI path, and runs a fresh random calibration challenge with a `[S1]` citation and no full source/prompt echo. It logs the GGUF SHA-256, size, elapsed time, app PSS snapshots and available system RAM without logging the answer. Newly imported test-only model files are removed after unload; pre-existing imported models are preserved.

A **PASS** means only that this GGUF loaded and passed the synthetic native inference smoke on that device. It does not establish clinical correctness, PDF-grounded accuracy, cloud-consent behavior or final performance. Missing model/device means **NOT TESTED**, never a fake-port PASS. GitHub CI compiles this test but does not run it or contain a model.
