#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
BASE=app/src/main/java/com/medmate/personal/brain
CORE="$(dirname "$(command -v kotlinc)")/../lib/kotlinx-coroutines-core-jvm.jar"
kotlinc "$BASE/BrainCore.kt" "$BASE/StudyDraftPrompt.kt" "$BASE/OnDeviceLlmPort.kt" "$BASE/LocalModelImport.kt" "$BASE/LlamaAndroidPort.kt" \
    scripts/testfixtures/FakeLlama.kt scripts/brain/LlamaAdapterSmoke.kt \
    -cp "$CORE" -include-runtime -d "$WORK/adapter.jar"
java -cp "$WORK/adapter.jar:$CORE" LlamaAdapterSmokeKt
