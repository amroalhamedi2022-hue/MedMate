#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
ROOT=app/src/main/java/com/medmate/personal
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
kotlinc "$ROOT/study/StudyModels.kt" "$ROOT/study/ReviewPlanner.kt" \
  "$ROOT/study/StudyExport.kt" scripts/export-smoke.kt -include-runtime -d "$TMP/export-tests.jar"
java -jar "$TMP/export-tests.jar"
