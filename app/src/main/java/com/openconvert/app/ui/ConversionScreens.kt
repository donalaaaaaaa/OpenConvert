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
internal fun ConversionScreen(
    draft: ConversionDraft?,
    presets: List<com.openconvert.app.domain.preset.Preset> = emptyList(),
    appliedPresetId: String? = null,
    onApplyPreset: (com.openconvert.app.domain.preset.Preset) -> Unit = {},
    onSavePreset: (String) -> Unit = {},
    onBack: () -> Unit,
    onTarget: (FileFormat) -> Unit,
    onQuality: (QualityPreset) -> Unit,
    onResolution: (ResolutionPreset) -> Unit,
    onRotate: (Int) -> Unit,
    onCropAspect: (String) -> Unit,
    onFlip: (Int) -> Unit,
    onStripMetadata: (Boolean) -> Unit,
    onStart: (Uri) -> Unit,
) {
    if (draft == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.convert_empty), color = Muted)
        }
        return
    }

    var targetMenuOpen by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    val targets = draft.document.format.availableTargets()
    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(draft.targetFormat.mimeType),
    ) { uri -> uri?.let(onStart) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        }
        item {
            Text(stringResource(R.string.convert_title), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        item {
            FileCard(
                name = draft.document.name,
                details = "${draft.document.format.displayName} · ${formatFileSize(draft.document.sizeBytes)}",
            )
        }
        if (presets.isNotEmpty()) {
            item {
                PresetStrip(
                    presets = presets,
                    appliedPresetId = appliedPresetId,
                    onApply = onApplyPreset,
                    onSaveCurrent = { showSavePresetDialog = true },
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel(stringResource(R.string.convert_to))
                Box {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { targetMenuOpen = true },
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
                    DropdownMenu(
                        expanded = targetMenuOpen,
                        onDismissRequest = { targetMenuOpen = false },
                    ) {
                        targets.forEach { format ->
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
        val hasQualitySetting = draft.targetFormat in setOf(
            FileFormat.JPG,
            FileFormat.WEBP,
            FileFormat.MP3,
            FileFormat.AAC,
            FileFormat.M4A,
            FileFormat.MP4,
            FileFormat.WEBM,
        )
        if (hasQualitySetting) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldLabel(if (draft.document.format.category == FileCategory.VIDEO) stringResource(R.string.convert_quality_video) else stringResource(R.string.convert_quality))
                    QualityPreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onQuality(preset) }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = draft.quality == preset,
                                onClick = { onQuality(preset) },
                            )
                            Column {
                                Text(stringResource(preset.labelRes), fontWeight = FontWeight.Medium)
                                if (preset == QualityPreset.BALANCED) {
                                    Text(stringResource(R.string.quality_recommended), color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    stringResource(R.string.convert_lossless, draft.targetFormat.displayName),
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
        }
        if (draft.targetFormat.category in setOf(FileCategory.IMAGE, FileCategory.VIDEO)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldLabel(stringResource(R.string.convert_size))
                    ResolutionPreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResolution(preset) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = draft.resolution == preset,
                                onClick = { onResolution(preset) },
                            )
                            Text(stringResource(preset.labelRes), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        if (draft.targetFormat.category == FileCategory.IMAGE) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldLabel(stringResource(R.string.convert_rotate))
                    listOf(
                        0 to stringResource(R.string.convert_rotate_none),
                        90 to "90°",
                        180 to "180°",
                        270 to "270°",
                    ).forEach { (degrees, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRotate(degrees) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = draft.rotateDegrees == degrees,
                                onClick = { onRotate(degrees) },
                            )
                            Text(label, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldLabel(stringResource(R.string.convert_crop))
                    listOf(
                        "free" to stringResource(R.string.convert_crop_free),
                        "1:1" to stringResource(R.string.convert_crop_square),
                        "4:3" to "4:3",
                        "3:2" to "3:2",
                        "16:9" to "16:9",
                        "9:16" to stringResource(R.string.convert_crop_portrait),
                    ).forEach { (aspect, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCropAspect(aspect) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = draft.cropAspect == aspect,
                                onClick = { onCropAspect(aspect) },
                            )
                            Text(label, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldLabel(stringResource(R.string.convert_flip))
                    listOf(
                        0 to stringResource(R.string.convert_flip_none),
                        1 to stringResource(R.string.convert_flip_h),
                        2 to stringResource(R.string.convert_flip_v),
                    ).forEach { (flip, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFlip(flip) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = draft.flip == flip,
                                onClick = { onFlip(flip) },
                            )
                            Text(label, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStripMetadata(!draft.stripMetadata) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = draft.stripMetadata,
                        onCheckedChange = onStripMetadata,
                    )
                    Column {
                        Text(stringResource(R.string.convert_strip_meta), fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.convert_strip_meta_sub), color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Button(
                onClick = { createDocument.launch(draft.suggestedOutputName) },
                enabled = draft.engineAvailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Text(if (draft.engineAvailable) stringResource(R.string.convert_start) else stringResource(R.string.convert_engine_missing), fontSize = 16.sp)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.privacy_hint), color = Muted, fontSize = 13.sp)
            }
        }
    }

    // 存为预设（计划书 §八）：把当前配置连同尺寸约束一起保存。
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text(stringResource(R.string.presets_save)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(
                            R.string.convert_save_preset_hint,
                            buildString {
                                append(draft.targetFormat.displayName)
                                append(" · ")
                                append(stringResource(draft.quality.labelRes))
                                if (draft.stripMetadata) append(stringResource(R.string.convert_strip_tag))
                            },
                        ),
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.convert_preset_name)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSavePreset(presetNameInput)
                        presetNameInput = ""
                        showSavePresetDialog = false
                    },
                    enabled = presetNameInput.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
internal fun ConversionProgressScreen(task: ConversionTask, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.conversion_running), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Text(task.sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${task.sourceFormat.displayName} → ${task.targetFormat.displayName}",
            color = Muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(32.dp))
        LinearProgressIndicator(
            progress = { task.progress / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = Ink,
            trackColor = SurfaceSoft,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "${task.progress}%",
            modifier = Modifier.liveProgressSemantics(
                AccessibilityCopy.progress(task.progress, task.bytesProcessed, task.bytesTotal),
            ),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
        if (task.bytesTotal > 0L && task.bytesProcessed > 0L) {
            Text(
                "${com.openconvert.app.domain.task.TaskCardFactory.formatSize(task.bytesProcessed)} / ${com.openconvert.app.domain.task.TaskCardFactory.formatSize(task.bytesTotal)}",
                color = Muted,
                fontSize = 13.sp,
            )
        }
        Text(stringResource(R.string.conversion_local), color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(36.dp))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel_conversion), color = Ink) }
        Spacer(Modifier.height(22.dp))
        PrivacyHint()
    }
}

@Composable
internal fun ConversionCompleteScreen(
    task: ConversionTask,
    outputName: String,
    outputUris: List<String>,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.convert_share_chooser)
    val parsedOutputUris = outputUris.map(Uri::parse)
    val outputUri = parsedOutputUris.firstOrNull()
    val outputSize = task.outputSize ?: 0L
    val savedBytes = task.fileSize - outputSize

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Icon(Icons.Outlined.CheckCircleOutline, contentDescription = stringResource(R.string.conversion_complete), modifier = Modifier.size(58.dp))
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.conversion_complete), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(outputName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatFileSize(outputSize), color = Muted)
            }
        }
        item {
            SettingsGroup {
                SettingRow(stringResource(R.string.conversion_original), formatFileSize(task.fileSize))
                HorizontalDivider(color = Border)
                SettingRow(stringResource(R.string.conversion_new_file), formatFileSize(outputSize))
                HorizontalDivider(color = Border)
                SettingRow(
                    stringResource(if (savedBytes >= 0) R.string.conversion_saved else R.string.conversion_grew),
                    formatFileSize(kotlin.math.abs(savedBytes)),
                )
            }
        }
        item {
            Button(
                onClick = {
                    outputUri?.let { uri ->
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, task.targetFormat.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                },
                enabled = outputUri != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (parsedOutputUris.size > 1) stringResource(R.string.action_open_first_file) else stringResource(R.string.action_open_file))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = {
                        if (parsedOutputUris.isNotEmpty()) {
                            val share = Intent(
                                if (parsedOutputUris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND,
                            ).apply {
                                type = task.targetFormat.mimeType
                                if (parsedOutputUris.size > 1) {
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(parsedOutputUris))
                                } else {
                                    putExtra(Intent.EXTRA_STREAM, parsedOutputUris.first())
                                }
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, shareChooserTitle))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.action_share), color = Ink)
                }
                TextButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_done), color = Ink)
                }
            }
        }
    }
}

@Composable
internal fun ConversionFailedScreen(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = stringResource(R.string.conversion_failed), modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.conversion_failed), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Muted, lineHeight = 21.sp)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_retry))
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back_home), color = Ink) }
    }
}
