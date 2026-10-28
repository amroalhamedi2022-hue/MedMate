#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
ROOT=app/src/main/java/com/medmate/personal
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
kotlinc "$ROOT/study/StudyModels.kt" "$ROOT/study/ReviewPlanner.kt" \
  "$ROOT/study/StudyFilter.kt" scripts/study-filter-smoke.kt \
  -include-runtime -d "$TMP/study-filter-tests.jar"
java -jar "$TMP/study-filter-tests.jar"
