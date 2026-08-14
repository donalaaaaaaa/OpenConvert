#!/usr/bin/env bash
# Build expat (static) for arm64 — vips needs it unconditionally.
set -euo pipefail

ROOT="$HOME/oc-native-build"
SRC="$ROOT/src"
NDK="$ROOT/ndk/android-ndk-r28c"
PREFIX="$ROOT/android-arm64/prefix"
DIST="/mnt/d/Hermes/Herme/OpenConvert/native/dist"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
TARGET=aarch64-linux-android

export CC="$TOOLCHAIN/bin/${TARGET}26-clang"
export CXX="$TOOLCHAIN/bin/${TARGET}26-clang++"
export CFLAGS="-O2 -fPIC -fvisibility=hidden"

if [ ! -f "$PREFIX/.done-expat" ]; then
  cd "$SRC"
  [ -d expat-2.6.2 ] || tar -xf "$DIST/expat-2.6.2.tar.gz"
  cd expat-2.6.2
  rm -rf build
  cmake -B build -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$PREFIX" \
    -DCMAKE_INSTALL_LIBDIR=lib \
    -DEXPAT_BUILD_TOOLS=OFF \
    -DEXPAT_BUILD_EXAMPLES=OFF \
    -DEXPAT_BUILD_TESTS=OFF \
    -DEXPAT_SHARED_LIBS=OFF \
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DCMAKE_C_FLAGS="$CFLAGS"
  cmake --build build -j"$(nproc)"
  cmake --install build
  touch "$PREFIX/.done-expat"
fi

ls -la "$PREFIX/lib" | grep expat
echo "EXPAT_OK"
