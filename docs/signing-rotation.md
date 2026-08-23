# 签名轮换（v1.0.0 → 下一版）

审计结论见 `docs/signing-audit.md`。口令进过 git 历史，但 **jks 从未入库/上架**（情况 A）。本机旧 jks 在审计中改口令后无法再打开，**v1.1.0 使用 v2 证书**。

已装 v1.0.0 的用户不能被 v1.1 覆盖，需卸载重装（本地历史会丢）。

## 证书

| 版本 | 证书 SHA-256 |
|---|---|
| v1.0.0（旧证） | `65776a273239fa049ffadcf95dc0f8a70d890d787c2370707a9b0c19b2f1d6ee` |
| v1.1.0 起（v2） | `887ce064a82998c27978c373d8405dcee12d36deb36bc9e837c27fe957c5b8a5` |

`versionCode`：Basic 1100 / Office 1101。仓库只保留 `signing.properties.example`，真实口令和 jks 永不入库。

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
