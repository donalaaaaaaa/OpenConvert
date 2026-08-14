#!/usr/bin/env bash
# Stage 1b: glib (static) + libvips (static) for arm64.
# glib is vips's portability layer; everything links into one .so later.
set -euo pipefail

ROOT="$HOME/oc-native-build"
SRC="$ROOT/src"
NDK="$ROOT/ndk/android-ndk-r28c"
PREFIX="$ROOT/android-arm64/prefix"
TOOLS="$ROOT/tools"
DIST="/mnt/d/Hermes/Herme/OpenConvert/native/dist"
MESON="python3 $SRC/meson-1.8.1/meson.py"
NPROC="$(nproc)"

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
TARGET=aarch64-linux-android
CC="$TOOLCHAIN/bin/${TARGET}26-clang"
CXX="$TOOLCHAIN/bin/${TARGET}26-clang++"
SYSROOT="$TOOLCHAIN/sysroot"

export PATH="$TOOLS/bin:$PATH"
export PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig"
export PKG_CONFIG_PATH=""
# glib's meson build needs python 'packaging'; WSL has no internet, so use the
# wheel extracted under ~/oc-native-build/pysite.
export PYTHONPATH="$ROOT/pysite"

build_done() { [ -f "$PREFIX/.done-$1" ]; }
mark_done() { touch "$PREFIX/.done-$1"; }

# ---------- meson cross file ----------
CROSS="$ROOT/android-aarch64.cross"
sed -e "s|@CC@|$CC|" \
    -e "s|@CXX@|$CXX|" \
    -e "s|@AR@|$TOOLCHAIN/bin/llvm-ar|" \
    -e "s|@RANLIB@|$TOOLCHAIN/bin/llvm-ranlib|" \
    -e "s|@STRIP@|$TOOLCHAIN/bin/llvm-strip|" \
    -e "s|@PKGCONFIG@|$TOOLS/bin/pkg-config|" \
    -e "s|@PREFIX@|$PREFIX|" \
    -e "s|@SYSROOT@|$SYSROOT|" \
    /mnt/d/Hermes/Herme/OpenConvert/native/android-aarch64.cross.in > "$CROSS"

# ---------- glib ----------
if ! build_done glib; then
  cd "$SRC"
  [ -d glib-2.80.3 ] || tar -xf "$DIST/glib-2.80.3.tar.gz"
  # GitLab tarballs omit git submodules; gvdb is required by gio.
  if [ ! -f glib-2.80.3/subprojects/gvdb/meson.build ]; then
    rm -rf glib-2.80.3/subprojects/gvdb
    tar -xzf "$DIST/gvdb.tar.gz" -C glib-2.80.3/subprojects --strip-components=0
    mv "glib-2.80.3/subprojects/gvdb-0854af0fdb6d527a8d1999835ac2c5059976c210" \
       glib-2.80.3/subprojects/gvdb
  fi
  # intl dependency (gettext stub). WSL has no internet for meson git clone.
  if [ ! -f glib-2.80.3/subprojects/proxy-libintl/meson.build ]; then
    rm -rf glib-2.80.3/subprojects/proxy-libintl
    tar -xzf "$DIST/proxy-libintl-0.4.tar.gz" -C glib-2.80.3/subprojects
    mv glib-2.80.3/subprojects/proxy-libintl-0.4 glib-2.80.3/subprojects/proxy-libintl
  fi
  cd glib-2.80.3
  rm -rf build
  $MESON setup build \
    --cross-file "$CROSS" \
    --prefix="$PREFIX" \
    --default-library=static \
    --buildtype=release \
    -Dtests=false \
    -Dintrospection=disabled \
    -Ddocumentation=false \
    -Dman-pages=disabled \
    -Dselinux=disabled \
    -Dlibmount=disabled \
    -Dlibelf=disabled \
    -Dnls=disabled
  $MESON compile -C build -j"$NPROC"
  $MESON install -C build
  mark_done glib
fi

# ---------- vips ----------
if ! build_done vips; then
  cd "$SRC"
  [ -d vips-8.18.5 ] || tar -xf "$DIST/vips-8.18.5.tar.xz"
  cd vips-8.18.5
  rm -rf build
  $MESON setup build \
    --cross-file "$CROSS" \
    --prefix="$PREFIX" \
    --default-library=static \
    --buildtype=release \
    -Dcplusplus=false \
    -Dexamples=false \
    -Ddeprecated=false \
    -Ddocs=false \
    -Dintrospection=disabled \
    -Dmodules=disabled \
    -Dvapi=false \
    -Dnsgif=true \
    -Dppm=true \
    -Danalyze=false \
    -Dradiance=false \
    -Djpeg=enabled \
    -Dpng=enabled \
    -Dwebp=enabled \
    -Dzlib=enabled \
    -Dtiff=disabled \
    -Dcgif=disabled \
    -Dspng=disabled \
    -Dimagequant=disabled \
    -Dexif=disabled \
    -Dlcms=disabled \
    -Dfontconfig=disabled \
    -Dheif=disabled \
    -Dheif-module=disabled \
    -Dmagick=disabled \
    -Dmagick-module=disabled \
    -Dmatio=disabled \
    -Dnifti=disabled \
    -Dopenslide=disabled \
    -Dopenslide-module=disabled \
    -Dopenexr=disabled \
    -Dopenjpeg=disabled \
    -Dcfitsio=disabled \
    -Dfftw=disabled \
    -Dhighway=disabled \
    -Dorc=disabled \
    -Dpangocairo=disabled \
    -Dpdfium=disabled \
    -Dpoppler=disabled \
    -Dpoppler-module=disabled \
    -Dquantizr=disabled \
    -Draw=disabled \
    -Drsvg=disabled \
    -Duhdr=disabled \
    -Djpeg-xl=disabled \
    -Djpeg-xl-module=disabled \
    -Darchive=disabled \
    -Dfuzzing_engine=none
  $MESON compile -C build -j"$NPROC"
  $MESON install -C build
  mark_done vips
fi

echo "=== stage1b done ==="
ls -la "$PREFIX/lib" | grep -E "glib|gobject|gio|gmodule|vips"
echo "STAGE1B_OK"
