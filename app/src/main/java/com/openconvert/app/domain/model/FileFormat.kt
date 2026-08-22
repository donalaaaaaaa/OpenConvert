package com.openconvert.app.domain.model

enum class FileCategory {
    IMAGE,
    PDF,
    AUDIO,
    VIDEO,
    ARCHIVE,
    OFFICE,
    UNKNOWN,
}

enum class FileFormat(
    val displayName: String,
    val category: FileCategory,
    val extensions: Set<String>,
    val mimeType: String,
) {
    JPG("JPG", FileCategory.IMAGE, setOf("jpg", "jpeg"), "image/jpeg"),
    PNG("PNG", FileCategory.IMAGE, setOf("png"), "image/png"),
    WEBP("WEBP", FileCategory.IMAGE, setOf("webp"), "image/webp"),
    AVIF("AVIF", FileCategory.IMAGE, setOf("avif"), "image/avif"),
    HEIC("HEIC", FileCategory.IMAGE, setOf("heic", "heif"), "image/heic"),
    GIF("GIF", FileCategory.IMAGE, setOf("gif"), "image/gif"),
    BMP("BMP", FileCategory.IMAGE, setOf("bmp"), "image/bmp"),
    TIFF("TIFF", FileCategory.IMAGE, setOf("tiff", "tif"), "image/tiff"),
    PDF("PDF", FileCategory.PDF, setOf("pdf"), "application/pdf"),
    MP3("MP3", FileCategory.AUDIO, setOf("mp3"), "audio/mpeg"),
    AAC("AAC", FileCategory.AUDIO, setOf("aac"), "audio/aac"),
    WAV("WAV", FileCategory.AUDIO, setOf("wav"), "audio/wav"),
    FLAC("FLAC", FileCategory.AUDIO, setOf("flac"), "audio/flac"),
    M4A("M4A", FileCategory.AUDIO, setOf("m4a"), "audio/mp4"),
    OGG("OGG", FileCategory.AUDIO, setOf("ogg", "oga"), "audio/ogg"),
    OPUS("OPUS", FileCategory.AUDIO, setOf("opus"), "audio/opus"),
    MP4("MP4", FileCategory.VIDEO, setOf("mp4", "m4v"), "video/mp4"),
    MOV("MOV", FileCategory.VIDEO, setOf("mov"), "video/quicktime"),
    MKV("MKV", FileCategory.VIDEO, setOf("mkv"), "video/x-matroska"),
    WEBM("WEBM", FileCategory.VIDEO, setOf("webm"), "video/webm"),
    AVI("AVI", FileCategory.VIDEO, setOf("avi"), "video/x-msvideo"),
    ZIP("ZIP", FileCategory.ARCHIVE, setOf("zip"), "application/zip"),
    TAR("TAR", FileCategory.ARCHIVE, setOf("tar"), "application/x-tar"),
    TAR_GZ("TAR.GZ", FileCategory.ARCHIVE, setOf("tar.gz", "tgz"), "application/gzip"),
    GZIP("GZIP", FileCategory.ARCHIVE, setOf("gz"), "application/gzip"),
    BZIP2("BZIP2", FileCategory.ARCHIVE, setOf("bz2"), "application/x-bzip2"),
    SEVEN_Z("7Z", FileCategory.ARCHIVE, setOf("7z"), "application/x-7z-compressed"),
    DOCX("DOCX", FileCategory.OFFICE, setOf("docx"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    DOC("DOC", FileCategory.OFFICE, setOf("doc"), "application/msword"),
    PPTX("PPTX", FileCategory.OFFICE, setOf("pptx"), "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    PPT("PPT", FileCategory.OFFICE, setOf("ppt"), "application/vnd.ms-powerpoint"),
    XLSX("XLSX", FileCategory.OFFICE, setOf("xlsx"), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    XLS("XLS", FileCategory.OFFICE, setOf("xls"), "application/vnd.ms-excel"),
    UNKNOWN("未知", FileCategory.UNKNOWN, emptySet(), "application/octet-stream");

    val preferredExtension: String
        get() = extensions.firstOrNull() ?: "bin"

    companion object {
        fun fromFileName(fileName: String): FileFormat {
            val lower = fileName.lowercase()
            val extension = lower.substringAfterLast('.', missingDelimiterValue = "")
            val doubleExtension = lower.substringAfterLast('.', missingDelimiterValue = "")
                .let { single ->
                    if (single == "gz" || single == "bz2") {
                        lower.substringBeforeLast('.').substringAfterLast('.', missingDelimiterValue = "")
                    } else {
                        ""
                    }
                }
            return entries.firstOrNull {
                extension in it.extensions || (doubleExtension.isNotEmpty() && "$doubleExtension.$extension" in it.extensions)
            } ?: UNKNOWN
        }
    }
}

/**
 * 该格式能否由本机引擎直接转换为 [target]（一进一出的 SINGLE 流程）。
 *
 * 唯一依据是 [ConversionGraph] 的转换边——UI 的可用性判断与 `ConverterRegistry`
 * 的实际引擎索引因此永远一致。历史上这里为 IMAGE / AUDIO 走过
 * `category == 同类 && this != target` 的旧规则，导致 UI 放出
 * JPG→AVIF / JPG→HEIC 等 registry 里没有引擎的组合。
 */
fun FileFormat.canConvertLocallyTo(target: FileFormat): Boolean =
    ConversionGraph.canConvert(this, target)


fun suggestedOutputName(sourceName: String, target: FileFormat): String {
    val baseName = sourceName.substringBeforeLast('.', missingDelimiterValue = sourceName)
        .ifBlank { "OpenConvert" }
    return "$baseName.${target.preferredExtension}"
}

fun FileFormat.availableTargets(): List<FileFormat> = ConversionGraph.targetsFor(this)
