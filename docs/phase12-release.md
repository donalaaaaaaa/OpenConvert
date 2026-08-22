# Phase 12 – Release 1.0 收尾（2026-08-22）

对应《OpenConvert 1.0 后续开发计划书》阶段 10 / §十三「产品发布准备」。

设备与构建基线仍是：OnePlus PHY110 · Android 16 / API 36 · JDK 17 · SDK 36 · 签名证书
`SHA-256 65776a273239fa049ffadcf95dc0f8a70d890d787c2370707a9b0c19b2f1d6ee`。

## 计划书对照

| 计划书条目 | 落地 |
|---|---|
| README：项目截图 | `docs/screenshots/01-home.png` … `05-complete.png`，README 顶部画廊 |
| README：功能 GIF | 本轮没有新的真机录屏；用四张产品截图代替。`device-artifacts/` 里带 `E2E` 文件名的完成页不进 README |
| README：架构图 | README 内 mermaid（检测 → Graph → Planner → 各引擎 → WorkManager） |
| README：Benchmark | 双 Edition 体积表 + 设置页导出说明，细节链到 apk-size baseline |
| README：格式表 / 隐私 / 环境 / 构建 | 已按当前 ConversionGraph 与双 Flavor 命令重写 |
| Release APK / AAB | Basic + Office 均已签名校验，见下方产物表 |
| GitHub Release：APK / ChangeLog / 截图 / 安装 / 已知问题 | 文案与本地产物已齐。**未覆盖** 2026-08-15 的 `v1.0.0` tag 资产：该 tag 指向 `2cb9371`，而双 Edition 与阶段 07–11 仍在未推送/未提交工作树里 |

## 产物

本地目录：`output/release-1.0/`（已 gitignore，不入库）。

| 文件 | 字节 | SHA256 |
|---|---:|---|
| `OpenConvert-v1.0.0-basic-arm64-v8a.apk` | 37,113,429 | `096E5B414125EAEA8DF685559F5DA08C2D536BB10D9F0E7515129682289E304F` |
| `OpenConvert-v1.0.0-office-arm64-v8a.apk` | 110,406,953 | `04C3A9254930A3DED8FD7A408BAB7D771A050F36C2B02EFEEF14686C69285050` |
| `OpenConvert-v1.0.0-basic.aab` | 68,464,949 | `2DE39539313FEF80669D20E2A9D0305F6F64181D32C5218CB33A9616C045EA61` |
| `OpenConvert-v1.0.0-office.aab` | 141,768,423 | `9E4AB623161CAFFB56433AA0BCE45ACA78F824C06059BF74BE474C9609F761D4` |

`apksigner verify --print-certs`：两个 APK 均通过，证书一致。

本轮没有重跑 `assemble*Release`。上述文件是工作树里已有的签名产物（体积与 2026-08-21 基线差约 1–2 KB，证书与 flavor 边界未变）。

## 新增文档

- `README.md` 重写
- `release_notes.md` 改为双 Edition + ChangeLog + 安装摘要
- `docs/install.md`
- `docs/known-issues.md`
- `docs/screenshots/`
- `LICENSE`（README 一直写 Apache-2.0，仓库里原先缺文件）

## 刻意没做的事

1. **没有用新 APK 覆盖 GitHub `v1.0.0`。** tag 仍指向 8 月 15 日单体包。把当前 Office/Basic 挂上去会让 tag 与资产对不上。要发正式双 Edition，需要先提交阶段 07–11 工作树，再决定是更新 `v1.0.0` 还是打 `v1.0.1`。
2. **没有换新 keystore。** 口令已进历史，但换证会使已装用户无法覆盖。发行说明里写明了。
3. **没有抬 Office 的 versionCode。** 与已验收的覆盖升级脚本保持一致；图形安装器限制写进安装说明。
4. **没有录功能 GIF。** 用产品截图。
5. **没有提交阶段 07–11 的代码。** Release 收尾只动展示与发行文件。

## 发布前检查清单（下一步人工确认）

- [ ] 提交并推送阶段 07–11 + 本轮文档
- [ ] 决定更新 `v1.0.0` 还是新建 tag
- [ ] `gh release upload` 四个产物 + `SHA256SUMS.txt`
- [ ] 用 `release_notes.md` 作为 Release body
