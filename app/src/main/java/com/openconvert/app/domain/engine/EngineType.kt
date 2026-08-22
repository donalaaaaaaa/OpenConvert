package com.openconvert.app.domain.engine

/**
 * 本机可用的转换引擎。由 `ConversionPlanner` 选择，`ConverterRegistry` 执行。
 *
 * 历史：这里曾与 `ConversionEngineSelector` 同文件，但 Selector 只回答"用哪个引擎"
 * 且没有任何调用方（死代码），已被 `domain.planner.ConversionPlanner` 取代——
 * 后者把引擎选择、编码模式、并发槽位与空间预检合成一次决策。
 */
enum class EngineType(val displayName: String) {
    LIBVIPS("libvips"),
    BITMAP_FACTORY("BitmapFactory"),
    MEDIA3_MEDIACODEC("Media3 / MediaCodec"),
    LITR("LiTr / MediaCodec"),
    FFMPEG_KIT("FFmpegKit"),
    LIBREOFFICE_KIT("LibreOfficeKit"),
    PDFBOX("PDFBox"),
    COMMONS_COMPRESS("Commons Compress"),
}
