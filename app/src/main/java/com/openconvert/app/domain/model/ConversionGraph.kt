package com.openconvert.app.domain.model

/**
 * 转换能力图（计划书 §三十二）：输入格式 → 支持的输出格式。
 * 禁止 UI 出现不支持的组合；也是 ConverterRegistry 的能力声明来源。
 */
object ConversionGraph {

    private val edges: Map<FileFormat, List<FileFormat>> = buildMap {
        // 图片（libvips / BitmapFactory）
        put(FileFormat.JPG, listOf(FileFormat.PNG, FileFormat.WEBP, FileFormat.PDF))
        put(FileFormat.PNG, listOf(FileFormat.JPG, FileFormat.WEBP, FileFormat.PDF))
        put(FileFormat.WEBP, listOf(FileFormat.JPG, FileFormat.PNG))

        // PDF（PdfBox / PdfRenderer）
        put(FileFormat.PDF, listOf(FileFormat.JPG, FileFormat.PNG))

        // 音频（FFmpeg / MediaExtractor 直拷）
        put(FileFormat.MP3, listOf(FileFormat.AAC, FileFormat.WAV, FileFormat.FLAC, FileFormat.M4A))
        put(FileFormat.AAC, listOf(FileFormat.MP3, FileFormat.WAV, FileFormat.FLAC, FileFormat.M4A))
        put(FileFormat.WAV, listOf(FileFormat.MP3, FileFormat.AAC, FileFormat.FLAC, FileFormat.M4A))
        put(FileFormat.FLAC, listOf(FileFormat.MP3, FileFormat.AAC, FileFormat.WAV, FileFormat.M4A))
        put(FileFormat.M4A, listOf(FileFormat.MP3, FileFormat.AAC, FileFormat.WAV, FileFormat.FLAC))

        // 视频（Media3 / LiTr / FFmpeg）
        put(
            FileFormat.MP4,
            listOf(
                FileFormat.WEBM,
                FileFormat.MP3,
                FileFormat.AAC,
                FileFormat.WAV,
                FileFormat.FLAC,
                FileFormat.M4A,
            ),
        )
        put(
            FileFormat.MOV,
            listOf(
                FileFormat.MP4,
                FileFormat.MP3,
                FileFormat.AAC,
                FileFormat.WAV,
                FileFormat.FLAC,
                FileFormat.M4A,
            ),
        )
        put(
            FileFormat.MKV,
            listOf(
                FileFormat.MP4,
                FileFormat.MP3,
                FileFormat.AAC,
                FileFormat.WAV,
                FileFormat.FLAC,
                FileFormat.M4A,
            ),
        )
        put(
            FileFormat.WEBM,
            listOf(
                FileFormat.MP4,
                FileFormat.MP3,
                FileFormat.AAC,
                FileFormat.WAV,
                FileFormat.FLAC,
                FileFormat.M4A,
            ),
        )

        // 压缩包（Commons Compress）
        put(FileFormat.ZIP, listOf(FileFormat.TAR, FileFormat.TAR_GZ))
        put(FileFormat.TAR, listOf(FileFormat.ZIP, FileFormat.TAR_GZ, FileFormat.GZIP, FileFormat.BZIP2))
        put(FileFormat.TAR_GZ, listOf(FileFormat.TAR, FileFormat.ZIP, FileFormat.GZIP))
        put(FileFormat.GZIP, listOf(FileFormat.ZIP, FileFormat.TAR))
        put(FileFormat.BZIP2, listOf(FileFormat.ZIP, FileFormat.TAR, FileFormat.GZIP))
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
