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
fun OpenConvertApp(
    viewModel: MainViewModel = viewModel(),
    taskCenter: TaskCenterViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val message by viewModel.message.collectAsStateWithLifecycle()
    val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(conversionState) {
        if (conversionState is ConversionUiState.Running &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(conversionState, currentRoute) {
        val showingConversion = conversionState !is ConversionUiState.Configuring
        val conversionRoutes = setOf(CONVERT, IMAGES_TO_PDF, PDF_TO_IMAGES, PDF_MERGE, PDF_SPLIT)
        if (showingConversion && currentRoute != null && currentRoute !in conversionRoutes) {
            navController.navigate(CONVERT)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in mainDestinations.map { it.route }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    mainDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val context = LocalContext.current
        val pickedFile by viewModel.pickedFile.collectAsStateWithLifecycle()
        val pickedCapabilities by viewModel.pickedCapabilities.collectAsStateWithLifecycle()

        // 首页 UI 2.0 能力面板（§6.3）：选中文件后浮出，用户在此决定做什么。
        pickedFile?.let { document ->
            pickedCapabilities?.let { capabilities ->
                FileCapabilitySheet(
                    document = document,
                    capabilities = capabilities,
                    onConvertTo = { format ->
                        if (viewModel.chooseConvertTarget(format)) {
                            viewModel.clearPickedFile()
                            navController.navigate(CONVERT)
                        }
                    },
                    onTool = { action ->
                        val route = routeForTool(action.kind)
                        viewModel.clearPickedFile()
                        if (route != null) {
                            navController.navigate(route)
                        }
                    },
                    onDismiss = viewModel::clearPickedFile,
                )
            }
        }

        NavHost(
            navController = navController,
            startDestination = HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(HOME) {
                val history by viewModel.history.collectAsStateWithLifecycle()
                HomeScreen(
                    recentTasks = history.take(3),
                    onFilePicked = { uri ->
                        // 首页 UI 2.0：先解析能力，弹面板让用户选做什么（§6.3），
                        // 不再直接跳进转换配置页。
                        viewModel.inspectFile(uri)
                    },
                    onPdfTools = { navController.navigate(PDF_TOOLS) },
                    onBatch = { navController.navigate(BATCH) },
                    onArchive = { navController.navigate(ARCHIVE) },
                    onOffice = if (BuildConfig.OFFICE_BUNDLED) {
                        { navController.navigate(OFFICE_TOOLS) }
                    } else {
                        null
                    },
                    onSettings = { navController.navigate(SETTINGS) },
                    onReuse = { task ->
                        if (viewModel.reuseConversion(task)) navController.navigate(CONVERT)
                    },
                    onDelete = { viewModel.deleteHistory(listOf(it.id)) },
                )
            }
            composable(TASKS) {
                val groups by taskCenter.taskGroups.collectAsStateWithLifecycle()
                val cards by taskCenter.taskCards.collectAsStateWithLifecycle()
                TaskCenterScreen(
                    groups = groups,
                    cards = cards,
                    onCancel = taskCenter::cancelTask,
                    onRetry = { card ->
                        if (viewModel.reuseConversion(card.task)) navController.navigate(CONVERT)
                    },
                    onOpen = { card -> HistoryOutputs.startOpen(context, card.task) },
                )
            }
            composable(HISTORY) {
                val history by viewModel.history.collectAsStateWithLifecycle()
                HistoryScreen(
                    history,
                    onClear = viewModel::clearHistory,
                    onDelete = viewModel::deleteHistory,
                    onReuse = { task ->
                        if (viewModel.reuseConversion(task)) navController.navigate(CONVERT)
                    },
                )
            }
            composable(SETTINGS) {
                val imageQuality by viewModel.imageQualityPreference.collectAsStateWithLifecycle()
                val videoQuality by viewModel.videoQualityPreference.collectAsStateWithLifecycle()
                val benchmarkRecordCount by viewModel.benchmarkRecordCount.collectAsStateWithLifecycle()
                SettingsScreen(
                    imageQuality = imageQuality,
                    videoQuality = videoQuality,
                    benchmarkRecordCount = benchmarkRecordCount,
                    onImageQuality = viewModel::setImageQualityPreference,
                    onVideoQuality = viewModel::setVideoQualityPreference,
                    onPrivacy = { navController.navigate(PRIVACY) },
                    onOfficeTools = if (BuildConfig.OFFICE_BUNDLED) {
                        { navController.navigate(OFFICE_TOOLS) }
                    } else {
                        null
                    },
                    onClearCache = viewModel::clearCache,
                    onRefreshBenchmark = viewModel::refreshBenchmarkStats,
                    onExportBenchmark = viewModel::exportBenchmarkReport,
                    onExportPresets = viewModel::exportPresets,
                    onImportPresets = viewModel::importPresets,
                )
            }
            composable(OFFICE_TOOLS) {
                OfficeToolsScreen(
                    onBack = navController::popBackStack,
                    onOfficePicked = { uri ->
                        if (viewModel.onDocumentPicked(uri)) navController.navigate(CONVERT)
                    },
                )
            }
            composable(CONVERT) {
                val draft by viewModel.draft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                val appliedPresetId by viewModel.appliedPresetId.collectAsStateWithLifecycle()
                // presets 需订阅才会在 Room 播种后刷新；presetsForCurrentDraft 读的是同一个 StateFlow。
                val allPresets by viewModel.presets.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> ConversionScreen(
                        draft = draft,
                        presets = draft?.let { d ->
                            allPresets.filter { preset ->
                                preset.category == d.document.format.category &&
                                    com.openconvert.app.domain.model.ConversionGraph.canConvert(
                                        d.document.format,
                                        preset.targetFormat,
                                    )
                            }
                        } ?: emptyList(),
                        appliedPresetId = appliedPresetId,
                        onApplyPreset = { viewModel.applyPreset(it) },
                        onSavePreset = viewModel::saveDraftAsPreset,
                        onBack = navController::popBackStack,
                        onTarget = viewModel::selectTarget,
                        onQuality = viewModel::selectQuality,
                        onResolution = viewModel::selectResolution,
                        onRotate = viewModel::selectRotate,
                        onCropAspect = viewModel::selectCropAspect,
                        onFlip = viewModel::selectFlip,
                        onStripMetadata = viewModel::selectStripMetadata,
                        onStart = viewModel::startConversion,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(
                        task = state.task,
                        onCancel = viewModel::cancelConversion,
                    )
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        task = state.task,
                        outputName = state.outputName,
                        outputUris = state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack()
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        message = state.message,
                        onRetry = viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack()
                        },
                    )
                }
            }
            composable(PRIVACY) {
                PrivacyScreen(onBack = navController::popBackStack)
            }
            composable(BATCH) {
                val batchUiState by viewModel.batchUiState.collectAsStateWithLifecycle()
                val batchPresets by viewModel.presets.collectAsStateWithLifecycle()
                when (val state = batchUiState) {
                    BatchUiState.Idle -> BatchPickerScreen(
                        onBack = navController::popBackStack,
                        onFilesPicked = { uris ->
                            if (viewModel.onBatchFilesPicked(uris)) navController.navigate(BATCH_PROGRESS)
                        },
                    )
                    is BatchUiState.Configuring -> BatchConfigureScreen(
                        draft = state.draft,
                        presets = availableBatchPresets(
                            sourceFormats = state.draft.documents.map { it.format },
                            commonFormats = state.draft.commonFormats,
                            presets = batchPresets,
                        ),
                        onApplyPreset = { viewModel.applyBatchPreset(it) },
                        onBack = {
                            viewModel.resetBatch()
                            navController.popBackStack()
                        },
                        onTarget = viewModel::selectBatchTarget,
                        onQuality = viewModel::selectBatchQuality,
                        onResolution = viewModel::selectBatchResolution,
                        onStart = { treeUri ->
                            viewModel.startBatch(treeUri)
                            navController.navigate(BATCH_PROGRESS)
                        },
                    )
                    is BatchUiState.Running -> {
                        LaunchedEffect(Unit) { viewModel.observeBatch() }
                        BatchProgressScreen(
                            job = state.job,
                            tasks = state.tasks,
                            onPause = viewModel::pauseBatch,
                            onResume = viewModel::resumeBatch,
                            onCancel = viewModel::cancelBatch,
                            onDone = {
                                viewModel.resetBatch()
                                navController.popBackStack(HOME, false)
                            },
                        )
                    }
                    is BatchUiState.Completed -> {
                        LaunchedEffect(Unit) { viewModel.observeBatch() }
                        BatchProgressScreen(
                            job = state.job,
                            tasks = state.tasks,
                            onPause = viewModel::pauseBatch,
                            onResume = viewModel::resumeBatch,
                            onCancel = viewModel::cancelBatch,
                            onDone = {
                                viewModel.resetBatch()
                                navController.popBackStack(HOME, false)
                            },
                        )
                    }
                }
            }
            composable(BATCH_PROGRESS) {
                LaunchedEffect(Unit) { viewModel.observeBatch() }
                val batchUiState by viewModel.batchUiState.collectAsStateWithLifecycle()
                when (val state = batchUiState) {
                    is BatchUiState.Running -> BatchProgressScreen(
                        job = state.job,
                        tasks = state.tasks,
                        onPause = viewModel::pauseBatch,
                        onResume = viewModel::resumeBatch,
                        onCancel = viewModel::cancelBatch,
                        onDone = {
                            viewModel.resetBatch()
                            navController.popBackStack(HOME, false)
                        },
                    )
                    is BatchUiState.Completed -> BatchProgressScreen(
                        job = state.job,
                        tasks = state.tasks,
                        onPause = viewModel::pauseBatch,
                        onResume = viewModel::resumeBatch,
                        onCancel = viewModel::cancelBatch,
                        onDone = {
                            viewModel.resetBatch()
                            navController.popBackStack(HOME, false)
                        },
                    )
                    else -> {
                        // 没有批量数据时回首页
                        LaunchedEffect(Unit) { navController.popBackStack(HOME, false) }
                    }
                }
            }
            composable(ARCHIVE) {
                ArchiveToolsScreen(
                    onBack = navController::popBackStack,
                    onCompressPicked = { uris ->
                        if (viewModel.onFilesToCompressPicked(uris)) navController.navigate(ARCHIVE_COMPRESS_SCREEN)
                    },
                    onExtractPicked = { uri ->
                        if (viewModel.onArchiveToExtractPicked(uri)) navController.navigate(ARCHIVE_EXTRACT_SCREEN)
                    },
                )
            }
            composable(ARCHIVE_COMPRESS_SCREEN) {
                val draft by viewModel.archiveCompressDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> ArchiveCompressScreen(
                        draft = draft,
                        onBack = navController::popBackStack,
                        onTarget = viewModel::selectArchiveTarget,
                        onStart = viewModel::startArchiveCompress,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(state.task, viewModel::cancelConversion)
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        state.task,
                        state.outputName,
                        state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack(ARCHIVE, false)
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        state.message,
                        viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack(ARCHIVE, false)
                        },
                    )
                }
            }
            composable(ARCHIVE_EXTRACT_SCREEN) {
                val draft by viewModel.archiveExtractDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> ArchiveExtractScreen(
                        draft = draft,
                        onBack = navController::popBackStack,
                        onStart = viewModel::startArchiveExtract,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(state.task, viewModel::cancelConversion)
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        state.task,
                        state.outputName,
                        state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack(ARCHIVE, false)
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        state.message,
                        viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack(ARCHIVE, false)
                        },
                    )
                }
            }
            composable(PDF_TOOLS) {
                PdfToolsScreen(
                    onBack = navController::popBackStack,
                    onImagesPicked = { uris ->
                        if (viewModel.onImagesPicked(uris)) navController.navigate(IMAGES_TO_PDF)
                    },
                    onPdfToImagesPicked = { uri ->
                        if (viewModel.onPdfToImagesPicked(uri)) navController.navigate(PDF_TO_IMAGES)
                    },
                    onPdfsToMergePicked = { uris ->
                        if (viewModel.onPdfsToMergePicked(uris)) navController.navigate(PDF_MERGE)
                    },
                    onPdfToSplitPicked = { uri ->
                        if (viewModel.onPdfToSplitPicked(uri)) navController.navigate(PDF_SPLIT)
                    },
                    onPdfToDeletePicked = { uri ->
                        if (viewModel.onPdfToDeletePicked(uri)) navController.navigate(PDF_DELETE)
                    },
                    onPdfToRotatePicked = { uri ->
                        if (viewModel.onPdfToRotatePicked(uri)) navController.navigate(PDF_ROTATE)
                    },
                    onPdfCompressPicked = { uri ->
                        if (viewModel.onPdfCompressPicked(uri)) navController.navigate(PDF_COMPRESS)
                    },
                    onPdfSecurityPicked = { uri ->
                        if (viewModel.onPdfSecurityPicked(uri, isEncrypt = true)) navController.navigate(PDF_SECURITY)
                    },
                    onPdfCropPicked = { uri ->
                        if (viewModel.onPdfCropPicked(uri)) navController.navigate(PDF_CROP)
                    },
                    onPdfMetadataPicked = { uri ->
                        if (viewModel.onPdfMetadataPicked(uri)) navController.navigate(PDF_METADATA)
                    },
                    onPdfPageManagerPicked = { uri ->
                        if (viewModel.onPdfPageManagerPicked(uri)) navController.navigate(PDF_PAGE_MANAGER)
                    },
                )
            }
            composable(PDF_COMPRESS) {
                val draft by viewModel.pdfCompressDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> PdfCompressScreen(
                        draft = draft,
                        onBack = navController::popBackStack,
                        onPreset = viewModel::selectPdfCompressPreset,
                        onStart = viewModel::startPdfCompress,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(state.task, viewModel::cancelConversion)
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        state.task,
                        state.outputName,
                        state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        state.message,
                        viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                }
            }
            composable(PDF_SECURITY) {
                val draft by viewModel.pdfSecurityDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> PdfSecurityScreen(
                        draft = draft,
                        onBack = navController::popBackStack,
                        onPasswordChange = viewModel::setPdfSecurityPassword,
                        onStart = viewModel::startPdfSecurity,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(state.task, viewModel::cancelConversion)
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        state.task,
                        state.outputName,
                        state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        state.message,
                        viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                }
            }
            composable(PDF_CROP) {
                val draft by viewModel.pdfCropDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> PdfCropScreen(
                        draft = draft,
                        onBack = navController::popBackStack,
                        onMarginChange = viewModel::setPdfCropMargins,
                        onStart = viewModel::startPdfCrop,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(state.task, viewModel::cancelConversion)
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        state.task,
                        state.outputName,
                        state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        state.message,
                        viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                }
            }
            composable(PDF_METADATA) {
                val draft by viewModel.pdfMetadataDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> PdfMetadataScreen(
                        draft = draft,
                        onBack = navController::popBackStack,
                        onSave = { title, author, subject, keywords, uri ->
                            viewModel.startPdfMetadata(uri, title, author, subject, keywords)
                        },
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(state.task, viewModel::cancelConversion)
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        state.task,
                        state.outputName,
                        state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        state.message,
                        viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                }
            }
            composable(PDF_PAGE_MANAGER) {
                val draft by viewModel.pdfPageManagerDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> PdfPageManagerScreen(
                        draft = draft,
                        onBack = navController::popBackStack,
                        onReorder = viewModel::reorderPdfPages,
                        onRotate = viewModel::rotatePdfPages,
                        onDelete = viewModel::deletePdfPages,
                        onStart = viewModel::startPdfPageManagerExport,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(state.task, viewModel::cancelConversion)
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        state.task,
                        state.outputName,
                        state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        state.message,
                        viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                }
            }
            composable(IMAGES_TO_PDF) {
                val pdfDraft by viewModel.imagesToPdfDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> ImagesToPdfScreen(
                        draft = pdfDraft,
                        onBack = navController::popBackStack,
                        onMove = viewModel::movePdfImage,
                        onRemove = viewModel::removePdfImage,
                        onStart = viewModel::startImagesToPdf,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(
                        task = state.task,
                        onCancel = viewModel::cancelConversion,
                    )
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        task = state.task,
                        outputName = state.outputName,
                        outputUris = state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack()
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        message = state.message,
                        onRetry = viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack()
                        },
                    )
                }
            }
            composable(PDF_TO_IMAGES) {
                val draft by viewModel.pdfToImagesDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> PdfToImagesScreen(
                        draft = draft,
                        onBack = navController::popBackStack,
                        onTarget = viewModel::selectPdfImageFormat,
                        onRanges = viewModel::updatePdfImageRanges,
                        onStart = viewModel::startPdfToImages,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(state.task, viewModel::cancelConversion)
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        state.task,
                        state.outputName,
                        state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        state.message,
                        viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                }
            }
            composable(PDF_MERGE) {
                val draft by viewModel.pdfMergeDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> PdfMergeScreen(
                        draft = draft,
                        onBack = navController::popBackStack,
                        onMove = viewModel::moveMergePdf,
                        onRemove = viewModel::removeMergePdf,
                        onStart = viewModel::startPdfMerge,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(state.task, viewModel::cancelConversion)
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        state.task,
                        state.outputName,
                        state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        state.message,
                        viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                }
            }
            composable(PDF_SPLIT) {
                val draft by viewModel.pdfSplitDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> PdfSplitScreen(
                        draft = draft,
                        onBack = navController::popBackStack,
                        onRanges = viewModel::updatePdfSplitRanges,
                        onStart = viewModel::startPdfSplit,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(state.task, viewModel::cancelConversion)
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        state.task,
                        state.outputName,
                        state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        state.message,
                        viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                }
            }
            composable(PDF_DELETE) {
                val draft by viewModel.pdfDeleteDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> PdfDeletePagesScreen(
                        draft = draft,
                        onBack = navController::popBackStack,
                        onTogglePage = viewModel::togglePdfDeletePage,
                        onStart = viewModel::startPdfDelete,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(state.task, viewModel::cancelConversion)
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        state.task,
                        state.outputName,
                        state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        state.message,
                        viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                }
            }
            composable(PDF_ROTATE) {
                val draft by viewModel.pdfRotateDraft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> PdfRotatePagesScreen(
                        draft = draft,
                        onBack = navController::popBackStack,
                        onDegrees = viewModel::selectPdfRotateDegrees,
                        onRanges = viewModel::updatePdfRotateRanges,
                        onStart = viewModel::startPdfRotate,
                    )
                    is ConversionUiState.Running -> ConversionProgressScreen(state.task, viewModel::cancelConversion)
                    is ConversionUiState.Completed -> ConversionCompleteScreen(
                        state.task,
                        state.outputName,
                        state.outputUris,
                        onDone = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                    is ConversionUiState.Failed -> ConversionFailedScreen(
                        state.message,
                        viewModel::retryConversion,
                        onBack = {
                            viewModel.resetConversion()
                            navController.popBackStack(PDF_TOOLS, false)
                        },
                    )
                }
            }
        }
    }
}

