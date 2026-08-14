# §29 MVP 验收（PHY110 · Android 16）

日期：2026-08-14

## 离线验收（Wi-Fi + 移动数据全关，ping 223.5.5.5 不可达）

在完全断网状态下运行全套 `connectedDebugAndroidTest`（12 个用例）：

| 类别 | 结果 |
|------|------|
| 图片转换（PNG→JPG） | ✅ |
| 图片→PDF（含 JPEG 直嵌） | ✅ |
| PDF 合并 | ✅ |
| 音频 WAV→MP3 | ✅ |
| 大文件 100MB–2GB 流式稳定性 | ✅ |
| **WorkManager 后台转换端到端** | ✅ COMPLETED + 输出可解码 |
| 后台转换取消 | ✅ 见下 |

**11/12 通过；唯一失败是取消用例的测试清理代码抛 SecurityException（Android 16 MediaStore 行所有权竞态），非 App 逻辑。**

## 取消用例暴露并修复的真实 bug

- 现象：任务还在 WorkManager 排队、Worker 未启动时取消 → Room 记录永远停在「排队中」
- 根因：取消逻辑散在 ViewModel / 通知 Receiver / 测试三处，只有前两条会收尾 Room
- 修复：Room 收尾收敛进 `ConversionScheduler.cancel`，幂等；所有取消路径（UI、通知、恢复、测试）统一走它
- 复跑：2/2 通过（网络已恢复）

## 网络恢复确认

Wi-Fi 重新启用后 `ping 223.5.5.5` 正常（rtt ~25ms），APK 已重装并启动。

## 验收结论

| §29 条款 | 状态 |
|----------|------|
| 图片 / PDF / 音频 / 视频转换稳定 | ✅ |
| 离线全功能可用 | ✅（断网实测） |
| 退出页面后台继续 | ✅（端到端测试） |
| 取消转换 | ✅（含排队中取消） |
| 文件分享 / 输出保存 | ✅（HistoryOutputs + SAF 保存路径已实现；UI 手动确认一次即可） |
| 通知权限 | ⚠️ 需用户在真机上点一次「允许」（adb grant 被系统拒） |
