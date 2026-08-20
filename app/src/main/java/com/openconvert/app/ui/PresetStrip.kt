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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openconvert.app.domain.preset.Preset
import com.openconvert.app.ui.theme.Border
import com.openconvert.app.ui.theme.Ink
import com.openconvert.app.ui.theme.Muted
import com.openconvert.app.ui.theme.SurfaceSoft

/**
 * 转换配置页的预设选择条（计划书 §八）。
 *
 * 一行横向 chips：选中即把预设的格式/质量/尺寸约束写入草稿。
 * 用户手动改任一参数后选中态自动清除（回到"自定义"）。
 */
@Composable
fun PresetStrip(
    presets: List<Preset>,
    appliedPresetId: String?,
    onApply: (Preset) -> Unit,
    onSaveCurrent: () -> Unit,
) {
    if (presets.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("预设", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Muted)
            Text(
                "存为预设",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onSaveCurrent),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 8.dp),
        ) {
            items(presets, key = { it.id }) { preset ->
                PresetChip(
                    preset = preset,
                    selected = preset.id == appliedPresetId,
                    onClick = { onApply(preset) },
                )
            }
        }
    }
}

@Composable
private fun PresetChip(preset: Preset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Ink else SurfaceSoft,
        border = BorderStroke(1.dp, if (selected) Ink else Border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                preset.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else Ink,
            )
            val detail = listOfNotNull(
                preset.targetFormat.displayName,
                preset.sizeSummary,
            ).joinToString(" · ")
            Text(
                detail,
                fontSize = 11.sp,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else Muted,
            )
        }
    }
}
