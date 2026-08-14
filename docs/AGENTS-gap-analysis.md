# OpenConvert Spec ↔ Codebase Gap

**Spec:** 附件《OpenConvert Android 全本地文件转换工具 MVP 开发计划》§15 / §26（Week 4）。仓库无独立 `AGENTS.md` 时以该计划为准。

**Product:** 100% 本地 Android 文件转换。图片、PDF、FFmpeg 音视频引擎已接入；Week 4 把转换从 `ViewModel` 协程迁到 WorkManager 前台任务。

## Phase table

| Spec | Mark | Evidence |
|------|------|----------|
| Week 1 UI + SAF + Room | ✅ | Compose 单 Activity、SAF、Room |
| Week 2 图片 + PDF | ✅ | `ImageConverter`、四个 PDF converter + androidTest |
| Week 3 FFmpeg Native | ✅ | `ffmpeg-kit-full:8.1.7`、`MediaConverter` |
| Week 4 WorkManager + FGS + 通知 + 后台 + 异常恢复 + 大文件 | ✅ | `ConversionWorker` / `ConversionNotifier` / `ConversionRecovery`；PHY110 上 100MB–2GB 流式拷贝通过 |

## P0 closed loop（本迭代，已落地）

`选择输出 → enqueue WorkManager → 前台服务通知栏进度 → 切走 App 仍继续 → 取消/失败/杀进程可恢复 → Room 历史可见`

## Remaining P1

- 通知权限需用户在首次转换时授予（系统 `pm grant` 在该机被拒绝）
- 2GB 数字是缓存写入稳定性，不是闪存吞吐基准
- ~~历史页仍无「再次转换 / 打开 / 分享」入口（计划 §17）~~ → ✅ 已落地：点击历史/最近转换条目弹出操作面板（打开 / 分享 / 再次转换 / 删除记录），`reuseConversion` 会带回原质量与分辨率

## Non-gaps

- 无 INTERNET 权限：保持。WorkManager 不加网络约束、不申请开机自启。
- LibreOffice / OCR / 登录 / 云：计划明确暂缓。
- 图片→PDF 走独立 PDF 工具流，不是 `ConversionRouter` 缺口。
