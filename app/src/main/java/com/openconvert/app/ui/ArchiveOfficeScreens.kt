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
internal fun ArchiveToolsScreen(
    onBack: () -> Unit,
    onCompressPicked: (List<Uri>) -> Unit,
    onExtractPicked: (Uri) -> Unit,
) {
    val compressPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) onCompressPicked(uris) }
    val extractPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onExtractPicked) }

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
                Text("压缩包", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("打包与解压都在本地完成", color = Muted, fontSize = 14.sp)
            }
        }
        item {
            ToolCard(
                "压缩文件",
                "选择多个文件打包为 ZIP 或 TAR",
                Icons.Outlined.Folder,
                Modifier.fillMaxWidth(),
                onClick = { compressPicker.launch(arrayOf("*/*")) },
            )
        }
        item {
            ToolCard(
                "解压文件",
                "解压 ZIP / TAR.GZ / TAR.BZ2 到文件夹",
                Icons.Outlined.Folder,
                Modifier.fillMaxWidth(),
                onClick = { extractPicker.launch(arrayOf("application/zip", "application/gzip", "application/x-tar", "application/x-bzip2")) },
            )
        }
        item {
            Text(
                "支持格式：ZIP、TAR、TAR.GZ、GZIP、BZIP2。",
                color = Muted,
                fontSize = 13.sp,
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun ArchiveCompressScreen(
    draft: ArchiveCompressDraft?,
    onBack: () -> Unit,
    onTarget: (FileFormat) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null || draft.documents.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("没有已选择的文件", color = Muted)
        }
        return
    }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(draft.targetFormat.mimeType),
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
                Text("压缩文件", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("已选择 ${draft.documents.size} 个文件", color = Muted, fontSize = 14.sp)
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
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(document.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(formatFileSize(document.sizeBytes), color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("压缩格式")
                listOf(FileFormat.ZIP, FileFormat.TAR, FileFormat.GZIP, FileFormat.BZIP2).forEach { format ->
                    val disabled = format in setOf(FileFormat.GZIP, FileFormat.BZIP2) && draft.documents.size > 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !disabled) { onTarget(format) }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = draft.targetFormat == format, onClick = { if (!disabled) onTarget(format) })
                        Column {
                            Text(format.displayName, fontWeight = FontWeight.Medium)
                            if (disabled) {
                                Text("仅单个文件", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            PrimaryPdfButton(
                "选择位置并压缩",
                onClick = { createDocument.launch(draft.suggestedOutputName) },
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun ArchiveExtractScreen(
    draft: ArchiveExtractDraft?,
    onBack: () -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("没有已选择的压缩包", color = Muted)
        }
        return
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onStart)
    }
    PdfConfigurationScaffold(
        title = "解压文件",
        subtitle = "选择保存解压文件的文件夹",
        onBack = onBack,
    ) {
        item {
            FileCard(draft.document.name, "压缩包 · ${formatFileSize(draft.document.sizeBytes)}")
        }
        item {
            Text(
                "解压后文件将保存到所选文件夹中。",
                color = Muted,
                fontSize = 13.sp,
            )
        }
        item {
            PrimaryPdfButton(
                "选择文件夹并解压",
                onClick = { folderPicker.launch(null) },
            )
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun OfficeToolsScreen(
    onBack: () -> Unit,
    onOfficePicked: (Uri) -> Unit,
) {
    val docPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onOfficePicked) }

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
                Text("Office 转换", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("Word · PPT · Excel 离线转换为 PDF", color = Muted, fontSize = 14.sp)
            }
        }

        // 状态卡片
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSoft),
                border = BorderStroke(1.dp, Border),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = null, tint = Ink)
                            Text("LibreOfficeKit 引擎", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Ink,
                        ) {
                            Text(
                                "已内置就绪",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Text(
                        "已内置完整 LibreOffice 离线文档渲染引擎。支持将 DOCX、PPTX、XLSX 高保真转换为 PDF，所有渲染 100% 在本地沙箱中完成，完全无需联网。",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        // 核心操作
        item {
            Button(
                onClick = {
                    docPicker.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/msword",
                            "application/vnd.ms-powerpoint",
                            "application/vnd.ms-excel",
                            "*/*",
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("选择 Office 文档转换", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        item { SectionTitle("支持转换格式") }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSoft),
                border = BorderStroke(1.dp, Border),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OfficeFormatRow("Word 文档", "DOCX / DOC → PDF", "保留完整排版、字体样式、表格与图片")
                    HorizontalDivider(color = Border)
                    OfficeFormatRow("演示文稿", "PPTX / PPT → PDF", "按幻灯片页转换为 PDF 页面，保持版式")
                    HorizontalDivider(color = Border)
                    OfficeFormatRow("电子表格", "XLSX / XLS → PDF", "将表格报表与工作表结构转为文档")
                }
            }
        }

        item { SectionTitle("设计与特性") }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSoft),
                border = BorderStroke(1.dp, Border),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FeatureDescRow("🔒 绝对隐私安全", "区别于云端转换，文件完全在设备本地沙箱中解析渲染，不申请联网权限。")
                    HorizontalDivider(color = Border)
                    FeatureDescRow("⚡ 原生高保真", "采用桌面级 LibreOffice 渲染核心，支持复杂表格、排版及矢量图形高保真输出。")
                }
            }
        }

        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun OfficeFormatRow(title: String, subtitle: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text(desc, color = Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun FeatureDescRow(title: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(desc, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}
