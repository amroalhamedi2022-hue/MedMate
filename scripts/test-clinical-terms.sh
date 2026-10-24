#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
kotlinc \
  app/src/main/java/com/medmate/personal/brain/ClinicalTermNormalizer.kt \
  app/src/main/java/com/medmate/personal/brain/BrainCore.kt \
  scripts/brain/ClinicalTermsSmoke.kt \
  -include-runtime -d "$out/clinical-terms.jar"
java -jar "$out/clinical-terms.jar"
