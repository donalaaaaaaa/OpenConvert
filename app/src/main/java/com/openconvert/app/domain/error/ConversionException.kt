package com.openconvert.app.domain.error

sealed class ConversionException(
    val userFriendlyMessage: String,
    val debugDetails: String = "",
    cause: Throwable? = null,
) : Exception(userFriendlyMessage, cause) {

    class UnsupportedFormat(format: String) :
        ConversionException("暂不支持该格式转换: $format", "Format not in ConversionGraph: $format")

    class InvalidFile(reason: String) :
        ConversionException("文件无效或已损坏: $reason", "File validation failed: $reason")

    class PermissionDenied :
        ConversionException("没有读取或写入此文件的权限，请重新授权", "SecurityException on file URI")

    class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) :
        ConversionException("存储空间不足，转换需要至少 ${(requiredBytes / 1024 / 1024)}MB 空间", "StorageGuard rejected: req=$requiredBytes, avail=$availableBytes")

    class CodecUnavailable(val codecName: String) :
        ConversionException("当前设备不支持该媒体编码方式", "Codec unavailable: $codecName")

    class OutOfMemoryRisk :
        ConversionException("文件分辨率过高可能导致内存溢出，请选择更小尺寸后重试", "OOM safeguard triggered")

    class PasswordRequired :
        ConversionException("该 PDF 文件受密码保护，请输入密码后继续", "Encrypted PDF without password")

    class WrongPassword :
        ConversionException("密码错误，无法解密该文件", "Password verification failed")

    class EngineFailure(message: String, cause: Throwable? = null) :
        ConversionException("转换引擎执行失败，可尝试切换兼容模式", message, cause)

    class TaskCancelled :
        ConversionException("任务已被取消", "Cancelled by user")

    class Unknown(message: String, cause: Throwable? = null) :
        ConversionException(message.ifBlank { "转换失败，请确认文件正常后重试" }, message, cause)

    companion object {
        fun wrap(throwable: Throwable): ConversionException {
            return when (throwable) {
                is ConversionException -> throwable
                is SecurityException -> PermissionDenied()
                is java.io.FileNotFoundException -> InvalidFile(throwable.message ?: "找不到文件")
                is OutOfMemoryError -> OutOfMemoryRisk()
                is kotlinx.coroutines.CancellationException -> TaskCancelled()
                else -> Unknown(throwable.message.orEmpty(), throwable)
            }
        }
    }
}
