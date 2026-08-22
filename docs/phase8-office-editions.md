# Phase 8 – Office 双 Edition 体积治理（2026-08-21）

设备：OnePlus PHY110 · Android 16 / API 36 · JDK17 + SDK36

对应《OpenConvert 1.0 后续开发计划书》阶段 09。

## 决策

正式发行采用同 applicationId、同签名的两个 Flavor：

- `basic`：默认轻量版，不包含 LibreOfficeKit native 库与运行资源。
- `office`：完整离线版，从 `src/office` 打入 LOKit 与字体/registry/program 资源。

未启用本地 zip 动态 Office Pack。该原型在 Android 16 上从 `files/` 加载后会在 soffice
线程初始化时触发 `DeploymentException` / SIGABRT；当前只有 APK `lib/` 内置路径经过真机验证。

## 运行时边界

- `BuildConfig.OFFICE_BUNDLED` 明确当前 Edition，不靠 `System.loadLibrary` 异常猜测产品能力。
- 轻量版的首页、设置页和文件能力面板不再宣传 Office 转换。
- Office 文件仍可作为普通文件参与 ZIP/TAR 打包，但不会出现 Office → PDF 目标。
- 历史 Office 任务在轻量版中重试时给出“需要安装 Office 版”，不会进入必然失败的 Worker。
- CI 和本地验证脚本均显式构建两种 Flavor，避免只验证其中一种。

## 体积结果

| 指标 | Basic | Office | Basic 减少 |
|---|---:|---:|---:|
| Release APK | 35.39 MiB | 105.29 MiB | 69.90 MiB / 66.39% |
| Release AAB | 65.29 MiB | 135.20 MiB | 69.91 MiB / 51.71% |
| PHY110 bundletool 下载 | 34.74 MiB | 103.60 MiB | 68.86 MiB / 66.46% |
| PHY110 即时安装 code | 107.23 MiB | 353.99 MiB | 246.76 MiB / 69.71% |
| PHY110 解压 native lib | 44.56 MiB | 221.15 MiB | 176.59 MiB / 79.85% |

Basic Release APK ZIP 检查：13 个 arm64 `.so`，0 个 `liblo-native` / `libnspr4` /
`assets/program|share|unpack` 条目。Office Release APK 为 26 个 `.so`、88 个 Office 条目。

## 验证

| 项目 | 结果 |
|---|---|
| Basic JVM | 219/219，40 类，0 失败 |
| Office JVM | 219/219，40 类，0 失败 |
| Basic Edition 边界 + 文件驱动流程 | 真机 6/6 |
| Office DOCX/PPTX/XLSX → PDF | 真机 1/1（单用例覆盖三种格式） |
| Office 全量 instrumented | 61/61，0 跳过 / 0 失败 |
| Debug | `assembleBasicDebug`、`assembleOfficeDebug` 通过 |
| Release | 两种 APK/AAB 均构建成功且签名证书一致 |

2026-08-21 增量复验：Basic Release 写入转换历史、自定义预设并通过系统文件选择器取得真实
SAF 持久授权后，以 `adb install -r` 原位安装同签名、同 versionCode 的 Office Release；Office
成功读取两条 Room 状态、保留并读取同一 SAF URI，随后 DOCX/PPTX/XLSX → PDF Release smoke
通过。详见 `docs/phase11-edition-upgrade.md`。

设备在测量完成后已恢复安装 Office Release。

## 后续

1. 若要实现商店内按需下载，优先采用 Play Feature Delivery / split APK；不重新启用已知崩溃的
   `files/` 动态加载路径。
2. 继续评估 FFmpegKit 自定义裁剪；Basic 当前剩余 44.56 MiB 解压 native 仍是下一体积来源。
