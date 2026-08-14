package com.openconvert.app.domain.model

import java.io.InputStream

/**
 * 统一文件识别器（计划书 §三十一）：MIME → 扩展名 → Magic Number 三层判断。
 * 避免单纯依赖扩展名：文件内容与扩展名不一致时以 Magic Number 为准。
 */
object FileTypeDetector {

    /**
     * 根据文件名/扩展名识别格式（第一层，最快）。
     */
    fun fromFileName(fileName: String): FileFormat = FileFormat.fromFileName(fileName)

    /**
     * 根据 MIME 类型推断格式（第二层）。
     * 返回 null 表示 MIME 不足以唯一确定格式。
     */
    fun fromMimeType(mimeType: String?): FileFormat? {
        if (mimeType.isNullOrBlank()) return null
        return when (mimeType.lowercase().trim()) {
            "image/jpeg" -> FileFormat.JPG
            "image/png" -> FileFormat.PNG
            "image/webp" -> FileFormat.WEBP
            "application/pdf" -> FileFormat.PDF
            "audio/mpeg" -> FileFormat.MP3
            "audio/aac", "audio/aacp" -> FileFormat.AAC
            "audio/wav", "audio/x-wav", "audio/wave" -> FileFormat.WAV
            "audio/flac", "audio/x-flac" -> FileFormat.FLAC
            "audio/mp4", "audio/x-m4a" -> FileFormat.M4A
            "video/mp4" -> FileFormat.MP4
            "video/quicktime" -> FileFormat.MOV
            "video/x-matroska", "video/webm" -> FileFormat.WEBM
            "application/zip", "application/x-zip-compressed" -> FileFormat.ZIP
            "application/x-tar" -> FileFormat.TAR
            "application/gzip", "application/x-gzip" -> FileFormat.GZIP
            "application/x-bzip2" -> FileFormat.BZIP2
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword" -> FileFormat.DOCX
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint" -> FileFormat.PPTX
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel" -> FileFormat.XLSX
            else -> null
        }
    }

    /**
     * 根据文件头 Magic Number 识别格式（第三层，最可靠）。
     * 读取流前 32 字节，不消耗整个文件。
     */
    fun fromMagicNumber(input: InputStream): FileFormat {
        val header = ByteArray(32)
        val read = readUpTo(input, header)
        return fromMagicBytes(header, read)
    }

    fun fromMagicBytes(header: ByteArray, length: Int): FileFormat {
        val h = header
        val n = length

        if (n >= 8 && h[0] == 0x89.toByte() && h[1] == 0x50.toByte() && h[2] == 0x4E.toByte() &&
            h[3] == 0x47.toByte() && h[4] == 0x0D.toByte() && h[5] == 0x0A.toByte() &&
            h[6] == 0x1A.toByte() && h[7] == 0x0A.toByte()
        ) {
            return FileFormat.PNG
        }

        if (n >= 4 && h[0] == 0xFF.toByte() && h[1] == 0xD8.toByte() && h[2] == 0xFF.toByte()) {
            return FileFormat.JPG
        }

        if (n >= 12 && h[0] == 'R'.code.toByte() && h[1] == 'I'.code.toByte() &&
            h[2] == 'F'.code.toByte() && h[3] == 'F'.code.toByte() &&
            h[8] == 'W'.code.toByte() && h[9] == 'E'.code.toByte() &&
            h[10] == 'B'.code.toByte() && h[11] == 'P'.code.toByte()
        ) {
            return FileFormat.WEBP
        }

        if (n >= 4 && h[0] == '%'.code.toByte() && h[1] == 'P'.code.toByte() &&
            h[2] == 'D'.code.toByte() && h[3] == 'F'.code.toByte()
        ) {
            return FileFormat.PDF
        }

        if (n >= 3 && h[0] == 'I'.code.toByte() && h[1] == 'D'.code.toByte() &&
            h[2] == '3'.code.toByte()
        ) {
            return FileFormat.MP3
        }

        if (n >= 4 && h[0] == 'f'.code.toByte() && h[1] == 'L'.code.toByte() &&
            h[2] == 'a'.code.toByte() && h[3] == 'C'.code.toByte()
        ) {
            return FileFormat.FLAC
        }

        // RIFF/WAVE: 52 49 46 46 xx xx xx xx 57 41 56 45
        if (n >= 12 && h[0] == 'R'.code.toByte() && h[1] == 'I'.code.toByte() &&
            h[2] == 'F'.code.toByte() && h[3] == 'F'.code.toByte() &&
            h[8] == 'W'.code.toByte() && h[9] == 'A'.code.toByte() &&
            h[10] == 'V'.code.toByte() && h[11] == 'E'.code.toByte()
        ) {
            return FileFormat.WAV
        }

        // ftyp box: 00 00 00 xx 66 74 79 70
        if (n >= 8 && h[4] == 'f'.code.toByte() && h[5] == 't'.code.toByte() &&
            h[6] == 'y'.code.toByte() && h[7] == 'p'.code.toByte()
        ) {
            return detectMp4Family(h, n)
        }

        if (n >= 4 && h[0] == 0x1A.toByte() && h[1] == 0x45.toByte() &&
            h[2] == 0xDF.toByte() && h[3] == 0xA3.toByte()
        ) {
            return FileFormat.MKV
        }

        if (n >= 4 && h[0] == 'P'.code.toByte() && h[1] == 'K'.code.toByte() &&
            (h[2] == 0x03.toByte() || h[2] == 0x05.toByte() || h[2] == 0x07.toByte())
        ) {
            return FileFormat.ZIP
        }

        if (n >= 3 && h[0] == 0x1F.toByte() && h[1] == 0x8B.toByte()) {
            return FileFormat.GZIP
        }

        if (n >= 3 && h[0] == 'B'.code.toByte() && h[1] == 'Z'.code.toByte() &&
            h[2] == 'h'.code.toByte()
        ) {
            return FileFormat.BZIP2
        }

        return FileFormat.UNKNOWN
    }

    /**
     * 三层合并识别：以 Magic Number 为准，失败回退到 MIME，再回退到扩展名。
     */
    fun detect(
        fileName: String?,
        mimeType: String?,
        input: InputStream?,
    ): FileFormat {
        val byName = if (fileName.isNullOrBlank()) null else fromFileName(fileName)
        val byMime = fromMimeType(mimeType)
        val magic = input?.let { fromMagicNumber(it) }

        // Office 文档（docx/pptx/xlsx）本质是 ZIP 容器，magic 只能识别到 ZIP；
        // 此时扩展名与 MIME 更能说明真实类型，优先采用。
        if (byName?.category == FileCategory.OFFICE && magic == FileFormat.ZIP) return byName
        if (byName?.category == FileCategory.OFFICE && byMime == null) return byName

        if (magic != null && magic != FileFormat.UNKNOWN) return magic
        if (byMime != null && byMime != FileFormat.UNKNOWN) return byMime
        return byName ?: FileFormat.UNKNOWN
    }

    private fun detectMp4Family(h: ByteArray, n: Int): FileFormat {
        // 检查 ftyp 品牌
        if (n >= 12) {
            val brand = String(h, 8, 4, Charsets.US_ASCII)
            return when {
                brand.startsWith("qt") -> FileFormat.MOV
                else -> FileFormat.MP4
            }
        }
        return FileFormat.MP4
    }

    private fun readUpTo(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }
}
