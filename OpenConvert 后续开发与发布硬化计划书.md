# OpenConvert 后续开发与发布硬化计划书

## 一、项目现状

OpenConvert 当前已经完成主要产品化能力，包括：

- Android 本地文件转换
- 图片、音视频、PDF、Office、压缩包处理
- Basic / Office 双 Edition
- ConversionPlanner
- WorkManager 任务调度
- 批量转换
- 自定义 Preset
- Benchmark
- 任务中心
- Room 历史记录
- PDF 工具箱
- 7Z / XZ
- FFmpegKit 精简
- TalkBack 无障碍支持
- 字符串资源外置
- GitHub Actions CI
- x86_64 Emulator Smoke Test
- arm64 真机 Instrumented Test
- 4 GB 大文件测试
- 自动 GitHub Release 基础链路
- Release 签名 Secrets
- 新版 v2 Keystore

因此后续开发目标不再是继续大量增加格式，而是提升：

1. 发布可靠性
2. 安全性
3. 版本升级体验
4. CI 完整性
5. 法律与许可证合规
6. 国际化
7. 开源项目治理

---

# 二、总体目标

下一阶段建议分为：

- Phase 13：版本与发布体系
- Phase 14：签名安全审计
- Phase 15：Release CI 硬化
- Phase 16：压缩包安全
- Phase 17：许可证与供应链治理
- Phase 18：国际化
- Phase 19：GitHub 开源治理
- Phase 20：长期测试与稳定性

建议完成 Phase 13～17 后发布：

**OpenConvert v1.1.0**

Phase 18～20 可继续进入：

**OpenConvert v1.2.0**

---

# 三、Phase 13：版本体系重构

优先级：

**P0**

## 目标

彻底解决：

- Git Tag 是 v1.1.0
- APK 内还是 1.0.0
- Basic / Office versionCode 相同
- Basic → Office 需要 adb 覆盖

这些版本管理问题。

## 13.1 建立统一版本配置

**状态：已接。** 未打 `v1.1.0` tag。

工作树：

```text
gradle.properties
OPENCONVERT_VERSION_NAME=1.1.0
OPENCONVERT_VERSION_CODE=1100
```

对应：

```text
Basic
versionCode = 1100
versionName = 1.1.0

Office
versionCode = 1101
versionName = 1.1.0-office
```

未来：

```text
1.2.0

Basic  = 1200
Office = 1201
```

## 13.2 Gradle 改造

**状态：已接。** `app/build.gradle.kts` 从 `gradle.properties` 读取；Office `versionCode = base + 1`。

## 13.3 CI 增加版本校验

**状态：已接。** `:app:verifyOpenConvertVersion` 每趟 CI 跑；`GITHUB_REF=refs/tags/v*` 时 tag 必须等于 `OPENCONVERT_VERSION_NAME`，否则失败。未打 tag 前此项只锁住属性格式。

## 验收标准

满足：

- Basic APK versionCode > 旧版 — 已验：Release 清单 `1100` / `1.1.0`
- Office APK versionCode > Basic — 已验：Release 清单 `1101` / `1.1.0-office`
- Basic 可直接升级 Office — PHY110 debug：`100` office → `1100` basic → `1101` office，均未加 `-r` 即 Success。发布证覆盖仍取决于证书（v1.1 用 v2，已装 1.0 不能覆盖）
- Tag 与 APK versionName 一致 — CI 已接线（错 tag 会红）；未打 `v1.1.0`
- CI 自动检测版本不一致 — `:app:verifyOpenConvertVersion`
- 不再依赖 adb 进行普通升级 — 安装器侧已具备条件；1.0.0→1.1.0 还取决于签名（Phase 14）

---

# 四、Phase 14：签名安全审计

优先级：

**P0**

## 背景

旧 Keystore 密码曾经进入 Git 历史。

但需要区分：

```text
密码泄露
```

和：

```text
Keystore / 私钥本身泄露
```

两者风险完全不同。

## 14.1 检查 Git 历史

**状态：已接。** 见 `docs/signing-audit.md`。

`git log --all --full-history -- '*.jks' '*.keystore'` 与 `git rev-list --objects --all` 均无 jks。`signing.properties` 从未入库。口令曾硬编码在 `8718db1` 的 `app/build.gradle.kts`，`751305d` 已外置。

## 14.2 检查 GitHub Release

**状态：已接。** `v1.0.0` 只有 APK/AAB/`SHA256SUMS.txt`。Actions 产物名只有 `OpenConvert-APKs`。Issue/PR/LFS 无 jks。wiki git 不存在。

## 14.3 决策

**情况 A（证据）+ 操作上走 v2。** jks 从未公开，本可留旧证做无缝覆盖。审计中本机旧 jks 改口令后无法打开，v1.1.0 使用 v2 证 `887ce064…b8a5`。已装 v1.0.0 需卸载重装。

## 验收标准

`docs/signing-audit.md` 已写：历史检查、未见 jks 入库、口令曾泄露、采用 v2、两证 SHA-256、CI 用 v2。

---

# 五、Phase 15：GitHub Release CI 硬化

优先级：

**P0**

## 15.1 发布构建禁止 unsigned

**状态：已接。** Tag 上 `scripts/verify-release-artifacts.sh --require-secrets` 缺任一 Secret 即 `exit 1`。Gradle `-PopenconvertRequireReleaseSigning=true` 在无凭据时配置期失败。本地开发仍可产出 unsigned。

## 15.2 验证 APK 签名

**状态：已接。** `apksigner verify` + 证书 SHA-256 必须是 v2 `887ce064…`；Basic / Office 必须同一把。

## 15.3 验证版本

**状态：已接。** `aapt dump badging`：Basic `1.1.0` / `1100`，Office `1.1.0-office` / `1101`；`--tag` 必须等于 `OPENCONVERT_VERSION_NAME`。

## 15.4 验证 ABI

**状态：已接。** `native-code` 与 `lib/` 只允许 `arm64-v8a`。

## 15.5 权限最小化

**状态：已接。** workflow 默认 `contents: read`；仅 `publish-release` 为 `contents: write`。

## 15.6 Actions 固定版本

**状态：已接。** checkout / setup-java / upload-artifact / download-artifact / emulator-runner 均钉到 commit SHA。

## 验收标准

无签名、签名错误、Tag 错误、versionName / versionCode 错误、ABI 错误 → `build-and-test` 失败，`publish-release` 不跑，不能创建 Release。

---

# 六、Phase 16：Release Notes 自动化

优先级：

**P0**

**状态：已接。** 人写的说明在 `docs/releases/vX.Y.Z.md`，必须带 `<!-- RELEASE_DOWNLOADS -->`。Tag 构建跑 `scripts/package-release.sh`，用实测 APK/AAB 写出 `RELEASE_NOTES.md`、`SHA256SUMS.txt`、`BUILD_INFO.txt` 再发布。体积不再手写。

## 验收标准

GitHub Release 的文件名、大小、SHA256、versionName、versionCode、证书指纹全部来自本次产物。

---

# 七、Phase 17：Archive 安全硬化

优先级：

**P1**

**状态：已接。** `ArchiveExtractionPolicy` 限制 entry 数 / 单文件 / 总展开 / 压缩比 / 深度 / 文件名。解压恢复目录、拒绝 `../` 与盘符路径、重名改为 `photo (1).jpg`，超限返回 `ARCHIVE_EXPANSION_LIMIT` 并删除未完成输出。

OpenConvert 开始支持：

- ZIP
- TAR
- GZIP
- BZIP2
- XZ
- 7Z

后，就必须把压缩包视为：

```text
不可信输入
```

## 17.1 新增 ArchiveExtractionPolicy

新增：

```text
ArchiveExtractionPolicy
```

负责限制：

```text
最大 Entry 数量
最大单文件展开大小
最大总展开大小
最大压缩倍率
最大目录深度
最大文件名长度
```

建议默认：

```text
最大 Entry：
10000

最大单文件：
用户剩余空间的合理比例

最大总展开：
根据 StorageGuard 动态计算

最大压缩倍率：
例如 1000x

最大路径深度：
32
```

## 17.2 防 Zip Bomb

统计：

```text
compressedSize
uncompressedSize
bytesWritten
```

达到阈值立即：

```text
停止解压
删除未完成输出
返回结构化错误
```

错误：

```text
ARCHIVE_EXPANSION_LIMIT
```

## 17.3 恢复目录结构

现在如果：

```text
a/config.json
b/config.json
```

全部只取：

```text
config.json
```

会出现重名。

应该安全恢复目录结构，同时拒绝：

```text
../
../../
绝对路径
Windows drive path
```

## 17.4 重名文件策略

出现：

```text
photo.jpg
photo.jpg
```

推荐：

```text
photo.jpg
photo (1).jpg
```

而不是静默覆盖。

## 17.5 增加恶意样本测试

增加：

```text
Zip Slip
10000 entries
超长文件名
重复文件名
高压缩比
深层目录
空 Entry
损坏 7Z
截断 TAR
伪造扩展名
```

## 验收标准

恶意压缩包：

- 不能目录穿越
- 不能无限占用空间
- 不能导致 OOM
- 不能静默覆盖文件
- 能返回明确错误

---

# 八、Phase 18：第三方许可证治理

优先级：

**P1**

**状态：已接。** `THIRD_PARTY_NOTICES.md` 列出发行包组件、版本、许可证和 LGPL/MPL 源码地址。设置 → 关于 → 开源许可证 读 APK 内 `assets/THIRD_PARTY_NOTICES.md`。`packaging.resources.excludes` 只去掉冲突的 `AL2.0`/`LGPL2.1` 残片，不排除 `NOTICE`/`LICENSE*`。

新增：

```text
THIRD_PARTY_NOTICES.md
```

至少列出：

| 组件 | 版本 | License | 用途 |
|---|---|---|---|
| libvips | 8.18.5 | LGPL | 图片 |
| LibreOfficeKit | 对应版本 | MPL-2.0 | Office |
| FFmpegKit | 8.1.7 | LGPL | 音频 |
| PdfBox Android | 对应版本 | Apache | PDF |
| Commons Compress | 对应版本 | Apache | Archive |
| LiTr | 对应版本 | License | WEBM |
| AndroidX | 对应版本 | Apache | Android |

增加：

```text
设置
→ 关于
→ 开源许可证
```

## 检查重点

确认：

```text
packaging.resources.excludes
```

没有误删必须分发的许可证文件。

## 验收标准

Release 包内或应用中可以查看第三方许可证信息。

---

# 九、Phase 19：国际化

优先级：

**P1 / P2**

**状态：已接。** 默认 `res/values` 仍是中文。`res/values-en/strings.xml` 与中文 1:1 覆盖首页、转换、Task Center、PDF、Archive、Preset、Settings、Error、History。领域文案走 `AppCopy.getOr`。仓库根目录有 `README_EN.md`、`SECURITY.md`、`CONTRIBUTING.md`，中文 README 顶部可切 English。

## 19.1 英文 UI

增加：

```text
res/values-en/strings.xml
```

覆盖全部核心页面：

- 首页
- 转换页
- Task Center
- PDF
- Archive
- Preset
- Settings
- Error
- History

## 19.2 README 国际化

建议：

```text
README.md
README_EN.md
```

README 顶部：

```text
简体中文 | English
```

英文 README 重点强调：

```text
100% offline
No INTERNET permission
No account
No upload
No cloud processing
```

这是 OpenConvert 非常强的卖点。

## 19.3 文档

核心英文文档优先：

```text
README_EN.md
SECURITY.md
CONTRIBUTING.md
```

安装说明可以稍后翻译。

## 验收标准

Android 系统语言切换 English 后：

核心流程无中文残留。

---

# 十、Phase 20：GitHub 开源治理

优先级：

**P1**

**状态：已接。** `SECURITY.md` / `CONTRIBUTING.md` / `CODE_OF_CONDUCT.md` 在仓库根目录。`.github` 有 Issue 表单（bug / feature）、`config.yml` 关掉空白 issue、PR 模板、Dependabot（Actions + Gradle，周一）。Bug 表单强制 Android / 设备 / 版本 / Edition / 输入输出 / 错误码 / 复现步骤，并写明不要上传私人文件。

新增：

```text
SECURITY.md
CONTRIBUTING.md
CODE_OF_CONDUCT.md
```

`.github` 增加：

```text
ISSUE_TEMPLATE/
├── bug.yml
├── feature.yml
└── config.yml

pull_request_template.md
dependabot.yml
```

## Bug Template

要求用户提供：

```text
Android 版本
设备型号
OpenConvert 版本
Basic / Office
输入格式
输出格式
错误代码
复现步骤
```

禁止默认要求用户上传隐私文件。

可以提示：

```text
如果文件包含私人信息，请不要公开上传。
```

---

# 十一、CI 测试矩阵升级

优先级：

**P1**

**状态：已接。** `build-and-test` 在 Unit 之后跑 `lintBasicDebug` + `lintOfficeDebug`。`emulator-smoke` 矩阵 API 31 / 34 / 36（x86_64，`-PopenconvertCiSmoke=true`，不含原生转换）。PR 只跑 API 34；push main / tag / dispatch 跑三档。矩阵由 `smoke-matrix` 输出，禁止在 job `if` 里读 `matrix.*`（会让整趟 workflow 0 秒失败）。Tag 发布 `needs` 构建和全部 smoke。真机仍只走 `workflow_dispatch`。

当前 Hosted Emulator 可以扩展成：

```text
API 31
API 34
API 36
```

不用每次全部跑完整转换。

## PR

运行：

```text
Unit Test
Lint
API 34 Smoke
```

## main

运行：

```text
Unit Test
Lint
API 31 Smoke
API 34 Smoke
API 36 Smoke
```

## Release

运行：

```text
Unit
Lint
Emulator
Release Build
Signature Check
Version Check
ABI Check
SHA256
```

## 手动真机

继续保留：

```text
workflow_dispatch
```

运行：

```text
Office Instrumented
Large File
Force Stop Recovery
Native Conversion
```

---

# 十二、仓库和 LFS 治理

优先级：

**P2**

**状态：已接。** 普通 PR `lfs: false`，不 assemble。main / tag / 真机才拉 LFS 并跑 `require-lfs-objects.sh`。NDK 722MB zip 已从树里拿掉，改走 `native/deps.lock` + `scripts/fetch-native-deps.sh`（版本、URL、SHA256）。`*.zip` 不再全局进 LFS，只留 office assets。

OpenConvert 包含大型：

```text
.so
.aar
.zip
```

不能无限依赖 Git LFS。

## 短期

普通 PR：

```yaml
lfs: false
```

只有：

```text
Office Test
Release
Native Build
```

才：

```yaml
lfs: true
```

## 长期

能从官方重新下载的依赖：

优先：

```text
版本号
下载地址
SHA256
```

通过脚本获取。

避免所有历史版本都永久存进 LFS。

## 验收标准

普通 Kotlin / UI PR 不需要下载几百 MB native 文件。

---

# 十三、暂缓功能

以下功能目前不建议优先：

## 更多图片格式输出

例如：

```text
AVIF
HEIC
TIFF
```

当前收益低于稳定性建设。

## 更多 Office 格式

LibreOfficeKit 已经足够重。

## Dynamic Feature

暂缓。

原因：

```text
当前主要分发方式是 GitHub APK
```

而：

```text
Play Feature Delivery
```

只有正式进入 Google Play 后才值得投入。

## 云端同步

不做。

这是 OpenConvert：

```text
100% Offline
```

最重要的产品定位。

不要为了多一个功能把最有辨识度的卖点自己拆了。

---

# 十四、版本规划

## OpenConvert v1.1.0

目标：

**Release Hardening**

包含：

- 新版本体系
- versionCode 修复
- 签名审计
- CI fail-closed
- 自动 Release Notes
- 自动 SHA256
- APK 签名验证
- ABI 检查
- ArchiveExtractionPolicy
- 压缩炸弹防护
- THIRD_PARTY_NOTICES

完成后：

```text
v1.1.0
```

即可视为第一个真正成熟的公开发行版。

---

# 十五、OpenConvert v1.2.0

目标：

**Open Source & International**

包含：

- English UI
- README_EN
- SECURITY.md
- CONTRIBUTING.md
- Issue Templates
- PR Template
- Dependabot
- API 31 / 34 / 36 CI
- LFS 优化
- 更多跨设备测试

---

# 十六、推荐执行顺序

严格按：

```text
01 版本体系
↓
02 签名历史审计
↓
03 CI Release fail-closed
↓
04 自动 Release Notes
↓
05 APK 签名 / 版本 / ABI 校验
↓
06 Archive 安全硬化
↓
07 第三方许可证
↓
08 发布 v1.1.0
↓
09 英文 UI
↓
10 GitHub 社区治理
↓
11 CI 多 Android 版本
↓
12 LFS 优化
↓
13 发布 v1.2.0
```

---

# 十七、v1.1.0 完成定义

只有下面全部通过才打正式 Tag：

- [x] `versionName = 1.1.0`
- [x] Basic versionCode 正确
- [x] Office versionCode 正确
- [ ] Git Tag 与 versionName 一致
- [x] APK 有正式签名
- [x] 证书 SHA256 正确
- [x] Release 只包含 arm64-v8a
- [x] JVM Unit Tests 全绿
- [x] Emulator Smoke 全绿
- [x] PHY110 Office Instrumented 全绿
- [x] Basic → Office 升级通过
- [x] 任务历史升级后保留
- [x] Preset 升级后保留
- [x] SAF Grant 升级后保留
- [x] Zip Bomb 测试通过
- [x] Zip Slip 测试通过
- [x] 重名压缩包测试通过
- [x] SHA256SUMS 自动生成
- [x] Release Notes 自动生成
- [x] THIRD_PARTY_NOTICES 完成
- [x] README 当前体积数据正确

达到以上状态后：

**OpenConvert v1.1.0 可以作为长期维护版本正式发布。**

---

# 十八、最终目标

OpenConvert 下一阶段不应该继续追求：

> “支持多少种格式。”

而应该开始追求：

> “一个陌生用户敢不敢下载安装，一个陌生开发者敢不敢参与维护，以及半年后自己还能不能安全发布下一版。”

功能广度已经足够。

接下来真正拉开项目质量差距的是：

**Release Engineering + Security + Stability + Documentation + Internationalization。**