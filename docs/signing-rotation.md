# 签名轮换（v1.0.0 → 下一版）

## 为什么换

旧 `app/openconvert-release.jks` 的口令曾进入 git 历史，必须视为泄露。
v1.0.0 仍用该证书发布，这样已经安装的用户可以覆盖升级。

**下一正式版必须使用新 keystore**，并且：

- `versionCode` ≥ 1100（v1.1.0：Basic 1100 / Office 1101，系统安装器才允许覆盖 1.0.0）
- 已装旧证应用**不能**被新证 APK 覆盖，用户需卸载重装（会丢掉本地历史）
- 仓库只保留 `signing.properties.example`，真实口令和 jks 永不入库

## 本地文件（均已 gitignore）

| 路径 | 用途 |
|---|---|
| `app/openconvert-release.jks` | 旧证，仅用于给已装 1.0.0 打补丁 |
| `app/openconvert-release-v2.jks` | 新证，下一版正式签名 |
| `signing.properties` | 当前本机构建指向哪一套 |

`signing.properties` 字段：

```
storeFile=app/openconvert-release-v2.jks
storePassword=...
keyAlias=openconvert
keyPassword=...
```

切回旧证覆盖 1.0.0 时把 `storeFile` 改回 `app/openconvert-release.jks`。

## GitHub Actions Secrets

| Secret | 含义 |
|---|---|
| `OPENCONVERT_KEYSTORE_BASE64` | v2 jks 的 base64 |
| `OPENCONVERT_STORE_PASSWORD` | store 口令 |
| `OPENCONVERT_KEY_ALIAS` | `openconvert` |
| `OPENCONVERT_KEY_PASSWORD` | key 口令 |

Tag 工作流解码 keystore 后走现有 Gradle 环境变量，不写进日志。

## 生成命令（需要换证时）

```powershell
keytool -genkeypair -v `
  -keystore app/openconvert-release-v2.jks `
  -alias openconvert `
  -keyalg RSA -keysize 2048 -validity 10950 `
  -dname "CN=OpenConvert, OU=OpenConvert, O=OpenConvert, L=Local, ST=Local, C=CN"
```

不要把口令写进提交、Issue 或聊天记录。
