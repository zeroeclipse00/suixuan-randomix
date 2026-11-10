package com.suixuan.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suixuan.app.ui.history.HistoryScreen
import com.suixuan.app.ui.tools.CoinScreen
import com.suixuan.app.ui.tools.DiceScreen
import com.suixuan.app.ui.tools.MagicBallScreen
import com.suixuan.app.ui.tools.TemplatesScreen
import com.suixuan.app.ui.wheel.WheelScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuixuanApp(model: AppViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.finishAnimation()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("随选", style = MaterialTheme.typography.headlineSmall)
                    Text("离线决策工具箱 · 开发预览", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }, actions = {
                IconButton(onClick = { model.navigate(Destination.TEMPLATES) }) {
                    Icon(Icons.Outlined.Style, contentDescription = "情境模板")
                }
                IconButton(onClick = { model.navigate(Destination.HISTORY) }, modifier = Modifier.testTag("historyButton")) {
                    Icon(Icons.Outlined.History, contentDescription = "历史记录")
                }
                IconButton(onClick = { settingsOpen = true }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置")
                }
            })
        },
        bottomBar = {
            NavigationBar {
                listOf(Destination.WHEEL, Destination.COIN, Destination.DICE, Destination.MAGIC).forEach { destination ->
                    val icon = when (destination) {
                        Destination.WHEEL -> Icons.Outlined.PieChart
                        Destination.COIN -> Icons.Outlined.Toll
                        Destination.DICE -> Icons.Outlined.Casino
                        else -> Icons.Outlined.AutoAwesome
                    }
                    NavigationBarItem(selected = state.destination == destination,
                        onClick = { model.navigate(destination) },
                        icon = { Icon(icon, contentDescription = null) }, label = { Text(destination.label) })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (state.destination) {
                Destination.WHEEL -> WheelScreen(state, model::addCandidate, model::editCandidate,
                    model::removeCandidate, model::loadExample, model::spin, model::retrySave, model::finishAnimation)
                Destination.HISTORY -> HistoryScreen(state.history, state.editable, model::clearHistory)
                Destination.COIN -> CoinScreen()
                Destination.DICE -> DiceScreen()
                Destination.MAGIC -> MagicBallScreen()
                Destination.TEMPLATES -> TemplatesScreen()
            }
        }
    }
    if (settingsOpen) AlertDialog(
        onDismissRequest = { settingsOpen = false }, title = { Text("偏好设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("减少动画")
                        Text("直接揭示结果，随机规则保持一致。", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.reduceMotion, onCheckedChange = model::setReduceMotion)
                }
                Text("当前版本已提供转盘与历史。其余工具和模板正在开发。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("完成") } },
    )
}
