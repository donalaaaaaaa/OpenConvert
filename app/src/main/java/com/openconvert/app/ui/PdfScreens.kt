package com.openconvert.app.ui

import android.content.Intent
import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.BuildConfig
import com.openconvert.app.domain.benchmark.BenchmarkReportFormat
import java.io.File
import com.openconvert.app.domain.model.BatchJob
import com.openconvert.app.domain.model.BatchJobStatus
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import com.openconvert.app.domain.model.availableTargets
import com.openconvert.app.ui.theme.Border
import com.openconvert.app.ui.theme.Ink
import com.openconvert.app.ui.theme.Muted
import com.openconvert.app.ui.theme.SurfaceSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PdfToolsScreen(
    onBack: () -> Unit,
    onImagesPicked: (List<Uri>) -> Unit,
    onPdfToImagesPicked: (Uri) -> Unit,
    onPdfsToMergePicked: (List<Uri>) -> Unit,
    onPdfToSplitPicked: (Uri) -> Unit,
    onPdfToDeletePicked: (Uri) -> Unit,
    onPdfToRotatePicked: (Uri) -> Unit,
    onPdfCompressPicked: (Uri) -> Unit = {},
    onPdfSecurityPicked: (Uri) -> Unit = {},
    onPdfCropPicked: (Uri) -> Unit = {},
    onPdfMetadataPicked: (Uri) -> Unit = {},
    onPdfWatermarkPicked: (Uri) -> Unit = {},
    onPdfPageManagerPicked: (Uri) -> Unit = {},
) {
    val imagesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) onImagesPicked(uris) }
    val pdfToImagesPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPdfToImagesPicked) }
    val mergePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) onPdfsToMergePicked(uris) }
    val splitPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPdfToSplitPicked) }
    val deletePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPdfToDeletePicked) }
    val rotatePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPdfToRotatePicked) }
    val compressPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPdfCompressPicked) }
    val securityPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPdfSecurityPicked) }
    val cropPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPdfCropPicked) }
    val metadataPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPdfMetadataPicked) }
    val watermarkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPdfWatermarkPicked) }
    val pageManagerPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPdfPageManagerPicked) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PDF 工具箱", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("全部操作 100% 离线完成，保护文件隐私", color = Muted, fontSize = 14.sp)
            }
        }
        item {
            ToolCard(
                "PDF 页面管理器",
                "可视化页面网格、拖拽重排、旋转与删除",
                Icons.Outlined.Description,
                Modifier.fillMaxWidth(),
                onClick = { pageManagerPicker.launch(arrayOf(FileFormat.PDF.mimeType)) },
            )
        }
        item {
            ToolCard(
                "PDF 智能压缩",
                "图像降采样与智能压缩，大幅减小体积",
                Icons.Outlined.Refresh,
                Modifier.fillMaxWidth(),
                onClick = { compressPicker.launch(arrayOf(FileFormat.PDF.mimeType)) },
            )
        }
        item {
            ToolCard(
                "图片转 PDF",
                "多张 JPG、PNG 或 WEBP 生成一个 PDF",
                Icons.Outlined.Image,
                Modifier.fillMaxWidth(),
                onClick = { imagesPicker.launch(arrayOf("image/*")) },
            )
        }
        item {
            ToolCard(
                "PDF 转图片",
                "导出全部页面或指定页为 JPG、PNG",
                Icons.Outlined.Description,
                Modifier.fillMaxWidth(),
                onClick = { pdfToImagesPicker.launch(arrayOf(FileFormat.PDF.mimeType)) },
            )
        }
        item {
            ToolCard(
                "PDF 合并",
                "选择多个 PDF 并调整顺序",
                Icons.Outlined.Add,
                Modifier.fillMaxWidth(),
                onClick = { mergePicker.launch(arrayOf(FileFormat.PDF.mimeType)) },
            )
        }
        item {
            ToolCard(
                "PDF 拆分",
                "按 1-3、5、8-10 等页码范围拆分",
                Icons.Outlined.Description,
                Modifier.fillMaxWidth(),
                onClick = { splitPicker.launch(arrayOf(FileFormat.PDF.mimeType)) },
            )
        }
        item {
            ToolCard(
                "PDF 加密 / 解密",
                "设置密码保护或移除已知密码导出副本",
                Icons.Outlined.Lock,
                Modifier.fillMaxWidth(),
                onClick = { securityPicker.launch(arrayOf(FileFormat.PDF.mimeType)) },
            )
        }
        item {
            ToolCard(
                "PDF 边距裁剪",
                "自动识别或自定义裁剪上下左右边距",
                Icons.Outlined.Description,
                Modifier.fillMaxWidth(),
                onClick = { cropPicker.launch(arrayOf(FileFormat.PDF.mimeType)) },
            )
        }
        item {
            ToolCard(
                "PDF 元数据",
                "查看与编辑文档标题、作者与关键词信息",
                Icons.Outlined.Description,
                Modifier.fillMaxWidth(),
                onClick = { metadataPicker.launch(arrayOf(FileFormat.PDF.mimeType)) },
            )
        }
        item {
            ToolCard(
                "PDF 文字水印",
                "斜向 / 居中 / 页脚半透明文字，离线写入每一页",
                Icons.Outlined.Description,
                Modifier.fillMaxWidth(),
                onClick = { watermarkPicker.launch(arrayOf(FileFormat.PDF.mimeType)) },
            )
        }
        item {
            ToolCard(
                "PDF 删除页面",
                "移除不需要的页面后另存新文件",
                Icons.Outlined.DeleteOutline,
                Modifier.fillMaxWidth(),
                onClick = { deletePicker.launch(arrayOf(FileFormat.PDF.mimeType)) },
            )
        }
        item {
            ToolCard(
                "PDF 旋转",
                "将全部或指定页面旋转 90° / 180° / 270°",
                Icons.Outlined.Refresh,
                Modifier.fillMaxWidth(),
                onClick = { rotatePicker.launch(arrayOf(FileFormat.PDF.mimeType)) },
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun PdfToImagesScreen(
    draft: PdfToImagesDraft?,
    onBack: () -> Unit,
    onTarget: (FileFormat) -> Unit,
    onRanges: (String) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null) return EmptyPdfSelection("没有已选择的 PDF")
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onStart)
    }
    PdfConfigurationScaffold(
        title = "PDF 转图片",
        subtitle = "${draft.pageCount} 页 · 可导出全部或指定页面",
        onBack = onBack,
    ) {
        item {
            FileCard(draft.document.name, "PDF · ${draft.pageCount} 页 · ${formatFileSize(draft.document.sizeBytes)}")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("输出格式")
                listOf(FileFormat.PNG, FileFormat.JPG).forEach { format ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onTarget(format) }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = draft.targetFormat == format, onClick = { onTarget(format) })
                        Column {
                            Text(format.displayName, fontWeight = FontWeight.Medium)
                            Text(
                                if (format == FileFormat.PNG) "无损，文件通常更大" else "高质量，文件通常更小",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("导出页码")
                OutlinedTextField(
                    value = draft.pageRanges,
                    onValueChange = onRanges,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("留空表示全部页面") },
                    supportingText = { Text("支持 1-3, 5, 8-10；范围 1-${draft.pageCount}") },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
        item {
            PrimaryPdfButton(
                "选择文件夹并导出图片",
                onClick = { folderPicker.launch(null) },
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun PdfMergeScreen(
    draft: PdfMergeDraft?,
    onBack: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null || draft.documents.isEmpty()) return EmptyPdfSelection("没有已选择的 PDF")
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FileFormat.PDF.mimeType),
    ) { uri -> uri?.let(onStart) }
    PdfConfigurationScaffold(
        title = "PDF 合并",
        subtitle = "已选择 ${draft.documents.size} 个 PDF · 按下方顺序合并",
        onBack = onBack,
    ) {
        itemsIndexed(draft.documents, key = { _, item -> item.uri.toString() }) { index, document ->
            PdfOrderRow(
                index = index,
                name = document.name,
                details = formatFileSize(document.sizeBytes),
                canMoveUp = index > 0,
                canMoveDown = index < draft.documents.lastIndex,
                canRemove = draft.documents.size > 2,
                onMove = onMove,
                onRemove = onRemove,
            )
        }
        item {
            Text("合并会保留原 PDF 页面的尺寸、文字和矢量内容。", color = Muted, fontSize = 13.sp)
        }
        item {
            PrimaryPdfButton(
                "选择位置并合并 PDF",
                onClick = {
                    createDocument.launch(draft.suggestedOutputName)
                },
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun PdfSplitScreen(
    draft: PdfSplitDraft?,
    onBack: () -> Unit,
    onRanges: (String) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null) return EmptyPdfSelection("没有已选择的 PDF")
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onStart)
    }
    PdfConfigurationScaffold(
        title = "PDF 拆分",
        subtitle = "${draft.pageCount} 页 · 每个逗号分隔的范围生成一个 PDF",
        onBack = onBack,
    ) {
        item {
            FileCard(draft.document.name, "PDF · ${draft.pageCount} 页 · ${formatFileSize(draft.document.sizeBytes)}")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("拆分页码")
                OutlinedTextField(
                    value = draft.pageRanges,
                    onValueChange = onRanges,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如：1-3, 5, 8-10") },
                    supportingText = { Text("页码范围 1-${draft.pageCount}") },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
        item {
            Text("示例 1-3, 5 会生成两个 PDF：第 1-3 页和第 5 页。", color = Muted, fontSize = 13.sp)
        }
        item {
            PrimaryPdfButton(
                "选择文件夹并拆分 PDF",
                onClick = { folderPicker.launch(null) },
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun PdfDeletePagesScreen(
    draft: PdfDeletePagesDraft?,
    onBack: () -> Unit,
    onTogglePage: (Int) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null) return EmptyPdfSelection("没有已选择的 PDF")
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FileFormat.PDF.mimeType),
    ) { uri -> uri?.let(onStart) }

    PdfConfigurationScaffold(
        title = "PDF 删除页面",
        subtitle = "${draft.pageCount} 页 · 点击选择要删除的页面",
        onBack = onBack,
    ) {
        item {
            FileCard(draft.document.name, "PDF · ${draft.pageCount} 页 · ${formatFileSize(draft.document.sizeBytes)}")
        }
        item {
            Text("删除后剩余 ${draft.remaining} 页", color = Muted, fontSize = 13.sp)
        }
        item {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                (1..draft.pageCount).forEach { page ->
                    val selected = page in draft.selectedPages
                    Surface(
                        modifier = Modifier
                            .size(width = 52.dp, height = 44.dp)
                            .clickable { onTogglePage(page) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) Ink else SurfaceSoft,
                        border = BorderStroke(1.dp, if (selected) Ink else Border),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "$page",
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else Ink,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                "原文件保持不变，删除后生成新的 PDF。",
                color = Muted,
                fontSize = 13.sp,
            )
        }
        item {
            PrimaryPdfButton(
                "选择位置并生成新 PDF",
                onClick = {
                    createDocument.launch(
                        "${draft.document.name.substringBeforeLast('.')}_删除页面.pdf",
                    )
                },
                enabled = draft.selectedPages.isNotEmpty() && draft.remaining >= 1,
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun PdfRotatePagesScreen(
    draft: PdfRotatePagesDraft?,
    onBack: () -> Unit,
    onDegrees: (Int) -> Unit,
    onRanges: (String) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null) return EmptyPdfSelection("没有已选择的 PDF")
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FileFormat.PDF.mimeType),
    ) { uri -> uri?.let(onStart) }

    PdfConfigurationScaffold(
        title = "PDF 旋转",
        subtitle = "${draft.pageCount} 页 · 可旋转全部或指定页面",
        onBack = onBack,
    ) {
        item {
            FileCard(draft.document.name, "PDF · ${draft.pageCount} 页 · ${formatFileSize(draft.document.sizeBytes)}")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("旋转角度")
                listOf(90, 180, 270).forEach { degrees ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onDegrees(degrees) }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = draft.degrees == degrees, onClick = { onDegrees(degrees) })
                        Text("$degrees°", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("旋转页面（留空 = 全部）")
                OutlinedTextField(
                    value = draft.pageRanges,
                    onValueChange = onRanges,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如：1-3, 5, 8-10") },
                    supportingText = { Text("页码范围 1-${draft.pageCount}") },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
        item {
            PrimaryPdfButton(
                "选择位置并旋转 PDF",
                onClick = {
                    createDocument.launch("${draft.document.name.substringBeforeLast('.')}_旋转${draft.degrees}.pdf")
                },
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun PdfCompressScreen(
    draft: PdfCompressDraft?,
    onBack: () -> Unit,
    onPreset: (com.openconvert.app.domain.converter.PdfCompressPreset) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null) return EmptyPdfSelection("没有已选择的 PDF")
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FileFormat.PDF.mimeType),
    ) { uri -> uri?.let(onStart) }

    PdfConfigurationScaffold(
        title = "PDF 智能压缩",
        subtitle = "优化内置图像流与降采样，大幅节省空间",
        onBack = onBack,
    ) {
        item {
            FileCard(draft.document.name, "PDF · ${formatFileSize(draft.document.sizeBytes)}")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("压缩档位")
                com.openconvert.app.domain.converter.PdfCompressPreset.entries.forEach { preset ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onPreset(preset) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (draft.preset == preset) Ink else Border),
                        color = if (draft.preset == preset) SurfaceSoft else MaterialTheme.colorScheme.background,
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = draft.preset == preset, onClick = { onPreset(preset) })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(preset.displayName, fontWeight = FontWeight.SemiBold)
                                Text("目标分辨率 ${preset.maxDpi} DPI", color = Muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            PrimaryPdfButton(
                "选择保存位置并开始压缩",
                onClick = {
                    createDocument.launch("${draft.document.name.substringBeforeLast('.')}_compressed.pdf")
                },
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun PdfSecurityScreen(
    draft: PdfSecurityDraft?,
    onBack: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null) return EmptyPdfSelection("没有已选择的 PDF")
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FileFormat.PDF.mimeType),
    ) { uri -> uri?.let(onStart) }

    PdfConfigurationScaffold(
        title = if (draft.isEncrypt) "PDF 加密保护" else "PDF 解密",
        subtitle = if (draft.isEncrypt) "设置打开密码与保护策略" else "验证已知密码并导出未加密副本",
        onBack = onBack,
    ) {
        item {
            FileCard(draft.document.name, "PDF · ${formatFileSize(draft.document.sizeBytes)}")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel(if (draft.isEncrypt) "设置保护密码" else "输入文档密码")
                OutlinedTextField(
                    value = draft.password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("请输入密码...") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )
            }
        }
        item {
            PrimaryPdfButton(
                if (draft.isEncrypt) "保存加密 PDF" else "导出解密副本",
                onClick = {
                    val suffix = if (draft.isEncrypt) "_protected" else "_unlocked"
                    createDocument.launch("${draft.document.name.substringBeforeLast('.')}$suffix.pdf")
                },
                enabled = draft.password.isNotBlank(),
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun PdfCropScreen(
    draft: PdfCropDraft?,
    onBack: () -> Unit,
    onMarginChange: (Float, Float, Float, Float) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null) return EmptyPdfSelection("没有已选择的 PDF")
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FileFormat.PDF.mimeType),
    ) { uri -> uri?.let(onStart) }

    var left by remember { mutableStateOf(draft.leftPt) }
    var top by remember { mutableStateOf(draft.topPt) }
    var right by remember { mutableStateOf(draft.rightPt) }
    var bottom by remember { mutableStateOf(draft.bottomPt) }

    PdfConfigurationScaffold(
        title = "PDF 边距裁剪",
        subtitle = "调整页面四周裁切边距（单位：Point）",
        onBack = onBack,
    ) {
        item {
            FileCard(draft.document.name, "PDF · ${draft.pageCount} 页 · ${formatFileSize(draft.document.sizeBytes)}")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("裁剪边距（左 / 上 / 右 / 下）")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = left.toString(),
                        onValueChange = {
                            left = it.toFloatOrNull() ?: left
                            onMarginChange(left, top, right, bottom)
                        },
                        label = { Text("左") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = top.toString(),
                        onValueChange = {
                            top = it.toFloatOrNull() ?: top
                            onMarginChange(left, top, right, bottom)
                        },
                        label = { Text("上") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = right.toString(),
                        onValueChange = {
                            right = it.toFloatOrNull() ?: right
                            onMarginChange(left, top, right, bottom)
                        },
                        label = { Text("右") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = bottom.toString(),
                        onValueChange = {
                            bottom = it.toFloatOrNull() ?: bottom
                            onMarginChange(left, top, right, bottom)
                        },
                        label = { Text("下") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }
        item {
            PrimaryPdfButton(
                "选择保存位置并裁剪 PDF",
                onClick = {
                    createDocument.launch("${draft.document.name.substringBeforeLast('.')}_cropped.pdf")
                },
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun PdfWatermarkScreen(
    draft: PdfWatermarkDraft?,
    onBack: () -> Unit,
    onChange: (String, Float, com.openconvert.app.domain.converter.PdfWatermarkPosition) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null) return EmptyPdfSelection("没有已选择的 PDF")
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FileFormat.PDF.mimeType),
    ) { uri -> uri?.let(onStart) }
    var text by remember { mutableStateOf(draft.text) }
    var opacity by remember { mutableStateOf(draft.opacity) }
    var position by remember { mutableStateOf(draft.position) }

    PdfConfigurationScaffold(
        title = "PDF 文字水印",
        subtitle = "离线写入每一页，默认半透明斜向居中",
        onBack = onBack,
    ) {
        item {
            FileCard(draft.document.name, "PDF · ${draft.pageCount} 页 · ${formatFileSize(draft.document.sizeBytes)}")
        }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onChange(it, opacity, position)
                },
                label = { Text("水印文字") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("位置")
                com.openconvert.app.domain.converter.PdfWatermarkPosition.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                position = option
                                onChange(text, opacity, option)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = position == option,
                            onClick = {
                                position = option
                                onChange(text, opacity, option)
                            },
                        )
                        Text(option.label)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldLabel("透明度 ${(opacity * 100).toInt()}%")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.12f, 0.18f, 0.28f, 0.40f).forEach { value ->
                        OutlinedButton(
                            onClick = {
                                opacity = value
                                onChange(text, value, position)
                            },
                        ) { Text("${(value * 100).toInt()}%") }
                    }
                }
            }
        }
        item {
            PrimaryPdfButton(
                "选择保存位置并加水印",
                onClick = {
                    if (text.trim().isEmpty()) return@PrimaryPdfButton
                    createDocument.launch("${draft.document.name.substringBeforeLast('.')}_watermark.pdf")
                },
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun PdfMetadataScreen(
    draft: PdfMetadataDraft?,
    onBack: () -> Unit,
    onSave: (String, String, String, String, Uri) -> Unit,
) {
    if (draft == null) return EmptyPdfSelection("没有已选择的 PDF")
    var title by remember { mutableStateOf(draft.metadata.title) }
    var author by remember { mutableStateOf(draft.metadata.author) }
    var subject by remember { mutableStateOf(draft.metadata.subject) }
    var keywords by remember { mutableStateOf(draft.metadata.keywords) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FileFormat.PDF.mimeType),
    ) { uri -> uri?.let { onSave(title, author, subject, keywords, it) } }

    PdfConfigurationScaffold(
        title = "PDF 元数据管理",
        subtitle = "查看与修改文档属性信息",
        onBack = onBack,
    ) {
        item {
            FileCard(draft.document.name, "PDF · ${draft.metadata.pageCount} 页 · ${formatFileSize(draft.metadata.fileSizeBytes)}")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("文档标题 (Title)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("作者 (Author)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("主题 (Subject)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("关键词 (Keywords)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
        item {
            PrimaryPdfButton(
                "保存并导出 PDF",
                onClick = {
                    createDocument.launch("${draft.document.name.substringBeforeLast('.')}_meta.pdf")
                },
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun PdfPageManagerScreen(
    draft: PdfPageManagerDraft?,
    onBack: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onRotate: (Set<String>, Int) -> Unit,
    onDelete: (Set<String>) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null) return EmptyPdfSelection("没有已选择的 PDF")
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FileFormat.PDF.mimeType),
    ) { uri -> uri?.let(onStart) }

    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    PdfConfigurationScaffold(
        title = "PDF 页面管理器",
        subtitle = "共 ${draft.pages.size} 页 · 支持拖拽重排、旋转与批量删除",
        onBack = onBack,
    ) {
        item {
            FileCard(draft.document.name, "PDF · ${draft.pages.size} 页 · ${formatFileSize(draft.document.sizeBytes)}")
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onRotate(selectedIds.ifEmpty { draft.pages.map { it.id }.toSet() }, 90) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("顺时针 90°")
                }
                OutlinedButton(
                    onClick = {
                        if (selectedIds.isNotEmpty()) {
                            onDelete(selectedIds)
                            selectedIds = emptySet()
                        }
                    },
                    enabled = selectedIds.isNotEmpty() && draft.pages.size > selectedIds.size,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除所选")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.pages.forEachIndexed { index, page ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedIds = if (page.id in selectedIds) selectedIds - page.id else selectedIds + page.id
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (page.id in selectedIds) Ink else Border),
                        color = if (page.id in selectedIds) SurfaceSoft else MaterialTheme.colorScheme.background,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("第 ${index + 1} 页 (原第 ${page.originalPageIndex + 1} 页)", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            if (page.rotationDegrees != 0) {
                                Text("${page.rotationDegrees}°", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                            }
                            IconButton(onClick = { onReorder(index, (index - 1).coerceAtLeast(0)) }, enabled = index > 0) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移")
                            }
                            IconButton(onClick = { onReorder(index, (index + 1).coerceAtMost(draft.pages.size - 1)) }, enabled = index < draft.pages.size - 1) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移")
                            }
                        }
                    }
                }
            }
        }
        item {
            PrimaryPdfButton(
                "选择保存位置并导出新 PDF",
                onClick = {
                    createDocument.launch("${draft.document.name.substringBeforeLast('.')}_reordered.pdf")
                },
                enabled = draft.pages.isNotEmpty(),
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun ImagesToPdfScreen(
    draft: ImagesToPdfDraft?,
    onBack: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null || draft.documents.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("没有已选择的图片", color = Muted)
        }
        return
    }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(FileFormat.PDF.mimeType),
    ) { uri -> uri?.let(onStart) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("图片转 PDF", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("已选择 ${draft.documents.size} 张图片 · 每张图片生成一页", color = Muted, fontSize = 14.sp)
            }
        }
        itemsIndexed(draft.documents, key = { _, document -> document.uri.toString() }) { index, document ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceSoft,
                border = BorderStroke(1.dp, Border),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", color = Muted, modifier = Modifier.padding(horizontal = 8.dp))
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(document.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(formatFileSize(document.sizeBytes), color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = { onMove(index, -1) }, enabled = index > 0) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移")
                    }
                    IconButton(onClick = { onMove(index, 1) }, enabled = index < draft.documents.lastIndex) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移")
                    }
                    IconButton(onClick = { onRemove(index) }, enabled = draft.documents.size > 1) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "移除")
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SurfaceSoft,
            ) {
                Text(
                    "页面使用 A4 尺寸，并根据图片方向自动选择横向或纵向；图片会保持原比例。",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        item {
            Button(
                onClick = { createDocument.launch(draft.suggestedOutputName) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Text("选择位置并生成 PDF", fontSize = 16.sp)
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() }
        }
    }
}
