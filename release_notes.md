# OpenConvert v1.0.0 正式版发布

OpenConvert 是一款专为 Android 打造的 **100% 离线、零云端依赖、极致隐私安全** 的万能多格式文件转换与处理中心。

> **核心原则**：Your files never leave your device. （应用在 Manifest 中彻底不声明任何网络权限，保证文件 100% 留存在本地设备处理）。

---

## 🌟 核心功能特性

### 1. 📄 PDF 2.0 工业级工具箱
- **PDF 页面可视化管理器**：网格缩略图异步秒开（支持 500+ 超大页数）、页面拖拽重排、顺/逆时针任意旋转与批量删除导出。
- **PDF 智能压缩**：图像流降采样与 JPEG 重编码，提供高质量 (300 DPI)、平衡推荐 (200 DPI)、极限压缩 (150 DPI) 三档压缩。
- **PDF 加密与解密**：支持 128-bit/256-bit AES 文档加密与已知密码 PDF 的离线解密导出。
- **PDF 边距裁剪**：自定义四周 Point 边距切除多余白边。
- **PDF 元数据管理**：查看与编辑标题 (Title)、作者 (Author)、主题 (Subject) 与关键词 (Keywords)。
- **图片与 PDF 互转**：多图合并生成 PDF、PDF 导出高清图片序列、PDF 合并与按范围拆分。

### 2. 🖼️ 图片全能转换与高级编辑
- **支持格式**：JPG, PNG, WEBP, AVIF, HEIC, GIF, BMP, TIFF
- **引擎架构**：`libvips 8.18.5` (C/SIMD 原生加速) + `BitmapFactory` 智能兜底。
- **高级处理**：尺寸缩放、比例裁剪、旋转翻转、隐私模式（EXIF / GPS 定位数据一键擦除）。

### 3. 🎬 视频与音频硬件转码
- **视频格式**：MP4, MOV, MKV, WEBM, AVI
- **音频格式**：MP3, AAC, WAV, FLAC, M4A, OGG, OPUS
- **引擎架构**：Android `Media3 Transformer / MediaCodec` 芯片硬件加速 + `LiTr` + `FFmpegKit`。
- **智能特性**：同编码智能流直拷 (`-c:v copy` / `-c:a copy`)、视频音频提取、硬件 H.264/VP8 转码。

### 4. 📑 Office 离线转换
- **格式支持**：DOCX, DOC, PPTX, PPT, XLSX, XLS → 高保真 PDF
- **内置引擎**：内置 `LibreOfficeKit` 原生离线渲染核心，开箱即用，无需额外下载资源包。

### 5. 📦 压缩包与归档
- **格式支持**：ZIP, TAR, TAR.GZ, GZIP, BZIP2
- **核心能力**：多文件打包压缩、多级压缩比调节、全格式本地解压还原。

### 6. ⚡ 转换预设体系与智能建议顾问
- **转换预设**：内置常用场景预设（原画、推荐、小体积、隐私擦除、无损母带等），支持自定义预设持久化存储。
- **智能建议**：纯离线规则引擎，根据文件特征与硬件能力智能推荐最佳格式与预估压缩率。

---

## 📱 安装包与校验信息

| 文件名 | 架构 | 文件大小 | SHA256 校验和 |
|---|---|---|---|
| `OpenConvert-v1.0.0-arm64-v8a.apk` | `arm64-v8a` | 106.8 MB | `E8F1A64B01B43B605FC54799858454B99135B2B7EE4C703A6FED0CEAD939DC9B` |

> 适用于 Android 8.0 (API 26) 及以上版本（在 Android 16 实测完美运行并通过 27/27 全套自动化系统测试）。
