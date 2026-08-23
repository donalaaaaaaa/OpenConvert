#!/usr/bin/env bash
# Fail if required Git LFS binaries are still pointer files.
# Run before Office / Release / native assemble.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

fail=0
check() {
  local f="$1" min="$2"
  local path="$ROOT/$f"
  if [[ ! -f "$path" ]]; then
    echo "missing $f" >&2
    fail=1
    return
  fi
  local sz
  sz="$(wc -c < "$path" | tr -d ' ')"
  if [[ "$sz" -lt 300 ]] && head -c 40 "$path" | grep -q 'git-lfs'; then
    echo "LFS pointer, not pulled: $f" >&2
    fail=1
    return
  fi
  if [[ "$sz" -lt "$min" ]]; then
    echo "too small ($sz < $min): $f" >&2
    fail=1
    return
  fi
  echo "ok $f ($sz bytes)"
}

check app/src/main/jniLibs/arm64-v8a/libvips_android.so 1000000
check app/src/office/jniLibs/arm64-v8a/liblo-native-code.so 100000000
check app/src/office/jniLibs/arm64-v8a/libc++_shared.so 100000

if [[ "$fail" -ne 0 ]]; then
  echo "need git lfs pull before Office / Release / native jobs" >&2
  exit 1
fi
