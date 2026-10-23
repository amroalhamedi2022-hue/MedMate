#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
kotlinc app/src/main/java/com/medmate/personal/data/BoundedLineReader.kt scripts/line-reader-smoke.kt \
  -include-runtime -d "$TMP/backup-lines.jar"
java -jar "$TMP/backup-lines.jar"
