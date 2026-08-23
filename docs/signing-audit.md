# OpenConvert 签名审计（Phase 14）

审计日期：2026-08-23（中国标准时间）  
仓库：`donalaaaaaaa/OpenConvert`，当时 `HEAD` 为 Phase 13 之后的 `main`。  
本文件只记录公开可核对的事实和决定，不含口令、不含 keystore 字节。

## 结论

| 问题 | 结论 |
|---|---|
| `*.jks` / `*.keystore` 是否进过 Git 对象库 | **否**。`git log --all --full-history -- '*.jks' '*.keystore'` 为空；`git rev-list --objects --all` 中无 `jks` / `keystore` / `openconvert-release` 路径 |
| `signing.properties` 是否进过 Git | **否** |
| LFS 是否存过 jks | **否** |
| GitHub Release / Actions Artifact / Issue / PR 是否出现过 jks | **否**。`v1.0.0` 资产只有 Basic/Office APK+AAB 与 `SHA256SUMS.txt`；Actions 产物名只有 `OpenConvert-APKs`；Issue/PR 搜索为空 |
| Wiki | 仓库 `has_wiki=true`，但 `OpenConvert.wiki.git` 不存在，无页面 |
| 口令是否进过 Git 历史 | **是**。`8718db1`（2026-08-14）的 `app/build.gradle.kts` 曾硬编码 store/key 口令（各 14 字符）。`751305d`（2026-08-20）改为 `signing.properties` / 环境变量，工作树已无字面口令 |
| 私钥 / keystore 文件是否曾公开 | **未发现**。按计划书条款，这是 **情况 A**（只有口令泄露） |

`.gitignore` 从仓库首个提交 `568dcf9` 起就忽略 `*.jks`、`*.keystore`、`signing.properties`。

## 证书指纹

从本地 v1.0.0 发布 APK 用 `apksigner verify --print-certs` 读出（与 `docs/install.md` 一致）：

```text
DN       CN=OpenConvert, OU=OpenConvert, O=OpenConvert, L=Local, ST=Local, C=CN
SHA-256  65776a273239fa049ffadcf95dc0f8a70d890d787c2370707a9b0c19b2f1d6ee
SHA-1    20ed25a161694571d0b0b0511377337d6e57b85a
```

| 来源 | 证书 SHA-256 |
|---|---|
| `output/release-1.0/OpenConvert-v1.0.0-basic-arm64-v8a.apk` | `65776a27…d6ee` |
| `output/release-1.0/OpenConvert-v1.0.0-office-arm64-v8a.apk` | 同上 |
| 历史口令可打开时的 `app/openconvert-release.jks` | 同上 |
| `app/openconvert-release-v2.jks`（本地新证） | `887ce064a82998c27978c373d8405dcee12d36deb36bc9e837c27fe957c5b8a5` |

两份 jks 文件 SHA-256 不同，不是同一把钥匙。

## 采用哪张证书

计划书情况 A 允许继续用旧证，好让已装 v1.0.0 的用户覆盖升级。

审计过程中对本地 `app/openconvert-release.jks` 执行了 `keytool -storepasswd`（PKCS12，`-keypasswd` 不受支持）。新口令未写入 `signing.properties`，历史口令随后被 keytool 拒绝。仓库内没有第二份旧 jks。因此 **本机已无法再用 v1 证签名**。

**v1.1.0 采用 v2 证书** `887ce064…b8a5`。

含义：

- 已安装的 v1.0.0（旧证）**不能**被 v1.1 APK 覆盖，必须卸载重装，本地历史会丢。
- 若操作者在其他机器 / 备份 / GitHub Secret 里仍有未改口令的旧 jks，可以恢复情况 A，再改口令并妥善保存。本审计不假设存在这份备份。
- CI 的 `OPENCONVERT_KEYSTORE_BASE64` 必须是 **v2 jks**。`docs/signing-rotation.md` 原先的指向与此一致。

## CI 使用哪个证书

| 构建 | 证书 |
|---|---|
| 已发布 `v1.0.0` | 旧证 `65776a27…d6ee` |
| 今后 tag / `assemble*Release`（本机 `signing.properties` 与 Actions Secrets） | v2 `887ce064…b8a5` |
| debug / 真机 instrumented | Android debug 证，与发布证无关 |

## 本机卫生

- `signing.properties` 仍指向 `app/openconvert-release-v2.jks`，且保持 gitignore。
- 不要把 jks、口令、Secret 值写进文档、Issue 或聊天。
- 旧证文件仍留在本机磁盘上，但已不能用已知口令打开；不要提交它。

## 真机（debug 证，与发布证无关）

PHY110：`com.openconvert.app.debug` 未加 `-r` 即覆盖成功：`100` / `1.0.0-office-debug` → `1100` / `1.1.0-debug` → `1101` / `1.1.0-office-debug`。
