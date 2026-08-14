#!/usr/bin/env bash
# Stage 1a: host tools (pkgconf) + image codec deps for arm64 (static).
# Runs inside WSL Ubuntu. No internet needed: sources are on /mnt/d.
set -euo pipefail

WSLHOME="$HOME"
ROOT="$WSLHOME/oc-native-build"
SRC="$ROOT/src"
NDK="$ROOT/ndk/android-ndk-r28c"
PREFIX="$ROOT/android-arm64/prefix"
TOOLS="$ROOT/tools"
DIST="/mnt/d/Hermes/Herme/OpenConvert/native/dist"
MESON="$SRC/meson-1.8.1/meson.py"
NPROC="$(nproc)"

export PATH="$TOOLS/bin:$PATH"
export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig"
export PKG_CONFIG_PATH=""

mkdir -p "$SRC" "$PREFIX" "$TOOLS/bin"

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
TARGET=aarch64-linux-android
export CC="$TOOLCHAIN/bin/${TARGET}26-clang"
export CXX="$TOOLCHAIN/bin/${TARGET}26-clang++"
export AR="$TOOLCHAIN/bin/llvm-ar"
export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
export STRIP="$TOOLCHAIN/bin/llvm-strip"
export CFLAGS="-O2 -fPIC -fvisibility=hidden"
export CPPFLAGS="-I$PREFIX/include"
export LDFLAGS="-L$PREFIX/lib"

extract() { # tarball [dir-name]
  local tb="$1" dir="${2:-}"
  cd "$SRC"
  if [ -n "$dir" ] && [ -d "$dir" ]; then return; fi
  echo "== extract $tb"
  tar -xf "$DIST/$tb"
}

build_done() { [ -f "$PREFIX/.done-$1" ]; }
mark_done() { touch "$PREFIX/.done-$1"; }

# ---------- host tool: pkgconf (built with HOST compiler, not the cross one) ----------
if [ ! -x "$TOOLS/bin/pkg-config" ]; then
  cd "$SRC"
  [ -d pkgconf-2.2.0 ] || tar -xf "$DIST/pkgconf-2.2.0.tar.xz"
  cd pkgconf-2.2.0
  env -u CC -u CXX -u AR -u RANLIB -u STRIP -u CFLAGS -u CPPFLAGS -u LDFLAGS \
      ./configure --prefix="$TOOLS" --with-sysroot=no >/dev/null
  make -j"$NPROC" >/dev/null
  make install >/dev/null
  ln -sf pkgconf "$TOOLS/bin/pkg-config"
fi
export PKGCONFIG="$TOOLS/bin/pkg-config"

# ---------- zlib ----------
if ! build_done zlib; then
  extract zlib-1.3.1.tar.gz zlib-1.3.1
  cd "$SRC/zlib-1.3.1"
  CHOST="$TARGET" ./configure --prefix="$PREFIX" --static >/dev/null
  make -j"$NPROC" >/dev/null
  make install >/dev/null
  mark_done zlib
fi

# ---------- libiconv ----------
if ! build_done iconv; then
  extract libiconv-1.17.tar.gz libiconv-1.17
  cd "$SRC/libiconv-1.17"
  ./configure --host="$TARGET" --prefix="$PREFIX" --enable-static --disable-shared \
      --disable-nls >/dev/null
  make -j"$NPROC" >/dev/null
  make install >/dev/null
  mark_done iconv
fi

# ---------- libffi ----------
if ! build_done ffi; then
  extract libffi-3.4.6.tar.gz libffi-3.4.6
  cd "$SRC/libffi-3.4.6"
  ./configure --host="$TARGET" --prefix="$PREFIX" --enable-static --disable-shared \
      --disable-docs >/dev/null
  make -j"$NPROC" >/dev/null
  make install >/dev/null
  mark_done ffi
fi

# ---------- pcre2 ----------
if ! build_done pcre2; then
  extract pcre2-10.44.tar.gz pcre2-10.44
  cd "$SRC/pcre2-10.44"
  ./configure --host="$TARGET" --prefix="$PREFIX" --enable-static --disable-shared \
      --disable-pcre2-16 --disable-pcre2-32 --disable-pcre2grep-libz \
      --disable-pcre2test-libreadline --disable-cpp >/dev/null
  make -j"$NPROC" >/dev/null
  make install >/dev/null
  mark_done pcre2
fi

# ---------- libpng ----------
if ! build_done png; then
  extract libpng-1.6.44.tar.gz libpng-1.6.44
  cd "$SRC/libpng-1.6.44"
  ./configure --host="$TARGET" --prefix="$PREFIX" --enable-static --disable-shared >/dev/null
  make -j"$NPROC" >/dev/null
  make install >/dev/null
  mark_done png
fi

# ---------- libjpeg-turbo (cmake, NEON intrinsics, no nasm needed for arm64) ----------
if ! build_done jpeg; then
  extract libjpeg-turbo-3.1.0.tar.gz libjpeg-turbo-3.1.0
  cd "$SRC/libjpeg-turbo-3.1.0"
  cmake -S . -B build -G Ninja \
      -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
      -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" \
      -DENABLE_SHARED=OFF -DENABLE_STATIC=ON -DWITH_JAVA=OFF >/dev/null
  cmake --build build -j "$NPROC" >/dev/null
  cmake --install build >/dev/null
  mark_done jpeg
fi

# ---------- libwebp ----------
if ! build_done webp; then
  extract libwebp-1.4.0.tar.gz libwebp-1.4.0
  cd "$SRC/libwebp-1.4.0"
  ./configure --host="$TARGET" --prefix="$PREFIX" --enable-static --disable-shared \
      --disable-tiff --disable-gif --disable-gl --disable-sdl --disable-png \
      --disable-jpeg --with-pic >/dev/null
  make -j"$NPROC" >/dev/null
  make install >/dev/null
  mark_done webp
fi

echo "=== stage1a done ==="
ls "$PREFIX/lib" | sort
echo "STAGE1A_OK"
