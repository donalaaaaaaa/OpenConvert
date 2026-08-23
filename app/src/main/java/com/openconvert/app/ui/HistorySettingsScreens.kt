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
internal fun HistoryScreen(
    history: List<ConversionTask>,
    onClear: () -> Unit,
    onDelete: (Collection<String>) -> Unit,
    onReuse: (ConversionTask) -> Unit,
) {
    val context = LocalContext.current
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var actionTask by remember { mutableStateOf<ConversionTask?>(null) }
    val selectable = history.filter { it.status != ConversionStatus.PENDING && it.status != ConversionStatus.RUNNING }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selecting) stringResource(R.string.history_selected, selected.size) else stringResource(R.string.history_title),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (selecting) stringResource(R.string.history_in_progress_locked) else stringResource(R.string.history_device_only),
                        color = Muted,
                        fontSize = 14.sp,
                    )
                }
                if (history.isNotEmpty()) {
                    if (selecting) {
                        TextButton(onClick = {
                            selecting = false
                            selected = emptySet()
                        }) { Text(stringResource(R.string.action_cancel), color = Ink) }
                        TextButton(
                            onClick = {
                                onDelete(selected)
                                selected = emptySet()
                                selecting = false
                            },
                            enabled = selected.isNotEmpty(),
                        ) { Text(stringResource(R.string.history_delete), color = Ink) }
                    } else {
                        TextButton(onClick = { selecting = true }) { Text(stringResource(R.string.history_select), color = Ink) }
                        TextButton(onClick = onClear) { Text(stringResource(R.string.history_clear), color = Ink) }
                    }
                }
            }
        }
        if (history.isEmpty()) {
            item {
                EmptyState(stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body))
            }
        } else {
            if (selecting && selectable.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = {
                            selected = if (selected.size == selectable.size) emptySet() else selectable.map { it.id }.toSet()
                        },
                    ) {
                        Text(if (selected.size == selectable.size) stringResource(R.string.history_unselect_all) else stringResource(R.string.history_select_all), color = Ink)
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.history_recent)) }
            items(history, key = { it.id }) { task ->
                val canSelect = task.status != ConversionStatus.PENDING && task.status != ConversionStatus.RUNNING
                HistoryRow(
                    task = task,
                    selecting = selecting,
                    checked = task.id in selected,
                    onToggle = {
                        if (!canSelect) return@HistoryRow
                        selected = if (task.id in selected) selected - task.id else selected + task.id
                    },
                    onLongPress = {
                        if (!canSelect) return@HistoryRow
                        selecting = true
                        selected = selected + task.id
                    },
                    onOpen = { if (!selecting) actionTask = task },
                )
            }
        }
    }
    if (!selecting) {
        actionTask?.let { task ->
            HistoryActionSheet(
                task = task,
                onDismiss = { actionTask = null },
                onOpen = {
                    HistoryOutputs.startOpen(context, task)
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
                    onDelete(listOf(task.id))
                    actionTask = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryActionSheet(
    task: ConversionTask,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onReuse: () -> Unit,
    onDelete: () -> Unit,
) {
    val outputs = HistoryOutputs.uris(task)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(task.sourceName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${task.sourceFormat.displayName} → ${task.targetFormat.displayName} · ${historyStatus(task)}",
                color = Muted,
                fontSize = 13.sp,
            )
            task.actualEngine?.let { engine ->
                Text(stringResource(R.string.history_engine, engine.displayName), color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            HistoryActionRow(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.history_open), enabled = outputs.isNotEmpty(), onClick = onOpen)
            HistoryActionRow(Icons.Outlined.Share, stringResource(R.string.action_share), enabled = outputs.isNotEmpty(), onClick = onShare)
            HistoryActionRow(Icons.Outlined.Refresh, stringResource(R.string.history_reuse), onClick = onReuse)
            HistoryActionRow(Icons.Outlined.DeleteOutline, stringResource(R.string.history_delete_record), tint = Muted, onClick = onDelete)
        }
    }
}

@Composable
internal fun HistoryActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color = Ink,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else Muted, modifier = Modifier.size(22.dp))
        Text(
            label,
            fontWeight = FontWeight.Medium,
            color = if (enabled) Ink else Muted,
        )
    }
}

@Composable
internal fun SettingsScreen(
    imageQuality: QualityPreset,
    videoQuality: QualityPreset,
    benchmarkRecordCount: Int,
    onImageQuality: (QualityPreset) -> Unit,
    onVideoQuality: (QualityPreset) -> Unit,
    onPrivacy: () -> Unit,
    onLicenses: () -> Unit,
    onOfficeTools: (() -> Unit)?,
    onClearCache: () -> Unit = {},
    onRefreshBenchmark: () -> Unit = {},
    onExportBenchmark: (Uri, BenchmarkReportFormat) -> Unit = { _, _ -> },
    onExportPresets: (Uri) -> Unit = {},
    onImportPresets: (Uri) -> Unit = {},
) {
    var picking by remember { mutableStateOf<String?>(null) }
    var showBenchmarkExport by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val markdownExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BenchmarkReportFormat.MARKDOWN.mimeType),
    ) { uri ->
        uri?.let { onExportBenchmark(it, BenchmarkReportFormat.MARKDOWN) }
    }
    val csvExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BenchmarkReportFormat.CSV.mimeType),
    ) { uri ->
        uri?.let { onExportBenchmark(it, BenchmarkReportFormat.CSV) }
    }
    val presetExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let(onExportPresets)
    }
    val presetImporter = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onImportPresets)
    }
    val reportTimestamp = remember {
        SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    }

    LaunchedEffect(Unit) { onRefreshBenchmark() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(stringResource(R.string.settings_title), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        item { SectionTitle(stringResource(R.string.settings_section_prefs)) }
        item {
            SettingsGroup {
                SettingRow(stringResource(R.string.settings_appearance), stringResource(R.string.settings_appearance_value))
                HorizontalDivider(color = Border)
                SettingRow(stringResource(R.string.settings_save_location), stringResource(R.string.settings_save_location_value))
                HorizontalDivider(color = Border)
                SettingRow(stringResource(R.string.settings_image_quality), stringResource(imageQuality.labelRes), onClick = { picking = "image" })
                HorizontalDivider(color = Border)
                SettingRow(stringResource(R.string.settings_video_quality), stringResource(videoQuality.labelRes), onClick = { picking = "video" })
            }
        }
        item { SectionTitle(stringResource(R.string.settings_section_system)) }
        item {
            SettingsGroup {
                SettingRow(
                    stringResource(R.string.settings_hw_accel),
                    stringResource(R.string.settings_hw_accel_value),
                )
                HorizontalDivider(color = Border)
                SettingRow(
                    stringResource(R.string.settings_clear_cache),
                    stringResource(R.string.settings_clear_cache_value),
                    onClick = onClearCache,
                )
                HorizontalDivider(color = Border)
                SettingRow(
                    stringResource(R.string.settings_benchmark),
                    if (benchmarkRecordCount > 0) {
                        stringResource(R.string.settings_benchmark_count, benchmarkRecordCount)
                    } else {
                        stringResource(R.string.settings_benchmark_empty)
                    },
                    onClick = if (benchmarkRecordCount > 0) {
                        { showBenchmarkExport = true }
                    } else {
                        null
                    },
                )
                HorizontalDivider(color = Border)
                SettingRow(
                    stringResource(R.string.settings_export_presets),
                    stringResource(R.string.settings_export_presets_value),
                    onClick = {
                        presetExporter.launch("openconvert-presets.json")
                    },
                )
                HorizontalDivider(color = Border)
                SettingRow(
                    stringResource(R.string.settings_import_presets),
                    stringResource(R.string.settings_import_presets_value),
                    onClick = { presetImporter.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
            }
        }
        item { SectionTitle(stringResource(R.string.settings_section_office)) }
        item {
            SettingsGroup {
                SettingRow(
                    stringResource(R.string.settings_office_engine),
                    stringResource(
                        if (BuildConfig.OFFICE_BUNDLED) {
                            R.string.settings_office_bundled
                        } else {
                            R.string.settings_office_basic
                        },
                    ),
                    onClick = onOfficeTools,
                )
            }
        }
        item { SectionTitle(stringResource(R.string.settings_section_about)) }
        item {
            SettingsGroup {
                SettingRow(stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_value), onClick = onPrivacy)
                HorizontalDivider(color = Border)
                SettingRow(stringResource(R.string.settings_licenses), stringResource(R.string.settings_licenses_value), onClick = onLicenses)
                HorizontalDivider(color = Border)
                SettingRow(stringResource(R.string.app_name), stringResource(R.string.app_version_label))
            }
        }
    }

    val title = when (picking) {
        "image" -> stringResource(R.string.settings_quality_image)
        "video" -> stringResource(R.string.settings_quality_video)
        else -> null
    }
    if (title != null) {
        val current = if (picking == "image") imageQuality else videoQuality
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(title) },
            text = {
                Column {
                    QualityPreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (picking == "image") onImageQuality(preset) else onVideoQuality(preset)
                                    picking = null
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = preset == current,
                                onClick = {
                                    if (picking == "image") onImageQuality(preset) else onVideoQuality(preset)
                                    picking = null
                                },
                            )
                            Text(preset.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_close), color = Ink) }
            },
            containerColor = MaterialTheme.colorScheme.background,
        )
    }
    if (showBenchmarkExport) {
        AlertDialog(
            onDismissRequest = { showBenchmarkExport = false },
            title = { Text(stringResource(R.string.settings_export_report)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.settings_export_report_body, benchmarkRecordCount),
                        color = Muted,
                        fontSize = 13.sp,
                    )
                    HistoryActionRow(
                        icon = Icons.AutoMirrored.Outlined.Article,
                        label = stringResource(R.string.settings_export_md),
                        onClick = {
                            showBenchmarkExport = false
                            markdownExporter.launch("OpenConvert-Benchmark-$reportTimestamp.md")
                        },
                    )
                    HistoryActionRow(
                        icon = Icons.Outlined.Description,
                        label = stringResource(R.string.settings_export_csv),
                        onClick = {
                            showBenchmarkExport = false
                            csvExporter.launch("OpenConvert-Benchmark-$reportTimestamp.csv")
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBenchmarkExport = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
internal fun PrivacyScreen(onBack: () -> Unit) {
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.privacy_title), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.privacy_body),
                    color = Muted,
                    lineHeight = 22.sp,
                )
            }
        }
        item {
            SettingsGroup {
                PrivacyFact(stringResource(R.string.privacy_no_network))
                HorizontalDivider(color = Border)
                PrivacyFact(stringResource(R.string.privacy_no_upload))
                HorizontalDivider(color = Border)
                PrivacyFact(stringResource(R.string.privacy_no_server))
                HorizontalDivider(color = Border)
                PrivacyFact(stringResource(R.string.privacy_local))
            }
        }
    }
}

@Composable
internal fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val body = remember {
        runCatching {
            context.assets.open("THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        }
        item {
            Text(stringResource(R.string.licenses_title), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        item {
            Text(
                text = body.ifBlank { stringResource(R.string.licenses_missing) },
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
    }
}
