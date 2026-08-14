#!/usr/bin/env bash
# Rebuild libwebp with sharp-yuv enabled (vips links SharpYuv* symbols).
set -euo pipefail

ROOT="$HOME/oc-native-build"
SRC="$ROOT/src"
NDK="$ROOT/ndk/android-ndk-r28c"
PREFIX="$ROOT/android-arm64/prefix"
DIST="/mnt/d/Hermes/Herme/OpenConvert/native/dist"

cd "$SRC"
[ -d libwebp-1.4.0 ] || tar -xf "$DIST/libwebp-1.4.0.tar.gz"
cd libwebp-1.4.0
rm -rf build
cmake -B build -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-26 \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" \
  -DCMAKE_INSTALL_LIBDIR=lib \
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DCMAKE_C_FLAGS="-O2 -fPIC -fvisibility=hidden" \
  -DWEBP_BUILD_ANIM_UTILS=OFF \
  -DWEBP_BUILD_CWEBP=OFF \
  -DWEBP_BUILD_DWEBP=OFF \
  -DWEBP_BUILD_EXTRAS=OFF \
  -DWEBP_BUILD_GIF2WEBP=OFF \
  -DWEBP_BUILD_IMG2WEBP=OFF \
  -DWEBP_BUILD_VWEBP=OFF \
  -DWEBP_BUILD_WEBPINFO=OFF \
  -DWEBP_BUILD_WEBPMUX=OFF \
  -DWEBP_ENABLE_SHARP_YUV=ON \
  -DBUILD_SHARED_LIBS=OFF
cmake --build build -j"$(nproc)"
cmake --install build
echo "=== check sharpyuv symbols ==="
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm" "$PREFIX/lib/libwebp.a" | grep -c "SharpYuv"
echo "LIBWEBP_SHARPYUV_OK"
