package com.openconvert.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openconvert.app.R
import com.openconvert.app.domain.task.TaskBucket
import com.openconvert.app.domain.task.TaskCardModel
import com.openconvert.app.domain.task.TaskGroup
import com.openconvert.app.ui.theme.Border
import com.openconvert.app.ui.theme.Ink
import com.openconvert.app.ui.theme.Muted
import com.openconvert.app.ui.theme.SurfaceSoft

/**
 * 任务中心 2.0（计划书 §七）：按状态分组的任务列表。
 *
 * 与「历史」页的区别：历史是完成记录的归档，任务中心关注**当前状态**——
 * 运行中的进度/速度、等待中的排队、失败的具体原因与下一步。
 */
@Composable
fun TaskCenterScreen(
    groups: List<TaskGroup>,
    cards: Map<String, TaskCardModel>,
    onCancel: (String) -> Unit,
    onRetry: (TaskCardModel) -> Unit,
    onOpen: (TaskCardModel) -> Unit,
) {
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.tasks_empty_title), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.tasks_empty_body), color = Muted, fontSize = 14.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(stringResource(R.string.tasks_title), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }

        groups.forEach { group ->
            item(key = "header-${group.bucket.name}") {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(group.bucket.labelRes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Muted,
                    )
                    Text("${group.count}", fontSize = 13.sp, color = Muted)
                }
            }
            items(group.tasks, key = { it.id }) { task ->
                cards[task.id]?.let { card ->
                    TaskCard(
                        card = card,
                        bucket = group.bucket,
                        onCancel = { onCancel(card.id) },
                        onRetry = { onRetry(card) },
                        onOpen = { onOpen(card) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    card: TaskCardModel,
    bucket: TaskBucket,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (bucket == TaskBucket.COMPLETED || bucket == TaskBucket.FAILED) {
                    Modifier
                        .clickable {
                            when (bucket) {
                                TaskBucket.COMPLETED -> onOpen()
                                TaskBucket.FAILED -> onRetry()
                                else -> Unit
                            }
                        }
                        .actionSemantics(AccessibilityCopy.taskCard(card, bucket))
                } else {
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = AccessibilityCopy.taskCard(card, bucket)
                    }
                },
            ),
        shape = RoundedCornerShape(14.dp),
        color = SurfaceSoft,
        border = BorderStroke(1.dp, Border),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(card.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(card.route, color = Muted, fontSize = 12.sp)
            card.engineText?.let { Text(it, color = Muted, fontSize = 12.sp) }

            if (bucket == TaskBucket.RUNNING || bucket == TaskBucket.PAUSED) {
                ProgressBar(percent = card.progressPercent)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${card.progressPercent}%", fontSize = 12.sp, color = Muted)
                    card.speedText?.let { Text(stringResource(R.string.tasks_speed, it), fontSize = 12.sp, color = Muted) }
                    card.remainingText?.let { Text(stringResource(R.string.tasks_remaining, it), fontSize = 12.sp, color = Muted) }
                }
                if (bucket == TaskBucket.RUNNING) {
                    Text(
                        stringResource(R.string.action_cancel),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable(onClick = onCancel)
                            .actionSemantics(stringResource(R.string.tasks_cancel))
                            .padding(top = 2.dp),
                    )
                }
            }

            if (bucket == TaskBucket.COMPLETED) {
                card.sizeSummary?.let { Text(it, fontSize = 12.sp, color = Muted) }
                card.elapsedText?.let { Text(it, fontSize = 12.sp, color = Muted) }
            }

            // §7.3：失败必须给出原因与下一步，不能只写「转换失败」。
            card.error?.let { error ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(error.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    error.detail?.let { Text(it, fontSize = 12.sp, color = Muted) }
                    error.suggestion?.let { Text(it, fontSize = 12.sp, color = Muted) }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(percent: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Border),
    ) {
        Box(
            Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Ink),
        )
    }
}
