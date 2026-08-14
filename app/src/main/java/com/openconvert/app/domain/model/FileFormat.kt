package com.openconvert.app.domain.model

enum class FileCategory {
    IMAGE,
    PDF,
    AUDIO,
    VIDEO,
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
    UNKNOWN("未知", FileCategory.UNKNOWN, emptySet(), "application/octet-stream");

    val preferredExtension: String
        get() = extensions.firstOrNull() ?: "bin"

    companion object {
        fun fromFileName(fileName: String): FileFormat {
            val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return entries.firstOrNull { extension in it.extensions } ?: UNKNOWN
        }
    }
}

fun FileFormat.canConvertLocallyTo(target: FileFormat): Boolean =
    when (category) {
        FileCategory.IMAGE -> target.category == FileCategory.IMAGE && this != target
        FileCategory.AUDIO -> target.category == FileCategory.AUDIO && this != target
        FileCategory.VIDEO -> target in availableTargets()
        else -> false
    }

fun suggestedOutputName(sourceName: String, target: FileFormat): String {
    val baseName = sourceName.substringBeforeLast('.', missingDelimiterValue = sourceName)
        .ifBlank { "OpenConvert" }
    return "$baseName.${target.preferredExtension}"
}

fun FileFormat.availableTargets(): List<FileFormat> = when (this) {
    FileFormat.JPG -> listOf(FileFormat.PNG, FileFormat.WEBP, FileFormat.PDF)
    FileFormat.PNG -> listOf(FileFormat.JPG, FileFormat.WEBP, FileFormat.PDF)
    FileFormat.WEBP -> listOf(FileFormat.JPG, FileFormat.PNG)
    FileFormat.PDF -> listOf(FileFormat.JPG, FileFormat.PNG)
    FileFormat.MP3 -> listOf(FileFormat.AAC, FileFormat.WAV, FileFormat.FLAC, FileFormat.M4A)
    FileFormat.AAC -> listOf(FileFormat.MP3, FileFormat.WAV, FileFormat.FLAC, FileFormat.M4A)
    FileFormat.WAV -> listOf(FileFormat.MP3, FileFormat.AAC, FileFormat.FLAC, FileFormat.M4A)
    FileFormat.FLAC -> listOf(FileFormat.MP3, FileFormat.AAC, FileFormat.WAV, FileFormat.M4A)
    FileFormat.M4A -> listOf(FileFormat.MP3, FileFormat.AAC, FileFormat.WAV, FileFormat.FLAC)
    FileFormat.MP4 -> listOf(
        FileFormat.WEBM,
        FileFormat.MP3,
        FileFormat.AAC,
        FileFormat.WAV,
        FileFormat.FLAC,
        FileFormat.M4A,
    )
    FileFormat.MOV, FileFormat.MKV -> listOf(
        FileFormat.MP4,
        FileFormat.MP3,
        FileFormat.AAC,
        FileFormat.WAV,
        FileFormat.FLAC,
        FileFormat.M4A,
    )
    FileFormat.WEBM -> listOf(
        FileFormat.MP4,
        FileFormat.MP3,
        FileFormat.AAC,
        FileFormat.WAV,
        FileFormat.FLAC,
        FileFormat.M4A,
    )
    FileFormat.UNKNOWN -> emptyList()
}
