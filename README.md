# OpenConvert

OpenConvert 是一款 Android 全本地文件转换工具。当前仓库处于 `0.1.0-alpha02` 的 Week 4 后台任务阶段。

## 当前已实现

- Kotlin + Jetpack Compose + Material 3 单 Activity 架构
- 首页、转换配置、历史、设置与隐私页面
- Android SAF 文件选择、持久读取权限、文件名/大小/格式识别
- JPG、PNG、WEBP、PDF、MP3、AAC、WAV、FLAC、M4A、MP4、MOV、MKV、WEBM 的 MVP 路由
- 图片 / PDF / FFmpeg 音视频真实本地转换
- WorkManager + 前台服务 + 通知栏进度 / 取消
- 进程被杀后的孤儿任务恢复
- 大文件流式拷贝与缓存空间预检
- Room 历史记录 1→2→3 迁移
- Manifest 明确不申请 `INTERNET` 权限

## 构建

需要 JDK 17 和 Android SDK 36。仓库已经包含 Gradle 8.13 Wrapper。

项目当前位于含中文字符的父目录中；Windows 上直接运行 Gradle 可以打包 APK，但 JUnit 的分叉进程可能无法从中文路径加载测试类。统一使用验证脚本：

```powershell
.\scripts\verify.ps1
```

连接 Android 设备后：

```text
app/src/androidTest/java/com/openconvert/app/domain/converter/
app/src/androidTest/java/com/openconvert/app/domain/work/LargeFileStabilityInstrumentedTest.kt
```
