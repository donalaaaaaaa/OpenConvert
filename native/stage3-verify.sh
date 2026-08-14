#!/usr/bin/env bash
set -euo pipefail
TC="$HOME/oc-native-build/ndk/android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin"
SO="/mnt/d/Hermes/Herme/OpenConvert/native/out/libvips_android.so"
echo "=== exported (dynamic) symbols ==="
"$TC/llvm-nm" -D "$SO" | grep -E " T " | head -20
echo "=== strip ==="
"$TC/llvm-strip" "$SO"
ls -la "$SO"
echo "STRIPPED_OK"
