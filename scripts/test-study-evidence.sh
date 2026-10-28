#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
kotlinc app/src/main/java/com/medmate/personal/brain/StudyEvidenceNote.kt \
  scripts/brain/StudyEvidenceNoteSmoke.kt -include-runtime -d "$out/check.jar"
java -jar "$out/check.jar"
