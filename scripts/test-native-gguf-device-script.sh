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
          *) echo "unsupported toybox command: $cmd" >&2; exit 90 ;;
        esac
        ;;
      am)
        if [[ "${2:-}" == instrument ]]; then
          printf 'INSTRUMENTED\n' >> "$FAKE_ADB_LOG"
          printf 'INSTRUMENTATION_STATUS_CODE: 0\n'
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
  local name="$1" expected="$2" corrupt="$3" push_fail="$4"
  local case_dir="$tmp/$name"
  mkdir -p "$case_dir/device-root"
  : > "$case_dir/adb.log"
  printf 'synthetic GGUF header; not a model\n' > "$case_dir/probe.gguf"
  local status=0
  (
    cd "$tmp"
    PATH="$tmp/bin:$PATH" \
      FAKE_DEVICE_ROOT="$case_dir/device-root" \
      FAKE_ADB_LOG="$case_dir/adb.log" \
      FAKE_GRADLE_LOG="$case_dir/gradle.log" \
      FAKE_DEVICE_CORRUPT="$corrupt" \
      FAKE_PUSH_FAIL="$push_fail" \
      GGUF_FILE="$case_dir/probe.gguf" \
      bash scripts/run-native-gguf-device-test.sh
  ) > "$case_dir/output.log" 2>&1 || status=$?

  if [[ "$expected" == pass ]]; then
    [[ "$status" == 0 ]] || { cat "$case_dir/output.log"; echo "FAIL: matching digest case exited $status" >&2; return 1; }
    grep -Fq 'PASS: device GGUF SHA-256 and byte count match' "$case_dir/output.log"
    grep -Fq 'INSTRUMENTED' "$case_dir/adb.log"
    ! find "$case_dir/device-root" -type f -print -quit | grep -q .
  elif [[ "$expected" == mismatch ]]; then
    [[ "$status" == 4 ]] || { cat "$case_dir/output.log"; echo "FAIL: mismatch case exited $status, expected 4" >&2; return 1; }
    grep -Fq 'refusing native inference' "$case_dir/output.log"
    ! grep -Fq 'INSTRUMENTED' "$case_dir/adb.log"
    ! find "$case_dir/device-root" -type f -print -quit | grep -q .
  else
    [[ "$status" != 0 ]] || { echo "FAIL: partial push should fail" >&2; return 1; }
    ! grep -Fq 'INSTRUMENTED' "$case_dir/adb.log"
    ! find "$case_dir/device-root" -type f -print -quit | grep -q .
  fi
}

run_case matching-digest pass 0 0
run_case mismatched-digest mismatch 1 0
run_case interrupted-partial-push fail 0 1

# A prebuilt CI bundle must install the exact supplied APK pair and skip Gradle.
prebuilt="$tmp/prebuilt-apks"
mkdir -p "$prebuilt/device-root"
: > "$prebuilt/adb.log"
printf 'prebuilt app APK; synthetic only\n' > "$prebuilt/app-debug.apk"
printf 'prebuilt AndroidTest APK; synthetic only\n' > "$prebuilt/app-debug-androidTest.apk"
printf 'synthetic GGUF header; not a model\n' > "$prebuilt/probe.gguf"
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
    bash scripts/run-native-gguf-device-test.sh
) > "$prebuilt/output.log" 2>&1 || status=$?
[[ "$status" == 0 ]] || { cat "$prebuilt/output.log"; echo "FAIL: prebuilt APK case exited $status" >&2; exit 1; }
[[ ! -e "$prebuilt/gradle.log" ]] || { echo "FAIL: prebuilt APK case unexpectedly invoked Gradle" >&2; exit 1; }
grep -Fq "install -r $prebuilt/app-debug.apk" "$prebuilt/adb.log"
grep -Fq "install -r $prebuilt/app-debug-androidTest.apk" "$prebuilt/adb.log"
grep -Fq 'INSTRUMENTED' "$prebuilt/adb.log"
! find "$prebuilt/device-root" -type f -print -quit | grep -q .

# Retain source-level privacy and order guards in addition to behavioral mocks.
script="$source_script"
grep -Fq 'never downloads or uploads weights' "$script"
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

echo "PASS: source-built and prebuilt APK paths; matching GGUF digest reaches instrumentation; mismatch and partial push fail closed and clean up; no model download."
