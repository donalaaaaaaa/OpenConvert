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
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
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
import com.openconvert.app.domain.model.ConversionTask
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

private const val HOME = "home"
private const val HISTORY = "history"
private const val SETTINGS = "settings"
private const val CONVERT = "convert"
private const val PRIVACY = "privacy"
private const val PDF_TOOLS = "pdf_tools"
private const val BATCH = "batch"
private const val BATCH_PROGRESS = "batch_progress"
private const val IMAGES_TO_PDF = "images_to_pdf"
private const val PDF_TO_IMAGES = "pdf_to_images"
private const val PDF_MERGE = "pdf_merge"
private const val PDF_SPLIT = "pdf_split"
private const val PDF_DELETE = "pdf_delete"
private const val PDF_ROTATE = "pdf_rotate"
private const val ARCHIVE = "archive"
private const val ARCHIVE_COMPRESS_SCREEN = "archive_compress"
private const val ARCHIVE_EXTRACT_SCREEN = "archive_extract"

private data class MainDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val mainDestinations = listOf(
    MainDestination(HOME, "首页", Icons.Outlined.Home),
    MainDestination(HISTORY, "历史", Icons.Outlined.History),
    MainDestination(SETTINGS, "设置", Icons.Outlined.Settings),
)

@Composable
fun OpenConvertApp(viewModel: MainViewModel = viewModel()) {
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
                        if (viewModel.onDocumentPicked(uri)) navController.navigate(CONVERT)
                    },
                    onPdfTools = { navController.navigate(PDF_TOOLS) },
                    onBatch = { navController.navigate(BATCH) },
                    onArchive = { navController.navigate(ARCHIVE) },
                    onSettings = { navController.navigate(SETTINGS) },
                    onReuse = { task ->
                        if (viewModel.reuseConversion(task)) navController.navigate(CONVERT)
                    },
                    onDelete = { viewModel.deleteHistory(listOf(it.id)) },
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
                SettingsScreen(
                    imageQuality = imageQuality,
                    videoQuality = videoQuality,
                    onImageQuality = viewModel::setImageQualityPreference,
                    onVideoQuality = viewModel::setVideoQualityPreference,
                    onPrivacy = { navController.navigate(PRIVACY) },
                )
            }
            composable(CONVERT) {
                val draft by viewModel.draft.collectAsStateWithLifecycle()
                val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
                when (val state = conversionState) {
                    ConversionUiState.Configuring -> ConversionScreen(
                        draft = draft,
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
                when (val state = batchUiState) {
                    BatchUiState.Idle -> BatchPickerScreen(
                        onBack = navController::popBackStack,
                        onFilesPicked = { uris ->
                            if (viewModel.onBatchFilesPicked(uris)) navController.navigate(BATCH_PROGRESS)
                        },
                    )
                    is BatchUiState.Configuring -> BatchConfigureScreen(
                        draft = state.draft,
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
                )
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

@Composable
private fun HomeScreen(
    recentTasks: List<ConversionTask>,
    onFilePicked: (android.net.Uri) -> Unit,
    onPdfTools: () -> Unit,
    onBatch: () -> Unit,
    onArchive: () -> Unit,
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
                    .clickable { picker.launch(arrayOf("*/*")) },
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
                    Text("PDF · 图片 · 视频 · 音频", color = Muted, fontSize = 13.sp)
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

@Composable
private fun ToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
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
private fun PdfToolsScreen(
    onBack: () -> Unit,
    onImagesPicked: (List<Uri>) -> Unit,
    onPdfToImagesPicked: (Uri) -> Unit,
    onPdfsToMergePicked: (List<Uri>) -> Unit,
    onPdfToSplitPicked: (Uri) -> Unit,
    onPdfToDeletePicked: (Uri) -> Unit,
    onPdfToRotatePicked: (Uri) -> Unit,
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
                Text("PDF 工具", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("转换、合并和拆分都在本地完成", color = Muted, fontSize = 14.sp)
            }
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
private fun PdfToImagesScreen(
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
private fun PdfMergeScreen(
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
private fun PdfSplitScreen(
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
private fun PdfDeletePagesScreen(
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
private fun PdfRotatePagesScreen(
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
private fun PdfConfigurationScaffold(
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
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
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
private fun PdfOrderRow(
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
private fun PrimaryPdfButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink),
    ) { Text(label, fontSize = 16.sp) }
}

@Composable
private fun EmptyPdfSelection(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message, color = Muted) }
}

@Composable
private fun ImagesToPdfScreen(
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

@Composable
private fun ConversionScreen(
    draft: ConversionDraft?,
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
            Text("没有已选择的文件", color = Muted)
        }
        return
    }

    var targetMenuOpen by remember { mutableStateOf(false) }
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
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        }
        item {
            Text("转换文件", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        item {
            FileCard(
                name = draft.document.name,
                details = "${draft.document.format.displayName} · ${formatFileSize(draft.document.sizeBytes)}",
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("转换为")
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
                    FieldLabel(if (draft.document.format.category == FileCategory.VIDEO) "压缩质量" else "质量")
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
                                Text(preset.label, fontWeight = FontWeight.Medium)
                                if (preset == QualityPreset.BALANCED) {
                                    Text("推荐", color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "${draft.targetFormat.displayName} 使用无损编码，不需要调整质量。",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
        }
        if (draft.targetFormat.category in setOf(FileCategory.IMAGE, FileCategory.VIDEO)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldLabel("尺寸")
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
                            Text(preset.label, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        if (draft.targetFormat.category == FileCategory.IMAGE) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldLabel("旋转")
                    listOf(0 to "不旋转", 90 to "90°", 180 to "180°", 270 to "270°").forEach { (degrees, label) ->
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
                    FieldLabel("裁剪比例")
                    listOf(
                        "free" to "原始比例",
                        "1:1" to "1:1 方形",
                        "4:3" to "4:3",
                        "3:2" to "3:2",
                        "16:9" to "16:9",
                        "9:16" to "9:16 竖屏",
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
                    FieldLabel("翻转")
                    listOf(0 to "不翻转", 1 to "水平翻转", 2 to "垂直翻转").forEach { (flip, label) ->
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
                        Text("删除全部元数据（隐私模式）", fontWeight = FontWeight.Medium)
                        Text("移除 EXIF、GPS 位置等拍摄信息", color = Muted, fontSize = 12.sp)
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
                Text(if (draft.engineAvailable) "选择位置并开始转换" else "该转换引擎尚未接入", fontSize = 16.sp)
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
                Text("文件不会离开您的设备", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ConversionProgressScreen(task: ConversionTask, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("正在转换", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
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
        Text("${task.progress}%", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text("正在本地处理", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(36.dp))
        TextButton(onClick = onCancel) { Text("取消转换", color = Ink) }
        Spacer(Modifier.height(22.dp))
        PrivacyHint()
    }
}

@Composable
private fun ConversionCompleteScreen(
    task: ConversionTask,
    outputName: String,
    outputUris: List<String>,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
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
            Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(58.dp))
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("转换完成", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(outputName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatFileSize(outputSize), color = Muted)
            }
        }
        item {
            SettingsGroup {
                SettingRow("原文件", formatFileSize(task.fileSize))
                HorizontalDivider(color = Border)
                SettingRow("新文件", formatFileSize(outputSize))
                HorizontalDivider(color = Border)
                SettingRow(
                    if (savedBytes >= 0) "节省" else "增加",
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
                Text(if (parsedOutputUris.size > 1) "打开第一个文件" else "打开文件")
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
                            context.startActivity(Intent.createChooser(share, "分享转换后的文件"))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("分享", color = Ink)
                }
                TextButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                    Text("完成", color = Ink)
                }
            }
        }
    }
}

@Composable
private fun ConversionFailedScreen(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(18.dp))
        Text("转换失败", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
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
            Text("调整设置后重试")
        }
        TextButton(onClick = onBack) { Text("返回首页", color = Ink) }
    }
}

@Composable
private fun PrivacyHint() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text("文件不会离开您的设备", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun HistoryScreen(
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
                        if (selecting) "已选 ${selected.size} 项" else "历史",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (selecting) "进行中的任务不能删除" else "仅保存在这台设备上",
                        color = Muted,
                        fontSize = 14.sp,
                    )
                }
                if (history.isNotEmpty()) {
                    if (selecting) {
                        TextButton(onClick = {
                            selecting = false
                            selected = emptySet()
                        }) { Text("取消", color = Ink) }
                        TextButton(
                            onClick = {
                                onDelete(selected)
                                selected = emptySet()
                                selecting = false
                            },
                            enabled = selected.isNotEmpty(),
                        ) { Text("删除", color = Ink) }
                    } else {
                        TextButton(onClick = { selecting = true }) { Text("选择", color = Ink) }
                        TextButton(onClick = onClear) { Text("清空", color = Ink) }
                    }
                }
            }
        }
        if (history.isEmpty()) {
            item {
                EmptyState("暂无历史记录", "完成或加入转换任务后会显示在这里。")
            }
        } else {
            if (selecting && selectable.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = {
                            selected = if (selected.size == selectable.size) emptySet() else selectable.map { it.id }.toSet()
                        },
                    ) {
                        Text(if (selected.size == selectable.size) "取消全选" else "全选", color = Ink)
                    }
                }
            }
            item { SectionTitle("最近") }
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
private fun HistoryActionSheet(
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
            Spacer(Modifier.height(8.dp))
            HistoryActionRow(Icons.Outlined.OpenInNew, "打开文件", enabled = outputs.isNotEmpty(), onClick = onOpen)
            HistoryActionRow(Icons.Outlined.Share, "分享", enabled = outputs.isNotEmpty(), onClick = onShare)
            HistoryActionRow(Icons.Outlined.Refresh, "再次转换", onClick = onReuse)
            HistoryActionRow(Icons.Outlined.DeleteOutline, "删除记录", tint = Muted, onClick = onDelete)
        }
    }
}

@Composable
private fun HistoryActionRow(
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
private fun SettingsScreen(
    imageQuality: QualityPreset,
    videoQuality: QualityPreset,
    onImageQuality: (QualityPreset) -> Unit,
    onVideoQuality: (QualityPreset) -> Unit,
    onPrivacy: () -> Unit,
) {
    var picking by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("设置", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        item { SectionTitle("偏好") }
        item {
            SettingsGroup {
                SettingRow("外观", "浅色")
                HorizontalDivider(color = Border)
                SettingRow("默认保存位置", "每次询问")
                HorizontalDivider(color = Border)
                SettingRow("图片默认质量", imageQuality.label, onClick = { picking = "image" })
                HorizontalDivider(color = Border)
                SettingRow("视频默认质量", videoQuality.label, onClick = { picking = "video" })
            }
        }
        item { SectionTitle("关于") }
        item {
            SettingsGroup {
                SettingRow("隐私", "无网络权限 · 本地处理", onClick = onPrivacy)
                HorizontalDivider(color = Border)
                SettingRow("OpenConvert", "0.1.0 Alpha")
            }
        }
    }

    val title = when (picking) {
        "image" -> "图片默认质量"
        "video" -> "视频默认质量"
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
                TextButton(onClick = { picking = null }) { Text("关闭", color = Ink) }
            },
            containerColor = MaterialTheme.colorScheme.background,
        )
    }
}

@Composable
private fun PrivacyScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("隐私", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "OpenConvert 的转换工作全部在您的设备本地完成。应用不会上传、收集或保存您的文件。",
                    color = Muted,
                    lineHeight = 22.sp,
                )
            }
        }
        item {
            SettingsGroup {
                PrivacyFact("无网络权限")
                HorizontalDivider(color = Border)
                PrivacyFact("无文件上传")
                HorizontalDivider(color = Border)
                PrivacyFact("无服务器")
                HorizontalDivider(color = Border)
                PrivacyFact("本地处理")
            }
        }
    }
}

@Composable
private fun FileCard(name: String, details: String) {
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
private fun HistoryRow(
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
                "${task.sourceFormat.displayName} → ${task.targetFormat.displayName}",
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
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
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
private fun SettingRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontWeight = FontWeight.Medium)
        Text(value, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun PrivacyFact(label: String) {
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
private fun EmptyState(title: String, subtitle: String) {
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
private fun SectionTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
}

private fun historyStatus(task: ConversionTask): String = when (task.status) {
    ConversionStatus.PENDING -> "排队中"
    ConversionStatus.RUNNING -> "${task.progress}%"
    ConversionStatus.FAILED -> "失败"
    ConversionStatus.CANCELLED -> "已取消"
    ConversionStatus.COMPLETED -> formatFileSize(task.outputSize ?: task.fileSize)
}

private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> "大小未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun BatchPickerScreen(
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
private fun BatchConfigureScreen(
    draft: BatchDraft,
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
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("批量转换", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "已选择 ${draft.documents.size} 个文件 · ${draft.documents.first().format.displayName} → ${draft.targetFormat.displayName}",
                    color = Muted,
                    fontSize = 14.sp,
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
private fun BatchProgressScreen(
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
            Text("已暂停", color = Muted, fontSize = 13.sp)
        } else if (isFinished) {
            Text("完成 $doneCount · 失败 $failedCount", color = Muted, fontSize = 13.sp)
        } else {
            current?.let {
                Text("当前：${it.sourceName}", color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("剩余：$remaining 个文件", color = Muted, fontSize = 13.sp)
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

@Composable
private fun ArchiveToolsScreen(
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
private fun ArchiveCompressScreen(
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
private fun ArchiveExtractScreen(
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
