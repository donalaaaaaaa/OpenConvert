# OpenConvert 1.0 后续开发计划书

## 一、项目概述

OpenConvert 是一款面向 Android 平台的完全本地文件转换工具，核心原则为：

> **Your files never leave your device.**

项目坚持零上传、零云端依赖，并且不申请网络权限，所有文件转换、处理与任务管理均在设备本地完成。

目前项目已经具备较完整的本地文件处理能力，包括：

- 图片格式转换与编辑
- 视频、音频本地转码
- PDF 合并、拆分、旋转、图片互转
- ZIP、TAR、GZIP、BZIP2 等压缩与解压
- DOCX、DOC、PPTX、PPT、XLSX、XLS 转 PDF
- 批量转换、暂停、继续、取消
- 大文件保护与异常恢复
- 本地任务管理

现阶段核心能力已经基本形成，因此后续开发重点由“增加更多格式”逐渐转向：

**稳定性、性能、智能调度、交互体验和产品化。**

---

# 二、当前项目基础

## 2.1 已实现功能

### 图片处理

当前支持：

- JPG
- PNG
- WEBP
- AVIF
- HEIC
- GIF
- BMP
- TIFF

并已具备：

- 分辨率缩放
- 比例裁剪
- 图片旋转
- 图片翻转
- EXIF / GPS / 元数据清除

底层采用 `libvips 8.18.5 + BitmapFactory`。

### 音视频转换

支持：

- MP4
- MOV
- MKV
- WEBM
- AVI
- MP3
- AAC
- WAV
- FLAC
- M4A
- OGG
- OPUS

当前采用：

- Media3 Transformer
- MediaCodec
- LiTr
- FFmpegKit

并已经支持部分同编码流直接复制。

### PDF

已实现：

- 图片生成 PDF
- PDF 提取图片
- PDF 合并
- PDF 拆分
- 页面删除
- 页面旋转

核心采用：

- PdfBox-Android
- PdfRenderer

### Office

当前已经支持：

- DOCX → PDF
- DOC → PDF
- PPTX → PDF
- PPT → PDF
- XLSX → PDF
- XLS → PDF

通过内置 LibreOfficeKit 完成本地离线渲染。

### 批量转换

项目已经支持：

- 多文件批量处理
- 动态并发调度
- 大文件串行
- 视频任务控流
- 暂停
- 恢复
- 批量取消

---

# 三、后续总体目标

OpenConvert 下一阶段正式进入：

# OpenConvert 1.0 产品化阶段

开发目标由单纯“实现转换能力”调整为：

1. 提升复杂场景稳定性
2. 建立智能转换决策机制
3. 改进文件驱动式 UI
4. 完善后台任务系统
5. 建立转换预设
6. 完善 PDF 工具能力
7. 提升 Office 兼容性
8. 建立性能 Benchmark
9. 控制 APK / AAB 体积
10. 完善 Release 与项目展示体系

最终目标是形成一套：

**稳定、离线、智能、高性能、可批量处理的大型 Android 本地文件转换中心。**

---

# 四、第一阶段：稳定性与异常场景测试

## 4.1 阶段目标

首先停止大规模增加新格式，对当前已有转换能力进行全面稳定性测试。

重点解决：

- App 强制退出
- Android 系统杀后台
- 内存不足
- 存储空间不足
- 输入文件损坏
- 输入文件被删除
- SAF URI 失效
- 输出目录失效
- 超大文件
- 批量任务异常
- 转换中断恢复

当前项目已经存在：

- StorageGuard
- BoundedIo
- ConversionRecovery

等基础保护机制。

下一阶段重点是验证这些机制在真实极端场景中的可靠性。

---

## 4.2 建立测试文件库

建立统一测试目录：

```text
test-assets/

├── image/
│   ├── jpg/
│   ├── png/
│   ├── webp/
│   ├── heic/
│   ├── avif/
│   └── corrupted/
│
├── video/
│
├── audio/
│
├── pdf/
│
├── office/
│
└── archive/
```

每种格式至少准备：

- 标准文件
- 空文件
- 损坏文件
- 超大文件
- 特殊编码文件
- 极端分辨率文件
- 扩展名错误文件
- MIME 与扩展名不一致文件

---

## 4.3 Android 环境测试

重点验证：

- Android 12
- Android 13
- Android 14
- Android 15
- Android 16

测试不同设备内存：

- 4 GB
- 6 GB
- 8 GB
- 12 GB+

并覆盖不同 SoC：

- Snapdragon
- MediaTek
- Exynos

---

## 4.4 大文件测试

建立：

```text
100 MB
500 MB
1 GB
2 GB
4 GB
```

测试梯度。

记录：

- 是否 OOM
- 转换时间
- 临时空间
- 峰值内存
- CPU 使用率
- 输出文件完整性

---

## 第一阶段验收标准

- 不因单个坏文件导致整个批量任务崩溃
- 系统杀进程后状态可以正确恢复
- 2 GB 以上文件保持流式处理
- 存储不足能够提前拦截
- 错误能够向用户显示具体原因
- 不出现任务永久卡在“处理中”

---

# 五、第二阶段：ConversionPlanner 智能调度系统

## 5.1 开发目的

当前架构已经形成：

```text
FileTypeDetector
        ↓
ConversionGraph
        ↓
ConverterRegistry
```

并由不同 Converter 分别处理图片、视频、PDF、音频与 Office。

下一步在其上增加：

# ConversionPlanner

负责在真正执行转换之前完成策略规划。

---

## 5.2 ConversionPlanner 架构

```text
输入文件

   ↓

FileTypeDetector

   ↓

ConversionGraph

   ↓

ConversionPlanner

   ↓

分析：
格式
编码
分辨率
文件大小
设备能力
当前负载

   ↓

选择最佳转换方案

   ↓

ConverterRegistry
```

---

## 5.3 视频智能决策

例如：

```text
MKV H264
    ↓
转换 MP4
    ↓
视频编码相同
    ↓
Stream Copy
```

无需重新编码。

如果：

```text
HEVC
 ↓
H264
```

则：

```text
检测 MediaCodec

       ↓

硬件支持？

YES           NO
 ↓             ↓

MediaCodec    FFmpeg
```

---

## 5.4 图片智能策略

根据图片大小选择：

```text
普通图片
 ↓
libvips

异常格式 / 解码失败
 ↓
BitmapFactory
```

同时根据设备剩余内存控制：

- tile size
- 并发数量
- 缓冲区
- 临时空间

---

## 第二阶段验收标准

ConversionPlanner 能够自动决定：

- 转换还是 Stream Copy
- 软件编码还是硬件编码
- 使用哪个 Converter
- 并发还是串行
- 是否需要预留临时空间

---

# 六、第三阶段：首页 UI 2.0

## 6.1 UI 核心理念

OpenConvert 从：

**工具驱动**

升级为：

# 文件驱动

用户首先选择文件，系统随后告诉用户这个文件可以做什么。

---

## 6.2 首页结构

```text
OpenConvert

┌────────────────────┐
│      选择文件       │
└────────────────────┘

最近使用

图片 → JPG
视频 → MP4
Word → PDF
PDF → 图片

────────────

图片
视频
音频
PDF
文档
压缩

────────────

最近任务
```

---

## 6.3 文件自动识别

例如用户选择：

```text
IMG_2856.HEIC
```

系统自动显示：

```text
IMG_2856.HEIC

转换为

JPG
PNG
WEBP
PDF

图片工具

裁剪
缩放
旋转
翻转
删除 EXIF
```

核心仍由现有 FileTypeDetector 和 ConversionGraph 提供能力判断。

---

# 七、第四阶段：任务中心 2.0

升级现有任务管理。

任务中心划分：

```text
正在运行

等待中

暂停

失败

已完成
```

---

## 7.1 单任务卡片

显示：

```text
movie.mkv

MKV → MP4

████████░░ 82%

速度：41 MB/s
剩余：1m 23s
引擎：MediaCodec
```

---

## 7.2 完成任务

显示：

```text
report.docx
      ↓
report.pdf

输入：18.4 MB
输出：7.1 MB

耗时：13 秒
```

提供：

- 打开
- 分享
- 查看目录
- 删除记录

---

## 7.3 错误任务

错误信息禁止仅显示：

```text
Conversion failed
```

需要映射为用户能理解的原因，例如：

```text
存储空间不足

需要：2.1 GB
当前剩余：1.3 GB
```

或者：

```text
视频编码不受当前设备硬件支持

已尝试：
MediaCodec

可切换：
FFmpeg 软件编码
```

---

# 八、第五阶段：转换预设系统

建立：

# ConversionPreset

用户可以保存转换参数。

---

## 8.1 图片预设

例如：

### 微信发送

```text
格式：JPEG
质量：85%
最长边：1920
删除 EXIF：是
```

### 网页图片

```text
格式：WEBP
质量：80%
```

### 头像

```text
比例：1:1
尺寸：1024×1024
格式：JPEG
```

---

## 8.2 视频预设

### 小体积

```text
720P
H264
AAC
```

### 高清

```text
1080P
H264
AAC
```

### 高质量

```text
1080P
HEVC
AAC
```

---

## 8.3 批量应用

支持：

```text
选择 50 张图片

      +

微信发送预设

      ↓

全部自动处理
```

---

# 九、第六阶段：PDF 工具增强

现有 PDF 功能继续扩展。

优先开发：

## P1

- PDF 压缩
- PDF 页面排序

## P2

- PDF 页面尺寸调整
- PDF 元数据删除
- PDF 页码
- PDF 图片压缩

## P3

- PDF 水印
- PDF 密码保护

---

# 十、第七阶段：Office 兼容性优化

Office 不再优先增加格式，而是提高转换质量。

重点测试：

### Word

- 中文字体
- 表格
- 页眉页脚
- 图片
- 分页
- 特殊字号

### PowerPoint

- 字体
- 图片
- 表格
- 图表
- 特殊版式
- 页面比例

### Excel

- 多 Sheet
- 横向页面
- 打印区域
- 合并单元格
- 大型表格
- 图表

---

## Office 验收目标

保证：

- 基本版式不偏移
- 中文文字不乱码
- 表格不严重错位
- 图片不丢失
- 页面尺寸正确

---

# 十一、第八阶段：性能 Benchmark

建立：

# OpenConvert Benchmark

所有主要 Converter 都输出统一性能指标。

---

## 11.1 测试指标

记录：

```text
总耗时

转换速度

峰值内存

CPU 使用率

输入文件大小

输出文件大小

压缩率

使用引擎

硬件编码状态
```

---

## 11.2 Benchmark 示例

| 类型 | 输入 | 输出 | 引擎 | 时间 |
|---|---|---|---|---:|
| HEIC | 20MB | JPEG | libvips | 1.2s |
| MKV | 1GB | MP4 | Stream Copy | 23s |
| HEVC | 1GB | H264 | MediaCodec | 4m |
| DOCX | 25MB | PDF | LibreOfficeKit | 8s |

最终可形成项目性能报告。

---

# 十二、第九阶段：安装包体积优化

目前项目集成：

- libvips
- FFmpegKit
- LibreOfficeKit
- PdfBox
- Media3
- LiTr
- Commons Compress

因此 Release 包体积将逐渐成为重点问题。

---

## 12.1 模块拆分

逐步调整为：

```text
app

├── core
├── image-engine
├── media-engine
├── pdf-engine
├── office-engine
└── archive-engine
```

---

## 12.2 Release 优化

继续完善：

- R8
- Resource Shrink
- ABI Split
- arm64-v8a
- Native SO Strip
- Android App Bundle

当前 Release 构建已经启用 R8、资源缩减和 arm64-v8a ABI Split。

下一步重点记录：

```text
Debug APK 大小

Release APK 大小

AAB 大小

安装后占用

各 Native Library 体积
```

---

# 十三、第十阶段：产品发布准备

完成产品化最后整理。

包括：

## README

新增：

- 项目截图
- 功能 GIF
- 架构图
- Benchmark
- 支持格式表
- 隐私说明
- 开发环境
- 构建方式

---

## Release

输出：

```text
OpenConvert-v1.0.0-arm64.apk

OpenConvert-v1.0.0.aab
```

---

## GitHub Release

提供：

- APK
- ChangeLog
- 功能截图
- 安装说明
- 已知问题

---

# 十四、版本规划

## v0.9

重点：

**稳定性。**

完成：

- 异常测试
- 大文件测试
- ConversionPlanner

---

## v0.95

重点：

**交互体验。**

完成：

- 首页 UI 2.0
- 文件驱动式操作
- 任务中心 2.0

---

## v0.97

重点：

**效率。**

完成：

- Preset 系统
- 智能批量转换
- PDF 增强

---

## v0.99

重点：

**兼容性和性能。**

完成：

- Office 兼容性
- Benchmark
- APK 体积治理

---

# v1.0

正式发布：

# OpenConvert 1.0

目标：

**Android 完全本地万能文件转换中心。**

---

# 十五、开发优先级

| 优先级 | 工作 |
|---|---|
| P0 | 异常恢复测试 |
| P0 | 大文件稳定性 |
| P0 | ConversionPlanner |
| P0 | 首页 UI 2.0 |
| P1 | 任务中心 2.0 |
| P1 | Preset 系统 |
| P1 | PDF 压缩 |
| P1 | PDF 页面排序 |
| P1 | Office 兼容性 |
| P2 | Benchmark |
| P2 | APK / AAB 体积治理 |
| P3 | 新文件格式 |

---

# 十六、建议开发顺序

实际开发按照以下顺序执行：

```text
01
稳定性测试体系

↓

02
ConversionPlanner

↓

03
首页 UI 2.0

↓

04
任务中心 2.0

↓

05
Preset 系统

↓

06
PDF 压缩 + 页面排序

↓

07
Office 兼容优化

↓

08
OpenConvert Benchmark

↓

09
APK / AAB 体积治理

↓

10
Release 1.0
```

---

# 十七、最终阶段目标

OpenConvert 1.0 不再以“支持多少格式”作为主要评价指标。

核心指标调整为：

### 稳定

超大文件和异常文件不会导致 App 崩溃。

### 快

能够优先 Stream Copy 和硬件编码。

### 智能

ConversionPlanner 自动选择最佳转换方案。

### 简单

用户选择文件以后即可看到所有可执行操作。

### 安全

继续保持：

**零网络权限、零上传、完全本地。**

### 可持续

建立模块化 Converter 架构、自动化测试与 Benchmark，为后续 1.x 版本继续迭代提供基础。

最终将 OpenConvert 建设为一个真正具备产品完整度的：

# Android Offline Universal File Conversion Center