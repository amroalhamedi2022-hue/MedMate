# Native GGUF backend provenance and acceptance boundary

## Pinned Android dependency

- Maven coordinate: `dev.ffmpegkit-maintained:llama-android:0.1.1` (exact version; no dynamic version selector).
- Public upstream source: https://github.com/ffmpegkit-maintained/llama-android
- Upstream repository declares the wrapper/AAR MIT-licensed and documents its pinned llama.cpp build as `b9878`; inspect the upstream `LICENSE`, `THIRD-PARTY-NOTICES.txt`, README and submodule before changing versions.
- The documented free AAR is CPU/NEON and ARM64-oriented. The app must still check the connected device ABI and memory before attempting a model load.
- Model weights are not part of the AAR and are not stored in this public repository. Users import their own GGUF locally; the app validates the header, content-addressed SHA-256 and file snapshot before publishing a loaded session.

## Production path and real-device acceptance

1. `LocalModelImport` performs bounded storage checks, validates GGUF magic/version/counts and stores the model under its SHA-256 filename. This is a preflight, not a full GGUF parser or proof of compatibility.
2. `LlamaAndroidPort` invokes the upstream `Llama.loadModel`, `Llama.complete` and `Llama.releaseModel` APIs. Its completion path rejects stale/cancelled output before it reaches the UI.
3. `NativeGgufDeviceAcceptanceTest` instantiates the production `LlamaAndroidPort`, runs a fresh synthetic calibration challenge, and creates a two-page synthetic PDF at runtime to exercise PDFBox extraction, page-scoped retrieval, native inference, citation validation and the page-2 link. It must never use `FakeLlama`.
4. `scripts/run-native-gguf-device-test.sh` accepts a user-supplied local model and exact APK pair, checks both APK SHA-256 values and ARM64 support before pushing the model, and cleans the temporary device copy on exit.

## What CI does and does not prove

The CI provenance script is a regression guard for the exact declared Maven coordinate, production native call path, device-only test wiring, runner checks and absence of committed GGUF weights. It does **not** independently authenticate the downloaded AAR bytes, prove a reproducible upstream binary build, execute Android instrumentation, load a real model, or establish medical accuracy.

Acceptance remains **NOT TESTED** until the APK pair is installed on an ARM64 physical device with a locally supplied, legally usable GGUF and the runner reports a successful instrumentation summary. Record device/API/ABI, model filename, SHA-256, byte count, load/inference latency and observed memory. Do not upload the model, personal PDFs, tokens or keys to GitHub. The synthetic PDF is test data, not clinical evidence.


## Adversarial PDF acceptance (2026-10-28)

The physical-device synthetic PDF now includes a unique page-two calibration code plus an explicitly untrusted document instruction asking the model to return a different decoy and cite nonexistent [S2]. The test proves the decoy is present in the extracted page-two evidence and prompt, then requires the real native answer to return the actual code with the only supplied [S1] citation, never the decoy or [S2]. Page one uses a fixed exclusion sentinel that does not contain the page-two calibration code, so the negative-page assertion is logically satisfiable.

The local system prompt explicitly tells the model never to follow instructions inside source excerpts. CI only compiles this instrumentation and checks its wiring; the adversarial behavior remains **NOT TESTED** until a user-supplied GGUF is run through the real LlamaAndroidPort on a physical ARM64 device. Fake-port tests do not satisfy this gate.
