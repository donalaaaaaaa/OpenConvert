#!/usr/bin/env bash
# Stage 2: link everything into one libvips_android.so + build the JNI object.
# Output: native/out/libvips_android.so  (arm64-v8a)
set -euo pipefail

ROOT="$HOME/oc-native-build"
NDK="$ROOT/ndk/android-ndk-r28c"
PREFIX="$ROOT/android-arm64/prefix"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
TARGET=aarch64-linux-android
CC="$TOOLCHAIN/bin/${TARGET}26-clang"
NATIVE="/mnt/d/Hermes/Herme/OpenConvert/native"
OUT="/mnt/d/Hermes/Herme/OpenConvert/native/out"
mkdir -p "$OUT"

if [ ! -f "$PREFIX/.done-vips" ]; then
  echo "ERROR: vips not built yet (run stage1b first)"; exit 1
fi

export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig"
export PKG_CONFIG_PATH=""

echo "=== pkg-config probe ==="
"$ROOT/tools/bin/pkg-config" --modversion vips glib-2.0

CFLAGS="$(PKG_CONFIG_PATH= PKG_CONFIG_LIBDIR=$PREFIX/lib/pkgconfig $ROOT/tools/bin/pkg-config --cflags vips)"

echo "=== compile JNI ==="
"$CC" -shared -fPIC -O2 \
  -I"$PREFIX/include" -I"$PREFIX/include/glib-2.0" -I"$PREFIX/lib/glib-2.0/include" \
  -I"$NDK/sysroot/usr/include" \
  -fvisibility=hidden \
  "$NATIVE/vips_jni.c" \
  -Wl,--version-script="$NATIVE/vips_android.map" \
  -Wl,--whole-archive \
  "$PREFIX/lib/libvips.a" \
  "$PREFIX/lib/libgobject-2.0.a" \
  "$PREFIX/lib/libgmodule-2.0.a" \
  "$PREFIX/lib/libgio-2.0.a" \
  "$PREFIX/lib/libglib-2.0.a" \
  "$PREFIX/lib/libintl.a" \
  "$PREFIX/lib/libpcre2-8.a" \
  "$PREFIX/lib/libjpeg.a" \
  "$PREFIX/lib/libpng.a" \
  "$PREFIX/lib/libwebp.a" \
  "$PREFIX/lib/libwebpdemux.a" \
  "$PREFIX/lib/libwebpmux.a" \
  "$PREFIX/lib/libz.a" \
  "$PREFIX/lib/libexpat.a" \
  "$PREFIX/lib/libiconv.a" \
  "$PREFIX/lib/libffi.a" \
  -Wl,--no-whole-archive \
  -llog -landroid -lm \
  -o "$OUT/libvips_android.so"

echo "=== result ==="
ls -la "$OUT/libvips_android.so"
"$TOOLCHAIN/bin/llvm-readelf" -d "$OUT/libvips_android.so" | grep NEEDED
echo "STAGE2_OK"
