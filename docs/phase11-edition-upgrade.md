# Phase 11 – Basic → Office 覆盖升级验收（2026-08-21）

设备：OnePlus PHY110 · Android 16 / API 36 · JDK17 + SDK36

对应《OpenConvert 1.0 后续开发计划书》阶段 09 的最后一项缺口。

## 结论

Basic Release 可以由 Office Release 原位覆盖，不清应用数据；升级后 Room 历史、自定义预设和
系统 SAF 持久授权全部保留，LibreOfficeKit 能力可用，DOCX/PPTX/XLSX → PDF 实际转换通过。

两个主 APK 和两个 Release 测试 APK 使用同一证书：
`SHA-256 65776a273239fa049ffadcf95dc0f8a70d890d787c2370707a9b0c19b2f1d6ee`。
Basic 与 Office 的 applicationId 都是 `com.openconvert.app`，versionCode 都是 `100`；版本名分别为
`1.0.0` 与 `1.0.0-office`。

## 验收链路

1. 构建签名的 Basic/Office Release 与对应 Release instrumentation APK。
2. `adb install -r` 安装 Basic，不卸载、不清除既有应用数据。
3. 把已跟踪的 `device-artifacts/OpenConvert_E2E_Source.jpg` 以唯一文件名放入 Downloads，并从
   Basic 的真实系统文件选择器选择，应用取得系统 DocumentsProvider 的持久读授权。
4. Basic 测试写入固定 ID 的完成历史记录与自定义预设，确认 SAF URI 可查询、可读取且 JPEG 头正确。
5. `adb install -r` 用 Office 覆盖 Basic，再用 Office 测试读取相同 Room 行与 URI grant。
6. 验证 Office Edition 暴露 DOCX → PDF、LOKit 可加载，并在升级后的 Release 上实际转换
   DOCX/PPTX/XLSX 三种格式。
7. 删除固定 ID 的测试行、释放测试 SAF grant、移除测试 APK 与 Downloads 标记文件；保留 Office
   Production Release。

## 自动化资产

- `BasicUpgradeSeedInstrumentedTest`：识别真实系统 SAF grant，写入并回读历史/预设。
- `OfficeUpgradeVerifyInstrumentedTest`：验证升级后的 Room、SAF 和 Office 能力，并提供精确清理用例。
- `scripts/verify-edition-upgrade.ps1`：构建、签名安装、动态查找文件选择器节点、两段测试、Release
  smoke 与清理的完整编排；可用 `-DeviceId` 指定 adb 设备。

Release instrumentation 由 `-PopenconvertTestBuildType=release` 显式启用。由于 AndroidJUnitRunner
运行在目标进程，测试目标构建会额外应用 `release-instrumentation-rules.pro`，保留跨 APK 直接引用的
测试依赖与应用符号；正常 Production Release 不读取该规则。验收结束后会无此参数重建正式 APK。

## 真机结果

| 检查 | 结果 |
|---|---|
| Basic Release 种子 | 1/1，通过；历史、预设、真实 SAF URI 可读 |
| Office 覆盖安装 | `adb install -r` 成功，版本变为 `1.0.0-office` |
| Office 状态验证 | 1/1，通过；两条 Room 状态与 SAF grant 保留 |
| 升级后 Office Release smoke | 1/1，通过；DOCX/PPTX/XLSX → PDF |
| 精确清理 | 1/1，通过；测试行、grant、测试 APK 与标记文件移除 |

## 后续

阶段 09 的双 Edition 发布链已闭环。Release 收尾见 `docs/phase12-release.md`。商店内按需 Office
能力仍应采用 Play Feature Delivery / split APK，不使用已确认在 Android 16 崩溃的 `files/`
动态加载方案。
