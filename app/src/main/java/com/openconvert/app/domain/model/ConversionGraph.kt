package com.openconvert.app.domain.model

/**
 * 转换能力图（计划书 §三十二）：能力声明的唯一来源。
 *
 * 分两类边，语义不同，不可混用：
 *
 * 1. [convertEdges] —— **格式转换边**。输入格式 → 输出格式，由 `ConverterRegistry`
 *    里注册的引擎（ImageConverter / MediaConverter / OfficeConverter / ArchiveConverter）
 *    直接执行，走 `ConversionKind.SINGLE` 流程：一个输入文件 → 一个输出文件。
 *    [targetsFor] / [canConvert] 只看这一类，`canConvertLocallyTo` 也只看这一类。
 *
 * 2. [toolEdges] —— **工具边**。需要多文件输入、目录输出或页面参数，因此有专属
 *    `ConversionKind`（如图片合成 PDF、PDF 导出图片序列）。这些能力不能塞进
 *    SINGLE 流程，否则 UI 会给出一个点了没反应的目标格式。
 *
 * 历史坑：早期把 `JPG → PDF`、`PDF → JPG` 也放进转换边，于是 UI 从
 * `targetsFor` 拿到 PDF 目标并显示出来，但 registry 里没有对应引擎，按钮永远
 * 是灰的「该转换引擎尚未接入」。现在两类边分开，UI 各取所需。
 */
object ConversionGraph {

    /** 一个输入 → 一个输出，registry 有引擎直接执行。 */
    private val convertEdges: Map<FileFormat, List<FileFormat>> = buildMap {
        // 图片（libvips 主引擎 / BitmapFactory 兜底）
        // 输出只有 JPG/PNG/WEBP：ImageConverter.writeBitmap 只能编码这三种。
        // AVIF/HEIC/GIF/BMP/TIFF 是只读输入（系统解码器支持解，不支持编）。
        val imageOutputs = listOf(FileFormat.JPG, FileFormat.PNG, FileFormat.WEBP)
        listOf(
            FileFormat.JPG,
            FileFormat.PNG,
            FileFormat.WEBP,
            FileFormat.AVIF,
            FileFormat.HEIC,
            FileFormat.GIF,
            FileFormat.BMP,
            FileFormat.TIFF,
        ).forEach { input ->
            put(input, imageOutputs.filter { it != input })
        }

        // 音频（FFmpeg 重编码 / MediaExtractor 同编码直拷）
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

        // 视频（Media3/MediaCodec 主引擎、LiTr VP8、FFmpeg 兜底）
        // 视频 → 音频是「提取音轨」，同样一进一出，属于转换边。
        val videoAudioOutputs = listOf(
            FileFormat.MP4,
            FileFormat.WEBM,
        ) + audioFormats
        put(FileFormat.MP4, videoAudioOutputs.filter { it != FileFormat.MP4 })
        put(FileFormat.MOV, videoAudioOutputs)
        put(FileFormat.MKV, videoAudioOutputs)
        put(FileFormat.WEBM, videoAudioOutputs.filter { it != FileFormat.WEBM })
        put(FileFormat.AVI, videoAudioOutputs)

        // 压缩包（Commons Compress）
        put(FileFormat.ZIP, listOf(FileFormat.TAR, FileFormat.TAR_GZ))
        put(FileFormat.TAR, listOf(FileFormat.ZIP, FileFormat.TAR_GZ, FileFormat.GZIP, FileFormat.BZIP2))
        put(FileFormat.TAR_GZ, listOf(FileFormat.TAR, FileFormat.ZIP, FileFormat.GZIP))
        put(FileFormat.GZIP, listOf(FileFormat.ZIP, FileFormat.TAR))
        put(FileFormat.BZIP2, listOf(FileFormat.ZIP, FileFormat.TAR, FileFormat.GZIP))

        // Office（LibreOfficeKit）
        listOf(
            FileFormat.DOCX,
            FileFormat.DOC,
            FileFormat.PPTX,
            FileFormat.PPT,
            FileFormat.XLSX,
            FileFormat.XLS,
        ).forEach { put(it, listOf(FileFormat.PDF)) }
    }

    /**
     * 需要专属 ConversionKind 的工具能力（多输入 / 目录输出 / 页面参数）。
     * 首页 UI 2.0 的「这个文件能做什么」面板由此驱动。
     */
    private val toolEdges: Map<FileFormat, List<ConversionKind>> = buildMap {
        val imageTools = listOf(ConversionKind.IMAGES_TO_PDF)
        listOf(
            FileFormat.JPG,
            FileFormat.PNG,
            FileFormat.WEBP,
            FileFormat.AVIF,
            FileFormat.HEIC,
            FileFormat.GIF,
            FileFormat.BMP,
            FileFormat.TIFF,
        ).forEach { put(it, imageTools) }

        put(
            FileFormat.PDF,
            listOf(
                ConversionKind.PDF_TO_IMAGES,
                ConversionKind.PDF_MERGE,
                ConversionKind.PDF_SPLIT,
                ConversionKind.PDF_DELETE_PAGES,
                ConversionKind.PDF_ROTATE_PAGES,
                ConversionKind.PDF_COMPRESS,
                ConversionKind.PDF_PAGE_MANAGER,
                ConversionKind.PDF_SECURITY,
                ConversionKind.PDF_CROP,
                ConversionKind.PDF_METADATA,
                ConversionKind.PDF_WATERMARK,
            ),
        )

        listOf(FileFormat.ZIP, FileFormat.TAR_GZ, FileFormat.BZIP2, FileFormat.SEVEN_Z)
            .forEach { put(it, listOf(ConversionKind.ARCHIVE_EXTRACT)) }
    }

    /**
     * 输入格式支持的所有输出格式（一进一出，registry 有引擎）。
     * 空 = 该格式没有直接的格式转换能力（可能仍有工具能力，见 [toolsFor]）。
     */
    fun targetsFor(input: FileFormat): List<FileFormat> = convertEdges[input] ?: emptyList()

    /** 该输入→输出组合是否被 registry 引擎支持。 */
    fun canConvert(input: FileFormat, target: FileFormat): Boolean =
        input != target && (convertEdges[input]?.contains(target) == true)

    /**
     * 该格式可用的工具能力（需要专属 ConversionKind）。
     * 任意格式都能作为压缩包的输入，因此 ARCHIVE_COMPRESS 对所有已知格式可用。
     */
    fun toolsFor(input: FileFormat): List<ConversionKind> {
        if (input == FileFormat.UNKNOWN) return emptyList()
        val specific = toolEdges[input] ?: emptyList()
        return specific + ConversionKind.ARCHIVE_COMPRESS
    }

    /** 该格式是否有任何可执行能力（转换或工具）。 */
    fun hasAnyCapability(input: FileFormat): Boolean =
        targetsFor(input).isNotEmpty() || toolsFor(input).isNotEmpty()

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
