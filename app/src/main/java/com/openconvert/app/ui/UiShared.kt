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
import androidx.compose.ui.res.stringResource
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
import com.openconvert.app.R
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

internal const val HOME = "home"
internal const val HISTORY = "history"
internal const val TASKS = "tasks"
internal const val SETTINGS = "settings"
internal const val CONVERT = "convert"
internal const val PRIVACY = "privacy"
internal const val LICENSES = "licenses"
internal const val PDF_TOOLS = "pdf_tools"
internal const val BATCH = "batch"
internal const val BATCH_PROGRESS = "batch_progress"
internal const val IMAGES_TO_PDF = "images_to_pdf"
internal const val PDF_TO_IMAGES = "pdf_to_images"
internal const val PDF_MERGE = "pdf_merge"
internal const val PDF_SPLIT = "pdf_split"
internal const val PDF_DELETE = "pdf_delete"
internal const val PDF_ROTATE = "pdf_rotate"
internal const val PDF_COMPRESS = "pdf_compress"
internal const val PDF_SECURITY = "pdf_security"
internal const val PDF_CROP = "pdf_crop"
internal const val PDF_METADATA = "pdf_metadata"
internal const val PDF_WATERMARK = "pdf_watermark"
internal const val PDF_PAGE_MANAGER = "pdf_page_manager"
internal const val ARCHIVE = "archive"
internal const val ARCHIVE_COMPRESS_SCREEN = "archive_compress"
internal const val ARCHIVE_EXTRACT_SCREEN = "archive_extract"
internal const val OFFICE_TOOLS = "office_tools"

internal data class MainDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

internal val mainDestinations = listOf(
    MainDestination(HOME, R.string.nav_home, Icons.Outlined.Home),
    MainDestination(TASKS, R.string.nav_tasks, Icons.Outlined.Sync),
    MainDestination(HISTORY, R.string.nav_history, Icons.Outlined.History),
    MainDestination(SETTINGS, R.string.nav_settings, Icons.Outlined.Settings),
)

/**
 * 首页 UI 2.0 能力面板里的工具 → 对应工具页路由（计划书 §6.3）。
 *
 * 这些工具页各自有自己的文件选择器（多文件输入 / 目录输出 / 页面参数），
 * 因此这里只做导航，不把已选文件带过去——避免与工具页自身的选择流程冲突。
 * 返回 null 表示该 kind 不是独立页面（SINGLE / BATCH 走别的入口）。
 */
internal fun routeForTool(kind: ConversionKind): String? = when (kind) {
    ConversionKind.IMAGES_TO_PDF -> IMAGES_TO_PDF
    ConversionKind.PDF_TO_IMAGES -> PDF_TO_IMAGES
    ConversionKind.PDF_MERGE -> PDF_MERGE
    ConversionKind.PDF_SPLIT -> PDF_SPLIT
    ConversionKind.PDF_DELETE_PAGES -> PDF_DELETE
    ConversionKind.PDF_ROTATE_PAGES -> PDF_ROTATE
    ConversionKind.PDF_COMPRESS -> PDF_COMPRESS
    ConversionKind.PDF_SECURITY -> PDF_SECURITY
    ConversionKind.PDF_CROP -> PDF_CROP
    ConversionKind.PDF_METADATA -> PDF_METADATA
    ConversionKind.PDF_WATERMARK -> PDF_WATERMARK
    ConversionKind.PDF_PAGE_MANAGER -> PDF_PAGE_MANAGER
    ConversionKind.ARCHIVE_COMPRESS -> ARCHIVE_COMPRESS_SCREEN
    ConversionKind.ARCHIVE_EXTRACT -> ARCHIVE_EXTRACT_SCREEN
    ConversionKind.SINGLE, ConversionKind.BATCH -> null
}

@Composable
internal fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .clickable(onClick = onClick)
                    .actionSemantics(AccessibilityCopy.tool(title, subtitle))
            } else {
                Modifier
            },
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceSoft),
        border = BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
internal fun PdfConfigurationScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 14.sp)
            }
        }
        content()
    }
}

@Composable
internal fun PdfOrderRow(
    index: Int,
    name: String,
    details: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
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
            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(details, color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = { onMove(index, -1) }, enabled = canMoveUp) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移")
            }
            IconButton(onClick = { onMove(index, 1) }, enabled = canMoveDown) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移")
            }
            IconButton(onClick = { onRemove(index) }, enabled = canRemove) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "移除")
            }
        }
    }
}

@Composable
internal fun PrimaryPdfButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink),
    ) { Text(label, fontSize = 16.sp) }
}

@Composable
internal fun EmptyPdfSelection(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message, color = Muted) }
}

@Composable
internal fun PrivacyHint() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(stringResource(R.string.privacy_hint), color = Muted, fontSize = 13.sp)
    }
}

@Composable
internal fun FileCard(name: String, details: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceSoft,
        border = BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(details, color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryRow(
    task: ConversionTask,
    selecting: Boolean = false,
    checked: Boolean = false,
    onToggle: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onOpen: () -> Unit = {},
) {
    val canSelect = task.status != ConversionStatus.PENDING && task.status != ConversionStatus.RUNNING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selecting) {
                        if (canSelect) onToggle()
                    } else {
                        onOpen()
                    }
                },
                onLongClick = onLongPress,
            )
            .actionSemantics(AccessibilityCopy.history(task), state = historyStatus(task))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (selecting) {
            Checkbox(
                checked = checked,
                onCheckedChange = { if (canSelect) onToggle() },
                enabled = canSelect,
                colors = CheckboxDefaults.colors(checkedColor = Ink),
            )
        }
        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceSoft) {
            Icon(
                if (task.status == ConversionStatus.COMPLETED) Icons.Outlined.CheckCircleOutline else Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(task.sourceName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append("${task.sourceFormat.displayName} → ${task.targetFormat.displayName}")
                    task.actualEngine?.let { append(" · ${it.displayName}") }
                },
                color = Muted,
                fontSize = 13.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(historyStatus(task), color = Muted, fontSize = 12.sp)
            Text(formatTime(task.createdAt), color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceSoft,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(content = content)
    }
}

@Composable
internal fun SettingRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(onClick = onClick)
                        .actionSemantics(AccessibilityCopy.setting(title, value))
                } else {
                    Modifier
                },
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontWeight = FontWeight.Medium)
        Text(value, color = Muted, fontSize = 13.sp)
    }
}

@Composable
internal fun PrivacyFact(label: String) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, tint = Ink, modifier = Modifier.size(21.dp))
        Text(label, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.History, contentDescription = null, tint = Muted, modifier = Modifier.size(32.dp))
        Text(title, fontWeight = FontWeight.Medium)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
}

internal fun historyStatus(task: ConversionTask): String = when (task.status) {
    ConversionStatus.PENDING -> "排队中"
    ConversionStatus.RUNNING -> "${task.progress}%"
    ConversionStatus.FAILED -> "失败"
    ConversionStatus.CANCELLED -> "已取消"
    ConversionStatus.COMPLETED -> formatFileSize(task.outputSize ?: task.fileSize)
}

internal fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> "大小未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

internal fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
