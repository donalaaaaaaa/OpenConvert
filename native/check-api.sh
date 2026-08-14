#!/usr/bin/env bash
P="$HOME/oc-native-build/android-arm64/prefix/include/vips"
echo "=== autorot/resize/crop/save_buffer ==="
grep -hn 'vips_autorot\|vips_resize\|vips_crop\|vips_jpegsave_buffer\|vips_pngsave_buffer\|vips_webpsave_buffer\|vips_concurrency_set\|vips_image_new_from_buffer\|vips_image_get_width\|vips_image_get_height\|vips_image_get_bands' "$P"/*.h | head -40
echo "=== ref helpers ==="
grep -hn 'g_object_unref\|VIPS_UNREF\|vips_object_unref' "$P"/object.h | head -10
