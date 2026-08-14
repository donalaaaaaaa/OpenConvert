#!/usr/bin/env bash
# Merge libsharpyuv.a into libwebp.a so vips (and our final link) find SharpYuv*.
set -euo pipefail
LIB="$HOME/oc-native-build/android-arm64/prefix/lib"
AR="$HOME/oc-native-build/ndk/android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar"
RANLIB="$HOME/oc-native-build/ndk/android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ranlib"
cd /tmp
rm -rf webpmerge && mkdir webpmerge && cd webpmerge
"$AR" x "$LIB/libwebp.a"
"$AR" x "$LIB/libsharpyuv.a"
"$AR" rcs "$LIB/libwebp.a" *.o
"$RANLIB" "$LIB/libwebp.a"
"$HOME/oc-native-build/ndk/android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm" "$LIB/libwebp.a" | grep -c "SharpYuv"
echo "MERGED_OK"
