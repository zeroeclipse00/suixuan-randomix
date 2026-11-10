package com.suixuan.app.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.suixuan.app.MainActivity
import com.suixuan.app.SuixuanApplication
import com.suixuan.app.domain.ChoiceItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WheelFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realWheelResultAppearsOnceInHistoryWithItsSnapshot() {
        val app = ApplicationProvider.getApplicationContext<SuixuanApplication>()
        val labels = listOf("食堂", "面馆", "盖饭", "水饺")
        runBlocking {
            app.container.repository.clearHistory()
            val draft = app.container.repository.observeDraft().first()
            app.container.repository.saveDraft(draft.revision,
                labels.mapIndexed { i, label -> ChoiceItem("ui-fixture-$i", label) })
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("spinButton").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("spinButton").performClick()
        compose.waitUntil(10_000) {
            val node = compose.onAllNodesWithTag("drawResult").fetchSemanticsNodes().firstOrNull()
            node?.config?.get(SemanticsProperties.Text)?.any { it.text in labels } == true
        }
        val text = compose.onNodeWithTag("drawResult").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        assertTrue(text in labels)
        compose.onNodeWithContentDescription("历史记录").performClick()
        compose.onNodeWithText("1 条已保存记录").assertExists()
        compose.onNodeWithText(text).performClick()
        compose.onNodeWithText("当时的选项", substring = true).assertExists()
        runBlocking { assertTrue(app.container.repository.observeHistory().first().single().result.displayText == text) }
    }
}
