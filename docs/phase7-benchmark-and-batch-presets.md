# Phase 7 – 批量预设与 Benchmark 生产接线（2026-08-20）

设备：OnePlus PHY110 · Android 16 / API 36 · JDK17 + SDK36

对应《OpenConvert 1.0 后续开发计划书》阶段 05（批量应用收尾）、阶段 08（指标采集起步），
并修正阶段 02 的一处生产接线缺口。

## 阶段 05：批量应用预设

- `BatchDraft` 保存预设 id、最长边、固定宽高、裁剪比例与去元数据选项。
- 批量配置页展示可用预设；应用后所有子任务的 `ConversionPayload` 都携带完整参数，
  不再出现“只换目标格式、尺寸约束丢失”。
- 筛选同时校验输入类别与共同目标格式。视频可提取 MP3，但不会因此误显示 AUDIO 类的
  “MP3 音频”预设。
- 新增 3 项 JVM 回归测试覆盖同目标跨类别、混合类别和非共同目标。

## 修正：Planner 真正驱动音视频生产路径

此前 `ConversionExecutor` 创建 `PlanRequest` 时没有传 `StreamCodecs`，所以真实设备上 Planner
无法识别 remux / audio copy；同时 `MediaConverter` 会重新独立规划，统一 Planner 的方案没有
进入执行层。

现改为：

```
MediaExtractor 编码探测
        ↓ 同一份 StreamCodecs
ConversionPlanner ──→ ConversionPlan
        ↓                    ↓
        └──── ConverterRegistry ────→ MediaConverter
```

- 执行器先用 `MediaConverter.inspectForPlanning` 读取真实音视频编码。
- `ConverterRegistry` 把 `ConversionPlan` 与同一次探测结果交给 `MediaConverter`。
- `MediaConverter` 的 bitrate 仍按媒体事实计算，但执行模式以统一 Planner 为准；无硬编时不会
  先盲试硬件路径。
- WEBM 只有在方案确为 `LITR_VP8` 时才调用 LiTr；兼容编码的 remux 不再被提前重编码。
- 真机生成 MPEG4+AAC 测试视频，以 MOV→MP4 生产路径确认 Benchmark 中
  `engine=FFMPEG_KIT`、`streamCopy=true`、`hardwareEncode=false`。

## 阶段 08：统一 Benchmark 指标

新增 `domain/benchmark/`：

- `BenchmarkRecord`：任务、路由、进/出体积、耗时、吞吐、压缩率、引擎、流拷贝、硬编、
  峰值内存、成功状态与记录时间。
- `BenchmarkMemorySampler`：转换期间每 100 ms 采样 Java 已用堆 + native heap，避免只看首尾
  而漏掉短时峰值。
- `BenchmarkCollector`：写入 `files/benchmark/records.jsonl`，512 KB 自动轮转一代；使用进程级
  共享锁，多个批量任务的 Collector 实例不会交错 JSON 行或竞争轮转。
- SINGLE 成功、引擎失败、Planner 提前拒绝与缺输出目标都会留记录；采集自身失败绝不改变
  转换结果。
- 工具类任务暂不写入：多输入归档、PDF 页面子集的“输入体积”口径不同，直接混入会产生
  不可比较的吞吐数据。

CPU 指标暂不采集。Android 可获得的稳定值是整个 App 进程（包含 Compose、Room、
WorkManager），不是单个并行转换任务；把它写成任务 CPU 会误导。待后续建立串行、隔离的
benchmark runner 后再补。

### 汇总报告与用户导出

- `BenchmarkReport` 把轮转文件和当前 JSONL 合并、按时间排序，并生成 Markdown / CSV。
- Markdown 包含总体成功率、耗时、吞吐、输入/输出体积，以及按「源格式 → 目标格式 + 引擎」
  聚合的次数、成功率、平均耗时、平均吞吐和平均压缩率。
- CSV 保留逐任务原始字段，写入 UTF-8 BOM，便于 Excel 直接识别中文；所有字段按 RFC 风格转义。
- 设置页新增「性能 Benchmark」入口，显示当前记录数，并通过系统文件选择器导出 `.md` / `.csv`；
  App 不申请存储权限，也不会自动上传数据。
- JSONL 解码采用向后兼容策略：损坏行或未来版本未知枚举会跳过，不会让整个报告导出失败。

### 阶段 09：PHY110 的 Bundle 分发体积

使用 bundletool 1.18.1 和真机导出的设备规格（arm64-v8a、xxxhdpi、zh-CN、API 36）生成
设备专用 APK Set。`get-size total` 的结果为 **108,634,276 字节（103.60 MiB）**。

相较通用 arm64 Release APK 的 105.29 MiB，仅减少约 1.69 MiB。原因是 103.60 MiB 的下载体积
仍包含必需的 `base-arm64_v8a.apk` 和 `base-master.apk`；Locale / density split 很小，无法消除
LibreOfficeKit、FFmpegKit 等主 native 负担。详见 `docs/apk-size-baseline-2026-08-20.md`。

同签名 Release APK 覆盖安装后，Android 16 报告即时 `code` 占用 353.99 MiB，其中解压后的
native 库为 221.15 MiB；这进一步确认 Office 引擎拆分的优先级。

## 验证

| 项目 | 结果 |
|---|---|
| 强制重跑 JVM 单测 | 218/218 通过（40 类，0 失败） |
| Debug APK | `assembleDebug` 通过 |
| Benchmark 报告/轮转真机定向测试 | 9/9 通过 |
| MediaConverter / Media3 / Benchmark 回归 | 12/12 通过 |
| Planner 流拷贝生产路径 | 3/3 通过（同组含图片成功/失败采集） |
| 全量真机 instrumented | 61/61 通过，0 跳过 / 0 失败 |
| Release APK / AAB | `assembleRelease`、`bundleRelease` 均通过；APK 签名验证通过 |
| PHY110 Bundle 下载体积 | bundletool：103.60 MiB |
| PHY110 Release 即时安装代码占用 | 353.99 MiB（native lib 221.15 MiB） |

## 下一步

> 2026-08-21：阶段 09 的 Basic / Office 双 Edition 已落地，见
> `docs/phase8-office-editions.md`。以下顺序已更新。

1. 阶段 04 收尾：任务卡显示实际执行引擎（含 fallback 后的最终引擎，而不只是计划引擎）。
2. 阶段 07：建立中文字体、复杂表格、页眉页脚、多 Sheet 等 Office 保真度素材矩阵。
3. 阶段 09 验收：Basic → Office 同签名覆盖升级的数据与 SAF 权限保留。
4. 阶段 08 后续：串行隔离 benchmark runner、CPU 指标和工具类多输入任务的独立统计口径。
