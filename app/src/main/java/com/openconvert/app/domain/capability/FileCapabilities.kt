package com.openconvert.app.domain.capability

import com.openconvert.app.AppCopy
import com.openconvert.app.BuildConfig
import com.openconvert.app.R
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
    val convertSectionTitleRes: Int
        get() = when (format.category) {
            FileCategory.AUDIO -> R.string.cap_convert_audio
            FileCategory.VIDEO -> R.string.cap_convert_video
            FileCategory.OFFICE -> R.string.cap_convert_office
            FileCategory.ARCHIVE -> R.string.cap_convert_archive
            else -> R.string.cap_convert_image
        }

    val toolSectionTitleRes: Int
        get() = when (format.category) {
            FileCategory.IMAGE -> R.string.cap_tools_image
            FileCategory.PDF -> R.string.cap_tools_pdf
            FileCategory.ARCHIVE -> R.string.cap_tools_archive
            else -> R.string.cap_tools_other
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
    val IMAGE_EDIT_HINTS: List<String>
        get() = listOf(
            AppCopy.getOr(R.string.convert_crop, "裁剪"),
            AppCopy.getOr(R.string.convert_size, "缩放"),
            AppCopy.getOr(R.string.convert_rotate, "旋转"),
            AppCopy.getOr(R.string.convert_flip, "翻转"),
            AppCopy.getOr(R.string.convert_strip_meta, "删除 EXIF"),
        )

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

    private fun describe(kind: ConversionKind): ToolAction {
        val (labelRes, descRes, label, desc) = when (kind) {
            ConversionKind.IMAGES_TO_PDF -> Quad(R.string.cap_tool_images_to_pdf, R.string.cap_tool_images_to_pdf_sub, "合成 PDF", "多张图片合并为一个 PDF")
            ConversionKind.PDF_TO_IMAGES -> Quad(R.string.cap_tool_pdf_to_images, R.string.cap_tool_pdf_to_images_sub, "导出图片", "每页导出为高清图片")
            ConversionKind.PDF_MERGE -> Quad(R.string.cap_tool_merge, R.string.cap_tool_merge_sub, "合并 PDF", "多个 PDF 按顺序合并")
            ConversionKind.PDF_SPLIT -> Quad(R.string.cap_tool_split, R.string.cap_tool_split_sub, "拆分 PDF", "按页码范围拆成多个文件")
            ConversionKind.PDF_DELETE_PAGES -> Quad(R.string.cap_tool_delete, R.string.cap_tool_delete_sub, "删除页面", "勾选并移除指定页")
            ConversionKind.PDF_ROTATE_PAGES -> Quad(R.string.cap_tool_rotate, R.string.cap_tool_rotate_sub, "旋转页面", "90° / 180° / 270°")
            ConversionKind.PDF_COMPRESS -> Quad(R.string.cap_tool_compress, R.string.cap_tool_compress_sub, "压缩 PDF", "图像降采样，显著减小体积")
            ConversionKind.PDF_PAGE_MANAGER -> Quad(R.string.cap_tool_pages, R.string.cap_tool_pages_sub, "页面管理", "缩略图拖拽重排、旋转、删除")
            ConversionKind.PDF_SECURITY -> Quad(R.string.cap_tool_security, R.string.cap_tool_security_sub, "加密 / 解密", "AES 密码保护或移除密码")
            ConversionKind.PDF_CROP -> Quad(R.string.cap_tool_crop, R.string.cap_tool_crop_sub, "裁剪边距", "切除多余白边")
            ConversionKind.PDF_METADATA -> Quad(R.string.cap_tool_metadata, R.string.cap_tool_metadata_sub, "编辑元数据", "标题 / 作者 / 主题 / 关键词")
            ConversionKind.PDF_WATERMARK -> Quad(R.string.cap_tool_watermark, R.string.cap_tool_watermark_sub, "文字水印", "斜向 / 居中 / 页脚半透明文字")
            ConversionKind.ARCHIVE_COMPRESS -> Quad(R.string.cap_tool_archive_compress, R.string.cap_tool_archive_compress_sub, "压缩打包", "ZIP · TAR · 7Z · GZIP · BZIP2 · XZ")
            ConversionKind.ARCHIVE_EXTRACT -> Quad(R.string.cap_tool_archive_extract, R.string.cap_tool_archive_extract_sub, "解压到文件夹", "还原压缩包内全部文件")
            ConversionKind.SINGLE, ConversionKind.BATCH ->
                Quad(R.string.cap_tool_convert, R.string.cap_tool_convert_sub, "转换", "标准格式转换")
        }
        return ToolAction(kind, AppCopy.getOr(labelRes, label), AppCopy.getOr(descRes, desc))
    }

    private data class Quad(val labelRes: Int, val descRes: Int, val label: String, val desc: String)
}
