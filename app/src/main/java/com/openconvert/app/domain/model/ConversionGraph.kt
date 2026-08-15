package com.openconvert.app.domain.model

/**
 * 转换能力图（计划书 §三十二）：输入格式 → 支持的输出格式。
 * 禁止 UI 出现不支持的组合；也是 ConverterRegistry 的能力声明来源。
 */
object ConversionGraph {

    private val edges: Map<FileFormat, List<FileFormat>> = buildMap {
        // 图片（libvips / BitmapFactory）
        val imageOutputs = listOf(FileFormat.JPG, FileFormat.PNG, FileFormat.WEBP, FileFormat.PDF)
        put(FileFormat.JPG, listOf(FileFormat.PNG, FileFormat.WEBP, FileFormat.PDF))
        put(FileFormat.PNG, listOf(FileFormat.JPG, FileFormat.WEBP, FileFormat.PDF))
        put(FileFormat.WEBP, listOf(FileFormat.JPG, FileFormat.PNG, FileFormat.PDF))
        put(FileFormat.AVIF, listOf(FileFormat.JPG, FileFormat.PNG, FileFormat.WEBP, FileFormat.PDF))
        put(FileFormat.HEIC, listOf(FileFormat.JPG, FileFormat.PNG, FileFormat.WEBP, FileFormat.PDF))
        put(FileFormat.GIF, listOf(FileFormat.JPG, FileFormat.PNG, FileFormat.WEBP, FileFormat.PDF))
        put(FileFormat.BMP, listOf(FileFormat.JPG, FileFormat.PNG, FileFormat.WEBP, FileFormat.PDF))
        put(FileFormat.TIFF, listOf(FileFormat.JPG, FileFormat.PNG, FileFormat.WEBP, FileFormat.PDF))

        // PDF（PdfBox / PdfRenderer）
        put(FileFormat.PDF, listOf(FileFormat.JPG, FileFormat.PNG, FileFormat.WEBP))

        // 音频（FFmpeg / MediaExtractor 直拷）
        val audioFormats = listOf(
            FileFormat.MP3,
            FileFormat.AAC,
            FileFormat.WAV,
            FileFormat.FLAC,
            FileFormat.M4A,
            FileFormat.OGG,
            FileFormat.OPUS,
        )
        audioFormats.forEach { inputAudio ->
            put(inputAudio, audioFormats.filter { it != inputAudio })
        }

        // 视频（Media3 / LiTr / FFmpeg）
        val videoAudioOutputs = listOf(
            FileFormat.MP4,
            FileFormat.WEBM,
            FileFormat.MP3,
            FileFormat.AAC,
            FileFormat.WAV,
            FileFormat.FLAC,
            FileFormat.M4A,
            FileFormat.OGG,
            FileFormat.OPUS,
        )
        put(FileFormat.MP4, listOf(FileFormat.WEBM, FileFormat.MP3, FileFormat.AAC, FileFormat.WAV, FileFormat.FLAC, FileFormat.M4A, FileFormat.OGG, FileFormat.OPUS))
        put(FileFormat.MOV, videoAudioOutputs)
        put(FileFormat.MKV, videoAudioOutputs)
        put(FileFormat.WEBM, listOf(FileFormat.MP4, FileFormat.MP3, FileFormat.AAC, FileFormat.WAV, FileFormat.FLAC, FileFormat.M4A, FileFormat.OGG, FileFormat.OPUS))
        put(FileFormat.AVI, videoAudioOutputs)

        // 压缩包（Commons Compress）
        put(FileFormat.ZIP, listOf(FileFormat.TAR, FileFormat.TAR_GZ))
        put(FileFormat.TAR, listOf(FileFormat.ZIP, FileFormat.TAR_GZ, FileFormat.GZIP, FileFormat.BZIP2))
        put(FileFormat.TAR_GZ, listOf(FileFormat.TAR, FileFormat.ZIP, FileFormat.GZIP))
        put(FileFormat.GZIP, listOf(FileFormat.ZIP, FileFormat.TAR))
        put(FileFormat.BZIP2, listOf(FileFormat.ZIP, FileFormat.TAR, FileFormat.GZIP))

        // Office（LibreOfficeKit）
        put(FileFormat.DOCX, listOf(FileFormat.PDF))
        put(FileFormat.DOC, listOf(FileFormat.PDF))
        put(FileFormat.PPTX, listOf(FileFormat.PDF))
        put(FileFormat.PPT, listOf(FileFormat.PDF))
        put(FileFormat.XLSX, listOf(FileFormat.PDF))
        put(FileFormat.XLS, listOf(FileFormat.PDF))
    }

    /** 输入格式支持的所有输出格式（空 = 不支持转换）。 */
    fun targetsFor(input: FileFormat): List<FileFormat> = edges[input] ?: emptyList()

    /** 该输入→输出组合是否被支持。 */
    fun canConvert(input: FileFormat, target: FileFormat): Boolean =
        input != target && (edges[input]?.contains(target) == true)

    /**
     * 多个输入文件的共同输出格式（批量转换用）。
     * 全部同类别且都有交集时才返回非空。
     */
    fun commonTargets(inputs: List<FileFormat>): List<FileFormat> {
        if (inputs.isEmpty()) return emptyList()
        if (inputs.map { it.category }.distinct().size != 1) return emptyList()
        return inputs
            .map { targetsFor(it).toSet() }
            .reduce { acc, next -> acc.intersect(next) }
            .sortedBy { it.displayName }
    }
}
