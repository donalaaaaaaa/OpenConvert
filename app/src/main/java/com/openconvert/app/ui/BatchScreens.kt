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

@Composable
internal fun BatchPickerScreen(
    onBack: () -> Unit,
    onFilesPicked: (List<Uri>) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) onFilesPicked(uris) }

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
                Text("批量转换", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("一次选择多个文件，统一转换为同一种格式", color = Muted, fontSize = 14.sp)
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    picker.launch(arrayOf("image/*", "video/*", "audio/*"))
                },
                shape = RoundedCornerShape(16.dp),
                color = SurfaceSoft,
                border = BorderStroke(1.dp, Border),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 30.dp, horizontal = 24.dp),
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
                    Text("选择多个文件", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text("图片 · 视频 · 音频（需同一类）", color = Muted, fontSize = 13.sp)
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() }
        }
    }
}

@Composable
internal fun BatchConfigureScreen(
    draft: BatchDraft,
    presets: List<com.openconvert.app.domain.preset.Preset> = emptyList(),
    onApplyPreset: (com.openconvert.app.domain.preset.Preset) -> Unit = {},
    onBack: () -> Unit,
    onTarget: (FileFormat) -> Unit,
    onQuality: (QualityPreset) -> Unit,
    onResolution: (ResolutionPreset) -> Unit,
    onStart: (Uri) -> Unit,
) {
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onStart)
    }
    var targetMenuOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.batch_title), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        R.string.batch_selected,
                        draft.documents.size,
                        draft.documents.first().format.displayName,
                        draft.targetFormat.displayName,
                    ),
                    color = Muted,
                    fontSize = 14.sp,
                )
            }
        }
        // §8.3：批量应用预设 —— 选 50 张图 + 微信发送 → 全部自动处理。
        if (presets.isNotEmpty()) {
            item {
                PresetStrip(
                    presets = presets,
                    appliedPresetId = draft.presetId,
                    onApply = onApplyPreset,
                    onSaveCurrent = {},
                    showSaveAction = false,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("转换为")
                Box {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { targetMenuOpen = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Border),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(draft.targetFormat.displayName, fontWeight = FontWeight.Medium)
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                    DropdownMenu(expanded = targetMenuOpen, onDismissRequest = { targetMenuOpen = false }) {
                        draft.commonFormats.forEach { format ->
                            DropdownMenuItem(
                                text = { Text(format.displayName) },
                                onClick = {
                                    onTarget(format)
                                    targetMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("质量")
                QualityPreset.entries.forEach { preset ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onQuality(preset) }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = draft.quality == preset, onClick = { onQuality(preset) })
                        Text(preset.label, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel("尺寸")
                ResolutionPreset.entries.forEach { preset ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onResolution(preset) }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = draft.resolution == preset, onClick = { onResolution(preset) })
                        Text(preset.label, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        item {
            Text(
                "小文件并行转换，视频最多同时 2 个，避免手机过热。",
                color = Muted,
                fontSize = 13.sp,
            )
        }
        item {
            Button(
                onClick = { folderPicker.launch(null) },
                enabled = draft.engineAvailable,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Text(if (draft.engineAvailable) "选择文件夹并开始批量转换" else "所选文件没有共同的目标格式", fontSize = 16.sp)
            }
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PrivacyHint() } }
    }
}

@Composable
internal fun BatchProgressScreen(
    job: BatchJob,
    tasks: List<ConversionTask>,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val isFinished = job.status == BatchJobStatus.COMPLETED || job.status == BatchJobStatus.CANCELLED
    val isPaused = job.status == BatchJobStatus.PAUSED
    val current = tasks.firstOrNull { it.status == ConversionStatus.RUNNING }
        ?: tasks.firstOrNull { it.status == ConversionStatus.PENDING }
    val doneCount = tasks.count { it.status == ConversionStatus.COMPLETED }
    val failedCount = tasks.count { it.status == ConversionStatus.FAILED }
    val remaining = tasks.size - doneCount - failedCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (isFinished) "批量转换结束" else "批量转换中", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(job.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        Text("$doneCount / ${job.total}", fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { job.progressPercent / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = Ink,
            trackColor = SurfaceSoft,
        )
        Spacer(Modifier.height(12.dp))
        Text("${job.progressPercent}%", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (isPaused) {
            Text(stringResource(R.string.batch_paused_label), color = Muted, fontSize = 13.sp)
        } else if (isFinished) {
            Text(stringResource(R.string.batch_done_fail, doneCount, failedCount), color = Muted, fontSize = 13.sp)
        } else {
            current?.let {
                Text(stringResource(R.string.batch_current, it.sourceName), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.batch_remaining_files, remaining), color = Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(34.dp))
        if (!isFinished) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isPaused) {
                    TextButton(onClick = onResume) { Text("继续", color = Ink) }
                } else {
                    TextButton(onClick = onPause) { Text("暂停", color = Ink) }
                }
                TextButton(onClick = onCancel) { Text("取消全部", color = Ink) }
            }
        }
        if (isFinished) {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Text("完成", fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        PrivacyHint()
    }
}
