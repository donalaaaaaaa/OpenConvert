# Phase 9 – 实际执行引擎闭环（2026-08-21）

设备：OnePlus PHY110 · Android 16 / API 36 · JDK17 + SDK36

对应《OpenConvert 1.0 后续开发计划书》阶段 04 的剩余项，并修正阶段 08 Benchmark
在 fallback 后仍记录计划主引擎的问题。

## 数据链

计划引擎只代表执行前决策，实际成功引擎由最终完成输出的 Converter 返回：

```
Converter Success(actualEngine)
        ↓
ExecutionResult.Success
        ↓
ConversionWorker → Room v7 ConversionTask.actualEngine
        ↓
任务中心 / 历史详情 / Benchmark
```

- 图像：libvips 成功记 `LIBVIPS`；失败回退 Bitmap 后记 `BITMAP_FACTORY`。
- 媒体：Media3、LiTr 成功分别记录自己的引擎；硬件路径失败并由 FFmpegKit 完成时记录
  `FFMPEG_KIT`，包括 stream copy。
- Office、PDF、归档分别记录 `LIBREOFFICE_KIT`、`PDFBOX`、`COMMONS_COMPRESS`。
- Benchmark 优先采用成功结果的实际引擎；仅失败或旧结果缺失时保留计划引擎用于诊断。

## 持久化与兼容

- Room 从 v6 升到 v7，给 `conversion_tasks` 增加可空 `actualEngine TEXT`。
- 旧任务迁移后保持 `NULL`，UI 不伪造引擎名。
- 读取未知的未来枚举值时降级为 `null`，避免新版写入的历史让旧版崩溃。
- 任务中心显示“引擎 · FFmpegKit”等实际引擎；历史列表与操作详情同步展示。

## 验证

- Basic JVM：222/222；Office JVM：222/222（各 41 类），均 0 失败。
- Basic 真机定向 10/10：v5→v7 真实迁移、Worker 落库、任务卡展示、生产转换与 Benchmark。
- Office 真机 1/1：内置 Office 转换覆盖 DOCX/PPTX/XLSX，并断言
  `LIBREOFFICE_KIT` 实际引擎。
