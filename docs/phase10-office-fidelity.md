# Phase 10 – Office 保真度素材矩阵（2026-08-21）

设备：OnePlus PHY110 · Android 16 / API 36 · JDK17 + SDK36

对应《OpenConvert 1.0 后续开发计划书》阶段 07。

## 交付

在 `app/src/androidTestOffice/assets/` 新增三份可随测试 APK 分发的确定性素材：

| 文件 | 布局与内容 | PDF 门禁 |
|---|---|---|
| `office-fidelity-doc.docx` | 2 页、中文字体、合并表格、页眉 `DOCX-HEADER`、页脚 `DOCX-FOOTER` | ≥ 2 页并命中 5 个文本标记 |
| `office-fidelity-slide.pptx` | 2 张中文幻灯片、中文表格 | ≥ 2 页并命中 3 个文本标记 |
| `office-fidelity-sheet.xlsx` | `数据总览`、`华东区域`、`华南区域` 3 个 Sheet，含合计公式和货币格式 | ≥ 3 页并命中 Sheet、公式与 `2,740,000` |

对应生成脚本位于 `scripts/office-fidelity/`。DOCX 使用 `python-docx`，PPTX/XLSX 使用
workspace artifact runtime；二进制素材不是手工黑盒，后续可以原样重建和审阅。

## 自动化断言

`OfficeConverterInstrumentedTest.bundledEnginePreservesOfficeFidelityMatrix` 对三份输入逐一执行
生产 `OfficeConverter`，并检查：

1. 返回 `ConversionResult.Success`，实际引擎必须为 `LIBREOFFICE_KIT`。
2. 输出以 `%PDF-` 开头且非空。
3. 使用 PDFBox 读取页数并抽取文本。
4. 忽略空白差异后验证中文、表格、页眉页脚、Sheet 名、公式结果与货币格式标记。

这是一道内容级保真度门禁：它能捕获漏页、漏 Sheet、中文丢失、表格内容丢失、公式未计算、
错误 fallback 等回归，但不替代像素级版式对比。

## 验证结果

| 项目 | 结果 |
|---|---|
| 素材结构/预览 | PPTX 2 页无溢出；XLSX 最终文件回读 3 Sheet、公式错误 0；DOCX 结构与分页标记完整 |
| Office JVM | 222/222，0 失败 |
| 新增 LOKit 保真度类 | 2/2（原 smoke + 新矩阵），0 失败 |
| Office 真机全矩阵 | 20 类、62/62，0 失败（测试类间重启目标进程） |
| AndroidTest 构建 | `assembleOfficeDebugAndroidTest` 通过 |

桌面环境没有 LibreOffice/soffice，因而未执行 DOCX 的桌面 PDF 渲染；最终验收直接使用目标 Android
上的 LOKit 输出，并由 PDFBox 检查实际分页与内容。PPTX、XLSX 已在生成时做源文件预览/回读检查。

## 已识别的稳定性债务

62 项全部运行在同一个 instrumentation 进程时，本轮在第 31 项
`ImageAdvancedInstrumentedTest.vipsRotate90SwapsDimensions` 启动后停滞。该测试在强制停止目标进程后
单独运行 0.17 秒通过；随后每个测试类之间重启目标进程，20 类、62 项全部通过。

这说明当前长套件存在跨类的 native 资源累积或生命周期隔离问题。发布门禁应采用按类进程隔离，
并在稳定性阶段继续定位 libvips/前序转换器的释放边界。

## 后续

1. 增加图表、嵌入对象、复杂分页、受保护文档和缺失字体替换素材。
2. 为关键页面建立渲染图片 golden diff，并定义可接受的抗锯齿/字体差异阈值。
3. 扩展到 Android 12–15、不同厂商 SoC 与字体环境。
4. 修复长套件的 native 资源累积后，恢复单进程 62/62 作为额外稳定性门禁。
