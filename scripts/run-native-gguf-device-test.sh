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
# Prefer an exact CI-built app + instrumentation APK pair when supplied. This lets
# device acceptance run without rebuilding; otherwise build both from this checkout.
if [[ -n "${APP_APK:-}" || -n "${TEST_APK:-}" ]]; then
  [[ -n "${APP_APK:-}" && -n "${TEST_APK:-}" ]] || {
    echo "Set both APP_APK and TEST_APK, or neither" >&2; exit 2;
  }
  APK="$APP_APK"
  TEST_APK_PATH="$TEST_APK"
else
  if [[ -x ./gradlew ]]; then GRADLE=(./gradlew); elif command -v gradle >/dev/null; then GRADLE=(gradle); else echo "Gradle wrapper or gradle required" >&2; exit 2; fi
  "${GRADLE[@]}" --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
  APK="app/build/outputs/apk/debug/app-debug.apk"
  TEST_APK_PATH="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
fi
[[ -f "$APK" && "$APK" == *.apk && -f "$TEST_APK_PATH" && "$TEST_APK_PATH" == *.apk ]] || {
  echo "Both app and AndroidTest APKs must be existing .apk files" >&2; exit 3;
}
# Install before pushing the large model; package installation can reset external app storage.
"${ADB[@]}" install -r "$APK"
"${ADB[@]}" install -r "$TEST_APK_PATH"

device_dir="/sdcard/Android/data/com.medmate.personal/files/native-test"
device_file="$device_dir/medmate-acceptance.gguf"
cleanup(){ "${ADB[@]}" shell rm -f "$device_file" >/dev/null 2>&1 || true; }
# Install the cleanup trap BEFORE mkdir/push: adb push can fail after creating a
# partial model file. EXIT also runs after INT/TERM handlers exit non-zero.
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
"${ADB[@]}" shell mkdir -p "$device_dir"
host_sha="$(sha256sum "$GGUF_FILE" | awk '{print $1}')"
host_bytes="$(wc -c < "$GGUF_FILE" | tr -d '[:space:]')"
[[ "$host_sha" =~ ^[0-9a-f]{64}$ && "$host_bytes" =~ ^[0-9]+$ ]] || { echo "Could not calculate a valid host model digest/size" >&2; exit 4; }
printf 'Host GGUF SHA-256: %s\nHost GGUF bytes: %s\n' "$host_sha" "$host_bytes"
"${ADB[@]}" push "$GGUF_FILE" "$device_file"
device_sha="$("${ADB[@]}" shell toybox sha256sum "$device_file" | tr -d '\r' | awk '{print $1}')"
device_bytes="$("${ADB[@]}" shell toybox stat -c %s "$device_file" | tr -d '\r')"
if [[ "$device_sha" != "$host_sha" || "$device_bytes" != "$host_bytes" ]]; then
  echo "FAIL: device-side GGUF digest/size differs from the local source; refusing native inference" >&2
  echo "Host: $host_sha ($host_bytes bytes); device: $device_sha ($device_bytes bytes)" >&2
  exit 4
fi
printf 'PASS: device GGUF SHA-256 and byte count match the local source (%s bytes).\n' "$device_bytes"
# Invoke instrumentation directly so the install step cannot erase the just-pushed file.
"${ADB[@]}" shell am instrument -w -e ggufPath "$device_file" com.medmate.personal.test/androidx.test.runner.AndroidJUnitRunner
