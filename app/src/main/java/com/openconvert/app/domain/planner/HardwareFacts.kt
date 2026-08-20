package com.openconvert.app.domain.planner

/**
 * Planner 需要的硬件事实，抽成接口以便单测在 JVM 上运行
 * （`DeviceCapabilities` 依赖 `MediaCodecList`，只能在设备/instrumented 环境用）。
 */
interface HardwareFacts {
    val hasH264HardwareEncoder: Boolean
    val hasVp8HardwareEncoder: Boolean

    companion object {
        /** 没有任何硬件编码器的保守假设（软件兜底路径）。 */
        val NONE = object : HardwareFacts {
            override val hasH264HardwareEncoder = false
            override val hasVp8HardwareEncoder = false
        }
    }
}

/** 生产实现：读真实 MediaCodecList。 */
class DeviceHardwareFacts : HardwareFacts {
    private val profile by lazy {
        com.openconvert.app.domain.device.DeviceCapabilities.getHardwareProfile()
    }
    override val hasH264HardwareEncoder: Boolean get() = profile.hasH264HardwareEncoder
    override val hasVp8HardwareEncoder: Boolean get() = profile.hasVp8HardwareEncoder
}

/**
 * Planner 需要的运行环境事实（空间、并发判定阈值）。
 */
data class RuntimeFacts(
    /** 临时目录可用字节数。 */
    val usableScratchBytes: Long,
    /** 超过此体积的任务强制串行，避免同时占用多份大缓冲。 */
    val serialThresholdBytes: Long = DEFAULT_SERIAL_THRESHOLD,
) {
    companion object {
        const val DEFAULT_SERIAL_THRESHOLD = 200L * 1024 * 1024
    }
}
