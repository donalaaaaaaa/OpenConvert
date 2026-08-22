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
                    Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.app_tagline), color = Muted, fontSize = 15.sp)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.nav_settings), tint = Ink)
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
                            stringResource(
                                if (BuildConfig.OFFICE_BUNDLED) {
                                    R.string.a11y_pick_formats_office
                                } else {
                                    R.string.a11y_pick_formats_basic
                                },
                            ),
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
                    Text(stringResource(R.string.home_pick_file), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(
                            if (BuildConfig.OFFICE_BUNDLED) {
                                R.string.home_formats_office
                            } else {
                                R.string.home_formats_basic
                            },
                        ),
                        color = Muted,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        item { SectionTitle(stringResource(R.string.home_section_tools)) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard(
                        stringResource(R.string.home_tool_image),
                        stringResource(R.string.home_tool_image_sub),
                        Icons.Outlined.Image,
                        Modifier.weight(1f),
                        onClick = { picker.launch(arrayOf("image/*")) },
                    )
                    ToolCard(
                        stringResource(R.string.home_tool_pdf),
                        stringResource(R.string.home_tool_pdf_sub),
                        Icons.Outlined.Description,
                        Modifier.weight(1f),
                        onClick = onPdfTools,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard(
                        stringResource(R.string.home_tool_video),
                        stringResource(R.string.home_tool_video_sub),
                        Icons.Outlined.VideoFile,
                        Modifier.weight(1f),
                        onClick = { picker.launch(arrayOf("video/*")) },
                    )
                    ToolCard(
                        stringResource(R.string.home_tool_audio),
                        stringResource(R.string.home_tool_audio_sub),
                        Icons.Outlined.AudioFile,
                        Modifier.weight(1f),
                        onClick = { picker.launch(arrayOf("audio/*")) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard(
                        stringResource(R.string.home_tool_batch),
                        stringResource(R.string.home_tool_batch_sub),
                        Icons.Outlined.Add,
                        Modifier.weight(1f),
                        onClick = onBatch,
                    )
                    ToolCard(
                        stringResource(R.string.home_tool_archive),
                        stringResource(R.string.home_tool_archive_sub),
                        Icons.Outlined.Folder,
                        Modifier.weight(1f),
                        onClick = onArchive,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolCard(
                        stringResource(R.string.home_tool_office),
                        stringResource(
                            if (BuildConfig.OFFICE_BUNDLED) {
                                R.string.home_tool_office_sub
                            } else {
                                R.string.home_tool_office_basic
                            },
                        ),
                        Icons.AutoMirrored.Outlined.Article,
                        Modifier.fillMaxWidth(),
                        onClick = onOffice,
                    )
                }
            }
        }

        item { SectionTitle(stringResource(R.string.home_section_recent)) }

        if (recentTasks.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.home_recent_empty),
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
