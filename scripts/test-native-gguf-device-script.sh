#!/usr/bin/env bash
set -euo pipefail

# Exercise the real GGUF acceptance script with a disposable fake ADB transport.
# This proves fail-closed digest ordering and EXIT cleanup, but never pretends to
# execute llama.cpp or validate a real model.
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_script="$repo_root/scripts/run-native-gguf-device-test.sh"
[[ -f "$source_script" ]]
bash -n "$source_script"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/scripts" "$tmp/bin"
cp "$source_script" "$tmp/scripts/run-native-gguf-device-test.sh"
chmod +x "$tmp/scripts/run-native-gguf-device-test.sh"

cat > "$tmp/gradlew" <<'GRADLE'
#!/usr/bin/env bash
set -euo pipefail
printf 'invoked\n' >> "${FAKE_GRADLE_LOG:?}"
mkdir -p app/build/outputs/apk/debug app/build/outputs/apk/androidTest/debug
printf 'synthetic app apk for script contract only\n' > app/build/outputs/apk/debug/app-debug.apk
printf 'synthetic androidTest apk for script contract only\n' > app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
GRADLE
chmod +x "$tmp/gradlew"

cat > "$tmp/bin/adb" <<'ADB'
#!/usr/bin/env bash
set -euo pipefail
: "${FAKE_DEVICE_ROOT:?}"
: "${FAKE_ADB_LOG:?}"
printf '%s\n' "$*" >> "$FAKE_ADB_LOG"
map_path() { printf '%s%s' "$FAKE_DEVICE_ROOT" "$1"; }
case "${1:-}" in
  devices)
    printf 'List of devices attached\nfake-serial\tdevice\n'
    ;;
  get-state)
    printf 'device\n'
    ;;
  install)
    exit 0
    ;;
  push)
    src="$2"; remote="$3"; target="$(map_path "$remote")"
    mkdir -p "$(dirname "$target")"
    if [[ "${FAKE_PUSH_FAIL:-0}" == 1 ]]; then
      head -c 7 "$src" > "$target"
      exit 1
    fi
    cp "$src" "$target"
    if [[ "${FAKE_DEVICE_CORRUPT:-0}" == 1 ]]; then
      printf 'corrupt' >> "$target"
    fi
    printf '1 file pushed\n'
    ;;
  shell)
    shift
    case "${1:-}" in
      getprop)
        [[ "${2:-}" == "ro.product.cpu.abilist64" ]] || { echo "unsupported fake getprop: ${2:-}" >&2; exit 89; }
        printf '%s\n' "${FAKE_ABI_LIST64:-arm64-v8a}"
        ;;
      mkdir)
        shift
        [[ "${1:-}" == -p ]] && shift
        mkdir -p "$(map_path "$1")"
        ;;
      rm)
        shift
        [[ "${1:-}" == -f ]] && shift
        rm -f "$(map_path "$1")"
        ;;
      toybox)
        cmd="$2"
        case "$cmd" in
          sha256sum)
            remote="$3"; target="$(map_path "$remote")"
            sha256sum "$target"
            ;;
          stat)
            [[ "$3" == "-c" && "$4" == "%s" ]] || { echo "unsupported toybox stat arguments" >&2; exit 90; }
            remote="$5"; target="$(map_path "$remote")"
            wc -c < "$target" | tr -d '[:space:]'
            ;;
          df)
            printf 'Filesystem 1K-blocks Used Available Use%% Mounted on\n'
            if [[ "${FAKE_DF_MODE:-valid}" == malformed ]]; then
              printf 'fakefs 1000000 0 unknown 0%% /sdcard\n'
            else
              printf 'fakefs 1000000 0 %s 0%% /sdcard\n' "${FAKE_FREE_KB:-2000000}"
            fi
            ;;
          *) echo "unsupported toybox command: $cmd" >&2; exit 90 ;;
        esac
        ;;
      am)
        if [[ "${2:-}" == instrument ]]; then
          printf 'INSTRUMENTED\n' >> "$FAKE_ADB_LOG"
          case "${FAKE_INSTRUMENT_MODE:-pass}" in
            pass)
              printf 'INSTRUMENTATION_STATUS: class=com.medmate.personal.brain.NativeGgufDeviceAcceptanceTest\n'
              printf 'INSTRUMENTATION_STATUS: test=realGgufPdfCitationRoundTrip\n'
              printf 'INSTRUMENTATION_STATUS_CODE: 1\n'
              printf 'INSTRUMENTATION_RESULT: stream=\nTime: 0.01\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n'
              ;;
            fail)
              printf 'INSTRUMENTATION_STATUS: class=com.medmate.personal.brain.NativeGgufDeviceAcceptanceTest\nFAILURES!!!\nTests run: 1, Failures: 1\nINSTRUMENTATION_CODE: -1\n'
              ;;
            incomplete)
              printf 'INSTRUMENTATION_STATUS: class=com.medmate.personal.brain.NativeGgufDeviceAcceptanceTest\nINSTRUMENTATION_CODE: -1\n'
              ;;
            *) echo "unsupported fake instrumentation mode" >&2; exit 94 ;;
          esac
        else
          echo "unsupported adb shell am command" >&2
          exit 91
        fi
        ;;
      *)
        echo "unsupported adb shell command: $*" >&2
        exit 92
        ;;
    esac
    ;;
  *)
    echo "unsupported fake adb command: $*" >&2
    exit 93
    ;;
esac
ADB
chmod +x "$tmp/bin/adb"

run_case() {
  local name="$1" expected="$2" corrupt="$3" push_fail="$4" instrument_mode="${5:-pass}" free_kb="${6:-2000000}" df_mode="${7:-valid}"
  local case_dir="$tmp/$name"
  mkdir -p "$case_dir/device-root"
  : > "$case_dir/adb.log"
  # 24-byte synthetic GGUF header only; deliberately NOT a valid model.
  printf 'GGUF\003\000\000\000' > "$case_dir/probe.gguf"
  head -c 16 /dev/zero >> "$case_dir/probe.gguf"
  local status=0
  (
    cd "$tmp"
    PATH="$tmp/bin:$PATH" \
      FAKE_DEVICE_ROOT="$case_dir/device-root" \
      FAKE_ADB_LOG="$case_dir/adb.log" \
      FAKE_GRADLE_LOG="$case_dir/gradle.log" \
      FAKE_DEVICE_CORRUPT="$corrupt" \
      FAKE_PUSH_FAIL="$push_fail" \
      FAKE_INSTRUMENT_MODE="$instrument_mode" \
      FAKE_FREE_KB="$free_kb" \
      FAKE_DF_MODE="$df_mode" \
      GGUF_FILE="$case_dir/probe.gguf" \
      bash scripts/run-native-gguf-device-test.sh
  ) > "$case_dir/output.log" 2>&1 || status=$?

  if [[ "$expected" == pass ]]; then
    [[ "$status" == 0 ]] || { cat "$case_dir/output.log"; echo "FAIL: matching digest case exited $status" >&2; return 1; }
    grep -Fq 'PASS: device GGUF SHA-256 and byte count match' "$case_dir/output.log"
    grep -Fq 'PASS: Android instrumentation reported a successful native GGUF/PDF acceptance test.' "$case_dir/output.log"
    grep -Fq 'OK (1 test)' "$case_dir/output.log"
    grep -Fq 'INSTRUMENTED' "$case_dir/adb.log"
    ! find "$case_dir/device-root" -type f -print -quit | grep -q .
  elif [[ "$expected" == mismatch ]]; then
    [[ "$status" == 4 ]] || { cat "$case_dir/output.log"; echo "FAIL: mismatch case exited $status, expected 4" >&2; return 1; }
    grep -Fq 'refusing native inference' "$case_dir/output.log"
    ! grep -Fq 'INSTRUMENTED' "$case_dir/adb.log"
    ! find "$case_dir/device-root" -type f -print -quit | grep -q .
  elif [[ "$expected" == instrument-failure ]]; then
    [[ "$status" == 5 ]] || { cat "$case_dir/output.log"; echo "FAIL: reported JUnit failure exited $status, expected 5" >&2; return 1; }
    grep -Fq 'instrumentation reported a test failure' "$case_dir/output.log"
    grep -Fq 'INSTRUMENTED' "$case_dir/adb.log"
    ! find "$case_dir/device-root" -type f -print -quit | grep -q .
  elif [[ "$expected" == instrument-incomplete ]]; then
    [[ "$status" == 5 ]] || { cat "$case_dir/output.log"; echo "FAIL: incomplete instrumentation output exited $status, expected 5" >&2; return 1; }
    grep -Fq 'missing a successful JUnit summary' "$case_dir/output.log"
    grep -Fq 'INSTRUMENTED' "$case_dir/adb.log"
    ! find "$case_dir/device-root" -type f -print -quit | grep -q .
  elif [[ "$expected" == storage ]]; then
    [[ "$status" == 6 ]] || { cat "$case_dir/output.log"; echo "FAIL: low-storage case exited $status, expected 6" >&2; return 1; }
    grep -Fq 'insufficient device free storage' "$case_dir/output.log"
    grep -Fq 'shell toybox df' "$case_dir/adb.log"
    ! grep -Fq 'push ' "$case_dir/adb.log"
    ! grep -Fq 'INSTRUMENTED' "$case_dir/adb.log"
    ! find "$case_dir/device-root" -type f -print -quit | grep -q .
  elif [[ "$expected" == storage-unverifiable ]]; then
    [[ "$status" == 6 ]] || { cat "$case_dir/output.log"; echo "FAIL: unparseable storage report exited $status, expected 6" >&2; return 1; }
    grep -Fq 'cannot verify device free storage' "$case_dir/output.log"
    grep -Fq 'shell toybox df' "$case_dir/adb.log"
    ! grep -Fq 'push ' "$case_dir/adb.log"
    ! grep -Fq 'INSTRUMENTED' "$case_dir/adb.log"
    ! find "$case_dir/device-root" -type f -print -quit | grep -q .
  else
    [[ "$status" != 0 ]] || { echo "FAIL: partial push should fail" >&2; return 1; }
    ! grep -Fq 'INSTRUMENTED' "$case_dir/adb.log"
    ! find "$case_dir/device-root" -type f -print -quit | grep -q .
  fi
}


run_invalid_header_case() {
  local name="$1" payload="$2"
  local case_dir="$tmp/$name"
  mkdir -p "$case_dir/device-root"
  : > "$case_dir/adb.log"
  printf '%s' "$payload" > "$case_dir/probe.gguf"
  local status=0
  (
    cd "$tmp"
    PATH="$tmp/bin:$PATH" \
      FAKE_DEVICE_ROOT="$case_dir/device-root" \
      FAKE_ADB_LOG="$case_dir/adb.log" \
      FAKE_GRADLE_LOG="$case_dir/gradle.log" \
      GGUF_FILE="$case_dir/probe.gguf" \
      bash scripts/run-native-gguf-device-test.sh
  ) > "$case_dir/output.log" 2>&1 || status=$?
  [[ "$status" == 4 ]] || { cat "$case_dir/output.log"; echo "FAIL: invalid GGUF header must exit 4" >&2; return 1; }
  grep -Fq 'GGUF magic/header preflight failed' "$case_dir/output.log"
  [[ ! -s "$case_dir/adb.log" ]] || { echo "FAIL: invalid GGUF reached ADB" >&2; return 1; }
  [[ ! -e "$case_dir/gradle.log" ]] || { echo "FAIL: invalid GGUF triggered Gradle" >&2; return 1; }
  ! find "$case_dir/device-root" -type f -print -quit | grep -q .
}
run_unsupported_abi_case() {
  local case_dir="$tmp/unsupported-abi"
  mkdir -p "$case_dir/device-root"
  : > "$case_dir/adb.log"
  printf 'GGUF\003\000\000\000' > "$case_dir/probe.gguf"
  head -c 16 /dev/zero >> "$case_dir/probe.gguf"
  local status=0
  (
    cd "$tmp"
    PATH="$tmp/bin:$PATH" \
      FAKE_DEVICE_ROOT="$case_dir/device-root" \
      FAKE_ADB_LOG="$case_dir/adb.log" \
      FAKE_GRADLE_LOG="$case_dir/gradle.log" \
      FAKE_ABI_LIST64="x86_64" \
      GGUF_FILE="$case_dir/probe.gguf" \
      bash scripts/run-native-gguf-device-test.sh
  ) > "$case_dir/output.log" 2>&1 || status=$?
  [[ "$status" == 7 ]] || { cat "$case_dir/output.log"; echo "FAIL: unsupported ABI must exit 7" >&2; return 1; }
  grep -Fq 'does not report arm64-v8a support' "$case_dir/output.log"
  grep -Fq 'getprop ro.product.cpu.abilist64' "$case_dir/adb.log"
  ! grep -Fq 'install -r' "$case_dir/adb.log"
  ! grep -Fq 'push ' "$case_dir/adb.log"
  ! grep -Fq 'INSTRUMENTED' "$case_dir/adb.log"
  [[ ! -e "$case_dir/gradle.log" ]] || { echo "FAIL: unsupported ABI triggered Gradle" >&2; return 1; }
  ! find "$case_dir/device-root" -type f -print -quit | grep -q .
}

run_invalid_header_case bad-magic 'not a GGUF model'
run_unsupported_abi_case
run_invalid_header_case short-header 'GGUF'

run_case matching-digest pass 0 0 pass
run_case mismatched-digest mismatch 1 0 pass
run_case interrupted-partial-push fail 0 1 pass
run_case instrumentation-reported-failure instrument-failure 0 0 fail
run_case instrumentation-missing-summary instrument-incomplete 0 0 incomplete
run_case low-storage storage 0 0 pass 1000
run_case unparseable-storage storage-unverifiable 0 0 pass 2000000 malformed

# A prebuilt CI bundle must install the exact supplied APK pair and skip Gradle.
prebuilt="$tmp/prebuilt-apks"
mkdir -p "$prebuilt/device-root"
: > "$prebuilt/adb.log"
printf 'prebuilt app APK; synthetic only\n' > "$prebuilt/app-debug.apk"
printf 'prebuilt AndroidTest APK; synthetic only\n' > "$prebuilt/app-debug-androidTest.apk"
# Synthetic 24-byte header only; never used to claim model inference.
printf 'GGUF\003\000\000\000' > "$prebuilt/probe.gguf"
head -c 16 /dev/zero >> "$prebuilt/probe.gguf"
status=0
(
  cd "$tmp"
  PATH="$tmp/bin:$PATH" \
    FAKE_DEVICE_ROOT="$prebuilt/device-root" \
    FAKE_ADB_LOG="$prebuilt/adb.log" \
    FAKE_GRADLE_LOG="$prebuilt/gradle.log" \
    FAKE_ABI_LIST64="arm64-v8a" \
    GGUF_FILE="$prebuilt/probe.gguf" \
    APP_APK="$prebuilt/app-debug.apk" \
    TEST_APK="$prebuilt/app-debug-androidTest.apk" \
    APP_APK_SHA256="$(sha256sum "$prebuilt/app-debug.apk" | awk '{print $1}')" \
    TEST_APK_SHA256="$(sha256sum "$prebuilt/app-debug-androidTest.apk" | awk '{print $1}')" \
    bash scripts/run-native-gguf-device-test.sh
) > "$prebuilt/output.log" 2>&1 || status=$?
[[ "$status" == 0 ]] || { cat "$prebuilt/output.log"; echo "FAIL: prebuilt APK case exited $status" >&2; exit 1; }
[[ ! -e "$prebuilt/gradle.log" ]] || { echo "FAIL: prebuilt APK case unexpectedly invoked Gradle" >&2; exit 1; }
grep -Fq "install -r $prebuilt/app-debug.apk" "$prebuilt/adb.log"
grep -Fq "install -r $prebuilt/app-debug-androidTest.apk" "$prebuilt/adb.log"
grep -Fq 'INSTRUMENTED' "$prebuilt/adb.log"
! find "$prebuilt/device-root" -type f -print -quit | grep -q .

! find "$prebuilt/device-root" -type f -print -quit | grep -q .

# A mismatched prebuilt APK must fail before install/push/instrumentation.
: > "$prebuilt/adb.log"
status=0
(
  cd "$tmp"
  PATH="$tmp/bin:$PATH" \
    FAKE_DEVICE_ROOT="$prebuilt/device-root" \
    FAKE_ADB_LOG="$prebuilt/adb.log" \
    FAKE_GRADLE_LOG="$prebuilt/gradle.log" \
    GGUF_FILE="$prebuilt/probe.gguf" \
    APP_APK="$prebuilt/app-debug.apk" \
    TEST_APK="$prebuilt/app-debug-androidTest.apk" \
    APP_APK_SHA256="$(printf '0%.0s' {1..64})" \
    TEST_APK_SHA256="$(sha256sum "$prebuilt/app-debug-androidTest.apk" | awk '{print $1}')" \
    bash scripts/run-native-gguf-device-test.sh
) > "$prebuilt/mismatch.log" 2>&1 || status=$?
[[ "$status" == 4 ]] || { cat "$prebuilt/mismatch.log"; echo "FAIL: wrong prebuilt APK digest should exit 4" >&2; exit 1; }
grep -Fq 'SHA-256 mismatch' "$prebuilt/mismatch.log"
! grep -Fq 'install -r' "$prebuilt/adb.log"
! grep -Fq 'push ' "$prebuilt/adb.log"
! grep -Fq 'INSTRUMENTED' "$prebuilt/adb.log"

run_preserves_existing_device_file_case() {
  local case_dir="$tmp/preserve-existing-device-file"
  local legacy_remote="/sdcard/Android/data/com.medmate.personal/files/native-test/medmate-acceptance.gguf"
  local legacy_target="$case_dir/device-root$legacy_remote"
  mkdir -p "$(dirname "$legacy_target")"
  printf 'user-owned pre-existing file; must survive\n' > "$legacy_target"
  : > "$case_dir/adb.log"
  printf 'GGUF\\003\\000\\000\\000' > "$case_dir/probe.gguf"
  head -c 16 /dev/zero >> "$case_dir/probe.gguf"
  local status=0
  (
    cd "$tmp"
    PATH="$tmp/bin:$PATH" \\
      FAKE_DEVICE_ROOT="$case_dir/device-root" \\
      FAKE_ADB_LOG="$case_dir/adb.log" \\
      FAKE_GRADLE_LOG="$case_dir/gradle.log" \\
      FAKE_ABI_LIST64="arm64-v8a" \\
      FAKE_FREE_KB="2000000" \\
      GGUF_FILE="$case_dir/probe.gguf" \\
      bash scripts/run-native-gguf-device-test.sh
  ) > "$case_dir/output.log" 2>&1 || status=$?
  [[ "$status" == 0 ]] || { cat "$case_dir/output.log"; echo "FAIL: unique-target cleanup case exited $status" >&2; return 1; }
  grep -Fq 'PASS: Android instrumentation reported a successful native GGUF/PDF acceptance test.' "$case_dir/output.log"
  [[ "$(cat "$legacy_target")" == 'user-owned pre-existing file; must survive' ]] || {
    echo "FAIL: test runner overwrote or deleted a pre-existing file" >&2; return 1;
  }
  local push_line
  push_line="$(grep -F 'push ' "$case_dir/adb.log" | tail -n 1)"
  [[ "$push_line" == *"/native-test/medmate-acceptance-"*".gguf" ]] || {
    echo "FAIL: GGUF must use a unique per-run remote filename" >&2; return 1;
  }
  [[ "$push_line" != *"$legacy_remote" ]] || {
    echo "FAIL: GGUF push targeted the legacy fixed filename" >&2; return 1;
  }
  local unexpected
  unexpected="$(find "$case_dir/device-root" -type f ! -path "$legacy_target" -print -quit)"
  [[ -z "$unexpected" ]] || { echo "FAIL: temporary GGUF was not cleaned: $unexpected" >&2; return 1; }
}
run_preserves_existing_device_file_case

script="$source_script"
grep -Fq 'never downloads or uploads weights' "$script"
grep -Fq 'device_file="$device_dir/medmate-acceptance-$-${RANDOM}-${RANDOM}.gguf"' "$script"
if grep -Eq '(^|[[:space:]])(curl|wget)[[:space:]]' "$script"; then
  echo "FAIL: device test script must not download model weights" >&2
  exit 1
fi
cleanup_line="$(grep -n '^cleanup()' "$script" | cut -d: -f1)"
trap_line="$(grep -n '^trap cleanup EXIT' "$script" | cut -d: -f1)"
push_line="$(grep -nF 'push "$GGUF_FILE" "$device_file"' "$script" | cut -d: -f1)"
hash_line="$(grep -nF 'device_sha=' "$script" | cut -d: -f1)"
instrument_line="$(grep -nF 'shell am instrument' "$script" | cut -d: -f1)"
[[ -n "$cleanup_line" && -n "$trap_line" && -n "$push_line" && -n "$hash_line" && -n "$instrument_line" ]]
(( cleanup_line < push_line && trap_line < push_line && push_line < hash_line && hash_line < instrument_line ))

echo "PASS: source-built and prebuilt APK paths; bad/short GGUF headers fail before ADB or Gradle; non-ARM64 devices fail before Gradle/install/push; low-storage preflight blocks model push; matching synthetic header/digest reaches fake instrumentation; APK hash mismatch, GGUF mismatch and partial push fail closed and clean up; no model download."
