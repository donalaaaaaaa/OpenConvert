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
internal fun HomeScreen(
    recentTasks: List<ConversionTask>,
    onFilePicked: (android.net.Uri) -> Unit,
    onPdfTools: () -> Unit,
    onBatch: () -> Unit,
    onArchive: () -> Unit,
    onOffice: (() -> Unit)?,
    onSettings: () -> Unit,
    onReuse: (ConversionTask) -> Unit,
    onDelete: (ConversionTask) -> Unit,
) {
    val context = LocalContext.current
    var actionTask by remember { mutableStateOf<ConversionTask?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onFilePicked)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text("OpenConvert", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text("本地文件转换", color = Muted, fontSize = 15.sp)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = Ink)
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { picker.launch(arrayOf("*/*")) }
                    .actionSemantics(
                        AccessibilityCopy.pickFile(
                            if (BuildConfig.OFFICE_BUNDLED) {
                                "PDF、图片、视频、音频、Office"
                            } else {
                                "PDF、图片、视频、音频"
                            },
                        ),
                    ),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceSoft,
                border = BorderStroke(1.dp, Border),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 34.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Ink) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(9.dp).size(22.dp),
                        )
                    }
                    Text("选择文件", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (BuildConfig.OFFICE_BUNDLED) {
                            "PDF · 图片 · 视频 · 音频 · Office"
                        } else {
                            "PDF · 图片 · 视频 · 音频"
                        },
                        color = Muted,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        item { SectionTitle("常用工具") }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard(
                        "图片转换",
                        "JPG · PNG · WEBP",
                        Icons.Outlined.Image,
                        Modifier.weight(1f),
                        onClick = { picker.launch(arrayOf("image/*")) },
                    )
                    ToolCard(
                        "PDF 工具",
                        "转换 · 合并 · 拆分",
                        Icons.Outlined.Description,
                        Modifier.weight(1f),
                        onClick = onPdfTools,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard(
                        "视频转换",
                        "MP4 · MOV · MKV",
                        Icons.Outlined.VideoFile,
                        Modifier.weight(1f),
                        onClick = { picker.launch(arrayOf("video/*")) },
                    )
                    ToolCard(
                        "音频转换",
                        "MP3 · WAV · FLAC",
                        Icons.Outlined.AudioFile,
                        Modifier.weight(1f),
                        onClick = { picker.launch(arrayOf("audio/*")) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard(
                        "批量转换",
                        "一次转换多个文件",
                        Icons.Outlined.Add,
                        Modifier.weight(1f),
                        onClick = onBatch,
                    )
                    ToolCard(
                        "压缩包",
                        "ZIP · TAR · GZIP · BZIP2",
                        Icons.Outlined.Folder,
                        Modifier.weight(1f),
                        onClick = onArchive,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard(
                        "Office 转换",
                        if (BuildConfig.OFFICE_BUNDLED) {
                            "DOCX · PPTX · XLSX → PDF"
                        } else {
                            "Office 版提供"
                        },
                        Icons.AutoMirrored.Outlined.Article,
                        Modifier.fillMaxWidth(),
                        onClick = onOffice,
                    )
                }
            }
        }

        item { SectionTitle("最近转换") }

        if (recentTasks.isEmpty()) {
            item {
                Text(
                    "还没有转换记录，选择一个文件开始。",
                    color = Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        } else {
            items(recentTasks, key = { it.id }) { task ->
                HistoryRow(
                    task = task,
                    onOpen = { actionTask = task },
                )
            }
        }
    }
    actionTask?.let { task ->
        HistoryActionSheet(
            task = task,
            onDismiss = { actionTask = null },
            onOpen = {
                if (!HistoryOutputs.startOpen(context, task)) {
                    /* snackbar is handled at app root only for viewModel messages */
                }
                actionTask = null
            },
            onShare = {
                HistoryOutputs.startShare(context, task)
                actionTask = null
            },
            onReuse = {
                onReuse(task)
                actionTask = null
            },
            onDelete = {
                onDelete(task)
                actionTask = null
            },
        )
    }
}
