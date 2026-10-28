#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
ROOT=app/src/main/java/com/medmate/personal
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
kotlinc "$ROOT/study/ReviewPlanner.kt" "$ROOT/study/StudyModels.kt" scripts/domain-smoke.kt \
  -include-runtime -d "$TMP/study-tests.jar"
java -jar "$TMP/study-tests.jar"
