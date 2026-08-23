package com.openconvert.app.domain.error

import com.openconvert.app.AppCopy
import com.openconvert.app.R
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.planner.PlanRejection
import com.openconvert.app.domain.planner.PlanRejectionMessages

/**
 * 任务中心 2.0 的错误呈现（计划书 §7.3）。
 *
 * 硬性要求：不允许只显示 "Conversion failed"。每个失败都要给出
 * **原因** + 可能的**下一步**。
 *
 * 各 Converter 内部已有 `toUserMessage()` 产出的中文短句，这里在其之上
 * 补一层结构化呈现：标题（发生了什么）、明细（具体数字/编码名）、
 * 建议（用户能做什么）。已有的裸字符串作为标题回落，保证不丢信息。
 */
data class ErrorPresentation(
    val title: String,
    val detail: String? = null,
    val suggestion: String? = null,
) {
    /** 通知栏/单行场景用的压缩表示。 */
    val oneLine: String get() = listOfNotNull(title, detail).joinToString(" — ")
}

object ErrorPresenter {

    /** 兜底文案：绝不出现在 UI 上的空错误。 */
    private const val FALLBACK_TITLE = "转换失败"

    private fun copy(id: Int, fallback: String, vararg args: Any): String =
        if (args.isEmpty()) AppCopy.getOr(id, fallback) else AppCopy.getOr(id, fallback, *args)

    fun fromRejection(rejection: PlanRejection): ErrorPresentation = when (rejection) {
        is PlanRejection.InsufficientSpace -> ErrorPresentation(
            title = copy(R.string.error_storage, "存储空间不足"),
            detail = copy(
                R.string.error_storage_detail,
                "需要 ${PlanRejectionMessages.formatBytes(rejection.requiredBytes)}，当前剩余 ${PlanRejectionMessages.formatBytes(rejection.availableBytes)}",
                PlanRejectionMessages.formatBytes(rejection.requiredBytes),
                PlanRejectionMessages.formatBytes(rejection.availableBytes),
            ),
            suggestion = copy(R.string.error_storage_hint, "清理缓存或删除部分文件后重试"),
        )

        is PlanRejection.NoUsableEncoder -> ErrorPresentation(
            title = copy(R.string.error_codec_hw, "当前设备硬件不支持该视频编码"),
            detail = copy(
                R.string.error_codec_tried,
                "已尝试：" + rejection.attempted.joinToString("、") { PlanRejectionMessages.engineLabel(it) },
                rejection.attempted.joinToString("、") { PlanRejectionMessages.engineLabel(it) },
            ),
            suggestion = copy(R.string.error_codec_hw_hint, "改用 MP4 目标格式，或降低分辨率后重试"),
        )

        is PlanRejection.UnsupportedRoute -> ErrorPresentation(
            title = copy(
                R.string.error_unsupported_route,
                "暂不支持 ${rejection.input} → ${rejection.target}",
                rejection.input,
                rejection.target,
            ),
            suggestion = copy(R.string.error_unsupported_hint, "在工具页查看该格式可用的操作"),
        )

        is PlanRejection.InvalidInput -> ErrorPresentation(
            title = copy(R.string.error_invalid_input, "文件无法处理"),
            detail = rejection.detail,
            suggestion = copy(R.string.error_invalid_input_hint, "确认文件完整后重新选择"),
        )
    }

    fun fromException(exception: ConversionException): ErrorPresentation = when (exception) {
        is ConversionException.InsufficientStorage -> ErrorPresentation(
            title = copy(R.string.error_storage, "存储空间不足"),
            detail = copy(
                R.string.error_storage_detail,
                "需要 ${PlanRejectionMessages.formatBytes(exception.requiredBytes)}，当前剩余 ${PlanRejectionMessages.formatBytes(exception.availableBytes)}",
                PlanRejectionMessages.formatBytes(exception.requiredBytes),
                PlanRejectionMessages.formatBytes(exception.availableBytes),
            ),
            suggestion = copy(R.string.error_storage_hint, "清理缓存或删除部分文件后重试"),
        )

        is ConversionException.CodecUnavailable -> ErrorPresentation(
            title = copy(R.string.error_codec, "当前设备不支持该媒体编码"),
            detail = exception.codecName,
            suggestion = copy(R.string.error_codec_hint, "改用 MP4 / MP3 等通用格式"),
        )

        is ConversionException.OutOfMemoryRisk -> ErrorPresentation(
            title = copy(R.string.error_oom, "文件分辨率过高，内存不足"),
            suggestion = copy(R.string.error_oom_hint, "选择更小的输出尺寸后重试"),
        )

        is ConversionException.PasswordRequired -> ErrorPresentation(
            title = copy(R.string.error_password, "该 PDF 受密码保护"),
            suggestion = copy(R.string.error_password_hint, "在 PDF 加密/解密工具中输入密码"),
        )

        is ConversionException.WrongPassword -> ErrorPresentation(
            title = copy(R.string.error_wrong_password, "密码错误"),
            suggestion = copy(R.string.error_wrong_password_hint, "确认密码后重试"),
        )

        is ConversionException.PermissionDenied -> ErrorPresentation(
            title = copy(R.string.error_permission, "没有访问该文件的权限"),
            suggestion = copy(R.string.error_permission_hint, "重新选择文件以重新授权"),
        )

        is ConversionException.InvalidFile -> ErrorPresentation(
            title = copy(R.string.error_invalid_file, "文件无效或已损坏"),
            detail = exception.debugDetails.takeIf { it.isNotBlank() },
            suggestion = copy(R.string.error_invalid_file_hint, "换一个文件重试"),
        )

        is ConversionException.UnsupportedFormat -> ErrorPresentation(
            title = exception.userFriendlyMessage,
            suggestion = copy(R.string.error_unsupported_hint, "在工具页查看该格式可用的操作"),
        )

        is ConversionException.TaskCancelled -> ErrorPresentation(
            title = copy(R.string.error_cancelled, "任务已取消"),
        )

        is ConversionException.Interrupted -> ErrorPresentation(
            title = copy(R.string.error_interrupted, ConversionRecoveryMessage.ORPHAN),
            suggestion = copy(R.string.error_interrupted_hint, "重新开始该转换"),
        )

        is ConversionException.ArchiveExpansionLimit -> ErrorPresentation(
            title = copy(R.string.error_archive_limit, "压缩包超出安全限制"),
            detail = exception.userFriendlyMessage,
            suggestion = copy(R.string.error_archive_limit_hint, "换一个压缩包，或减少其中的文件后重试"),
        )

        is ConversionException.EngineFailure -> ErrorPresentation(
            title = copy(R.string.error_engine, "转换引擎执行失败"),
            suggestion = copy(R.string.error_engine_hint, "可尝试切换兼容模式后重试"),
        )

        is ConversionException.Unknown -> ErrorPresentation(
            title = exception.userFriendlyMessage.ifBlank { copy(R.string.error_fallback, FALLBACK_TITLE) },
            suggestion = copy(R.string.error_confirm_retry, "确认文件正常后重试"),
        )
    }

    /**
     * Room 里存的是历史遗留的裸字符串。识别已知模式给出建议，
     * 否则原样作标题——绝不丢失既有信息，也绝不显示空错误。
     */
    fun fromStoredMessage(
        message: String?,
        sourceFormat: FileFormat? = null,
        targetFormat: FileFormat? = null,
        code: ConversionError.Code? = null,
    ): ErrorPresentation {
        val text = message?.trim().orEmpty()
        val resolved = code ?: ConversionError.fromMessage(text.takeIf { it.isNotEmpty() })
        if (text.isEmpty()) {
            return fromCode(resolved)
        }
        // 明显是占位/英文技术串时补一句人话。
        if (text.equals("Conversion failed", ignoreCase = true)) {
            val route = if (sourceFormat != null && targetFormat != null) {
                "${sourceFormat.displayName} → ${targetFormat.displayName}"
            } else {
                null
            }
            return ErrorPresentation(
                title = copy(R.string.error_fallback, FALLBACK_TITLE),
                detail = route,
                suggestion = copy(R.string.error_confirm_retry, "确认文件完整后重试"),
            )
        }
        return when {
            text.contains("空间不足") -> ErrorPresentation(
                title = text,
                suggestion = copy(R.string.error_storage_hint, "清理缓存或删除部分文件后重试"),
            )
            text.contains("权限") -> ErrorPresentation(
                title = text,
                suggestion = copy(R.string.error_permission_hint, "重新选择文件以重新授权"),
            )
            text.contains("损坏") || text.contains("无效") -> ErrorPresentation(
                title = text,
                suggestion = copy(R.string.error_invalid_file_hint, "换一个文件重试"),
            )
            text.contains("内存") || text.contains("分辨率过高") -> ErrorPresentation(
                title = text,
                suggestion = copy(R.string.error_oom_hint, "选择更小的输出尺寸后重试"),
            )
            text.contains("中断") -> ErrorPresentation(
                title = text,
                suggestion = copy(R.string.error_interrupted_hint, "重新开始该转换"),
            )
            text.contains("超出安全限制") ||
                text.contains("展开体积") ||
                text.contains("不安全路径") ||
                text.contains("文件数量超过") -> ErrorPresentation(
                title = text,
                suggestion = copy(R.string.error_archive_limit_hint, "换一个压缩包，或减少其中的文件后重试"),
            )
            else -> {
                val coded = fromCode(resolved)
                ErrorPresentation(title = text, suggestion = coded.suggestion.takeIf { resolved != ConversionError.Code.UNKNOWN })
            }
        }
    }

    fun fromCode(code: ConversionError.Code): ErrorPresentation = when (code) {
        ConversionError.Code.INSUFFICIENT_STORAGE -> ErrorPresentation(
            title = copy(R.string.error_storage, "存储空间不足"),
            suggestion = copy(R.string.error_storage_hint, "清理缓存或删除部分文件后重试"),
        )
        ConversionError.Code.PERMISSION_DENIED -> ErrorPresentation(
            title = copy(R.string.error_permission, "没有访问该文件的权限"),
            suggestion = copy(R.string.error_permission_hint, "重新选择文件以重新授权"),
        )
        ConversionError.Code.INVALID_FILE -> ErrorPresentation(
            title = copy(R.string.error_invalid_file, "文件无效或已损坏"),
            suggestion = copy(R.string.error_invalid_file_hint, "换一个文件重试"),
        )
        ConversionError.Code.OUT_OF_MEMORY -> ErrorPresentation(
            title = copy(R.string.error_oom, "文件分辨率过高，内存不足"),
            suggestion = copy(R.string.error_oom_hint, "选择更小的输出尺寸后重试"),
        )
        ConversionError.Code.INTERRUPTED -> ErrorPresentation(
            title = copy(R.string.error_interrupted, ConversionRecoveryMessage.ORPHAN),
            suggestion = copy(R.string.error_interrupted_hint, "重新开始该转换"),
        )
        ConversionError.Code.TASK_CANCELLED -> ErrorPresentation(
            title = copy(R.string.error_cancelled, "任务已取消"),
        )
        ConversionError.Code.CODEC_UNAVAILABLE -> ErrorPresentation(
            title = copy(R.string.error_codec, "当前设备不支持该媒体编码"),
            suggestion = copy(R.string.error_codec_hint, "改用 MP4 / MP3 等通用格式"),
        )
        ConversionError.Code.UNSUPPORTED_FORMAT -> ErrorPresentation(
            title = copy(R.string.error_unsupported, "暂不支持该格式转换"),
            suggestion = copy(R.string.error_unsupported_hint, "在工具页查看该格式可用的操作"),
        )
        ConversionError.Code.PASSWORD_REQUIRED -> ErrorPresentation(
            title = copy(R.string.error_password, "该 PDF 受密码保护"),
            suggestion = copy(R.string.error_password_hint, "在 PDF 加密/解密工具中输入密码"),
        )
        ConversionError.Code.WRONG_PASSWORD -> ErrorPresentation(
            title = copy(R.string.error_wrong_password, "密码错误"),
            suggestion = copy(R.string.error_wrong_password_hint, "确认密码后重试"),
        )
        ConversionError.Code.ENGINE_FAILURE -> ErrorPresentation(
            title = copy(R.string.error_engine, "转换引擎执行失败"),
            suggestion = copy(R.string.error_engine_hint, "可尝试切换兼容模式后重试"),
        )
        ConversionError.Code.ARCHIVE_EXPANSION_LIMIT -> ErrorPresentation(
            title = copy(R.string.error_archive_limit, "压缩包超出安全限制"),
            suggestion = copy(R.string.error_archive_limit_hint, "换一个压缩包，或减少其中的文件后重试"),
        )
        ConversionError.Code.UNKNOWN -> ErrorPresentation(
            title = copy(R.string.error_fallback, FALLBACK_TITLE),
            suggestion = copy(R.string.error_unknown_hint, "重新选择文件后重试"),
        )
    }
}
