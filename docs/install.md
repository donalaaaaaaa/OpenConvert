# OpenConvert 1.0 安装说明

适用版本：`1.0.0`（versionCode `100`）  
架构：仅 `arm64-v8a`  
系统：Android 8.0（API 26）及以上，已在 Android 16 真机验证。

## 选哪一个包

OpenConvert 有两个同签名、同 `applicationId` 的 Edition：

| 包 | 适合谁 | 体积（Release APK） | Office → PDF |
|---|---|---:|---|
| **Basic** `OpenConvert-v1.0.0-basic-arm64-v8a.apk` | 只要图片 / 音视频 / PDF / 压缩包 | 35.39 MiB | 否 |
| **Office** `OpenConvert-v1.0.0-office-arm64-v8a.apk` | 还需要 DOCX/DOC/PPTX/PPT/XLSX/XLS → PDF | 105.29 MiB | 是，内置 LibreOfficeKit |

商店上传请用对应的 `.aab`。AAB 本身不是用户下载体积：PHY110 上 bundletool 估算为 Basic 34.74 MiB、Office 103.60 MiB。

两个包的证书指纹相同：

```text
CN=OpenConvert
SHA-256  65776a273239fa049ffadcf95dc0f8a70d890d787c2370707a9b0c19b2f1d6ee
```

## 侧载安装

1. 在 [GitHub Releases](https://github.com/donalaaaaaaa/OpenConvert/releases) 下载对应 APK 与 `SHA256SUMS.txt`。
2. 校验哈希（PowerShell）：

   ```powershell
   Get-FileHash .\OpenConvert-v1.0.0-office-arm64-v8a.apk -Algorithm SHA256
   ```

3. 在系统设置里允许该文件管理器 / 浏览器「安装未知应用」。
4. 打开 APK 安装。首次启动后，若要用后台通知，请在系统弹窗里允许通知。

应用 **不申请存储权限**，输入输出都走系统文件选择器（SAF）。

## Basic → Office 原地升级

同签名覆盖可以保留：

- 转换历史（Room）
- 自定义预设
- 已授予的 SAF 持久读权限

真机验收用的是：

```text
adb install -r OpenConvert-v1.0.0-office-arm64-v8a.apk
```

注意：v1.0.0 两个 Edition 的 `versionCode` 都是 `100`。系统「打开 APK」安装器在 versionCode 不升高时可能拒绝覆盖；侧载升级请用 `adb install -r`，或先卸载再装（卸载会丢掉本地历史）。下一版 `1.1.0` 起 Basic=`1100`、Office=`1101`，图形安装器可直接覆盖升级。体积和哈希以对应 GitHub Release 的 `BUILD_INFO.txt` / `SHA256SUMS.txt` 为准，不要抄本页 1.0 数字。

不支持把 Office 降回 Basic 并继续转换 Office 文档。轻量版会隐藏 Office → PDF，并对历史 Office 任务给出「需要安装 Office 版」。

## 构建自己的包

需要 JDK 17、Android SDK 36、仓库根目录的 `signing.properties`（见 `signing.properties.example`）。

```powershell
.\gradlew.bat assembleBasicRelease assembleOfficeRelease
.\gradlew.bat bundleBasicRelease bundleOfficeRelease
```

没有签名凭据时，Gradle 会产出 **unsigned** APK 并打印警告，不会回落到硬编码口令。

## 不支持

- 32 位 `armeabi-v7a`、模拟器常用的 `x86_64`
- 在线安装 / 应用内更新（Manifest 不声明 `INTERNET`）
- 把 Office 引擎作为运行时 zip 动态加载（该路径已在 Android 16 上验证崩溃）
