package com.openconvert.app.domain.model

enum class FileCategory {
    IMAGE,
    PDF,
    AUDIO,
    VIDEO,
    ARCHIVE,
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
    PDF("PDF", FileCategory.PDF, setOf("pdf"), "application/pdf"),
    MP3("MP3", FileCategory.AUDIO, setOf("mp3"), "audio/mpeg"),
    AAC("AAC", FileCategory.AUDIO, setOf("aac"), "audio/aac"),
    WAV("WAV", FileCategory.AUDIO, setOf("wav"), "audio/wav"),
    FLAC("FLAC", FileCategory.AUDIO, setOf("flac"), "audio/flac"),
    M4A("M4A", FileCategory.AUDIO, setOf("m4a"), "audio/mp4"),
    MP4("MP4", FileCategory.VIDEO, setOf("mp4"), "video/mp4"),
    MOV("MOV", FileCategory.VIDEO, setOf("mov"), "video/quicktime"),
    MKV("MKV", FileCategory.VIDEO, setOf("mkv"), "video/x-matroska"),
    WEBM("WEBM", FileCategory.VIDEO, setOf("webm"), "video/webm"),
    ZIP("ZIP", FileCategory.ARCHIVE, setOf("zip"), "application/zip"),
    TAR("TAR", FileCategory.ARCHIVE, setOf("tar"), "application/x-tar"),
    TAR_GZ("TAR.GZ", FileCategory.ARCHIVE, setOf("tar.gz", "tgz"), "application/gzip"),
    GZIP("GZIP", FileCategory.ARCHIVE, setOf("gz"), "application/gzip"),
    BZIP2("BZIP2", FileCategory.ARCHIVE, setOf("bz2"), "application/x-bzip2"),
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

fun FileFormat.canConvertLocallyTo(target: FileFormat): Boolean =
    when (category) {
        FileCategory.IMAGE -> target.category == FileCategory.IMAGE && this != target
        FileCategory.AUDIO -> target.category == FileCategory.AUDIO && this != target
        FileCategory.VIDEO -> target in availableTargets()
        FileCategory.ARCHIVE -> target in availableTargets()
        else -> false
    }

fun suggestedOutputName(sourceName: String, target: FileFormat): String {
    val baseName = sourceName.substringBeforeLast('.', missingDelimiterValue = sourceName)
        .ifBlank { "OpenConvert" }
    return "$baseName.${target.preferredExtension}"
}

fun FileFormat.availableTargets(): List<FileFormat> = ConversionGraph.targetsFor(this)
