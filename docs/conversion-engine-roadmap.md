# Android 本地多格式转换高效方案清单

后续引擎优化按此文件执行。速度优先级：

`Remux/Copy → MediaCodec/Media3 → 专用 Native → 多线程软件编码 → FFmpeg 兜底`

不要把 FFmpeg 当所有格式的主引擎。FFmpegKit 仅作过渡兼容层。

## 当前已落地（对照）

- **统一原则 §一**：所有音视频任务转换前先用 Android `MediaExtractor` 探测真实编码/码率，再决定 Remux / 拷流 / 硬件转码 / 软件兜底（不用 ffprobe，避免与 FFmpeg 抢 SAF fd）
- 视频：能拷流则 Remux；MP4 压缩走 `h264_mediacodec`；**MP4→WEBM 默认 LiTr + MediaCodec VP8 + Opus**，禁止默认 VP9
- **音频 §3**：同编码转换（M4A↔AAC、MP3→MP3、FLAC→FLAC、WAV→WAV）走 `demux → -c:a copy` 直拷换容器，不重新编码；其余按目标重新编码
- 任务：WorkManager + 前台服务，不在 Compose 里跑转码
- 大文件：流式读写；ARM64-v8a
- PDF：PdfBox-Android + 系统 PdfRenderer；合并用 setupMixed 内存设置
- **图片→PDF §8**：JPEG 且无 EXIF 旋转时 `JPEGFactory.createFromStream` 直接嵌入压缩流，不解码、不重新压缩（更快、无损、体积更小、内存更低）；其他格式走 `LosslessFactory` 位图路径
- 图片互转：当前仍是 BitmapFactory（后续换 libvips）

## 下一阶段引擎（未做）

- 视频主引擎迁 Media3 Transformer；FFmpeg 自编译 .so 仅兜底 AVI/MKV/TS/冷门编码
- 图片：libvips + JNI；AVIF=libavif；HEIC=libheif 精简 codec
- PDF 高速渲染可选 MuPDF（注意授权）
- Office / 压缩包 / 字幕按清单分引擎，不塞进 FFmpeg

完整条目见用户提供的《Android 本地多格式转换高效方案清单》。
