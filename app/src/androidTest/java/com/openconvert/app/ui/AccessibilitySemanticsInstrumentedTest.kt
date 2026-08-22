package com.openconvert.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilitySemanticsInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun toolCardAnnouncesTitleAndIsClickable() {
        compose.setContent {
            ToolCard(
                title = "PDF 工具",
                subtitle = "转换 · 合并 · 拆分",
                icon = Icons.Outlined.Description,
                onClick = {},
            )
        }
        compose.onNodeWithContentDescription("PDF 工具，转换 · 合并 · 拆分")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun settingRowAnnouncesTitleAndValue() {
        compose.setContent {
            SettingRow("清理缓存", "一键清理转换中间件与缩略图缓存", onClick = {})
        }
        compose.onNodeWithContentDescription("清理缓存，一键清理转换中间件与缩略图缓存")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
