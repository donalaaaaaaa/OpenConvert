# OpenConvert

**简体中文** | [English](README_EN.md)

完全本地的 Android 文件转换中心。

> **Your files never leave your device.**
>
> Manifest 不声明 `INTERNET`。没有账号，没有上传，没有云端队列。

<p align="center">
  <img src="docs/screenshots/01-home.png" width="180" alt="首页：选择文件与常用工具" />
  <img src="docs/screenshots/02-convert.png" width="180" alt="转换配置：目标格式与质量" />
  <img src="docs/screenshots/03-pdf-tools.png" width="180" alt="PDF 工具箱" />
  <img src="docs/screenshots/05-complete.png" width="180" alt="转换完成：体积对比与打开/分享" />
</p>

<p align="center">
  <a href="https://github.com/donalaaaaaaa/OpenConvert/releases">下载 Release</a>
  ·
  <a href="docs/install.md">安装说明</a>
  ·
  <a href="docs/known-issues.md">已知问题</a>
  ·
  <a href="docs/releases/">更新日志</a>
  ·
  <a href="CONTRIBUTING.md">贡献</a>
  ·
  <a href="SECURITY.md">安全</a>
</p>

---

## 选哪个包

| Edition | 内容 | Release APK | 给谁 |
|---|---|---|---|
| **Basic** | 图片、音视频、PDF、压缩包 | 以 [Releases](https://github.com/donalaaaaaaa/OpenConvert/releases) 实测为准 | 默认选择 |
| **Office** | Basic + DOCX/DOC/PPTX/PPT/XLSX/XLS → PDF | 同上 | 需要离线 Office |

两个包同一 `applicationId`、同一签名。Basic 可以原地覆盖升级到 Office，历史、预设和 SAF 授权会保留。详见 [安装说明](docs/install.md)。

---

## 能做什么

### 图片

JPG / PNG / WEBP / AVIF / HEIC / GIF / BMP / TIFF 读入；输出 JPG / PNG / WEBP。

- 缩放、比例裁剪、旋转、翻转
- 擦除 EXIF / GPS / 元数据
- 引擎：`libvips 8.18.5`，`BitmapFactory` 兜底

### 音视频

视频：MP4、MOV、MKV、WEBM、AVI  
音频：MP3、AAC、WAV、FLAC、M4A、OGG、OPUS

- Media3 Transformer / MediaCodec 硬编优先
- 同编码流拷贝（不重编码）
- 视频抽音轨
- LiTr VP8（无硬件则不能出 WEBM）
- 音频与 MP4 软件兜底走 FFmpegKit audio

### PDF

- 多图生成 PDF、PDF 导出图片
- 合并、按范围拆分
- 页面管理：缩略图、拖拽重排、旋转、删除导出
- 三档压缩（300 / 200 / 150 DPI）
- AES 128/256 加密与已知密码解密
- 边距裁剪、元数据编辑

### Office（仅 Office Edition）

DOCX / DOC / PPTX / PPT / XLSX / XLS → PDF，内置 LibreOfficeKit，不另下资源包。

### 压缩包

ZIP、TAR、TAR.GZ、GZIP、BZIP2、XZ、7Z：打包、多级压缩比、解压。

### 任务与预设

- 文件驱动首页：选完文件再看能做什么
- 批量、暂停、继续、取消；大文件串行、视频控流
- 12 个内置预设（微信图、头像、隐私分享、720P/1080P、无损母带等），可自定义
- ConversionPlanner 按能力图、编码、硬件、空间和并发槽位选路径
- 任务中心按运行 / 等待 / 暂停 / 失败 / 完成分组，展示引擎、耗时和结构化错误

---

## 支持格式

| 类别 | 输入 | 输出 | 引擎 |
|---|---|---|---|
| 图片 | JPG, PNG, WEBP, AVIF, HEIC, GIF, BMP, TIFF | JPG, PNG, WEBP | libvips + BitmapFactory |
| 视频 | MP4, MOV, MKV, WEBM, AVI | MP4, WEBM，以及音频格式 | Media3 / LiTr / FFmpegKit |
| 音频 | MP3, AAC, WAV, FLAC, M4A, OGG, OPUS | 同左互转 | FFmpegKit + 流拷贝 |
| PDF | PDF、多张图片 | PDF、JPG、PNG | PdfBox-Android + PdfRenderer |
| 文档 | DOCX, DOC, PPTX, PPT, XLSX, XLS | PDF | LibreOfficeKit（Office Edition） |
| 压缩包 | 文件/目录, ZIP, TAR, GZ, BZ2, XZ, 7Z | ZIP, TAR, GZIP, BZIP2, XZ, 7Z, 解压目录 | Commons Compress |

AVIF / HEIC / GIF / BMP / TIFF 是只读输入，不能作为图片输出格式。

---

## 架构

```mermaid
flowchart TD
    pick[SAF 选文件] --> detect[FileTypeDetector<br/>MIME / 扩展名 / Magic]
    detect --> graph[ConversionGraph<br/>转换边 + 工具边]
    graph --> planner[ConversionPlanner]
    planner --> hw[HardwareFacts]
    planner --> space[StorageGuard]
    planner --> exec[ConversionExecutor]
    exec --> registry[ConverterRegistry]
    registry --> image[ImageConverter]
    registry --> media[MediaConverter]
    registry --> pdf[Pdf converters]
    registry --> office[OfficeConverter]
    registry --> archive[ArchiveConverter]
    image --> vips[libvips JNI]
    image --> bmp[BitmapFactory]
    media --> m3[Media3 / MediaCodec]
    media --> litr[LiTr VP8]
    media --> ffmpeg[FFmpegKit]
    pdf --> pdfbox[PdfBox-Android]
    office --> lokit[LibreOfficeKit]
    archive --> compress[Commons Compress]
    exec --> wm[WorkManager + Room]
    wm --> bench[BenchmarkCollector]
    wm --> ui[任务中心 / 历史]
```

生产路径会先探测真实音视频编码，把同一份 `StreamCodecs` 和 `ConversionPlan` 交给执行器。Benchmark 记录引擎、是否流拷贝、硬编、峰值内存和压缩率。

---

## Benchmark 与体积

设置页可导出 Markdown 汇总或带 BOM 的 CSV。采集在设备本地，不会上传。

| 指标 | Basic | Office |
|---|---:|---:|
| Release APK（2026-08-20 基线） | 35.39 MiB | 105.29 MiB |
| Release AAB（2026-08-20 基线） | 65.29 MiB | 135.20 MiB |
| PHY110 估算下载 | 34.74 MiB | 103.60 MiB |
| PHY110 即时 code | 107.23 MiB | 353.99 MiB |
| 解压 native | 44.56 MiB | 221.15 MiB |

现网安装包大小以 [GitHub Releases](https://github.com/donalaaaaaaa/OpenConvert/releases) 的 `BUILD_INFO.txt` 为准，不要抄这张基线表。Office 体积几乎全部来自 `liblo-native-code.so`（未压缩约 171 MiB）。完整拆分见 [`docs/apk-size-baseline-2026-08-20.md`](docs/apk-size-baseline-2026-08-20.md)。

PHY110 / Android 16 上：Office 全量 instrumented **61/61**，Basic → Office 覆盖升级后 DOCX/PPTX/XLSX → PDF 通过。

---

## 隐私

- 不申请 `INTERNET`
- `allowBackup=false`，不走系统云备份
- 输入输出只走 SAF，不扫整盘
- 转换前按约 2 倍体积预检可用空间
- 大文件走流式拷贝，避免一次性读进内存

---

## 构建

环境：JDK 17、Android SDK 36、NDK 27+（只在重编 JNI 时需要）。

```powershell
# 单元测试（两个 Edition）
.\gradlew.bat testBasicDebugUnitTest testOfficeDebugUnitTest

# 签名 Release APK / AAB
.\gradlew.bat assembleBasicRelease assembleOfficeRelease
.\gradlew.bat bundleBasicRelease bundleOfficeRelease

# 已连接真机时跑 instrumented
.\gradlew.bat connectedOfficeDebugAndroidTest
```

签名凭据放在已 gitignore 的 `signing.properties`，或使用 `OPENCONVERT_STORE_PASSWORD` / `_KEY_ALIAS` / `_KEY_PASSWORD`。模板见 `signing.properties.example`。缺凭据时产出 unsigned 包。

发布产物不要提交进仓库，挂到 GitHub Releases。当前打好的包与校验和在发行说明里。

更细的阶段记录：

- [阶段 06 产品化](docs/phase6-v1.0-productization.md)
- [阶段 07 Benchmark / 预设](docs/phase7-benchmark-and-batch-presets.md)
- [阶段 08 双 Edition](docs/phase8-office-editions.md)
- [阶段 11 覆盖升级](docs/phase11-edition-upgrade.md)
- [阶段 12 Release 收尾](docs/phase12-release.md)

---

## 许可证

- 本项目：**Apache License 2.0**（见 [LICENSE](LICENSE)）
- 第三方组件、版本、LGPL/MPL 源码地址：[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- 应用内：设置 → 关于 → 开源许可证
