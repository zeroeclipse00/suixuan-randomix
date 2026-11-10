package com.suixuan.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suixuan.app.data.StoredDraw
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(history: List<StoredDraw>, canClear: Boolean, clear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<StoredDraw?>(null) }
    val formatter = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault()) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("选择的足迹", style = MaterialTheme.typography.headlineMedium)
                Text("${history.size} 条已保存记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { confirmClear = true }, enabled = canClear && history.isNotEmpty()) { Text("清空") }
        }
        if (history.isEmpty()) {
            Text("还没有记录。完成一次转盘选择后，它会出现在这里。", Modifier.padding(vertical = 40.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(history, key = { it.result.request.id }) { record ->
                Card(Modifier.fillMaxWidth().clickable { detail = record }) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(record.result.displayText, style = MaterialTheme.typography.titleLarge)
                        Text("转盘 · ${formatter.format(Instant.ofEpochMilli(record.committedAtMillis))}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("从 ${record.result.request.items.size} 个选项中选出", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("清空历史记录？") }, text = { Text("历史将被删除，当前候选项会保留。") },
        confirmButton = { TextButton(onClick = { clear(); confirmClear = false }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
    detail?.let { record ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(record.result.displayText) },
            text = { Text("当时的选项\n\n" + record.result.request.items.joinToString("\n") { it.label }) },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("关闭") } })
    }
}
