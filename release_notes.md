# OpenConvert v1.0.0

100% 离线的 Android 文件转换中心。文件不会离开设备，应用不声明网络权限。

> 这是 1.0 产品化收尾版：双 Edition、文件驱动首页、任务中心、预设、Planner、Benchmark。  
> 安装步骤见 [`docs/install.md`](docs/install.md)，限制见 [`docs/known-issues.md`](docs/known-issues.md)。

<p align="center">
  <img src="docs/screenshots/01-home.png" width="160" alt="首页" />
  <img src="docs/screenshots/02-convert.png" width="160" alt="转换" />
  <img src="docs/screenshots/03-pdf-tools.png" width="160" alt="PDF 工具" />
  <img src="docs/screenshots/05-complete.png" width="160" alt="完成" />
</p>

## 下载

| 文件 | 用途 | 大小 | SHA256 |
|---|---|---:|---|
| `OpenConvert-v1.0.0-basic-arm64-v8a.apk` | 默认安装包 | 35.39 MiB | `096E5B414125EAEA8DF685559F5DA08C2D536BB10D9F0E7515129682289E304F` |
| `OpenConvert-v1.0.0-office-arm64-v8a.apk` | 含 Office → PDF | 105.29 MiB | `04C3A9254930A3DED8FD7A408BAB7D771A050F36C2B02EFEEF14686C69285050` |
| `OpenConvert-v1.0.0-basic.aab` | Play / 商店上传 | 65.29 MiB | `2DE39539313FEF80669D20E2A9D0305F6F64181D32C5218CB33A9616C045EA61` |
| `OpenConvert-v1.0.0-office.aab` | Play / 商店上传 | 135.20 MiB | `9E4AB623161CAFFB56433AA0BCE45ACA78F824C06059BF74BE474C9609F761D4` |

签名证书：`CN=OpenConvert`，SHA-256 `65776a273239fa049ffadcf95dc0f8a70d890d787c2370707a9b0c19b2f1d6ee`。

只支持 **arm64-v8a**，Android 8.0+。PHY110 / Android 16 上 Office 全量 instrumented 61/61，Basic → Office 原地覆盖后 DOCX/PPTX/XLSX → PDF 通过。

2026-08-15 挂在本 tag 上的单体包 `OpenConvert-v1.0.0-arm64-v8a.apk`（106.8 MiB）已被双 Edition 取代，请改用上表两个 APK。

## 安装摘要

1. 只要图片 / 音视频 / PDF / 压缩包 → 装 Basic。还要 Word / PPT / Excel 转 PDF → 装 Office。
2. 用 `Get-FileHash -Algorithm SHA256` 核对上表。
3. 允许「安装未知应用」后打开 APK。通知权限需在系统弹窗点一次允许。
4. Basic 升 Office：`adb install -r OpenConvert-v1.0.0-office-arm64-v8a.apk`（两个包 versionCode 都是 100，图形安装器可能拒绝同版本覆盖）。

完整说明：[docs/install.md](docs/install.md)。

## ChangeLog

相对仓库早期能力，1.0 收尾补齐的是产品化，而不是再堆格式。

### 稳定性与调度

- 异常输入真机用例：空文件、截断、损坏主体、扩展名撒谎、输出失效
- ConversionPlanner 统一能力、编码模式、硬件事实、空间预算、并发槽位
- 生产路径先探测真实编码，再把同一份计划交给 MediaConverter
- 取消路径收敛到 `ConversionScheduler.cancel`，排队中取消也会收尾 Room

### 交互

- 文件驱动首页与能力面板
- 任务中心按状态分组，展示估算速度、剩余时间、实际引擎、结构化错误
- 12 个内置预设 + 自定义预设；批量页可套用已有预设

### PDF / Office

- 页面管理器、三档压缩、加密解密、裁剪、元数据
- Office 保真度素材：中文、复杂表、页眉页脚、多 Sheet、公式结果
- LibreOfficeKit 从默认包剥离：Basic 35.39 MiB，Office 105.29 MiB

### 度量与发布

- Benchmark JSONL + 设置页导出 Markdown / CSV
- R8、资源缩减、arm64 ABI split、签名外置
- 双 Edition 同签名覆盖升级已真机验收

## 已知问题（发行时）

- 仅 arm64-v8a
- 图片输出只有 JPG / PNG / WEBP
- Office 图表 / 嵌入对象 / 受保护文档未做内容级门禁
- 无 PDF 水印
- versionCode 相同，侧载覆盖请用 adb
- 当前 keystore 口令曾进 git 历史，视为已泄露；换证会切断覆盖升级
- 通知权限无法靠 adb 在所有机型上预授权

完整列表：[docs/known-issues.md](docs/known-issues.md)。
