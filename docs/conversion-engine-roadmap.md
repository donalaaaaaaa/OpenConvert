# Android 本地多格式转换高效方案清单

后续引擎优化按此文件执行。速度优先级：

`Remux/Copy → MediaCodec/Media3 → 专用 Native → 多线程软件编码 → FFmpeg 兜底`

不要把 FFmpeg 当所有格式的主引擎。FFmpegKit 仅作过渡兼容层。

## 当前已落地（对照）

- **统一原则 §一**：所有音视频任务转换前先用 Android `MediaExtractor` 探测真实编码/码率，再决定 Remux / 拷流 / 硬件转码 / 软件兜底（不用 ffprobe，避免与 FFmpeg 抢 SAF fd）
- **视频主引擎 §二（2026-08-14）**：MP4 重编码走 `media3-transformer`（MediaCodec H.264 硬件 + AAC），失败自动回退 FFmpeg。证据：PHY110 上 `engine=media3 result=success`。Media3 要求所有 Transformer API 调用在其绑定 Looper 线程；MediaCodec 需 16 像素对齐
- 视频 Remux：能拷流则拷流（H.264/HEVC + AAC → MP4 不重编码）
- **MP4→WEBM**：LiTr + MediaCodec VP8 + Opus，禁止默认 VP9；LiTr 失败回退 FFmpeg libvpx realtime
- **音频 §3**：同编码转换（M4A↔AAC、MP3→MP3、FLAC→FLAC、WAV→WAV）走 `demux → -c:a copy` 直拷换容器，不重新编码；其余按目标重新编码
- 任务：WorkManager + 前台服务，不在 Compose 里跑转码
- 大文件：流式读写；ARM64-v8a
- PDF：PdfBox-Android + 系统 PdfRenderer；合并用 setupMixed 内存设置
- **图片→PDF §8**：JPEG 且无 EXIF 旋转时 `JPEGFactory.createFromStream` 直接嵌入压缩流，不解码、不重新压缩（更快、无损、体积更小、内存更低）；其他格式走 `LosslessFactory` 位图路径
- **图片互转（2026-08-14）**：主引擎 libvips 8.18.5（WSL + Linux NDK r28c 交叉编译：vips/glib/ffi/pcre2/zlib/iconv/expat/png/jpeg-turbo/webp 全静态，链成单个 `libvips_android.so` 8.2MB，version script 只导出 JNI 符号）。JNI 入口：probeBuffer / convertBuffer（EXIF 旋转用 vips_autorot）。BitmapFactory 自动兜底。注意：libwebp 需 `-Dsharp-yuv=enabled` 且 libwebpdemux/libwebpmux 要一起链

## 已知约束

- FFmpegKit 8.1.7 构建无 libx264（仅 mpeg4/h264_mediacodec）；软件 H.264 已由 Media3 取代
- MP4→MP4 自身压缩不在产品矩阵内（`canConvertLocallyTo` 拒绝同格式）

## 下一阶段引擎（未做）

- 图片：AVIF=libavif；HEIC=libheif 精简 codec
- PDF 高速渲染可选 MuPDF（注意授权）
- Office / 压缩包 / 字幕按清单分引擎，不塞进 FFmpeg

完整条目见用户提供的《Android 本地多格式转换高效方案清单》。
