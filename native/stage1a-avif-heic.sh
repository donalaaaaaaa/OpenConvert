#!/usr/bin/env bash
# Stage 1a: build libavif + libheif arm64-v8a (static)
# Depends on: libaom, libdav1d, libav1, libpng, zlib (already built)
set -euo pipefail

ROOT="$HOME/oc-native-build"
SRC="$ROOT/src"
NDK="$ROOT/ndk/android-ndk-r28c"
PREFIX="$ROOT/android-arm64/prefix"
TOOLS="$ROOT/tools/bin"

echo "=== Stage 1a: libavif + libheif ==="

# libaom (already built from previous stages)
# libdav1d (already built from previous stages)
# libav1 (already built from previous stages)
# libpng (already built from previous stages)
# zlib (already built from previous stages)

# libavif
cd "$SRC"
[ -d libavif-1.5.0 ] || tar -xf "$ROOT/dist/libavif-1.5.0.tar.gz"
cd libavif-1.5.0
mkdir -p build && cd build

cmake -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_SYSTEM_NAME=Android \
  -DCMAKE_SYSTEM_VERSION=26 \
  -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
  -DCMAKE_ANDROID_NDK="$NDK" \
  -DBUILD_SHARED_LIBS=OFF \
  -DAVIF_ENABLE_GAV1=ON \
  -DAVIF_ENABLE_HEVC=ON \
  -DAVIF_ENABLE_JPEG=ON \
  -DAVIF_ENABLE_LIBPNG=ON \
  -DAVIF_ENABLE_LIBSHARP=OFF \
  -DAVIF_ENABLE_LIBYUV=OFF \
  -DAVIF_ENABLE_WERROR=OFF \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" \
  -DCMAKE_PREFIX_PATH="$PREFIX" \
  -DCMAKE_C_FLAGS="-fPIC" \
  -DCMAKE_CXX_FLAGS="-fPIC" \

ninja install

# libheif
cd "$SRC"
[ -d libheif-1.18.0 ] || tar -xf "$ROOT/dist/libheif-1.18.0.tar.gz"
cd libheif-1.18.0
mkdir -p build && cd build

cmake -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_SYSTEM_NAME=Android \
  -DCMAKE_SYSTEM_VERSION=26 \
  -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
  -DCMAKE_ANDROID_NDK="$NDK" \
  -DBUILD_SHARED_LIBS=OFF \
  -DENABLE_LIBDE265=ON \
  -DENABLE_LIBAOM=ON \
  -DENABLE_LIBPNG=ON \
  -DENABLE_JPEG=ON \
  -DENABLE_EXAMPLES=OFF \
  -DENABLE_TOOLS=OFF \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" \
  -DCMAKE_PREFIX_PATH="$PREFIX" \

ninja install

echo "libavif + libheif build DONE"
ls -la "$PREFIX/lib/" | grep -E "avif|heif"