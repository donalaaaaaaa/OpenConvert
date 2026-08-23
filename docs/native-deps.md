# Native / LFS inputs

OpenConvert keeps **runtime** arm64 binaries in Git LFS (vips, LibreOfficeKit).
Rebuild **inputs** (NDK zip, codec tarballs) are not part of a normal clone.

| Kind | Where | How to get |
|---|---|---|
| `libvips_android.so` | LFS · `app/src/main/jniLibs/` | `git lfs pull` |
| Office `liblo-native-code.so` + NSS | LFS · `app/src/office/jniLibs/` | `git lfs pull` |
| NDK r28c, libvips/glib sources | `native/dist/` (gitignored) | `./scripts/fetch-native-deps.sh` |
| Upstream LO Android APK | `native/dist/lo-viewer.apk` | `./scripts/fetch-office-pack.sh` |

Lock file: [`native/deps.lock`](../native/deps.lock) — name, dest, SHA-256, URL.

CI: pull requests checkout with `lfs: false` and skip assemble. Tag / main assemble and `workflow_dispatch` Office tests set `lfs: true` and run `scripts/require-lfs-objects.sh`.
