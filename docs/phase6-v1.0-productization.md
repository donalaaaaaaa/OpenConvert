# Phase 6 – v1.0 产品化第 1 轮（2026-08-20）

设备：OnePlus PHY110 · Android 16 / API 36 · JDK17 便携 + SDK 36

对应《OpenConvert 1.0 后续开发计划书》§十六 建议开发顺序的 01（部分）与 02（全部）。

## 本轮完成

### 修复：能力声明双轨（阶段 02 的前置缺陷）

`FileFormat.canConvertLocallyTo` 原来对 IMAGE / AUDIO 走 `category == 同类 && this != target`
的旧规则，只有 VIDEO / ARCHIVE / OFFICE 才查 `ConversionGraph`。UI 的 `engineAvailable`
用的正是它，于是会放出 JPG→AVIF / JPG→HEIC / JPG→GIF / JPG→BMP / JPG→TIFF ——
这些边 Graph 没有、`ImageConverter` 也不支持，用户点下去只能拿到 registry 的失败。

- `canConvertLocallyTo` 收敛为 `ConversionGraph.canConvert` 的单一依据
- `ConversionGraph` 拆成两类边，语义不再混淆：
  - **转换边** `convertEdges`：一进一出，registry 有引擎，走 `ConversionKind.SINGLE`
  - **工具边** `toolEdges`：需要多文件输入 / 目录输出 / 页面参数，有专属 `ConversionKind`
  - 原先混在转换边里的 `JPG→PDF`、`PDF→JPG` 移入工具边（它们实际走
    `IMAGES_TO_PDF` / `PDF_TO_IMAGES`），UI 不再显示点了没反应的目标格式
- 图片转换边收窄到 JPG / PNG / WEBP：libvips 构建是 `-Dheif=disabled -Dtiff=disabled`，
  `ImageConverter.writeBitmap` 也只能编码这三种。AVIF / HEIC / GIF / BMP / TIFF 是**只读输入**
- 新增 `ConversionGraph.toolsFor()` / `hasAnyCapability()`，为首页 UI 2.0 的
  「这个文件能做什么」面板提供数据源
- `MainViewModel.onDocumentPicked`：只有工具能力、没有转换边的格式（典型是 PDF）
  提示「请在工具页处理」，不再笼统说「不支持」

回归护栏（两条，防止再次跑偏）：
- 遍历**全部格式对**断言 `canConvertLocallyTo ≡ ConversionGraph.canConvert`
- 遍历**每条转换边**断言有引擎能接（按各 Converter 的 `supports()` 规则复刻判定）

### 阶段 02：ConversionPlanner 智能调度系统

新增 `domain/planner/`。把原先散落且互不相连的四块决策串成执行前的一次判断：

```
ConversionGraph      能力校验（这条边存在吗）
       ↓
MediaEncodePlanner   编码模式（Remux / 流拷贝 / 硬编 / 软编）
       ↓
HardwareFacts        硬件事实（有没有 H.264 / VP8 硬件编码器）
       ↓
StorageGuard         临时空间预检（带精确数字）
       ↓
ConversionPlan       引擎 + 兜底 + 编码模式 + 并发槽位 + 空间预算 + 决策理由
```

- `HardwareFacts` 抽成接口，`DeviceCapabilities`（依赖 `MediaCodecList`，只能在设备上跑）
  只出现在 `DeviceHardwareFacts` 生产实现里 → 20 项 Planner 单测可在纯 JVM 执行
- 并发槽位（计划书 §5.4 验收项）：流拷贝 PARALLEL；视频重编码 SERIAL（硬件编码器独占）；
  超过 200 MB 阈值 SERIAL。大视频若能拷流仍然 PARALLEL
- 无硬件编码器时的降级路径写进 reason：FFmpegKit 8.1.7 无 libx264，
  只能退 mpeg4；VP8 退 libvpx realtime
- 拒绝路径返回结构化 `PlanRejection`，`PlanRejectionMessages` 翻译成计划书 §7.3 要求的文案：
  空间不足输出「需要 2.1 GB / 当前剩余 1.3 GB」，编码器缺失输出「已尝试：MediaCodec 硬件编码」
- 接入 `ConversionExecutor`：SINGLE 走 Planner 并打 `planner=` 日志
  （engine / fallback / mode / streamCopy / slot / reason —— 正是阶段 08 Benchmark 需要的字段）；
  工具类 kind 输入形态不同，仍走 StorageGuard，但错误文案统一走 `PlanRejectionMessages`
- 删除死代码 `ConversionEngineSelector` 及其单测：它只回答"用哪个引擎"，
  且 `ConverterRegistry` / `ConversionExecutor` 从未引用。`EngineType` 移入独立文件

### 阶段 01 起步：test-assets 生成器

`domain/testassets/TestAssetFactory`：纯 JVM、确定性（线性同余，同 seed 同输出，
便于失败复现），提供计划书 §4.2 要求的五种变体 —— 标准 / 空文件 / 截断 /
魔数正确但内容损坏 / 内容与扩展名不一致，外加稀疏大文件（不占物理块）。

配套 8 项单测覆盖：确定性可复现、空文件长度为 0、截断文件仍可识别魔数但必然解码失败、
损坏文件头部合法主体是垃圾、**magic number 胜过说谎的扩展名**（内容 PDF / 扩展名 .png →
识别为 PDF）、稀疏文件长度正确、11 个魔数常量与 `FileTypeDetector` 逐一对齐
（防止测试素材本身写错）、空内容识别为 UNKNOWN 而非误判。

引擎真实读取损坏文件的 I/O 路径留给 androidTest，尚未编写。

### 签名凭据外置（阶段 10 前置的安全项）

口令原先硬编码在 `app/build.gradle.kts`。现改为三级来源：

1. `signing.properties`（仓库根，已 gitignore）
2. 环境变量 `OPENCONVERT_STORE_PASSWORD` / `_KEY_ALIAS` / `_KEY_PASSWORD`（CI）
3. 都缺失 → **不配置签名**，产出 unsigned APK 并打印警告，绝不回落到硬编码

新增 `signing.properties.example` 模板入库。`.gitignore` 补充 `*.apk` / `*.aab`
（发布产物应挂 GitHub Releases，不进仓库）与 `native/dist/`、`native/out/`。

Kotlin DSL 坑：`build.gradle.kts` 顶部需显式 `import java.util.Properties`，
写 `java.util.Properties()` 全限定名会报 `Unresolved reference: util`。

验证：`assembleRelease` 通过，产物 105.3 MB，`apksigner verify --print-certs` 确认
签名生效（CN=OpenConvert，SHA-256 `65776a27…`）。

> ⚠️ 该 keystore 口令已进入 git 历史，必须视为**已泄露**。正式对外发布前需生成新
> keystore（注意：更换签名后已安装的旧版本无法覆盖升级）。


## 真机验证暴露并修复的真实 bug

- 现象：接入 Planner 后 `ConversionWorkerInstrumentedTest.backgroundConversionRunsToCompletion`
  失败，「conversion never reached COMPLETED within 90s」
- 根因：Planner 把 `inputBytes == 0` 判为空文件直接拒绝。但 `task.fileSize` 来自
  SAF `OpenableColumns.SIZE` / MediaStore 元数据，**对未知大小合法返回 0**
  （测试里 MediaStore 行刚插入、流尚未 flush 时就是 0）→ 真实转换在 Planner 阶段被误杀
- 修复：新增 `PlanRequest.isSizeVerified`，只有体积经过真实 stat/statSize 校验时
  0 才判定为空文件；未校验的 0 放行，真正的空文件由引擎读流时拦截
- 复跑：27/27 通过

## 测试结果

| 项目 | 结果 |
|------|------|
| 单元测试 | 28 类 / 134 项全绿（新增 Planner 21 项 + 能力图回归护栏 5 项 + TestAssetFactory 8 项） |
| 真机 instrumented | **27/27 通过**（PHY110 / Android 16） |
| `assembleDebug` | 通过 |
| `assembleRelease` | 通过，105.3 MB，签名已验证 |
| Planner 真机日志 | `engine=LIBVIPS fallback=BITMAP_FACTORY streamCopy=false slot=PARALLEL` 已确认 |

## 下一步（未做）

> 2026-08-20 后续进展：阶段 01 异常输入真机用例、阶段 03、阶段 04、阶段 05
> 主流程以及阶段 08 指标采集已继续推进；当前状态与验证结果见
> `docs/phase7-benchmark-and-batch-presets.md`，以下清单保留为本轮结束时的历史快照。

- 阶段 01 剩余：用 `TestAssetFactory` 写异常场景 instrumented 用例（空 / 损坏 / 错扩展名 / 超大）
- 阶段 03 首页 UI 2.0：文件驱动入口，数据源已就绪（`ConversionGraph.toolsFor`）
- 阶段 04 任务中心 2.0：按状态分组、速度 / 剩余时间 / 引擎名展示。
  `ConversionException` 定义了 11 类结构化错误但生产路径仍返回裸字符串，需一并接入
- 阶段 05 Preset：`PresetDao` + Room v5 表 + 12 个内置预设都在，但 `presetDao()` 零调用，
  预设从未写库、UI 无入口
- 阶段 08 Benchmark：Planner 日志字段已齐，缺统一指标结构与峰值内存 / 压缩率采集

## 仓库层面的实际状况（前一轮结论已更正）

1. **LFS 早已配置且已推送成功。** `.gitattributes` 有 `*.so filter=lfs`，
   `liblo-native-code.so` 在 git 里是 134 字节的 LFS 指针，不是 171 MB blob；
   `origin/main` 与本地同为 `2cb9371`，v1.0.0 连同全部 .so 已上传。
   GitHub 100 MB 单文件硬限对 LFS 对象不适用 → **不需要 BFG / 重写历史**。
   `.git` 1.7 GB 的构成是 `.git/lfs` 893 MB（本地缓存）+ `.git/objects` 754 MB。
2. **真正的约束是 GitHub LFS 免费额度**：存储 1 GB / 月流量 1 GB，当前已占 893 MB。
   再加大体积 .so 就会超额。
3. `.git/objects` 里有 `native/dist/` 的上游 tarball（vips 25 MB、glib 9.7 MB 等约 45 MB）。
   属于可重新下载的构建输入，本不该入库；为 45 MB 重写历史不划算，
   已在 `.gitignore` 挡住后续新增。
4. Release 签名口令已外置（见上）。
