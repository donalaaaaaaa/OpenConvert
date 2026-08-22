# APK / AAB 体积基线（2026-08-20）

对应《OpenConvert 1.0 后续开发计划书》阶段 09。

构建环境：JDK17、SDK36、arm64-v8a，Release 开启 R8 与资源缩减。

## 构建产物

| 产物 | 字节 | MiB |
|---|---:|---:|
| Debug APK | 134,984,383 | 128.73 |
| Release APK | 110,405,097 | 105.29 |
| Release AAB | 141,769,717 | 135.20 |
| PHY110 bundletool 下载体积 | 108,634,276 | 103.60 |
| Basic flavor Release APK（2026-08-21） | 37,112,209 | 35.39 |
| Office flavor Release APK（2026-08-21） | 110,404,513 | 105.29 |

当前正式 Release APK 比 Debug 小 24,579,286 字节（约 18.2%），说明 R8 / 资源缩减有效，
但 native 引擎仍决定总体积。

注意：AAB 文件本身不是用户最终下载体积。Play 会按设备生成 split APK；135.20 MiB 只能作为
仓库发布产物与上传包基线，不能直接当作商店下载大小。

## PHY110 设备定向 Bundle 测量

工具：bundletool 1.18.1。设备规格由已连接的 OnePlus PHY110 导出：Android 16 / API 36、
arm64-v8a、640 dpi（xxxhdpi）、zh-CN。命令流程为 `get-device-spec` → `build-apks` →
`get-size total`。

| 项目 | 字节 | MiB |
|---|---:|---:|
| `.apks` 容器文件（不是下载体积） | 127,710,727 | 121.79 |
| bundletool 估算下载体积（MIN = MAX） | 108,634,276 | 103.60 |

生成的 APK Set 包含 `base-arm64_v8a.apk`、`base-master.apk`、`base-xxxhdpi.apk` 和
`base-zh.apk`。设备筛选只比通用 arm64 Release APK 减少 1,770,821 字节（约 1.60%）；
Locale / density 资源本身很小，LibreOfficeKit、FFmpegKit 等 arm64 native 库仍是必选内容。
这证明仅依赖 AAB split 不足以解决体积问题，Office 引擎拆包仍是收益最大的路径。

## 2026-08-21：双 Edition 落地后的对比

| 指标 | Basic | Office | Basic 减少 |
|---|---:|---:|---:|
| Release APK | 37,112,209 B / 35.39 MiB | 110,404,513 B / 105.29 MiB | 69.90 MiB / 66.39% |
| Release AAB | 68,464,949 B / 65.29 MiB | 141,768,423 B / 135.20 MiB | 69.91 MiB / 51.71% |
| PHY110 bundletool 下载 | 36,432,308 B / 34.74 MiB | 108,633,355 B / 103.60 MiB | 68.86 MiB / 66.46% |

Basic APK 内有 13 个 arm64 `.so`，Office APK 为 26 个；ZIP 条目检查确认 Basic 中没有
`liblo-native-code.so`、NSS 入口库或 `assets/program|share|unpack`。因此这不是仅靠 UI 隐藏的
“伪轻量版”，而是真正切断了发布包边界。

## Release 覆盖安装后的代码占用

将本轮签名 Release APK 覆盖安装到 PHY110 后，立即执行 Android 16 的
`cmd package get-package-storage-stats`：

| 分类 | 字节 | MiB |
|---|---:|---:|
| code | 371,181,056 | 353.99 |
| 其中 APK | 110,405,097 | 105.29 |
| 其中解压 native lib | 231,896,608 | 221.15 |
| data | 417,792 | 0.40 |
| cache | 155,648 | 0.15 |

`code` 还包含 dex / 优化产物，且可能随系统后台编译继续变化，因此这是「覆盖安装后立即取数」
的设备基线，不是所有设备上的固定值。最关键的结论不变：native 库解压后单独占 221.15 MiB，
远大于 Kotlin / Compose 层可优化的空间。

双 Edition 同签名覆盖安装后的即时对比：

| 分类 | Basic | Office |
|---|---:|---:|
| code | 112,440,832 B / 107.23 MiB | 371,181,056 B / 353.99 MiB |
| APK | 37,112,209 B / 35.39 MiB | 110,404,513 B / 105.29 MiB |
| 解压 native lib | 46,723,720 B / 44.56 MiB | 231,896,608 B / 221.15 MiB |
| data | 417,792 B | 417,792 B |
| cache | 155,648 B | 155,648 B |

同 applicationId、同签名的覆盖安装保持了相同 data/cache 数值；测量后设备恢复为 Office 版。

## Release APK 的 arm64 native 构成

Release APK 内含 26 个 `.so`：未压缩合计 231,896,608 字节，ZIP 压缩后合计约 84.31 MiB，
占 APK 文件约 80.1%。前五项如下：

| 库 | 未压缩 MiB | APK 内压缩 MiB | 主要来源 |
|---|---:|---:|---|
| `liblo-native-code.so` | 171.26 | 62.78 | LibreOfficeKit |
| `libavcodec.so` | 18.22 | 9.12 | FFmpegKit |
| `libvips_android.so` | 14.33 | 5.43 | libvips |
| `libavfilter.so` | 4.87 | 2.05 | FFmpegKit |
| `libavformat.so` | 3.39 | 1.84 | FFmpegKit |

`liblo-native-code.so` 单项占 Office 版全部 native 未压缩体积约 77.4%。双 Edition 已把它从
默认 Basic 包移除，Basic Release APK 降到 35.39 MiB。

## 2026-08-22：FFmpegKit full → audio

生产调用面只用 lame / opus / vorbis / aac / flac / mpeg4 / 流拷贝；WEBM 主路径是 LiTr。
因此依赖从 `ffmpeg-kit-full:8.1.7` 换成 `ffmpeg-kit-audio:8.1.7`，不再打进 libvpx /
dav1d / kvazaar / gnutls。无 VP8 硬件编码器时 WEBM 直接 `NoUsableEncoder`，不再走
FFmpeg libvpx。

| 指标 | full（2026-08-21） | audio（本轮） | 减少 |
|---|---:|---:|---:|
| Basic Release APK | 37,112,209 B / 35.39 MiB | 32,782,661 B / 31.26 MiB | 4.13 MiB / 11.67% |
| `libavcodec.so` 未压缩 | 18.22 MiB | 11.08 MiB | 7.14 MiB |
| `libavfilter.so` 未压缩 | 4.87 MiB | 2.78 MiB | 2.09 MiB |

Basic APK 仍含 13 个 arm64 `.so`；解压 native 合计约 34.93 MiB（此前安装态约 44.56 MiB）。

## 结论与下一步

1. Office 已按 Basic / Office 双 Flavor 拆分；下一步验证 Basic → Office 同签名覆盖升级的数据保留。
2. FFmpegKit 已从 full 换到 audio；自定义 demuxer/codec 精简构建收益低于 Dynamic Feature，暂缓。
3. `libvips_android.so` 已是项目核心图片引擎，收益/风险比低于前两项，暂不优先拆。
4. PHY110 的 split 下载体积已记录；后续增加至少一台不同 density / locale 的设备作横向对照。
5. 已记录两种 Edition 的即时安装代码占用；后续在系统 dex 优化后再次取数。
