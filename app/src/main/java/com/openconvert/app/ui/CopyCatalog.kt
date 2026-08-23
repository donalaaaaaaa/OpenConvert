package com.openconvert.app.ui

import com.openconvert.app.R
import com.openconvert.app.domain.error.ConversionError
import com.openconvert.app.domain.error.ConversionRecoveryMessage
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.preset.Preset

object ToolCopy {
    fun convertSectionTitleRes(category: FileCategory): Int = when (category) {
        FileCategory.AUDIO -> R.string.cap_convert_audio
        FileCategory.VIDEO -> R.string.cap_convert_video
        FileCategory.OFFICE -> R.string.cap_convert_office
        FileCategory.ARCHIVE -> R.string.cap_convert_archive
        else -> R.string.cap_convert_image
    }

    fun toolSectionTitleRes(category: FileCategory): Int = when (category) {
        FileCategory.IMAGE -> R.string.cap_tools_image
        FileCategory.PDF -> R.string.cap_tools_pdf
        FileCategory.ARCHIVE -> R.string.cap_tools_archive
        else -> R.string.cap_tools_other
    }

    fun labelRes(kind: ConversionKind): Int = when (kind) {
        ConversionKind.IMAGES_TO_PDF -> R.string.cap_tool_images_to_pdf
        ConversionKind.PDF_TO_IMAGES -> R.string.cap_tool_pdf_to_images
        ConversionKind.PDF_MERGE -> R.string.cap_tool_merge
        ConversionKind.PDF_SPLIT -> R.string.cap_tool_split
        ConversionKind.PDF_DELETE_PAGES -> R.string.cap_tool_delete
        ConversionKind.PDF_ROTATE_PAGES -> R.string.cap_tool_rotate
        ConversionKind.PDF_COMPRESS -> R.string.cap_tool_compress
        ConversionKind.PDF_PAGE_MANAGER -> R.string.cap_tool_pages
        ConversionKind.PDF_SECURITY -> R.string.cap_tool_security
        ConversionKind.PDF_CROP -> R.string.cap_tool_crop
        ConversionKind.PDF_METADATA -> R.string.cap_tool_metadata
        ConversionKind.PDF_WATERMARK -> R.string.cap_tool_watermark
        ConversionKind.ARCHIVE_COMPRESS -> R.string.cap_tool_archive_compress
        ConversionKind.ARCHIVE_EXTRACT -> R.string.cap_tool_archive_extract
        ConversionKind.SINGLE, ConversionKind.BATCH -> R.string.cap_tool_convert
    }

    fun descriptionRes(kind: ConversionKind): Int = when (kind) {
        ConversionKind.IMAGES_TO_PDF -> R.string.cap_tool_images_to_pdf_sub
        ConversionKind.PDF_TO_IMAGES -> R.string.cap_tool_pdf_to_images_sub
        ConversionKind.PDF_MERGE -> R.string.cap_tool_merge_sub
        ConversionKind.PDF_SPLIT -> R.string.cap_tool_split_sub
        ConversionKind.PDF_DELETE_PAGES -> R.string.cap_tool_delete_sub
        ConversionKind.PDF_ROTATE_PAGES -> R.string.cap_tool_rotate_sub
        ConversionKind.PDF_COMPRESS -> R.string.cap_tool_compress_sub
        ConversionKind.PDF_PAGE_MANAGER -> R.string.cap_tool_pages_sub
        ConversionKind.PDF_SECURITY -> R.string.cap_tool_security_sub
        ConversionKind.PDF_CROP -> R.string.cap_tool_crop_sub
        ConversionKind.PDF_METADATA -> R.string.cap_tool_metadata_sub
        ConversionKind.PDF_WATERMARK -> R.string.cap_tool_watermark_sub
        ConversionKind.ARCHIVE_COMPRESS -> R.string.cap_tool_archive_compress_sub
        ConversionKind.ARCHIVE_EXTRACT -> R.string.cap_tool_archive_extract_sub
        ConversionKind.SINGLE, ConversionKind.BATCH -> R.string.cap_tool_convert_sub
    }
}

object ErrorCopy {
    fun titleRes(code: ConversionError.Code): Int = when (code) {
        ConversionError.Code.INSUFFICIENT_STORAGE -> R.string.error_storage
        ConversionError.Code.PERMISSION_DENIED -> R.string.error_permission
        ConversionError.Code.INVALID_FILE -> R.string.error_invalid_file
        ConversionError.Code.OUT_OF_MEMORY -> R.string.error_oom
        ConversionError.Code.INTERRUPTED -> R.string.error_interrupted
        ConversionError.Code.TASK_CANCELLED -> R.string.error_cancelled
        ConversionError.Code.CODEC_UNAVAILABLE -> R.string.error_codec
        ConversionError.Code.UNSUPPORTED_FORMAT -> R.string.error_unsupported
        ConversionError.Code.PASSWORD_REQUIRED -> R.string.error_password
        ConversionError.Code.WRONG_PASSWORD -> R.string.error_wrong_password
        ConversionError.Code.ENGINE_FAILURE -> R.string.error_engine
        ConversionError.Code.ARCHIVE_EXPANSION_LIMIT -> R.string.error_archive_limit
        ConversionError.Code.UNKNOWN -> R.string.error_fallback
    }

    fun suggestionRes(code: ConversionError.Code): Int? = when (code) {
        ConversionError.Code.INSUFFICIENT_STORAGE -> R.string.error_storage_hint
        ConversionError.Code.PERMISSION_DENIED -> R.string.error_permission_hint
        ConversionError.Code.INVALID_FILE -> R.string.error_invalid_file_hint
        ConversionError.Code.OUT_OF_MEMORY -> R.string.error_oom_hint
        ConversionError.Code.INTERRUPTED -> R.string.error_interrupted_hint
        ConversionError.Code.TASK_CANCELLED -> null
        ConversionError.Code.CODEC_UNAVAILABLE -> R.string.error_codec_hint
        ConversionError.Code.UNSUPPORTED_FORMAT -> R.string.error_unsupported_hint
        ConversionError.Code.PASSWORD_REQUIRED -> R.string.error_password_hint
        ConversionError.Code.WRONG_PASSWORD -> R.string.error_wrong_password_hint
        ConversionError.Code.ENGINE_FAILURE -> R.string.error_engine_hint
        ConversionError.Code.ARCHIVE_EXPANSION_LIMIT -> R.string.error_archive_limit_hint
        ConversionError.Code.UNKNOWN -> R.string.error_unknown_hint
    }

    fun interruptedTitle(): String = ConversionRecoveryMessage.ORPHAN
}

object PresetCopy {
    fun nameRes(id: String): Int? = when (id) {
        "img_wechat" -> R.string.preset_img_wechat
        "img_web" -> R.string.preset_img_web
        "img_avatar" -> R.string.preset_img_avatar
        "img_original" -> R.string.preset_img_original
        "img_privacy" -> R.string.preset_img_privacy
        "video_small" -> R.string.preset_video_small
        "video_hd" -> R.string.preset_video_hd
        "video_high_quality" -> R.string.preset_video_high_quality
        "audio_lossless" -> R.string.preset_audio_lossless
        "audio_high" -> R.string.preset_audio_high
        "audio_standard" -> R.string.preset_audio_standard
        "audio_speech" -> R.string.preset_audio_speech
        else -> null
    }

    fun descriptionRes(id: String): Int? = when (id) {
        "img_wechat" -> R.string.preset_img_wechat_desc
        "img_web" -> R.string.preset_img_web_desc
        "img_avatar" -> R.string.preset_img_avatar_desc
        "img_original" -> R.string.preset_img_original_desc
        "img_privacy" -> R.string.preset_img_privacy_desc
        "video_small" -> R.string.preset_video_small_desc
        "video_hd" -> R.string.preset_video_hd_desc
        "video_high_quality" -> R.string.preset_video_high_quality_desc
        "audio_lossless" -> R.string.preset_audio_lossless_desc
        "audio_high" -> R.string.preset_audio_high_desc
        "audio_standard" -> R.string.preset_audio_standard_desc
        "audio_speech" -> R.string.preset_audio_speech_desc
        else -> null
    }

    fun displayName(preset: Preset, resolve: (Int) -> String): String {
        val res = if (preset.isBuiltIn) nameRes(preset.id) else null
        return res?.let(resolve) ?: preset.name
    }
}
