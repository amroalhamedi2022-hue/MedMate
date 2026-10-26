#!/usr/bin/env bash
set -euo pipefail
script="scripts/run-native-gguf-device-test.sh"
[[ -f "$script" ]]
bash -n "$script"
cleanup_line="$(grep -n '^cleanup()' "$script" | cut -d: -f1)"
trap_line="$(grep -n '^trap cleanup EXIT' "$script" | cut -d: -f1)"
push_line="$(grep -nF 'push "$GGUF_FILE" "$device_file"' "$script" | cut -d: -f1)"
[[ -n "$cleanup_line" && -n "$trap_line" && -n "$push_line" ]]
(( cleanup_line < push_line && trap_line < push_line )) || {
  echo "FAIL: model cleanup trap must be installed before adb push" >&2
  exit 1
}
grep -Fq 'shell rm -f "$device_file"' "$script"
grep -Fq 'never downloads or uploads weights' "$script"
if grep -Eq '(^|[[:space:]])(curl|wget)[[:space:]]' "$script"; then
  echo "FAIL: device test script must not download model weights" >&2
  exit 1
fi
echo "PASS: GGUF device script validates and removes partial or complete temporary model copies on exit"
