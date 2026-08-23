# OpenConvert 1.1.0 安装说明

适用版本：`1.1.0`（Basic `1100` / Office `1101`）  
架构：仅 `arm64-v8a`  
系统：Android 8.0（API 26）及以上，已在 Android 16 真机验证。

当前发行页：[v1.1.0](https://github.com/donalaaaaaaa/OpenConvert/releases/tag/v1.1.0)。体积和哈希只认该页的 `BUILD_INFO.txt` / `SHA256SUMS.txt`。

## 选哪一个包

两个 Edition 同一 `applicationId`、同一 v2 签名：

| 包 | 适合谁 | Release APK | Office → PDF |
|---|---|---:|---|
| **Basic** `OpenConvert-v1.1.0-basic-arm64-v8a.apk` | 只要图片 / 音视频 / PDF / 压缩包 | 31.40 MiB | 否 |
| **Office** `OpenConvert-v1.1.0-office-arm64-v8a.apk` | 还需要 DOCX/DOC/PPTX/PPT/XLSX/XLS → PDF | 102.85 MiB | 是，内置 LibreOfficeKit |

商店上传请用对应的 `.aab`（Basic 56.49 MiB / Office 127.95 MiB）。AAB 本身不是用户下载体积。

证书：

```text
CN=OpenConvert
SHA-256  887ce064a82998c27978c373d8405dcee12d36deb36bc9e837c27fe957c5b8a5
```

**已装 v1.0.0 的用户不能覆盖升级**（旧证 `65776a27…`）。先卸载再装，本地历史会丢。

## 侧载安装

1. 在 [GitHub Releases](https://github.com/donalaaaaaaa/OpenConvert/releases/tag/v1.1.0) 下载对应 APK 与 `SHA256SUMS.txt`。
2. 校验哈希（PowerShell）：

   ```powershell
   Get-FileHash .\OpenConvert-v1.1.0-office-arm64-v8a.apk -Algorithm SHA256
   ```

3. 在系统设置里允许该文件管理器 / 浏览器「安装未知应用」。
4. 打开 APK 安装。首次启动后，若要用后台通知，请在系统弹窗里允许通知。

应用 **不申请存储权限**，输入输出都走系统文件选择器（SAF）。

## Basic → Office 原地升级

同为 v1.1、同一 v2 证时，图形安装器可直接覆盖（`1100` → `1101`），并保留：

- 转换历史（Room）
- 自定义预设
- 已授予的 SAF 持久读权限

PHY110 上已用签名包验收：`scripts/verify-edition-upgrade.ps1`。

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
