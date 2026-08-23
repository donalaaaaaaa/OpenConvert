package com.openconvert.app.domain.converter

import com.openconvert.app.domain.work.StorageGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveExtractionPolicyTest {

    private fun session(
        maxEntries: Int = 10_000,
        maxPathDepth: Int = 32,
        maxFileNameLength: Int = 255,
        maxCompressionRatio: Long = 1_000L,
        maxSingleFileBytes: Long = 100L * 1024 * 1024,
        maxTotalUncompressedBytes: Long = 200L * 1024 * 1024,
    ) = ArchiveExtractionSession(
        ArchiveExtractionPolicy(
            maxEntries = maxEntries,
            maxPathDepth = maxPathDepth,
            maxFileNameLength = maxFileNameLength,
            maxCompressionRatio = maxCompressionRatio,
            maxSingleFileBytes = maxSingleFileBytes,
            maxTotalUncompressedBytes = maxTotalUncompressedBytes,
        ),
    )

    @Test
    fun usableSpaceBudgetLeavesSafetyMargin() {
        val usable = 500L * 1024 * 1024
        val policy = ArchiveExtractionPolicy.forUsableSpace(usable)
        assertEquals(usable - StorageGuard.SAFETY_MARGIN_BYTES, policy.maxSingleFileBytes)
        assertEquals(policy.maxSingleFileBytes, policy.maxTotalUncompressedBytes)
        assertEquals(10_000, policy.maxEntries)
        assertEquals(32, policy.maxPathDepth)
        assertEquals(1_000L, policy.maxCompressionRatio)
    }

    @Test
    fun restoresNestedDirectories() {
        val accepted = session().decidePath("a/config.json") as ArchivePathDecision.Accept
        assertEquals(listOf("a"), accepted.directories)
        assertEquals("config.json", accepted.fileName)
        val other = session().decidePath("b\\config.json") as ArchivePathDecision.Accept
        assertEquals(listOf("b"), other.directories)
        assertEquals("config.json", other.fileName)
    }

    @Test
    fun rejectsZipSlipAndAbsoluteAndDrivePaths() {
        val s = session()
        assertEquals(
            ArchiveRejectReason.PATH_TRAVERSAL,
            (s.decidePath("../evil.txt") as ArchivePathDecision.Reject).reason,
        )
        assertEquals(
            ArchiveRejectReason.PATH_TRAVERSAL,
            (s.decidePath("ok/../../etc/passwd") as ArchivePathDecision.Reject).reason,
        )
        assertEquals(
            ArchiveRejectReason.ABSOLUTE_PATH,
            (s.decidePath("/etc/passwd") as ArchivePathDecision.Reject).reason,
        )
        assertEquals(
            ArchiveRejectReason.ABSOLUTE_PATH,
            (s.decidePath("\\\\server\\share\\evil.txt") as ArchivePathDecision.Reject).reason,
        )
        assertEquals(
            ArchiveRejectReason.WINDOWS_DRIVE,
            (s.decidePath("C:/Windows/system.ini") as ArchivePathDecision.Reject).reason,
        )
        assertEquals(
            ArchiveRejectReason.WINDOWS_DRIVE,
            (s.decidePath("D:evil.txt") as ArchivePathDecision.Reject).reason,
        )
    }

    @Test
    fun skipsEmptyAndDirectoryOnlyEntries() {
        val s = session()
        assertEquals(ArchivePathDecision.Skip, s.decidePath(""))
        assertEquals(ArchivePathDecision.Skip, s.decidePath("///"))
        assertEquals(ArchivePathDecision.Skip, s.decidePath("docs/"))
        assertEquals(ArchivePathDecision.Skip, s.decidePath("."))
    }

    @Test
    fun rejectsDeepAndOverlongNames() {
        val deep = (1..33).joinToString("/") { "d$it" } + "/file.txt"
        assertEquals(
            ArchiveRejectReason.TOO_DEEP,
            (session(maxPathDepth = 32).decidePath(deep) as ArchivePathDecision.Reject).reason,
        )
        assertEquals(
            ArchiveRejectReason.NAME_TOO_LONG,
            (session(maxFileNameLength = 8).decidePath("toolongname.txt") as ArchivePathDecision.Reject).reason,
        )
    }

    @Test
    fun uniqueNamesGetNumericSuffix() {
        val s = session()
        assertEquals("photo.jpg", s.uniqueName("photo.jpg", emptySet()))
        assertEquals("photo (1).jpg", s.uniqueName("photo.jpg", setOf("photo.jpg")))
        assertEquals(
            "photo (2).jpg",
            s.uniqueName("photo.jpg", setOf("photo.jpg", "photo (1).jpg")),
        )
        assertEquals("README (1)", s.uniqueName("README", setOf("README")))
    }

    @Test
    fun declaredRatioAndSizeAreRejected() {
        val s = session(
            maxCompressionRatio = 10,
            maxSingleFileBytes = 1_000,
            maxTotalUncompressedBytes = 1_500,
        )
        assertEquals(
            ArchiveRejectReason.RATIO_TOO_HIGH,
            s.beginEntry(compressedSize = 10, uncompressedSize = 200),
        )
        assertEquals(
            ArchiveRejectReason.ENTRY_TOO_LARGE,
            s.beginEntry(compressedSize = 100, uncompressedSize = 2_000),
        )
    }

    @Test
    fun actualBytesWrittenCatchZipBomb() {
        val s = session(
            maxCompressionRatio = 4,
            maxSingleFileBytes = 10_000,
            maxTotalUncompressedBytes = 10_000,
        )
        assertEquals(null, s.beginEntry(compressedSize = 8, uncompressedSize = -1))
        assertEquals(null, s.recordWritten(20, compressedSize = 8))
        assertEquals(
            ArchiveRejectReason.RATIO_TOO_HIGH,
            s.recordWritten(20, compressedSize = 8),
        )
    }

    @Test
    fun tooManyEntriesAreRejected() {
        val s = session(maxEntries = 2)
        assertEquals(null, s.beginEntry(-1, 4))
        assertEquals(null, s.beginEntry(-1, 4))
        assertEquals(ArchiveRejectReason.TOO_MANY_ENTRIES, s.beginEntry(-1, 4))
    }

    @Test
    fun totalBudgetStopsLaterEntries() {
        val s = session(maxSingleFileBytes = 100, maxTotalUncompressedBytes = 100)
        assertEquals(null, s.beginEntry(compressedSize = 50, uncompressedSize = 50))
        assertEquals(null, s.recordWritten(50, compressedSize = 50))
        assertEquals(
            ArchiveRejectReason.TOTAL_TOO_LARGE,
            s.beginEntry(compressedSize = 50, uncompressedSize = 60),
        )
    }
}
