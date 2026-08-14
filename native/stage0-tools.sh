#!/usr/bin/env bash
# Stage 0: bootstrap userspace build tools inside WSL (no sudo needed).
# Produces: ~/oc-native-build/tools/{bin/meson-wrapper,bin/pkg-config,bin/nasm}
set -euo pipefail

ROOT="$HOME/oc-native-build"
TOOLS="$ROOT/tools"
SRC="$ROOT/src"
mkdir -p "$TOOLS/bin" "$SRC"
cd "$SRC"
export PATH="$TOOLS/bin:$PATH"

# --- meson (pure python, use release tarball; no pip needed) ---
if [ ! -x "$TOOLS/bin/meson" ]; then
  MESON_VER=1.8.1
  curl -fL --retry 3 -o meson.tar.gz "https://github.com/mesonbuild/meson/releases/download/${MESON_VER}/meson-${MESON_VER}.tar.gz"
  rm -rf "meson-${MESON_VER}"
  tar -xf meson.tar.gz
  printf '#!/usr/bin/env bash\nexec python3 "%s/meson.py" "$@"\n' "$SRC/meson-${MESON_VER}" > "$TOOLS/bin/meson"
  chmod +x "$TOOLS/bin/meson"
fi

# --- pkgconf (meson's pkg-config provider) ---
if [ ! -x "$TOOLS/bin/pkg-config" ]; then
  PKGCONF_VER=2.2.0
  curl -fL --retry 3 -o pkgconf.tar.xz "https://github.com/pkgconf/pkgconf/releases/download/pkgconf-${PKGCONF_VER}/pkgconf-${PKGCONF_VER}.tar.xz"
  rm -rf "pkgconf-${PKGCONF_VER}"
  tar -xf pkgconf.tar.xz
  cd "pkgconf-${PKGCONF_VER}"
  ./configure --prefix="$TOOLS" >/dev/null
  make -j"$(nproc)" >/dev/null
  make install >/dev/null
  cd "$SRC"
  ln -sf pkgconf "$TOOLS/bin/pkg-config"
fi

# --- nasm (libjpeg-turbo SIMD assembler) ---
if [ ! -x "$TOOLS/bin/nasm" ]; then
  NASM_VER=2.16.03
  curl -fL --retry 3 -o nasm.tar.gz "https://www.nasm.us/pub/nasm/releasebuilds/${NASM_VER}/nasm-${NASM_VER}.tar.gz"
  rm -rf "nasm-${NASM_VER}"
  tar -xf nasm.tar.gz
  cd "nasm-${NASM_VER}"
  ./configure --prefix="$TOOLS" >/dev/null
  make -j"$(nproc)" >/dev/null
  make install >/dev/null
  cd "$SRC"
fi

echo "=== tool versions ==="
"$TOOLS/bin/meson" --version
"$TOOLS/bin/pkg-config" --version
"$TOOLS/bin/nasm" --version
echo "STAGE0_OK"
