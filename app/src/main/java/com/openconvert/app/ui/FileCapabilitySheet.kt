package com.openconvert.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.openconvert.app.AppCopy
import com.openconvert.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openconvert.app.data.saf.SelectedDocument
import com.openconvert.app.domain.capability.FileCapabilities
import com.openconvert.app.domain.capability.FileCapabilityResolver
import com.openconvert.app.domain.capability.ToolAction
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.ui.theme.Border
import com.openconvert.app.ui.theme.Ink
import com.openconvert.app.ui.theme.Muted
import com.openconvert.app.ui.theme.SurfaceSoft

/**
 * 首页 UI 2.0 的能力面板（计划书 §6.3）。
 *
 * 用户选中文件后弹出：先显示文件本身，再列出「转换为」哪些格式、
 * 有哪些「工具」可用。内容完全由 [FileCapabilityResolver] 决定，
 * 不在这里硬编码任何格式规则。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FileCapabilitySheet(
    document: SelectedDocument,
    capabilities: FileCapabilities,
    onConvertTo: (FileFormat) -> Unit,
    onTool: (ToolAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    document.name,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    buildString {
                        append(document.format.displayName)
                        append(" · ")
                        append(formatSheetSize(document.sizeBytes))
                        if (document.magicVerified) append(" · ").append(stringResource(R.string.verified_content))
                    },
                    color = Muted,
                    fontSize = 13.sp,
                )
            }

            if (capabilities.convertTargets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(capabilities.convertSectionTitleRes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Muted,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 8.dp),
                    ) {
                        items(capabilities.convertTargets, key = { it.name }) { format ->
                            FormatChip(format = format, onClick = { onConvertTo(format) })
                        }
                    }
                }
            }

            // 图片的编辑能力是 SINGLE 转换的参数，不是独立 kind——作为提示展示。
            if (document.format.category == FileCategory.IMAGE) {
                Text(
                    stringResource(R.string.cap_image_edit_hints),
                    color = Muted,
                    fontSize = 12.sp,
                )
            }

            if (capabilities.tools.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(capabilities.toolSectionTitleRes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Muted,
                    )
                    capabilities.tools.forEach { action ->
                        ToolRow(action = action, onClick = { onTool(action) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatChip(format: FileFormat, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .actionSemantics(AccessibilityCopy.convertTo(format.displayName)),
        shape = MaterialTheme.shapes.medium,
        color = Ink,
    ) {
        Text(
            format.displayName,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ToolRow(action: ToolAction, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .actionSemantics(AccessibilityCopy.tool(
                stringResource(ToolCopy.labelRes(action.kind)),
                stringResource(ToolCopy.descriptionRes(action.kind)),
            )),
        shape = MaterialTheme.shapes.medium,
        color = SurfaceSoft,
        border = BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(ToolCopy.labelRes(action.kind)), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(ToolCopy.descriptionRes(action.kind)), color = Muted, fontSize = 12.sp)
            }
        }
    }
}

private fun formatSheetSize(bytes: Long): String = when {
    bytes <= 0L -> AppCopy.getOr(R.string.size_unknown, "大小未知")
    bytes >= 1024L * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
