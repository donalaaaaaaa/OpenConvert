#!/usr/bin/env bash
# Package tagged OpenConvert artifacts and write notes from measured files.
#
#   ./scripts/package-release.sh --out output-release --tag v1.1.0
#   ./scripts/package-release.sh --out output-release --tag v1.1.0 --require-aab
#
# Human text comes from docs/releases/<tag>.md and must contain
# <!-- RELEASE_DOWNLOADS -->. Sizes, SHA256, version codes and the cert
# fingerprint are read from the APKs/AABs. New scripts/*.sh must stay LF.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

OUT=""
TAG=""
REQUIRE_AAB=0
BASIC_APK=""
OFFICE_APK=""
BASIC_AAB=""
OFFICE_AAB=""

usage() {
  echo "usage: $0 --out DIR --tag vX.Y.Z [--require-aab]" >&2
  exit 2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out) OUT="${2:-}"; [[ -n "$OUT" ]] || usage; shift 2 ;;
    --tag) TAG="${2:-}"; [[ -n "$TAG" ]] || usage; shift 2 ;;
    --require-aab) REQUIRE_AAB=1; shift ;;
    --basic) BASIC_APK="${2:-}"; shift 2 ;;
    --office) OFFICE_APK="${2:-}"; shift 2 ;;
    --basic-aab) BASIC_AAB="${2:-}"; shift 2 ;;
    --office-aab) OFFICE_AAB="${2:-}"; shift 2 ;;
    -h|--help) usage ;;
    *) echo "unknown arg: $1" >&2; usage ;;
  esac
done

[[ -n "$OUT" && -n "$TAG" ]] || usage

fail() {
  echo "package-release: $*" >&2
  exit 1
}

VERSION_NAME="$(grep -E '^OPENCONVERT_VERSION_NAME=' gradle.properties | tail -1 | cut -d= -f2- | tr -d '\r')"
VERSION_CODE="$(grep -E '^OPENCONVERT_VERSION_CODE=' gradle.properties | tail -1 | cut -d= -f2- | tr -d '\r')"
[[ -n "$VERSION_NAME" ]] || fail "OPENCONVERT_VERSION_NAME missing"
[[ "$VERSION_CODE" =~ ^[0-9]+$ ]] || fail "OPENCONVERT_VERSION_CODE is not an int"
TAG_VERSION="${TAG#v}"
[[ "$TAG_VERSION" == "$VERSION_NAME" ]] || \
  fail "tag '$TAG' != OPENCONVERT_VERSION_NAME $VERSION_NAME"
OFFICE_CODE=$((VERSION_CODE + 1))
OFFICE_NAME="${VERSION_NAME}-office"

NOTES_SRC="docs/releases/v${VERSION_NAME}.md"
[[ -f "$NOTES_SRC" ]] || fail "missing $NOTES_SRC"
grep -q '<!-- RELEASE_DOWNLOADS -->' "$NOTES_SRC" || \
  fail "$NOTES_SRC needs <!-- RELEASE_DOWNLOADS --> placeholder"

pick_one() {
  local found="" f
  for f in "$@"; do
    [[ -f "$f" ]] || continue
    [[ "$f" == *unsigned* ]] && continue
    if [[ -n "$found" ]]; then
      fail "multiple matches: $found and $f"
    fi
    found="$f"
  done
  printf '%s' "$found"
}

if [[ -z "$BASIC_APK" ]]; then
  BASIC_APK="$(pick_one app/build/outputs/apk/basic/release/*.apk || true)"
fi
if [[ -z "$OFFICE_APK" ]]; then
  OFFICE_APK="$(pick_one app/build/outputs/apk/office/release/*.apk || true)"
fi
if [[ -z "$BASIC_AAB" ]]; then
  BASIC_AAB="$(pick_one app/build/outputs/bundle/basicRelease/*.aab || true)"
fi
if [[ -z "$OFFICE_AAB" ]]; then
  OFFICE_AAB="$(pick_one app/build/outputs/bundle/officeRelease/*.aab || true)"
fi

[[ -n "$BASIC_APK" && -f "$BASIC_APK" ]] || fail "basic release apk not found"
[[ -n "$OFFICE_APK" && -f "$OFFICE_APK" ]] || fail "office release apk not found"
if [[ "$REQUIRE_AAB" -eq 1 ]]; then
  [[ -n "$BASIC_AAB" && -f "$BASIC_AAB" ]] || fail "basic release aab not found"
  [[ -n "$OFFICE_AAB" && -f "$OFFICE_AAB" ]] || fail "office release aab not found"
fi

find_sdk() {
  local d
  for d in \
    "${ANDROID_HOME:-}" \
    "${ANDROID_SDK_ROOT:-}" \
    "${LOCALAPPDATA:-}/Android/Sdk" \
    "${HOME}/AppData/Local/Android/Sdk" \
    "${HOME}/Android/Sdk"
  do
    if [[ -n "$d" && -d "$d/build-tools" ]]; then
      printf '%s\n' "$d"
      return 0
    fi
  done
  return 1
}

find_build_tool() {
  local name="$1"
  if command -v "$name" >/dev/null 2>&1; then
    command -v "$name"
    return 0
  fi
  local sdk latest
  sdk="$(find_sdk)" || fail "Android SDK not found (need $name)"
  latest="$(ls -1d "$sdk/build-tools"/* 2>/dev/null | LC_ALL=C sort | tail -1)"
  [[ -n "$latest" ]] || fail "no build-tools under $sdk"
  if [[ -x "$latest/$name" ]]; then
    printf '%s\n' "$latest/$name"
  elif [[ -f "$latest/${name}.bat" ]]; then
    printf '%s\n' "$latest/${name}.bat"
  else
    fail "$name not in $latest"
  fi
}

APKSIGNER="$(find_build_tool apksigner)"

bytes_of() {
  wc -c < "$1" | tr -d ' \t\r\n'
}

mib_of() {
  awk -v b="$1" 'BEGIN { printf "%.2f", b / 1024 / 1024 }'
}

sha_of() {
  # GNU sha256sum prefixes the hash with '\' when the path contains '\'.
  local hash
  hash="$(sha256sum "$1" | awk '{print $1}')"
  hash="${hash#\\}"
  printf '%s\n' "$hash"
}

normalize_sha() {
  printf '%s' "$1" | tr 'A-F' 'a-f' | tr -d ' :\r\n'
}

cert_line() {
  local apk="$1" field="$2"
  "$APKSIGNER" verify --print-certs "$apk" 2>&1 | grep -i "$field" | head -1
}

CERT_SHA="$(normalize_sha "$(cert_line "$BASIC_APK" 'SHA-256 digest' | awk -F': ' '{print $NF}')")"
[[ -n "$CERT_SHA" ]] || fail "no cert SHA-256 from $BASIC_APK"
OFFICE_CERT="$(normalize_sha "$(cert_line "$OFFICE_APK" 'SHA-256 digest' | awk -F': ' '{print $NF}')")"
[[ "$CERT_SHA" == "$OFFICE_CERT" ]] || fail "Basic/Office certificates differ"
CERT_DN="$(cert_line "$BASIC_APK" 'certificate DN' | awk -F': ' '{print $NF}' | tr -d '\r')"
[[ -n "$CERT_DN" ]] || CERT_DN="CN=OpenConvert"

COMMIT="${GITHUB_SHA:-$(git rev-parse HEAD)}"
BUILD_DATE="${BUILD_DATE:-$(date -u +%Y-%m-%d)}"

mkdir -p "$OUT"
# drop previous generated names so a re-run does not mix editions
rm -f "$OUT"/OpenConvert-"$TAG"-* "$OUT"/SHA256SUMS.txt "$OUT"/BUILD_INFO.txt "$OUT"/RELEASE_NOTES.md

BASIC_APK_NAME="OpenConvert-${TAG}-basic-arm64-v8a.apk"
OFFICE_APK_NAME="OpenConvert-${TAG}-office-arm64-v8a.apk"
BASIC_AAB_NAME="OpenConvert-${TAG}-basic.aab"
OFFICE_AAB_NAME="OpenConvert-${TAG}-office.aab"

cp "$BASIC_APK" "$OUT/$BASIC_APK_NAME"
cp "$OFFICE_APK" "$OUT/$OFFICE_APK_NAME"
if [[ -n "$BASIC_AAB" && -f "$BASIC_AAB" ]]; then
  cp "$BASIC_AAB" "$OUT/$BASIC_AAB_NAME"
else
  BASIC_AAB=""
fi
if [[ -n "$OFFICE_AAB" && -f "$OFFICE_AAB" ]]; then
  cp "$OFFICE_AAB" "$OUT/$OFFICE_AAB_NAME"
else
  OFFICE_AAB=""
fi

BASIC_APK_BYTES="$(bytes_of "$OUT/$BASIC_APK_NAME")"
OFFICE_APK_BYTES="$(bytes_of "$OUT/$OFFICE_APK_NAME")"
BASIC_APK_MIB="$(mib_of "$BASIC_APK_BYTES")"
OFFICE_APK_MIB="$(mib_of "$OFFICE_APK_BYTES")"
BASIC_APK_SHA="$(sha_of "$OUT/$BASIC_APK_NAME")"
OFFICE_APK_SHA="$(sha_of "$OUT/$OFFICE_APK_NAME")"

TABLE=""
append_row() {
  local file="$1" purpose="$2" bytes="$3" sha="$4"
  TABLE="${TABLE}| \`${file}\` | ${purpose} | $(mib_of "$bytes") MiB | \`${sha}\` |"$'\n'
}

TABLE="| 文件 | 用途 | 大小 | SHA256 |
|---|---|---:|---|
"
append_row "$BASIC_APK_NAME" "默认安装包" "$BASIC_APK_BYTES" "$BASIC_APK_SHA"
append_row "$OFFICE_APK_NAME" "含 Office → PDF" "$OFFICE_APK_BYTES" "$OFFICE_APK_SHA"

(
  cd "$OUT"
  sha256sum "$BASIC_APK_NAME" "$OFFICE_APK_NAME" > SHA256SUMS.txt
)

if [[ -n "$BASIC_AAB" ]]; then
  BASIC_AAB_BYTES="$(bytes_of "$OUT/$BASIC_AAB_NAME")"
  BASIC_AAB_SHA="$(sha_of "$OUT/$BASIC_AAB_NAME")"
  append_row "$BASIC_AAB_NAME" "Play / 商店上传" "$BASIC_AAB_BYTES" "$BASIC_AAB_SHA"
  ( cd "$OUT" && sha256sum "$BASIC_AAB_NAME" >> SHA256SUMS.txt )
fi
if [[ -n "$OFFICE_AAB" ]]; then
  OFFICE_AAB_BYTES="$(bytes_of "$OUT/$OFFICE_AAB_NAME")"
  OFFICE_AAB_SHA="$(sha_of "$OUT/$OFFICE_AAB_NAME")"
  append_row "$OFFICE_AAB_NAME" "Play / 商店上传" "$OFFICE_AAB_BYTES" "$OFFICE_AAB_SHA"
  ( cd "$OUT" && sha256sum "$OFFICE_AAB_NAME" >> SHA256SUMS.txt )
fi

DOWNLOADS="## 下载

${TABLE}
签名证书：\`${CERT_DN}\`，SHA-256 \`${CERT_SHA}\`。

Basic \`${VERSION_NAME}\` / \`${VERSION_CODE}\` · Office \`${OFFICE_NAME}\` / \`${OFFICE_CODE}\` · 仅 arm64-v8a。

大小和哈希来自本次构建产物，见附件 \`SHA256SUMS.txt\` / \`BUILD_INFO.txt\`。
"

python - "$NOTES_SRC" "$OUT/RELEASE_NOTES.md" "$DOWNLOADS" <<'PY'
import sys
src, dest, block = sys.argv[1], sys.argv[2], sys.argv[3]
text = open(src, encoding="utf-8").read()
marker = "<!-- RELEASE_DOWNLOADS -->"
if marker not in text:
    raise SystemExit(f"package-release: marker missing in {src}")
open(dest, "w", encoding="utf-8", newline="\n").write(text.replace(marker, block, 1))
PY

cat > "$OUT/BUILD_INFO.txt" <<EOF
OpenConvert ${VERSION_NAME}

Git Commit:
${COMMIT}

Basic:
versionName ${VERSION_NAME}
versionCode ${VERSION_CODE}
arm64-v8a
${BASIC_APK_MIB} MiB
${BASIC_APK_BYTES} bytes
SHA256 ${BASIC_APK_SHA}

Office:
versionName ${OFFICE_NAME}
versionCode ${OFFICE_CODE}
arm64-v8a
${OFFICE_APK_MIB} MiB
${OFFICE_APK_BYTES} bytes
SHA256 ${OFFICE_APK_SHA}
EOF

if [[ -n "$BASIC_AAB" ]]; then
  cat >> "$OUT/BUILD_INFO.txt" <<EOF

Basic AAB:
$(mib_of "$BASIC_AAB_BYTES") MiB
${BASIC_AAB_BYTES} bytes
SHA256 ${BASIC_AAB_SHA}
EOF
fi
if [[ -n "$OFFICE_AAB" ]]; then
  cat >> "$OUT/BUILD_INFO.txt" <<EOF

Office AAB:
$(mib_of "$OFFICE_AAB_BYTES") MiB
${OFFICE_AAB_BYTES} bytes
SHA256 ${OFFICE_AAB_SHA}
EOF
fi

cat >> "$OUT/BUILD_INFO.txt" <<EOF

Certificate SHA256:
${CERT_SHA}

Build date:
${BUILD_DATE}
EOF

echo "packaged $OUT"
echo "  $BASIC_APK_NAME ${BASIC_APK_MIB} MiB"
echo "  $OFFICE_APK_NAME ${OFFICE_APK_MIB} MiB"
echo "  cert=$CERT_SHA"
