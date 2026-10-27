#!/usr/bin/env bash
set -euo pipefail
: "${GGUF_FILE:?Set GGUF_FILE to a local .gguf file; this script never downloads or uploads weights}"
[[ -f "$GGUF_FILE" ]] || { echo "GGUF_FILE is not a regular file" >&2; exit 2; }
case "${GGUF_FILE,,}" in *.gguf) ;; *) echo "GGUF_FILE must end in .gguf" >&2; exit 2 ;; esac
# Reject obvious wrong/partial inputs before invoking ADB, installing APKs, or pushing bytes.
# GGUF has a 4-byte ASCII magic followed by a 20-byte minimum header. This is only
# a format preflight; it does NOT prove the model is complete, compatible or safe.
host_bytes_preflight="$(wc -c < "$GGUF_FILE" | tr -d '[:space:]')"
host_magic_preflight="$(od -An -tx1 -N4 "$GGUF_FILE" | tr -d '[:space:]')"
if [[ ! "$host_bytes_preflight" =~ ^[0-9]+$ || "$host_bytes_preflight" -lt 24 || "$host_magic_preflight" != "47475546" ]]; then
  echo "FAIL: GGUF magic/header preflight failed (requires 47475546 magic and at least 24 bytes); refusing device interaction" >&2
  exit 4
fi
printf 'PASS: GGUF magic and minimum header size checked; full model validity still requires native inference.\n'
command -v adb >/dev/null || { echo "adb is required" >&2; exit 2; }
if [[ -n "${ANDROID_SERIAL:-}" ]]; then ADB=(adb -s "$ANDROID_SERIAL"); else
  count="$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')"
  [[ "$count" == "1" ]] || { echo "Connect exactly one device or set ANDROID_SERIAL" >&2; exit 2; }
  ADB=(adb)
fi
"${ADB[@]}" get-state >/dev/null
# This native acceptance path is specifically for the packaged ARM64 llama.cpp
# libraries. Reject unsupported devices before Gradle, APK installation, or GGUF transfer.
device_abi64="$("${ADB[@]}" shell getprop ro.product.cpu.abilist64 | tr -d '\r' | tr -d '[:space:]')"
case ",${device_abi64}," in
  *,arm64-v8a,*) printf 'PASS: device reports arm64-v8a support.\n' ;;
  *) echo "FAIL: device does not report arm64-v8a support; refusing APK install and GGUF transfer" >&2; exit 7 ;;
esac
# Prefer an exact CI-built app + instrumentation APK pair when supplied. This lets
# device acceptance run without rebuilding; otherwise build both from this checkout.
PREBUILT_APK_PAIR=0
if [[ -n "${APP_APK:-}" || -n "${TEST_APK:-}" ]]; then
  [[ -n "${APP_APK:-}" && -n "${TEST_APK:-}" ]] || {
    echo "Set both APP_APK and TEST_APK, or neither" >&2; exit 2;
  }
  APK="$APP_APK"
  TEST_APK_PATH="$TEST_APK"
  PREBUILT_APK_PAIR=1
else
  if [[ -x ./gradlew ]]; then GRADLE=(./gradlew); elif command -v gradle >/dev/null; then GRADLE=(gradle); else echo "Gradle wrapper or gradle required" >&2; exit 2; fi
  "${GRADLE[@]}" --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
  APK="app/build/outputs/apk/debug/app-debug.apk"
  TEST_APK_PATH="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
fi
[[ -f "$APK" && "$APK" == *.apk && -f "$TEST_APK_PATH" && "$TEST_APK_PATH" == *.apk ]] || {
  echo "Both app and AndroidTest APKs must be existing .apk files" >&2; exit 3;
}
# Prebuilt CI artifacts must be bound to the exact SHA-256 values recorded in
# the downloaded bundle. This check happens before installing either package.
if [[ "$PREBUILT_APK_PAIR" == 1 ]]; then
  if [[ -z "${APP_APK_SHA256:-}" || -z "${TEST_APK_SHA256:-}" ]]; then
    echo "Set APP_APK_SHA256 and TEST_APK_SHA256 when supplying prebuilt APKs" >&2
    exit 2
  fi
  verify_apk_sha() {
    local file="$1" expected="$2" label="$3" actual
    [[ "$expected" =~ ^[0-9a-fA-F]{64}$ ]] || {
      echo "Expected $label SHA-256 must be 64 hexadecimal characters" >&2; exit 2;
    }
    actual="$(sha256sum "$file" | awk '{print $1}')"
    if [[ "${expected,,}" != "$actual" ]]; then
      echo "FAIL: $label SHA-256 mismatch; refusing to install prebuilt APK" >&2
      echo "Expected: ${expected,,}; actual: $actual" >&2
      exit 4
    fi
    printf 'PASS: %s SHA-256 verified: %s\n' "$label" "$actual"
  }
  verify_apk_sha "$APK" "$APP_APK_SHA256" "app-debug.apk"
  verify_apk_sha "$TEST_APK_PATH" "$TEST_APK_SHA256" "app-debug-androidTest.apk"
fi
# Install before pushing the large model; package installation can reset external app storage.
"${ADB[@]}" install -r "$APK"
"${ADB[@]}" install -r "$TEST_APK_PATH"

device_dir="/sdcard/Android/data/com.medmate.personal/files/native-test"
device_file="$device_dir/medmate-acceptance-$-${RANDOM}-${RANDOM}.gguf"
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
# Fail closed before pushing a large local model if device storage cannot be verified.
# A 64 MiB reserve protects app/runtime data from a nearly-full shared data partition.
# Command substitution under `set -e` would otherwise exit with awk's status
# before the explicit fail-closed diagnostic and exit code below.
free_kb=""
if free_kb="$("${ADB[@]}" shell toybox df -k "$device_dir" | tr -d '\r' | awk 'NF >= 4 && $1 !~ /^Filesystem$/ {available=$4} END {if (available ~ /^[0-9]+$/) print available; else exit 1}')"; then
  :
else
  free_kb=""
fi
if [[ ! "$free_kb" =~ ^[0-9]+$ ]]; then
  echo "FAIL: cannot verify device free storage; refusing to push GGUF" >&2
  exit 6
fi
if ! awk -v available_kb="$free_kb" -v model_bytes="$host_bytes" 'BEGIN { required=model_bytes+67108864; exit !(available_kb*1024 >= required) }'; then
  echo "FAIL: insufficient device free storage for GGUF; refusing push (requires model bytes + 64 MiB margin)" >&2
  exit 6
fi
printf 'Device free storage: %s KiB; storage preflight passed.\n' "$free_kb"
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
# adb can return a successful shell status even when AndroidJUnitRunner reports
# failed tests. Acceptance must be based on the instrumentation result, not adb alone.
set +e
instrumentation_output="$("${ADB[@]}" shell am instrument -w -e ggufPath "$device_file" com.medmate.personal.test/androidx.test.runner.AndroidJUnitRunner 2>&1)"
instrumentation_status=$?
set -e
printf '%s\n' "$instrumentation_output"
if [[ "$instrumentation_status" -ne 0 ]]; then
  echo "FAIL: Android instrumentation command exited with status $instrumentation_status" >&2
  exit 5
fi
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED' <<<"$instrumentation_output"; then
  echo "FAIL: Android instrumentation reported a test failure; refusing acceptance." >&2
  exit 5
fi
if ! grep -Fq 'NativeGgufDeviceAcceptanceTest' <<<"$instrumentation_output"; then
  echo "FAIL: expected NativeGgufDeviceAcceptanceTest was not reported by instrumentation." >&2
  exit 5
fi
if ! grep -Eq 'OK \([1-9][0-9]* tests?\)' <<<"$instrumentation_output"; then
  echo "FAIL: Android instrumentation output is missing a successful JUnit summary." >&2
  exit 5
fi
printf 'PASS: Android instrumentation reported a successful native GGUF/PDF acceptance test.\n'
