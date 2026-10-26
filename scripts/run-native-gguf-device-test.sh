#!/usr/bin/env bash
set -euo pipefail
: "${GGUF_FILE:?Set GGUF_FILE to a local .gguf file; this script never downloads or uploads weights}"
[[ -f "$GGUF_FILE" ]] || { echo "GGUF_FILE is not a regular file" >&2; exit 2; }
case "${GGUF_FILE,,}" in *.gguf) ;; *) echo "GGUF_FILE must end in .gguf" >&2; exit 2 ;; esac
command -v adb >/dev/null || { echo "adb is required" >&2; exit 2; }
if [[ -n "${ANDROID_SERIAL:-}" ]]; then ADB=(adb -s "$ANDROID_SERIAL"); else
  count="$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')"
  [[ "$count" == "1" ]] || { echo "Connect exactly one device or set ANDROID_SERIAL" >&2; exit 2; }
  ADB=(adb)
fi
"${ADB[@]}" get-state >/dev/null
if [[ -x ./gradlew ]]; then GRADLE=(./gradlew); elif command -v gradle >/dev/null; then GRADLE=(gradle); else echo "Gradle wrapper or gradle required" >&2; exit 2; fi
# Build and install before pushing the large model; package installation can reset external app storage.
"${GRADLE[@]}" --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -f "$APK" && -f "$TEST_APK" ]] || { echo "Debug APKs were not produced" >&2; exit 3; }
"${ADB[@]}" install -r "$APK"
"${ADB[@]}" install -r "$TEST_APK"

device_dir="/sdcard/Android/data/com.medmate.personal/files/native-test"
device_file="$device_dir/medmate-acceptance.gguf"
"${ADB[@]}" shell mkdir -p "$device_dir"
"${ADB[@]}" push "$GGUF_FILE" "$device_file"
cleanup(){ "${ADB[@]}" shell rm -f "$device_file" >/dev/null 2>&1 || true; }
trap cleanup EXIT
# Invoke instrumentation directly so the install step cannot erase the just-pushed file.
"${ADB[@]}" shell am instrument -w -e ggufPath "$device_file" com.medmate.personal.test/androidx.test.runner.AndroidJUnitRunner
