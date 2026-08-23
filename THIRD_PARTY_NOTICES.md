# 第三方许可证

OpenConvert 自身是 [Apache License 2.0](LICENSE)。下面是发行包里会带上的第三方组件。
设置 → 关于 → 开源许可证 读的就是这份文件（打进 APK `assets/THIRD_PARTY_NOTICES.md`）。

版本以仓库当前依赖为准。换依赖时改这一份，不要另抄一份进 README。

## 清单

| 组件 | 版本 | License | 用途 | 出现在 |
|---|---|---|---|---|
| libvips | 8.18.5 | LGPL-2.1-or-later | 图片转换 | Basic / Office |
| LibreOfficeKit | gurecn/LibreOffice-android v1.0（2025-07） | MPL-2.0 | Office → PDF | **仅 Office** |
| FFmpegKit audio | 8.1.7 | LGPL-3.0-or-later | 音频编解码 | Basic / Office |
| PdfBox Android | 2.0.27.0 | Apache-2.0 | PDF 读写 | Basic / Office |
| Apache Commons Compress | 1.27.1 | Apache-2.0 | ZIP / TAR / 7Z / GZIP / BZIP2 | Basic / Office |
| XZ for Java | 1.10 | 0BSD / Public Domain | XZ / TAR.XZ | Basic / Office |
| LiTr | 1.5.7 | BSD-2-Clause | WEBM / 硬件转码 | Basic / Office |
| AndroidX / Jetpack / Compose BOM | 见 `app/build.gradle.kts` | Apache-2.0 | UI、Room、WorkManager、Media3 | Basic / Office |
| smart-exception-java | 0.2.1 | Apache-2.0 | FFmpegKit 运行时依赖 | Basic / Office |

AndroidX 精确坐标：

- `androidx.core:core-ktx:1.17.0`
- `androidx.activity:activity-compose:1.12.3`
- `androidx.lifecycle:lifecycle-*:2.9.4`
- `androidx.navigation:navigation-compose:2.9.8`
- `androidx.exifinterface:exifinterface:1.4.2`
- `androidx.documentfile:documentfile:1.1.0`
- `androidx.media3:media3-transformer:1.11.0`
- `androidx.room:room-*:2.8.4`
- `androidx.work:work-runtime-ktx:2.10.5`
- `androidx.compose:compose-bom:2026.06.00`

## 必须提供对应源码的组件

LGPL / MPL 要求能拿到与发行二进制对应的源码。本仓库不镜像整棵上游树，按下面地址取：

| 组件 | 源码 |
|---|---|
| libvips 8.18.5 | https://github.com/libvips/libvips/releases/tag/v8.18.5 ；本仓库交叉编译说明见 `docs/conversion-engine-roadmap.md`。静态链入 glib / libffi / pcre2 / zlib / libiconv / expat / libpng / libjpeg-turbo / libwebp |
| FFmpegKit 8.1.7 audio（LGPL，非 GPL 变体） | https://github.com/ffmpegkit-maintained/ffmpeg-kit ；Maven `dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7`。音频侧含 lame / opus / libvorbis / speex 等，见该仓库许可证表 |
| LibreOfficeKit | 二进制取自 https://github.com/gurecn/LibreOffice-android v1.0；JNI 绑定同源 https://github.com/premdeeparora12-bit/LibreOffice-Android。上游工程 https://www.libreoffice.org/download/download-libreoffice/ |

需要某一版对应的构建脚本或 `.so` 对照表，请开 GitHub Issue。

## 打包排除项

`app/build.gradle.kts` 里：

```text
packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
```

只丢掉旧 Android Support 库留下的**重名短许可证残片**（多个 AAR 打进同一个 ZIP 会冲突）。**没有**排除：

- `META-INF/NOTICE`
- `META-INF/LICENSE`
- `META-INF/LICENSE.txt`
- 本文件（`assets/THIRD_PARTY_NOTICES.md`）

不要再往 excludes 里加 `NOTICE` / `LICENSE*`。

## 许可证原文

- Apache-2.0：https://www.apache.org/licenses/LICENSE-2.0 ；本仓库 [LICENSE](LICENSE)
- LGPL-2.1：https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
- LGPL-3.0：https://www.gnu.org/licenses/lgpl-3.0.html
- MPL-2.0：https://www.mozilla.org/en-US/MPL/2.0/
- BSD-2-Clause（LiTr）：https://github.com/linkedin/LiTr/blob/main/LICENSE
- 0BSD / Public Domain（XZ for Java）：https://git.tukaani.org/?p=xz-java.git
