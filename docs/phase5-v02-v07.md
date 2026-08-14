# Phase 5 – v0.2–v0.7 增量（2026-08-14）

设备：OnePlus PHY110 · Android 16 / API 36

## 本轮完成

### Release 工程化（计划书 Phase 1）
- versionCode 100 / versionName 0.1.0；debug 与 release 分离（`applicationIdSuffix .debug`）
- Release 开启 R8 `minifyEnabled` + `shrinkResources`，ProGuard 规则覆盖 FFmpegKit / PdfBox / LiTr / Media3 / Room / WorkManager / JNI / Commons Compress
- Release 签名：`app/openconvert-release.jks`（开发期密钥，发布前更换）
- ABI Split：仅 arm64-v8a（P0 规划），产物 `app-arm64-v8a-release.apk` ≈ 33.6 MB
- `assembleRelease` 通过（R8 全量混淆）

### PDF 补全（计划书 Phase 2）
- 删除页面：`PdfDeletePagesConverter`，勾选删除、至少保留一页、越界忽略、重复去重
- 旋转页面：`PdfRotatePagesConverter`，90/180/270，全部或指定页（复用 pageRanges 解析）
- UI 入口加入 PDF 工具页；`ConversionKind.PDF_DELETE_PAGES / PDF_ROTATE_PAGES`
- 真机测试 4/4：删除、拒绝删全部、旋转 90°、拒绝非法角度

### 批量转换（计划书 Phase 3）
- Room v4：`batch_jobs` 表 + `conversion_tasks.batchId` 列（MIGRATION_3_4）
- `BatchJob` 领域模型 + `BatchSettingsCodec`（JSON）
- `BatchScheduler`：enqueue / pause / resume / cancel；暂停只取消 WorkManager，Room 任务保持 PENDING 可恢复
- `BatchConcurrency`：图片 3 路并行、视频 2 路并行（Semaphore）
- `ConversionWorker`：批量任务完成后聚合 BatchJob done/failed；孤儿恢复跳过 PAUSED 批次
- UI：批量入口 → 多选文件（2–200）→ 共同格式计算 → 目标格式/质量/尺寸 → 文件夹 → 进度页（done/total、当前文件、暂停/继续/取消）
- 单测：BatchJob 进度计算、BatchSettingsCodec 往返、BatchJobStatus 枚举

### 压缩包（计划书 Phase 7）
- `ArchiveConverter`（Apache Commons Compress 1.27.1）：多文件 → ZIP/TAR；单文件 → GZIP/BZIP2；ZIP/TAR.GZ/TAR.BZ2 → 解压
- 全流式处理（64 KB buffer），SAF content:// 输出；测试路径支持 file://
- `FileCategory.ARCHIVE` + ZIP/TAR/TAR_GZ/GZIP/BZIP2 格式识别（含双扩展名 tar.gz）
- UI：压缩包工具页（压缩 / 解压）
- 真机测试 3/3：ZIP 打包校验、多文件 GZIP 拒绝、ZIP 解压内容还原

## 测试结果

- 单元测试：全部通过（含新增 PdfDeletePagesAndBatchTest、ArchiveFormatTest）
- 真机 instrumented：21/21 通过（新增 PdfPageEditInstrumentedTest 4 项 + ArchiveConverterInstrumentedTest 3 项）
- Debug / Release 构建均通过

## 下一步（未做）

- Phase 5 视频：视频→GIF、静音、裁剪、H.265 参数化
- Phase 6 音频：OGG / OPUS
- Phase 9 性能：磁盘空间预测 UI、电量 / 温度保护
- Phase 10 UI：4 导航（首页/历史/工具/设置）

## 2026-08-14 追加：图片高级功能（Phase 4）

- libvips JNI 扩展：`convertBuffer` 增加 rotate（90/180/270）与 flip（水平/垂直），WSL 重编译 libvips_android.so（14.3MB）
- Bitmap 路径同样支持旋转/翻转/裁剪（回退一致）
- 裁剪比例：free / 1:1 / 4:3 / 3:2 / 16:9 / 9:16（cover-crop 居中裁剪）
- 隐私模式：删除全部元数据（EXIF/GPS），强制走 Bitmap 路径（Bitmap.compress 不写 EXIF）
- `ImageEditMath` 纯逻辑单测 6 项；真机 5/5（旋转换尺寸、180° 像素翻转、水平镜像、方形裁剪、去元数据）
- Convert Hub（Phase 8）同批落地：FileTypeDetector（magic number 三层识别接入 SAF）、ConversionGraph（能力图单一来源）、ConverterRegistry（统一引擎注册 + engine/elapsed 日志）
