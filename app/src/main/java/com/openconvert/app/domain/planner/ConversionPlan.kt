package com.openconvert.app.domain.planner

import com.openconvert.app.domain.converter.EncodeMode
import com.openconvert.app.domain.engine.EngineType

/**
 * Planner 的产物（计划书 §5.2）：真正执行前定下的完整策略。
 * 所有字段都是决策结果，执行层只读不判断。
 */
data class ConversionPlan(
    val primaryEngine: EngineType,
    val fallbackEngine: EngineType?,
    /** 音视频专用；图片/文档/归档为 null。 */
    val encodeMode: EncodeMode?,
    /** true = 不重新编码（Remux / 流拷贝），速度最优。 */
    val isStreamCopy: Boolean,
    val concurrency: ConcurrencySlot,
    /** 预估需要的临时空间，已含安全余量。 */
    val requiredScratchBytes: Long,
    /** 人类可读的决策依据，写进 Benchmark 与任务中心「引擎」一行。 */
    val reason: String,
)

/** 调度槽位（计划书 §5.4：按剩余内存控制并发）。 */
enum class ConcurrencySlot(val label: String) {
    /** 小文件，可与其他任务并行。 */
    PARALLEL("并行"),
    /** 大文件或硬件编码器独占，必须串行。 */
    SERIAL("串行"),
}

/**
 * 拒绝执行的结构化原因。计划书 §7.3 要求错误必须带具体数字/已尝试引擎，
 * 不允许只显示 "Conversion failed"。
 */
sealed interface PlanRejection {
    /** 能力图里没有这条边。 */
    data class UnsupportedRoute(val input: String, val target: String) : PlanRejection

    /** 空间不足，带精确数字供 UI 显示「需要 X / 剩余 Y」。 */
    data class InsufficientSpace(
        val requiredBytes: Long,
        val availableBytes: Long,
    ) : PlanRejection

    /** 硬件编码器缺失且没有软件兜底路径。 */
    data class NoUsableEncoder(val codec: String, val attempted: List<EngineType>) : PlanRejection

    /** 输入文件本身无效（空文件、magic 与容器不符）。 */
    data class InvalidInput(val detail: String) : PlanRejection
}

/** Planner 的返回值：要么给出可执行方案，要么给出带数字的拒绝原因。 */
sealed interface PlanResult {
    data class Ready(val plan: ConversionPlan) : PlanResult
    data class Rejected(val rejection: PlanRejection) : PlanResult
}
