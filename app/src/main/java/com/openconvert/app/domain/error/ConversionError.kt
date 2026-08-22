package com.openconvert.app.domain.error

import com.openconvert.app.domain.planner.PlanRejection

/**
 * 稳定错误码。UI / 通知 / 任务中心都认这个，不再靠裸中文字符串分拣。
 * Room 里存枚举名；旧记录没有码时用 [fromMessage] 回推。
 */
object ConversionError {
    enum class Code {
        UNSUPPORTED_FORMAT,
        INVALID_FILE,
        PERMISSION_DENIED,
        INSUFFICIENT_STORAGE,
        CODEC_UNAVAILABLE,
        OUT_OF_MEMORY,
        PASSWORD_REQUIRED,
        WRONG_PASSWORD,
        ENGINE_FAILURE,
        TASK_CANCELLED,
        INTERRUPTED,
        UNKNOWN,
    }

    fun parse(raw: String?): Code? =
        raw?.let { runCatching { Code.valueOf(it) }.getOrNull() }

    fun fromException(exception: ConversionException): Code = exception.code

    fun fromRejection(rejection: PlanRejection): Code = when (rejection) {
        is PlanRejection.InsufficientSpace -> Code.INSUFFICIENT_STORAGE
        is PlanRejection.NoUsableEncoder -> Code.CODEC_UNAVAILABLE
        is PlanRejection.UnsupportedRoute -> Code.UNSUPPORTED_FORMAT
        is PlanRejection.InvalidInput -> Code.INVALID_FILE
    }

    fun fromMessage(message: String?): Code {
        val text = message?.trim().orEmpty()
        if (text.isEmpty()) return Code.UNKNOWN
        return when {
            text.contains("空间不足") -> Code.INSUFFICIENT_STORAGE
            text.contains("权限") -> Code.PERMISSION_DENIED
            text.contains("密码错误") -> Code.WRONG_PASSWORD
            text.contains("密码") -> Code.PASSWORD_REQUIRED
            text.contains("内存") || text.contains("分辨率过高") -> Code.OUT_OF_MEMORY
            text.contains("中断") -> Code.INTERRUPTED
            text.contains("取消") -> Code.TASK_CANCELLED
            text.contains("损坏") || text.contains("无效") -> Code.INVALID_FILE
            text.contains("编码") && (text.contains("不支持") || text.contains("硬件")) -> Code.CODEC_UNAVAILABLE
            text.contains("不支持") -> Code.UNSUPPORTED_FORMAT
            else -> Code.UNKNOWN
        }
    }
}
