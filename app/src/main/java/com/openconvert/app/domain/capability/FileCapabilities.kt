package com.openconvert.app.domain.capability

import com.openconvert.app.BuildConfig
import com.openconvert.app.domain.model.ConversionGraph
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat

/**
 * 首页 UI 2.0（计划书 §六）的数据模型：**文件驱动**。
 *
 * 用户先选文件，系统再告诉他这个文件能做什么——而不是先选工具、再找文件。
 * 例如选中 `IMG_2856.HEIC` 应得到：
 *
 * ```
 * 转换为   JPG / PNG / WEBP
 * 图片工具 裁剪 / 缩放 / 旋转 / 翻转 / 删除 EXIF / 合成 PDF
 * ```
 *
 * 能力判断全部委托 [ConversionGraph]，不在 UI 层重复规则。
 */
data class FileCapabilities(
    val format: FileFormat,
    /** 一进一出的目标格式（registry 有引擎）。 */
    val convertTargets: List<FileFormat>,
    /** 需要专属 ConversionKind 的工具能力。 */
    val tools: List<ToolAction>,
) {
    val hasAnything: Boolean get() = convertTargets.isNotEmpty() || tools.isNotEmpty()

    /** 分区标题，UI 直接用。 */
    val convertSectionTitle: String
        get() = when (format.category) {
            FileCategory.IMAGE -> "转换为"
            FileCategory.AUDIO -> "转换音频格式"
            FileCategory.VIDEO -> "转换 / 提取"
            FileCategory.OFFICE -> "导出为"
            FileCategory.ARCHIVE -> "转换压缩格式"
            else -> "转换为"
        }

    val toolSectionTitle: String
        get() = when (format.category) {
            FileCategory.IMAGE -> "图片工具"
            FileCategory.PDF -> "PDF 工具"
            FileCategory.ARCHIVE -> "压缩包工具"
            else -> "其他操作"
        }
}

/** 一个可执行的工具入口。 */
data class ToolAction(
    val kind: ConversionKind,
    val label: String,
    val description: String,
)

object FileCapabilityResolver {

    /**
     * 图片的编辑能力不是独立 ConversionKind——它们是 SINGLE 转换的参数
     * （crop / rotate / flip / stripMetadata），因此单独列出供 UI 展示。
     */
    val IMAGE_EDIT_HINTS = listOf("裁剪", "缩放", "旋转", "翻转", "删除 EXIF")

    fun resolve(format: FileFormat): FileCapabilities = FileCapabilities(
        format = format,
        convertTargets = targetsForEdition(format),
        tools = ConversionGraph.toolsFor(format).map(::describe),
    )

    /** 当前发行版真正可执行的单文件转换能力。 */
    fun canConvertInEdition(input: FileFormat, output: FileFormat): Boolean =
        ConversionGraph.canConvert(input, output) &&
            (input.category != FileCategory.OFFICE || BuildConfig.OFFICE_BUNDLED)

    fun targetsForEdition(format: FileFormat): List<FileFormat> =
        if (format.category == FileCategory.OFFICE && !BuildConfig.OFFICE_BUNDLED) {
            emptyList()
        } else {
            ConversionGraph.targetsFor(format)
        }

    private fun describe(kind: ConversionKind): ToolAction = when (kind) {
        ConversionKind.IMAGES_TO_PDF -> ToolAction(kind, "合成 PDF", "多张图片合并为一个 PDF")
        ConversionKind.PDF_TO_IMAGES -> ToolAction(kind, "导出图片", "每页导出为高清图片")
        ConversionKind.PDF_MERGE -> ToolAction(kind, "合并 PDF", "多个 PDF 按顺序合并")
        ConversionKind.PDF_SPLIT -> ToolAction(kind, "拆分 PDF", "按页码范围拆成多个文件")
        ConversionKind.PDF_DELETE_PAGES -> ToolAction(kind, "删除页面", "勾选并移除指定页")
        ConversionKind.PDF_ROTATE_PAGES -> ToolAction(kind, "旋转页面", "90° / 180° / 270°")
        ConversionKind.PDF_COMPRESS -> ToolAction(kind, "压缩 PDF", "图像降采样，显著减小体积")
        ConversionKind.PDF_PAGE_MANAGER -> ToolAction(kind, "页面管理", "缩略图拖拽重排、旋转、删除")
        ConversionKind.PDF_SECURITY -> ToolAction(kind, "加密 / 解密", "AES 密码保护或移除密码")
        ConversionKind.PDF_CROP -> ToolAction(kind, "裁剪边距", "切除多余白边")
        ConversionKind.PDF_METADATA -> ToolAction(kind, "编辑元数据", "标题 / 作者 / 主题 / 关键词")
        ConversionKind.ARCHIVE_COMPRESS -> ToolAction(kind, "压缩打包", "ZIP · TAR · GZIP · BZIP2")
        ConversionKind.ARCHIVE_EXTRACT -> ToolAction(kind, "解压到文件夹", "还原压缩包内全部文件")
        ConversionKind.SINGLE, ConversionKind.BATCH ->
            ToolAction(kind, "转换", "标准格式转换")
    }
}
