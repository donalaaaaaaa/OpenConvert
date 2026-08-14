# OpenConvert

OpenConvert 是一款 Android 全本地文件转换工具。当前版本 `0.1.0`（Release 工程化完成：R8 + 签名 + ABI Split）。

## 当前已实现

- Kotlin + Jetpack Compose + Material 3 单 Activity 架构
- 首页、转换配置、历史、设置、隐私、PDF 工具、批量转换、压缩包页面
- Android SAF 文件选择、持久读取权限、文件名/大小/格式识别
- JPG、PNG、WEBP、PDF、MP3、AAC、WAV、FLAC、M4A、MP4、MOV、MKV、WEBM 的转换路由
- ZIP / TAR / TAR.GZ / GZIP / BZIP2 压缩与解压（Apache Commons Compress）
- 图片 / PDF / FFmpeg 音视频真实本地转换
- libvips 8.18.5 图片主引擎（JNI，arm64-v8a），BitmapFactory 兜底
- Media3 / MediaCodec 视频主引擎，FFmpeg 兜底
- WorkManager + 前台服务 + 通知栏进度 / 取消
- 进程被杀后的孤儿任务恢复
- 批量转换：BatchJob + 并发闸门（图片 3 并行 / 视频 2 并行）+ 暂停 / 继续 / 取消
- 大文件流式拷贝与缓存空间预检
- Room 历史记录 1→2→3→4 迁移（v4 增加 batchId 与 batch_jobs 表）
- Manifest 明确不申请 `INTERNET` 权限

## PDF 工具

- 图片 → PDF（JPG/PNG/WEBP，可排序、A4、自动方向）
- PDF → 图片（JPG/PNG，DPI/质量/页码范围）
- PDF 合并（拖动排序）
- PDF 拆分（1-3、5、8-10 范围）
- PDF 删除页面（勾选删除，至少保留一页）
- PDF 旋转（90° / 180° / 270°，全部或指定页面）

## 批量转换

- 一次选择 2–200 个同类别文件（图片 / 视频 / 音频）
- 自动计算共同输出格式，禁止不支持的组合
- 小文件并行（3 路），视频限流（2 路），避免内存爆炸与过热
- 进度 = 已完成 / 总数，支持暂停 / 继续 / 取消全部
- 每个文件独立 ConversionTask，历史完整可追溯

## 构建

需要 JDK 17 和 Android SDK 36。仓库已经包含 Gradle 8.13 Wrapper。

项目当前位于含中文字符的父目录中；Windows 上直接运行 Gradle 可以打包 APK，但 JUnit 的分叉进程可能无法从中文路径加载测试类。统一使用验证脚本：

```powershell
.\scripts\verify.ps1
```

Release 构建（R8 + 签名 + arm64-v8a ABI Split）：

```powershell
.\gradlew.bat assembleRelease
```

签名密钥：`app/openconvert-release.jks`（alias `openconvert`，密码 `openconvert123`，仅供开发期使用；正式发布前应更换）。

连接 Android 设备后：

```text
app/src/androidTest/java/com/openconvert/app/domain/converter/
app/src/androidTest/java/com/openconvert/app/domain/work/LargeFileStabilityInstrumentedTest.kt
```

真机验收（PHY110 · Android 16）：21/21 通过，含 PDF 删除/旋转与压缩包端到端用例。
