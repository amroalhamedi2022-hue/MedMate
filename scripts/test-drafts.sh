#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
kotlinc app/src/main/java/com/medmate/personal/brain/BrainCore.kt \
  app/src/main/java/com/medmate/personal/brain/OfflineStudyDrafts.kt \
  scripts/brain/OfflineDraftSmoke.kt -include-runtime -d "$out/offline.jar"
java -jar "$out/offline.jar"
kotlinc app/src/main/java/com/medmate/personal/brain/BrainCore.kt \
  app/src/main/java/com/medmate/personal/brain/GeneratedStudyDrafts.kt \
  scripts/brain/GeneratedDraftSmoke.kt -include-runtime -d "$out/generated.jar"
java -jar "$out/generated.jar"
