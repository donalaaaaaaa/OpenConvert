# LibreOfficeKit Android 可行性验证报告

日期：2026-08-14 · 设备：OnePlus PHY110（Android 16 / API 36）

## 结论

**LibreOfficeKit 在 Android 上可行**，DOCX / PPTX / XLSX → PDF 真机转换全部通过；
首批中文与复杂布局的内容级保真度回归也已自动化。

| 输入 | 文档类型 | 输出 | 大小 | 结果 |
|------|---------|------|------|------|
| test-doc.docx | Writer (type=0) | output-doc.pdf | 13,981 B | ✅ %PDF-1.7 |
| test-slide.pptx | Impress (type=2) | output-slide.pdf | 6,561 B | ✅ %PDF-1.7 |
| test-sheet.xlsx | Calc (type=1) | output-sheet.pdf | 14,571 B | ✅ %PDF-1.7 |

## 2026-08-21 保真度矩阵

| 素材 | 重点覆盖 | 自动断言 | PHY110 结果 |
|---|---|---|---|
| `office-fidelity-doc.docx` | 中文字体、两页、合并表格、页眉页脚 | LOKit 实际引擎、PDF ≥ 2 页、5 个文本标记 | ✅ |
| `office-fidelity-slide.pptx` | 两页中文演示、中文表格 | LOKit 实际引擎、PDF ≥ 2 页、3 个文本标记 | ✅ |
| `office-fidelity-sheet.xlsx` | 3 个中文 Sheet、公式与货币格式 | LOKit 实际引擎、PDF ≥ 3 页、Sheet/公式/格式化值标记 | ✅ |

素材由 `scripts/office-fidelity/` 下的三个生成脚本确定性产出，测试在
`OfficeConverterInstrumentedTest.bundledEnginePreservesOfficeFidelityMatrix` 中读取最终 PDF，
同时检查页数、抽取文本、格式化公式结果和 `EngineType.LIBREOFFICE_KIT`。

本轮 Office JVM 为 222/222；真机按测试类隔离运行 20 类、62/62。整套 62 项若全部放在同一
instrumentation 进程中，曾在第 31 项原生图像测试处因前序资源累积停滞；该项独立运行 0.17 秒通过，
按类强制重启目标进程后全矩阵通过。当前 CI/真机回归应继续采用进程隔离，并把单进程资源释放列为稳定性债务。

## 素材来源

- `liblo-native-code.so`（arm64-v8a，171 MB）+ 12 个 NSS 配套库 + `assets/program`、`assets/share`、`assets/unpack` 资源：提取自 [gurecn/LibreOffice-android](https://github.com/gurecn/LibreOffice-android) v1.0 release APK（132 MB，2025-07）
- 官方 JNI 绑定类（`org.libreoffice.kit.*`）：[premdeeparora12-bit/LibreOffice-Android](https://github.com/premdeeparora12-bit/LibreOffice-Android)（与 gurecn 同一 LO 构建，BuildId 一致）
- 验证工程：`office-verify/`（独立 APK，不进入主工程）

## 正确的初始化序列（踩坑后确认）

1. **加载顺序**：`nspr4 → plds4 → plc4 → nssutil3 → freebl3 → sqlite3 → softokn3 → nss3 → nssckbi → nssdbm3 → smime3 → ssl3 → c++_shared → lo-native-code`（LibreOfficeKit 静态块内 NativeLibLoader 完成）
2. **unpack assets 到 `getApplicationInfo().dataDir`**（必须！不是 filesDir 子目录）——`assets/unpack/` 下的 `program/`、`user/`、`etc/` 复制到应用数据根目录
3. `LibreOfficeKit.putenv("SAL_LOG=+WARN+INFO")`
4. `LibreOfficeKit.init(activity)`（内部调 `initializeNative(dataDir, cacheDir, apkFile, assetManager)`，apkFile 必须是 `getPackageResourcePath()` 的 base.apk）
5. `Office(handle)` → `office.documentLoad(path)` → `document.initializeForRendering()` → `document.saveAs(pdfPath, "pdf", "")`

## 常见失败（已排除）

- `DeploymentException`（SIGABRT）：unpack 目标目录错误（用 filesDir 子目录而非 dataDir 根）→ 修复
- 混用不同构建的 .so 与 kit 类 → 需同源（gurecn 与 premdeeparora 构建一致可用）
- gurecn 官方 viewer UI 层 NPE：与 LOKit 无关（UI 混淆代码 bug），初始化与转换本身成功

## 工程决策（对 OpenConvert）

- Office 引擎以**双 Flavor** 提供：`basic`（轻量，无 Office）/ `office`（内置 LOKit，DOCX/PPTX/XLSX→PDF）
- 集成方案：`OfficeConverter` 实现 `Converter` 接口，`ConversionGraph` 增加 DOCX/PPTX/XLSX → PDF 边，WorkManager/Room/SAF 复用现有 SINGLE 流程
- 真机已验证的 LOKit 生命周期：进程内单例初始化，documentLoad/saveAs 可在同一线程串行执行
- **Office Pack 可选下载（动态加载）已知限制（Android 16 / PHY110）**：
  - 库从 `files/` 目录 `System.load` 后，LO 内部 soffice 线程初始化抛 `DeploymentException`（SIGABRT）
  - 同 .so 打包进 APK（`lib/`）时完全正常 → 判定为 linker namespace / 加载路径差异，非代码问题
  - 已保留 `OfficePackManager`（zip 安装到 filesDir/office-pack）作为未来增强入口，但当前默认走内置 Flavor 方案
  - 若后续需要动态下载：优先研究 split APK（PackageInstaller）或与 FFmpeg 的 libc++_shared 兼容性

2026-08-21 复验：双 Flavor 已重新成为正式构建边界；Basic APK 35.39 MiB 且不含任何 LOKit
条目，Office APK 105.29 MiB；最新 Office 真机按类隔离全量为 62/62。详见
`docs/phase8-office-editions.md` 与 `docs/phase10-office-fidelity.md`。

## 验证脚本

```powershell
# 验证 APK（独立工程）
cd D:\Hermes\Herme\OpenConvert\office-verify
.\gradlew.bat :app:assembleOfficeDebug
adb install -r app\build\outputs\apk\office\debug\app-office-arm64-v8a-debug.apk
adb logcat -s LOKVerify:*
```
