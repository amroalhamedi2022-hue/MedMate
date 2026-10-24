#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
kotlinc \
  app/src/main/java/com/medmate/personal/brain/ClinicalTermNormalizer.kt \
  app/src/main/java/com/medmate/personal/brain/BrainCore.kt \
  app/src/main/java/com/medmate/personal/brain/StudyBrain.kt \
  app/src/main/java/com/medmate/personal/brain/GatewayPolicy.kt \
  scripts/brain/BrainPipelineSmoke.kt \
  -include-runtime -d "$out/brain.jar"
java -jar "$out/brain.jar"
