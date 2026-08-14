#!/usr/bin/env bash
set -uo pipefail
TC="$HOME/oc-native-build/ndk/android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin"
NM="$TC/llvm-nm"

echo "=== scan prefix libs ==="
for f in "$HOME"/oc-native-build/android-arm64/prefix/lib/*.a; do
  out=$("$NM" "$f" 2>/dev/null | grep -E " [TtWw] .*g_libintl_bindtextdomain" || true)
  [ -n "$out" ] && echo "DEFINED: $f" && echo "$out"
done

echo "=== scan glib build tree ==="
while IFS= read -r f; do
  out=$("$NM" "$f" 2>/dev/null | grep -E " [TtWw] .*g_libintl_bindtextdomain" || true)
  [ -n "$out" ] && echo "DEFINED: $f"
done < <(find "$HOME/oc-native-build/src/glib-2.80.3/build" -name "*.a" 2>/dev/null)

echo "=== which archives reference it (U) ==="
for f in "$HOME"/oc-native-build/android-arm64/prefix/lib/*.a; do
  out=$("$NM" "$f" 2>/dev/null | grep " U g_libintl_bindtextdomain" || true)
  [ -n "$out" ] && echo "REF: $f"
done
echo "SCAN_DONE"
