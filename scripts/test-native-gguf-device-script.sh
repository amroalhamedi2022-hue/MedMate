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
grep -Fq 'host_sha="$(sha256sum "$GGUF_FILE" | awk' "$script"
grep -Fq 'toybox sha256sum "$device_file"' "$script"
grep -Fq 'toybox stat -c %s "$device_file"' "$script"
grep -Fq 'device-side GGUF digest/size differs' "$script"
hash_line="$(grep -nF 'device_sha=' "$script" | cut -d: -f1)"
instrument_line="$(grep -nF 'shell am instrument' "$script" | cut -d: -f1)"
[[ -n "$hash_line" && -n "$instrument_line" ]] && (( hash_line < instrument_line )) || {
  echo "FAIL: device digest must be verified before instrumentation" >&2
  exit 1
}
grep -Fq 'never downloads or uploads weights' "$script"
if grep -Eq '(^|[[:space:]])(curl|wget)[[:space:]]' "$script"; then
  echo "FAIL: device test script must not download model weights" >&2
  exit 1
fi
echo "PASS: GGUF device script validates and removes partial or complete temporary model copies on exit"
