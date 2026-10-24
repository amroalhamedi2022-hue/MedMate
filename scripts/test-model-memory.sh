#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
kotlinc app/src/main/java/com/medmate/personal/brain/LocalModelImport.kt \
  app/src/main/java/com/medmate/personal/brain/ModelMemoryGate.kt \
  scripts/brain/ModelMemoryGateSmoke.kt -include-runtime -d "$out/memory.jar"
java -jar "$out/memory.jar"
