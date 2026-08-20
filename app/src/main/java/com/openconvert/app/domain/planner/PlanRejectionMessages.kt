package com.openconvert.app.domain.planner

import com.openconvert.app.domain.engine.EngineType

/**
 * 把结构化拒绝原因翻译成用户能看懂的中文（计划书 §7.3）。
 *
 * 硬性要求：不允许输出 "Conversion failed" 这类无信息量文案。
 * 空间不足必须带「需要 X / 剩余 Y」；编码器缺失必须说明已尝试哪些引擎。
 */
object PlanRejectionMessages {

    fun describe(rejection: PlanRejection): String = when (rejection) {
        is PlanRejection.UnsupportedRoute ->
            "暂不支持 ${rejection.input} → ${rejection.target}"

        is PlanRejection.InsufficientSpace -> buildString {
            append("存储空间不足\n\n需要：")
            append(formatBytes(rejection.requiredBytes))
            append("\n当前剩余：")
            append(formatBytes(rejection.availableBytes))
        }

        is PlanRejection.NoUsableEncoder -> buildString {
            append("视频编码不受当前设备硬件支持\n\n已尝试：")
            append(rejection.attempted.joinToString("、") { engineLabel(it) })
        }

        is PlanRejection.InvalidInput ->
            "文件无法处理：${rejection.detail}"
    }

    fun engineLabel(engine: EngineType): String = when (engine) {
        EngineType.LIBVIPS -> "libvips"
        EngineType.BITMAP_FACTORY -> "系统解码器"
        EngineType.MEDIA3_MEDIACODEC -> "MediaCodec 硬件编码"
        EngineType.LITR -> "LiTr 硬件编码"
        EngineType.FFMPEG_KIT -> "FFmpeg 软件编码"
        EngineType.LIBREOFFICE_KIT -> "LibreOfficeKit"
        EngineType.PDFBOX -> "PdfBox"
        EngineType.COMMONS_COMPRESS -> "Commons Compress"
    }

    /** 1 位小数的人类可读体积，用于错误文案里的精确数字。 */
    fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format("%.1f GB", bytes / gb)
            bytes >= mb -> String.format("%.1f MB", bytes / mb)
            bytes >= kb -> String.format("%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
