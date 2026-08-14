package com.openconvert.app.domain.converter

data class PdfPageRange(val firstPage: Int, val lastPage: Int) {
    init {
        require(firstPage >= 1 && lastPage >= firstPage)
    }

    val pages: IntRange get() = firstPage..lastPage
    val label: String get() = if (firstPage == lastPage) "$firstPage" else "$firstPage-$lastPage"
}

fun parsePdfPageRanges(value: String, pageCount: Int): Result<List<PdfPageRange>> = runCatching {
    require(pageCount > 0) { "PDF 没有可处理的页面" }
    val normalized = value.trim().replace('，', ',')
    if (normalized.isBlank()) return@runCatching listOf(PdfPageRange(1, pageCount))

    val ranges = normalized
        .split(',', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { part ->
            val match = PAGE_RANGE.matchEntire(part)
                ?: throw IllegalArgumentException("页码格式不正确：$part")
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].ifBlank { match.groupValues[1] }.toInt()
            require(start in 1..pageCount && end in 1..pageCount) {
                "页码必须在 1-$pageCount 之间"
            }
            require(end >= start) { "页码范围不能倒序：$part" }
            PdfPageRange(start, end)
        }
    require(ranges.isNotEmpty()) { "请输入需要处理的页码" }
    ranges
}

fun flattenPdfPages(ranges: List<PdfPageRange>): List<Int> =
    ranges.flatMap { it.pages }.distinct()

private val PAGE_RANGE = Regex("^(\\d+)(?:\\s*-\\s*(\\d+))?$")
