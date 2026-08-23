#!/usr/bin/env bash
# Fetch / verify native build inputs listed in native/deps.lock.
# Does not commit blobs. native/dist/ stays gitignored.
#
# Usage:
#   ./scripts/fetch-native-deps.sh              # verify present files; download missing if url set
#   ./scripts/fetch-native-deps.sh --verify     # verify only (no download)
#   ./scripts/fetch-native-deps.sh --only ndk-r28c-linux
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCK="$ROOT/native/deps.lock"
VERIFY_ONLY=0
ONLY=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --verify) VERIFY_ONLY=1; shift ;;
    --only) ONLY="${2:-}"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

if [[ ! -f "$LOCK" ]]; then
  echo "missing $LOCK" >&2
  exit 1
fi

is_lfs_pointer() {
  local f="$1"
  [[ -f "$f" ]] || return 1
  local sz
  sz="$(wc -c < "$f" | tr -d ' ')"
  [[ "$sz" -lt 300 ]] && head -c 40 "$f" | grep -q 'git-lfs'
}

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    python -c "import hashlib,sys; h=hashlib.sha256(); f=open(sys.argv[1],'rb');
h.update(f.read()); print(h.hexdigest())" "$1"
  fi
}

download() {
  local url="$1" dest="$2"
  mkdir -p "$(dirname "$dest")"
  local tmp="${dest}.part"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 --retry-delay 2 -o "$tmp" "$url"
  else
    echo "curl required to fetch $url" >&2
    exit 1
  fi
  mv "$tmp" "$dest"
}

ok=0
missing=0
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ "$line" =~ ^[[:space:]]*# ]] && continue
  [[ -z "${line// }" ]] && continue
  IFS='|' read -r name dest sha url <<<"$line"
  [[ -z "$name" || -z "$dest" || -z "$sha" ]] && continue
  if [[ -n "$ONLY" && "$name" != "$ONLY" ]]; then
    continue
  fi
  path="$ROOT/$dest"
  if is_lfs_pointer "$path"; then
    echo "LFS pointer (not a real blob): $dest"
    rm -f "$path"
  fi
  if [[ -f "$path" ]]; then
    got="$(sha256_of "$path")"
    if [[ "$got" != "$sha" ]]; then
      echo "SHA256 mismatch $dest" >&2
      echo "  want $sha" >&2
      echo "  got  $got" >&2
      exit 1
    fi
    echo "ok $name"
    ok=$((ok + 1))
    continue
  fi
  if [[ "$VERIFY_ONLY" -eq 1 || -z "${url:-}" ]]; then
    echo "missing $name ($dest)"
    missing=$((missing + 1))
    continue
  fi
  echo "fetch $name"
  download "$url" "$path"
  got="$(sha256_of "$path")"
  if [[ "$got" != "$sha" ]]; then
    echo "SHA256 mismatch after download $dest" >&2
    echo "  want $sha" >&2
    echo "  got  $got" >&2
    rm -f "$path"
    exit 1
  fi
  echo "ok $name (downloaded)"
  ok=$((ok + 1))
done < "$LOCK"

echo "verified=$ok missing=$missing"
# --verify is hash-only for files already on disk. Missing rows are expected
# after we stop tracking NDK / tarballs; use a normal run to download.
