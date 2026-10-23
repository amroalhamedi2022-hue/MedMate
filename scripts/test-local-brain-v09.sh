#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
BASE=app/src/main/java/com/medmate/personal/brain
kotlinc "$BASE/BrainCore.kt" "$BASE/ExtractiveTutor.kt" "$BASE/LocalModelImport.kt" \
    scripts/brain/ModelAndTutorSmoke.kt -include-runtime -d "$WORK/brain-v09.jar"
java -jar "$WORK/brain-v09.jar"
