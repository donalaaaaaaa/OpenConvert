package com.openconvert.app.domain.error

sealed class ConversionException(
    val code: ConversionError.Code,
    val userFriendlyMessage: String,
    val debugDetails: String = "",
    cause: Throwable? = null,
) : Exception(userFriendlyMessage, cause) {

    class UnsupportedFormat(format: String) : ConversionException(
        ConversionError.Code.UNSUPPORTED_FORMAT,
        "暂不支持该格式转换: $format",
        "Format not in ConversionGraph: $format",
    )

    class InvalidFile(reason: String) : ConversionException(
        ConversionError.Code.INVALID_FILE,
        "文件无效或已损坏: $reason",
        "File validation failed: $reason",
    )

    class PermissionDenied : ConversionException(
        ConversionError.Code.PERMISSION_DENIED,
        "没有读取或写入此文件的权限，请重新授权",
        "SecurityException on file URI",
    )

    class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : ConversionException(
        ConversionError.Code.INSUFFICIENT_STORAGE,
        "存储空间不足，转换需要至少 ${(requiredBytes / 1024 / 1024)}MB 空间",
        "StorageGuard rejected: req=$requiredBytes, avail=$availableBytes",
    )

    class CodecUnavailable(val codecName: String) : ConversionException(
        ConversionError.Code.CODEC_UNAVAILABLE,
        "当前设备不支持该媒体编码方式",
        "Codec unavailable: $codecName",
    )

    class OutOfMemoryRisk : ConversionException(
        ConversionError.Code.OUT_OF_MEMORY,
        "文件分辨率过高可能导致内存溢出，请选择更小尺寸后重试",
        "OOM safeguard triggered",
    )

    class PasswordRequired : ConversionException(
        ConversionError.Code.PASSWORD_REQUIRED,
        "该 PDF 文件受密码保护，请输入密码后继续",
        "Encrypted PDF without password",
    )

    class WrongPassword : ConversionException(
        ConversionError.Code.WRONG_PASSWORD,
        "密码错误，无法解密该文件",
        "Password verification failed",
    )

    class EngineFailure(message: String, cause: Throwable? = null) : ConversionException(
        ConversionError.Code.ENGINE_FAILURE,
        "转换引擎执行失败，可尝试切换兼容模式",
        message,
        cause,
    )

    class TaskCancelled : ConversionException(
        ConversionError.Code.TASK_CANCELLED,
        "任务已被取消",
        "Cancelled by user",
    )

    class Interrupted : ConversionException(
        ConversionError.Code.INTERRUPTED,
        ConversionRecoveryMessage.ORPHAN,
        "Process killed or work vanished",
    )

    class ArchiveExpansionLimit(
        val reason: com.openconvert.app.domain.converter.ArchiveRejectReason,
    ) : ConversionException(
        ConversionError.Code.ARCHIVE_EXPANSION_LIMIT,
        reason.userMessage(),
        "ArchiveExtractionPolicy rejected: $reason",
    )

    class Unknown(message: String, cause: Throwable? = null) : ConversionException(
        ConversionError.Code.UNKNOWN,
        message.ifBlank { "转换失败，请确认文件正常后重试" },
        message,
        cause,
    )

    companion object {
        fun wrap(throwable: Throwable): ConversionException = when (throwable) {
            is ConversionException -> throwable
            is SecurityException -> PermissionDenied()
            is java.io.FileNotFoundException -> InvalidFile(throwable.message ?: "找不到文件")
            is OutOfMemoryError -> OutOfMemoryRisk()
            is kotlinx.coroutines.CancellationException -> TaskCancelled()
            else -> Unknown(throwable.message.orEmpty(), throwable)
        }
    }
}

/** 避免 ConversionException 直接依赖 work 包。 */
object ConversionRecoveryMessage {
    const val ORPHAN = "上次转换被系统中断，请重试"
}
