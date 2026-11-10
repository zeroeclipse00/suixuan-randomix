package com.suixuan.app.ui.wheel

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.suixuan.app.domain.ChoiceItem
import com.suixuan.app.domain.InputValidator
import com.suixuan.app.domain.WheelGeometry
import com.suixuan.app.ui.AppState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WheelScreen(
    state: AppState,
    add: (String) -> Boolean,
    edit: (String, String) -> Boolean,
    remove: (String) -> Any,
    example: () -> Unit,
    spin: () -> Unit,
    retry: () -> Unit,
    animationFinished: (String?) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var pendingInput by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ChoiceItem?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ChoiceItem?>(null) }
    val rotation = remember { Animatable(0f) }
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val wheelSize = (windowHeight * 0.27f).coerceIn(150.dp, 240.dp)
    val wheelItems = if (state.animating) state.result?.request?.items ?: state.draft.items else state.draft.items
    LaunchedEffect(state.draft.revision) {
        pendingInput?.let { value ->
            if (state.draft.items.any { it.label == value.trim() }) {
                if (input == value) input = ""
                pendingInput = null
            }
        }
        editing?.let { item ->
            if (state.draft.items.any { it.id == item.id && it.label == editText.trim() }) editing = null
        }
    }
    LaunchedEffect(state.result?.request?.id, state.animating) {
        val result = state.result
        if (result?.selectedIndex != null && result.request.items.size == state.draft.items.size) {
            val target = WheelGeometry.finalRotation(result.selectedIndex, result.request.items.size,
                rotation.value, if (state.animating) 3 else 0)
            if (state.animating) {
                rotation.animateTo(target, tween(1800, easing = FastOutSlowInEasing))
                animationFinished(result.request.id)
            } else rotation.snapTo(target)
        } else if (result == null) rotation.snapTo(0f)
    }
    fun submit() {
        if (add(input)) pendingInput = input
    }
    Column(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("把犹豫，交给转盘", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("写下选项，给今天一个答案。", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (!state.loading && state.draft.items.isEmpty()) item {
            TextButton(onClick = example, enabled = state.editable, modifier = Modifier.testTag("loadExample")) {
                Text("先试试：今天吃什么")
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WheelCanvas(wheelItems, rotation.value,
                    Modifier.widthIn(max = wheelSize).fillMaxWidth().aspectRatio(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (state.animating) "选择中…" else if (state.busy) "正在确认保存…" else "这次的选择",
                        style = MaterialTheme.typography.labelLarge)
                    Text(if (state.busy) "稍等片刻" else state.result?.displayText ?: "等你转动",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("drawResult"))
                }
            }
        }
        state.error?.let { message -> item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(message)
                    if (state.pendingSave) TextButton(onClick = retry, enabled = !state.busy) { Text("重试保存") }
                }
            }
        } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("我的选项", style = MaterialTheme.typography.titleLarge)
                Text("${state.draft.items.size} / 20", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("添加一个选项") },
                    singleLine = true, enabled = state.editable, modifier = Modifier.weight(1f).testTag("candidateInput"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }))
                IconButton(onClick = { submit() }, enabled = state.editable && input.isNotBlank(),
                    modifier = Modifier.testTag("addCandidate")) {
                    Icon(Icons.Outlined.Add, contentDescription = "添加选项")
                }
            }
        }
        if (state.loading) item { CircularProgressIndicator() }
        if (!state.loading && state.draft.items.isEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("还没有选项。可以先试试“今天吃什么”。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.draft.items, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, Modifier.weight(1f).padding(vertical = 12.dp))
                    IconButton(onClick = { editing = item; editText = item.label }, enabled = state.editable) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑 ${item.label}")
                    }
                    IconButton(onClick = { deleting = item }, enabled = state.editable) {
                        Icon(Icons.Outlined.Close, contentDescription = "删除 ${item.label}")
                    }
                }
            }
        }
    }
    Button(onClick = spin, enabled = state.editable && state.draft.items.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(52.dp).testTag("spinButton")) {
        Text(if (state.animating) "正在转动" else if (state.busy) "正在保存" else "帮我选一个",
            style = MaterialTheme.typography.titleMedium)
    }
    }
    editing?.let { item ->
        AlertDialog(onDismissRequest = { if (!state.savingDraft) editing = null }, title = { Text("编辑选项") },
            text = { Column {
                OutlinedTextField(value = editText, onValueChange = { editText = it }, singleLine = true,
                    enabled = !state.savingDraft, label = { Text("选项名称") })
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = { edit(item.id, editText) }, enabled = state.editable) { Text("保存") } },
            dismissButton = { TextButton(onClick = { editing = null }, enabled = !state.savingDraft) { Text("取消") } })
    }
    deleting?.let { item ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这个选项？") }, text = { Text(item.label) },
            confirmButton = { TextButton(onClick = { remove(item.id); deleting = null }, enabled = state.editable) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
}

private val Slices = listOf(Color(0xFFC6DAC5), Color(0xFFF0C8A5), Color(0xFFDCD8BC), Color(0xFFAAC9BF), Color(0xFFE8B99D))

@Composable
private fun WheelCanvas(items: List<ChoiceItem>, angle: Float, modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "转盘，共 ${items.size} 个选项" }) {
        val radius = size.minDimension * 0.45f
        val center = center
        val origin = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        if (items.isEmpty()) drawCircle(Color(0xFFE2E6D9), radius, center)
        else rotate(angle, center) {
            val sweep = 360f / items.size
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(31, 58, 42)
                textAlign = Paint.Align.CENTER
                textSize = if (items.size > 10) 11.dp.toPx() else 14.dp.toPx()
                isFakeBoldText = true
            }
            items.forEachIndexed { index, item ->
                drawArc(Slices[index % Slices.size], -90f + index * sweep, sweep,
                    useCenter = true, topLeft = origin, size = arcSize)
                val degrees = -90f + (index + 0.5f) * sweep
                val radians = Math.toRadians(degrees.toDouble())
                val x = center.x + cos(radians).toFloat() * radius * 0.65f
                val y = center.y + sin(radians).toFloat() * radius * 0.65f
                val label = item.label.let { if (it.length > 6) it.take(5) + "…" else it }
                drawContext.canvas.nativeCanvas.apply {
                    save(); rotate(degrees + 90f, x, y)
                    drawText(label, x, y + textPaint.textSize / 3f, textPaint); restore()
                }
            }
        }
        drawCircle(Color(0xFF275C45), radius * 0.12f, center)
        drawCircle(Color(0xFFF7F5F0), radius * 0.045f, center)
        val pointer = Path().apply {
            moveTo(center.x - 11.dp.toPx(), center.y - radius - 4.dp.toPx())
            lineTo(center.x + 11.dp.toPx(), center.y - radius - 4.dp.toPx())
            lineTo(center.x, center.y - radius + 22.dp.toPx()); close()
        }
        drawPath(pointer, Color(0xFF275C45))
    }
}
