package com.openconvert.app.domain.converter

import com.openconvert.app.domain.work.StorageGuard

data class ArchiveExtractionPolicy(
    val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    val maxPathDepth: Int = DEFAULT_MAX_PATH_DEPTH,
    val maxFileNameLength: Int = DEFAULT_MAX_FILE_NAME_LENGTH,
    val maxCompressionRatio: Long = DEFAULT_MAX_COMPRESSION_RATIO,
    val maxSingleFileBytes: Long,
    val maxTotalUncompressedBytes: Long,
) {
    companion object {
        const val DEFAULT_MAX_ENTRIES = 10_000
        const val DEFAULT_MAX_PATH_DEPTH = 32
        const val DEFAULT_MAX_FILE_NAME_LENGTH = 255
        const val DEFAULT_MAX_COMPRESSION_RATIO = 1_000L

        fun forUsableSpace(usableBytes: Long): ArchiveExtractionPolicy {
            val budget = (usableBytes - StorageGuard.SAFETY_MARGIN_BYTES).coerceAtLeast(0L)
            return ArchiveExtractionPolicy(
                maxSingleFileBytes = budget,
                maxTotalUncompressedBytes = budget,
            )
        }
    }
}

enum class ArchiveRejectReason {
    PATH_TRAVERSAL,
    ABSOLUTE_PATH,
    WINDOWS_DRIVE,
    TOO_DEEP,
    NAME_TOO_LONG,
    TOO_MANY_ENTRIES,
    ENTRY_TOO_LARGE,
    TOTAL_TOO_LARGE,
    RATIO_TOO_HIGH,
    ;

    fun userMessage(): String = when (this) {
        PATH_TRAVERSAL,
        ABSOLUTE_PATH,
        WINDOWS_DRIVE,
        TOO_DEEP,
        NAME_TOO_LONG,
        -> "压缩包包含不安全路径，已停止解压"

        TOO_MANY_ENTRIES,
        -> "压缩包内文件数量超过限制，已停止解压"

        ENTRY_TOO_LARGE,
        TOTAL_TOO_LARGE,
        RATIO_TOO_HIGH,
        -> "压缩包展开体积超过安全限制，已停止解压"
    }
}

sealed class ArchivePathDecision {
    data class Accept(
        val directories: List<String>,
        val fileName: String,
    ) : ArchivePathDecision()

    data object Skip : ArchivePathDecision()

    data class Reject(val reason: ArchiveRejectReason) : ArchivePathDecision()
}

class ArchiveExtractionSession(
    val policy: ArchiveExtractionPolicy,
) {
    var entryCount: Int = 0
        private set
    var totalWritten: Long = 0L
        private set
    var entryWritten: Long = 0L
        private set

    fun decidePath(rawName: String): ArchivePathDecision {
        if (rawName.contains('\u0000')) {
            return ArchivePathDecision.Reject(ArchiveRejectReason.PATH_TRAVERSAL)
        }
        val normalized = rawName.replace('\\', '/')
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) {
            return ArchivePathDecision.Skip
        }
        if (DRIVE_PREFIX.containsMatchIn(normalized)) {
            return ArchivePathDecision.Reject(ArchiveRejectReason.WINDOWS_DRIVE)
        }
        if (normalized.startsWith("/")) {
            return ArchivePathDecision.Reject(ArchiveRejectReason.ABSOLUTE_PATH)
        }
        if (parts.any { it == ".." }) {
            return ArchivePathDecision.Reject(ArchiveRejectReason.PATH_TRAVERSAL)
        }
        if (normalized.endsWith('/')) {
            return ArchivePathDecision.Skip
        }
        if (parts.size > policy.maxPathDepth) {
            return ArchivePathDecision.Reject(ArchiveRejectReason.TOO_DEEP)
        }
        if (parts.any { it.length > policy.maxFileNameLength }) {
            return ArchivePathDecision.Reject(ArchiveRejectReason.NAME_TOO_LONG)
        }
        return ArchivePathDecision.Accept(
            directories = parts.dropLast(1),
            fileName = parts.last(),
        )
    }

    fun beginEntry(compressedSize: Long, uncompressedSize: Long): ArchiveRejectReason? {
        if (entryCount >= policy.maxEntries) {
            return ArchiveRejectReason.TOO_MANY_ENTRIES
        }
        if (uncompressedSize > 0) {
            declaredLimit(compressedSize, uncompressedSize)?.let { return it }
        }
        entryCount += 1
        entryWritten = 0L
        return null
    }

    fun recordWritten(bytes: Int, compressedSize: Long): ArchiveRejectReason? {
        if (bytes <= 0) return null
        entryWritten += bytes
        totalWritten += bytes
        if (entryWritten > policy.maxSingleFileBytes) {
            return ArchiveRejectReason.ENTRY_TOO_LARGE
        }
        if (totalWritten > policy.maxTotalUncompressedBytes) {
            return ArchiveRejectReason.TOTAL_TOO_LARGE
        }
        if (compressedSize > 0 && entryWritten > compressedSize * policy.maxCompressionRatio) {
            return ArchiveRejectReason.RATIO_TOO_HIGH
        }
        return null
    }

    fun uniqueName(desired: String, existing: Set<String>): String {
        if (desired !in existing) return desired
        val dot = desired.lastIndexOf('.')
        val stem: String
        val ext: String
        if (dot > 0) {
            stem = desired.substring(0, dot)
            ext = desired.substring(dot)
        } else {
            stem = desired
            ext = ""
        }
        var index = 1
        while (index <= policy.maxEntries) {
            val candidate = "$stem ($index)$ext"
            if (candidate !in existing) return candidate
            index += 1
        }
        return "$stem (${entryCount + 1})$ext"
    }

    private fun declaredLimit(compressedSize: Long, uncompressedSize: Long): ArchiveRejectReason? {
        if (uncompressedSize > policy.maxSingleFileBytes) {
            return ArchiveRejectReason.ENTRY_TOO_LARGE
        }
        if (totalWritten + uncompressedSize > policy.maxTotalUncompressedBytes) {
            return ArchiveRejectReason.TOTAL_TOO_LARGE
        }
        if (compressedSize > 0 && uncompressedSize > compressedSize * policy.maxCompressionRatio) {
            return ArchiveRejectReason.RATIO_TOO_HIGH
        }
        return null
    }

    private companion object {
        val DRIVE_PREFIX = Regex("^[A-Za-z]:")
    }
}
