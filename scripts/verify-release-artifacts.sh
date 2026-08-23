#!/usr/bin/env bash
# Fail-closed checks for a tagged OpenConvert release.
#
#   ./scripts/verify-release-artifacts.sh --require-secrets
#   ./scripts/verify-release-artifacts.sh --basic BASIC.apk --office OFFICE.apk [--tag v1.1.0]
#
# Does not print secret values. New scripts/*.sh must stay LF in git.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

EXPECTED_CERT_SHA256="${OPENCONVERT_EXPECTED_CERT_SHA256:-887ce064a82998c27978c373d8405dcee12d36deb36bc9e837c27fe957c5b8a5}"
EXPECTED_CERT_SHA256="$(printf '%s' "$EXPECTED_CERT_SHA256" | tr 'A-F' 'a-f' | tr -d ' :')"

BASIC_APK=""
OFFICE_APK=""
TAG=""
REQUIRE_SECRETS=0

usage() {
  echo "usage: $0 --require-secrets" >&2
  echo "       $0 --basic BASIC.apk --office OFFICE.apk [--tag vX.Y.Z]" >&2
  exit 2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --require-secrets) REQUIRE_SECRETS=1; shift ;;
    --basic)
      BASIC_APK="${2:-}"
      [[ -n "$BASIC_APK" ]] || usage
      shift 2
      ;;
    --office)
      OFFICE_APK="${2:-}"
      [[ -n "$OFFICE_APK" ]] || usage
      shift 2
      ;;
    --tag)
      TAG="${2:-}"
      [[ -n "$TAG" ]] || usage
      shift 2
      ;;
    -h|--help) usage ;;
    *) echo "unknown arg: $1" >&2; usage ;;
  esac
done

fail() {
  echo "verify-release-artifacts: $*" >&2
  exit 1
}

require_secrets() {
  local missing=0 name
  for name in \
    OPENCONVERT_KEYSTORE_BASE64 \
    OPENCONVERT_STORE_PASSWORD \
    OPENCONVERT_KEY_ALIAS \
    OPENCONVERT_KEY_PASSWORD
  do
    if [[ -z "${!name:-}" ]]; then
      echo "missing secret: $name" >&2
      missing=1
    fi
  done
  if [[ "$missing" -ne 0 ]]; then
    fail "tag release cannot proceed unsigned"
  fi
  echo "release secrets present"
}

if [[ "$REQUIRE_SECRETS" -eq 1 ]]; then
  require_secrets
  if [[ -z "$BASIC_APK" && -z "$OFFICE_APK" ]]; then
    exit 0
  fi
fi

[[ -n "$BASIC_APK" && -n "$OFFICE_APK" ]] || usage
[[ -f "$BASIC_APK" ]] || fail "basic apk not found: $BASIC_APK"
[[ -f "$OFFICE_APK" ]] || fail "office apk not found: $OFFICE_APK"
[[ "$BASIC_APK" != *unsigned* ]] || fail "basic apk looks unsigned: $BASIC_APK"
[[ "$OFFICE_APK" != *unsigned* ]] || fail "office apk looks unsigned: $OFFICE_APK"

VERSION_NAME="$(grep -E '^OPENCONVERT_VERSION_NAME=' gradle.properties | tail -1 | cut -d= -f2- | tr -d '\r')"
VERSION_CODE="$(grep -E '^OPENCONVERT_VERSION_CODE=' gradle.properties | tail -1 | cut -d= -f2- | tr -d '\r')"
[[ -n "$VERSION_NAME" ]] || fail "OPENCONVERT_VERSION_NAME missing"
[[ "$VERSION_CODE" =~ ^[0-9]+$ ]] || fail "OPENCONVERT_VERSION_CODE is not an int"
OFFICE_CODE=$((VERSION_CODE + 1))
OFFICE_NAME="${VERSION_NAME}-office"

if [[ -n "$TAG" ]]; then
  TAG_VERSION="${TAG#v}"
  [[ "$TAG_VERSION" == "$VERSION_NAME" ]] || \
    fail "tag '$TAG' != OPENCONVERT_VERSION_NAME $VERSION_NAME"
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
AAPT="$(find_build_tool aapt)"

normalize_sha() {
  printf '%s' "$1" | tr 'A-F' 'a-f' | tr -d ' :\r\n'
}

cert_sha256() {
  local apk="$1" line digest
  line="$("$APKSIGNER" verify --print-certs "$apk" 2>&1 | grep -i 'SHA-256 digest' | head -1)" || true
  digest="$(printf '%s' "$line" | awk -F': ' '{print $NF}')"
  digest="$(normalize_sha "$digest")"
  [[ -n "$digest" ]] || fail "no SHA-256 digest from apksigner for $apk"
  printf '%s\n' "$digest"
}

verify_signed() {
  local apk="$1"
  if ! "$APKSIGNER" verify --verbose "$apk" >/tmp/oc-apksigner-verify.txt 2>&1; then
    cat /tmp/oc-apksigner-verify.txt >&2
    fail "apksigner verify failed (unsigned or bad signature): $apk"
  fi
}

badging_field() {
  local apk="$1" key="$2"
  "$AAPT" dump badging "$apk" | grep -E "^${key}:" | head -1
}

package_attr() {
  local line="$1" key="$2"
  printf '%s' "$line" | sed -n "s/.*${key}='\([^']*\)'.*/\1/p"
}

check_apk() {
  local label="$1" apk="$2" want_name="$3" want_code="$4"
  echo "checking $label: $apk" >&2

  verify_signed "$apk"

  local pkg name code native
  pkg="$(badging_field "$apk" package)"
  name="$(package_attr "$pkg" versionName)"
  code="$(package_attr "$pkg" versionCode)"
  [[ "$name" == "$want_name" ]] || fail "$label versionName '$name' != '$want_name'"
  [[ "$code" == "$want_code" ]] || fail "$label versionCode '$code' != '$want_code'"

  native="$("$AAPT" dump badging "$apk" | grep -E '^native-code:' || true)"
  if [[ -z "$native" ]]; then
    fail "$label has no native-code (expected arm64-v8a only)"
  fi
  printf '%s' "$native" | grep -Eq "arm64-v8a" || fail "$label native-code missing arm64-v8a: $native"
  printf '%s' "$native" | grep -Eq "x86_64|x86'|armeabi" && \
    fail "$label native-code has a non-arm64 ABI: $native"

  if unzip -Z -1 "$apk" | grep -E '^lib/(x86_64|x86|armeabi|armeabi-v7a)/' >/dev/null; then
    fail "$label contains a non-arm64 native library"
  fi
  unzip -Z -1 "$apk" | grep -Eq '^lib/arm64-v8a/' || \
    fail "$label has no lib/arm64-v8a/"

  local sha
  sha="$(cert_sha256 "$apk")"
  [[ "$sha" == "$EXPECTED_CERT_SHA256" ]] || \
    fail "$label cert SHA-256 $sha != expected $EXPECTED_CERT_SHA256"
  printf '%s\n' "$sha"
}

BASIC_SHA="$(check_apk basic "$BASIC_APK" "$VERSION_NAME" "$VERSION_CODE")"
OFFICE_SHA="$(check_apk office "$OFFICE_APK" "$OFFICE_NAME" "$OFFICE_CODE")"
[[ "$BASIC_SHA" == "$OFFICE_SHA" ]] || \
  fail "Basic/Office certificates differ ($BASIC_SHA vs $OFFICE_SHA)"

echo "release artifacts ok"
echo "  tag=${TAG:-<none>} name=$VERSION_NAME"
echo "  basic=$VERSION_CODE office=$OFFICE_CODE"
echo "  cert=$BASIC_SHA"
echo "  abi=arm64-v8a"
