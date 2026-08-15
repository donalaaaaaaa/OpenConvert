# OpenConvert

OpenConvert 是一款专为 Android 打造的**完全本地、高隐私、高性能万能文件转换中心**。

> **核心原则：Your files never leave your device.** （你的文件始终留在设备本地，0 上传、0 云端依赖、不申请联网权限）。

---

## 🌟 核心功能一览

### 1. 图片转换与高级编辑
- **格式支持**：JPG, PNG, WEBP, AVIF, HEIC, GIF, BMP, TIFF
- **引擎架构**：`libvips 8.18.5` (C/SIMD 原生加速) + `BitmapFactory` 智能兜底
- **高级能力**：
  - 分辨率缩放（25% / 50% / 75% / 原始 / 自定义宽高）
  - 比例裁剪（自由 / 1:1 / 4:3 / 3:2 / 16:9 / 9:16）
  - 旋转翻转（90° / 180° / 270° / 水平翻转 / 垂直翻转）
  - 隐私保护：EXIF / GPS / 全元数据擦除模式

### 2. 视频与音频硬件转码
- **视频格式**：MP4, MOV, MKV, WEBM, AVI
- **音频格式**：MP3, AAC, WAV, FLAC, M4A, OGG, OPUS
- **引擎架构**：Android `Media3 Transformer / MediaCodec` 硬件加速 + `LiTr` + `FFmpegKit` 兜底
- **智能特性**：同编码智能直拷 (`-c:a copy` / `-c:v copy`)、视频音频提取、硬件 H.264 / VP8 编码

### 3. PDF 工业级工具箱
- **图片 ↔ PDF**：多图合并生成 PDF（支持拖拽排序、边距调整、A4/Letter/自适应方向）、PDF 提取为高清图片序列
- **PDF 操作**：
  - PDF 多文件合并（拖拽排序）
  - PDF 自定义拆分（单页 / 范围拆分）
  - PDF 页面删除（可视化勾选删除）
  - PDF 页面旋转（90° / 180° / 270°）

### 4. 压缩与归档工具
- **格式支持**：ZIP, TAR, TAR.GZ, GZIP, BZIP2
- **核心能力**：多文件打包压缩、多级压缩比调节、全格式本地解压还原

### 5. Office 离线转换
- **格式支持**：DOCX, DOC, PPTX, PPT, XLSX, XLS → 高保真 PDF
- **内置引擎**：完整内置 `LibreOfficeKit` 离线渲染核心，开箱即用，无需额外下载解压

### 6. 批量并发转换系统
- 一键选取多达数百个文件进行批量转换
- 动态并发调度闸门（小文件并发 / 大文件串行 / 视频控流），彻底规避 OOM 与发热过载
- 支持暂停、继续与批量取消

---

## 📊 支持格式矩阵

| 类别 | 输入格式 | 输出格式 | 核心引擎 |
|---|---|---|---|
| **图片** | JPG, PNG, WEBP, AVIF, HEIC, GIF, BMP, TIFF | JPG, PNG, WEBP, PDF | `libvips 8.18.5` (JNI) + `BitmapFactory` |
| **视频** | MP4, MOV, MKV, WEBM, AVI | MP4, WEBM, MP3, AAC, WAV, FLAC, M4A, OGG, OPUS | `Media3` (MediaCodec 硬件) + `LiTr` + `FFmpegKit` |
| **音频** | MP3, AAC, WAV, FLAC, M4A, OGG, OPUS | MP3, AAC, WAV, FLAC, M4A, OGG, OPUS | `FFmpegKit` + 同编码直拷流提取 |
| **PDF** | PDF, 多张图片 | PDF, JPG, PNG, WEBP | `PdfBox-Android` + `PdfRenderer` |
| **文档** | DOCX, DOC, PPTX, PPT, XLSX, XLS | PDF | `LibreOfficeKit` 离线 Native 引擎 |
| **压缩包** | 文件/文件夹, ZIP, TAR, GZ, BZ2 | ZIP, TAR, TAR.GZ, GZIP, BZIP2, 解压目录 | `Apache Commons Compress` |

---

## 🏛️ 技术架构

```text
                     OpenConvert
                          │
                          ▼
                   FileTypeDetector (MIME / Ext / Magic Bytes)
                          │
                          ▼
                   ConversionGraph (能力路由与合法校验)
                          │
                          ▼
                  ConverterRegistry
                          │
        ┌─────────────────┼──────────────────┐
        │                 │                  │
        ▼                 ▼                  ▼
  ImageConverter     VideoConverter      PdfConverter
        │                 │                  │
        ▼                 ▼                  ▼
    libvips          Media3/Codec        PdfBox/Renderer
        │                 │
        ▼                 ▼
   BitmapFactory        FFmpeg
                          │
              ┌───────────┴───────────┐
              ▼                       ▼
        AudioConverter         OfficeConverter
              │                       │
           FFmpegKit            LibreOfficeKit
```

---

## 🔒 隐私与系统安全

- **零网络依赖**：应用 Manifest 中**不申请 `INTERNET` 权限**，从系统底层彻底杜绝任何网络上传。
- **存储保护 (`StorageGuard`)**：转换前根据文件体积与临时空间需求进行 2 倍安全空间预检，空间不足提前拦截。
- **大文件保护 (`BoundedIo`)**：流式分块拷贝与内存受限加载，100MB ~ 4GB 大文件稳定运行不 OOM。
- **孤儿任务恢复 (`ConversionRecovery`)**：系统杀进程或异常关机后，重启应用自动同步 Room 状态并清理残余缓存。

---

## 🛠️ 构建与测试

### 环境要求
- JDK 17
- Android SDK 36 (Build-Tools 36.0.0)
- NDK 27+ (用于 C/JNI 模块)

### 快速命令

```powershell
# 1. 运行全套单元测试
.\gradlew.bat testDebugUnitTest

# 2. 构建 Release 签名安装包（启用 R8 混淆 + 资源缩减 + arm64-v8a ABI Split）
.\gradlew.bat assembleRelease

# 3. 连接 Android 真机运行全套自动化测试
.\gradlew.bat connectedDebugAndroidTest
```

### 📱 真机验证指标
在 **OnePlus PHY110 (Android 16)** 真机上实测通过：
- **27/27 全套自动化用例 100% 通过**（包含真实 DOCX/PPTX/XLSX 转 PDF、图片裁剪/旋转/去元数据、PDF 合并/删除/旋转、音视频硬件编码与大文件稳定性）。

---

## 📄 开源协议说明

- 本项目开源核心遵循 **Apache License 2.0**
- `LibreOfficeKit` 遵循 **MPL-2.0**
- `libvips` 遵循 **LGPL-2.1+**
- `FFmpegKit` 遵循 **LGPL-3.0+**


