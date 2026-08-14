#!/usr/bin/env bash
set -uo pipefail
NDK="$HOME/oc-native-build/ndk/android-ndk-r28c"
CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android28-clang"
cd /tmp
cp /mnt/d/Hermes/Herme/OpenConvert/native/iconv_test.c t.c
if "$CC" t.c -o t 2>/tmp/iconv_err.txt; then
  echo "ICONV_IN_BIONIC_API28"
else
  echo "ICONV_MISSING_API28"
  head -3 /tmp/iconv_err.txt
fi
