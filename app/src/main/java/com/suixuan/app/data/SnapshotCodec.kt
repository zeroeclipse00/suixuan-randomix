package com.suixuan.app.data

import com.suixuan.app.domain.ChoiceItem
import com.suixuan.app.domain.DrawRequest
import com.suixuan.app.domain.DrawResult
import com.suixuan.app.domain.InputValidator
import com.suixuan.app.domain.ToolType
import java.util.Collections
import org.json.JSONArray
import org.json.JSONObject

/** A versioned value snapshot; no future lookup of a mutable template is needed. */
internal object SnapshotCodec {
    private const val VERSION = 1

    fun encodeItems(items: List<ChoiceItem>): String = itemsArray(items).toString()

    fun decodeItems(json: String): List<ChoiceItem> = itemsFromArray(JSONArray(json))

    fun encode(result: DrawResult): String = JSONObject().apply {
        put("snapshotVersion", VERSION)
        put("request", JSONObject().apply {
            put("id", result.request.id)
            put("tool", result.request.tool.name)
            put("items", itemsArray(result.request.items))
            put("diceCount", result.request.diceCount)
            put("createdAtMillis", result.request.createdAtMillis)
        })
        put("selectedItemId", result.selectedItemId ?: JSONObject.NULL)
        put("selectedIndex", result.selectedIndex ?: JSONObject.NULL)
        put("values", JSONArray().apply { result.values.forEach { put(it) } })
        put("displayText", result.displayText)
    }.toString()

    /** Reject malformed envelopes without generating a second random result. */
    fun validate(result: DrawResult) {
        require(result.request.id.isNotBlank()) { "抽取编号不能为空" }
        require(result.displayText.isNotBlank()) { "抽取结果不能为空" }
        when (result.request.tool) {
            ToolType.WHEEL, ToolType.MAGIC_BALL -> {
                val validItems = if (result.request.tool == ToolType.WHEEL) {
                    InputValidator.wheelItems(result.request.items)
                } else {
                    InputValidator.magicAnswers(result.request.items)
                }
                require(validItems == result.request.items) { "抽取请求必须使用已经保存的规范化输入" }
                val index = result.selectedIndex
                require(index != null && index in validItems.indices) { "选中索引不合法" }
                require(result.selectedItemId == validItems[index].id) { "选中项目与索引不一致" }
                require(result.displayText == validItems[index].label) { "结果文字与选中项目不一致" }
                require(result.values.isEmpty()) { "候选抽取不应包含骰子或字母数值" }
            }
            ToolType.COIN -> {
                require(result.selectedIndex == null && result.selectedItemId == null) { "硬币结果不应包含选项索引" }
                require(result.values.size == 1 && result.values.single() in 0..1) { "硬币结果不合法" }
                require(result.displayText == if (result.values.single() == 0) "正面" else "反面") { "硬币文字与数值不一致" }
            }
            ToolType.DICE -> {
                require(result.selectedIndex == null && result.selectedItemId == null) { "骰子结果不应包含选项索引" }
                InputValidator.diceCount(result.request.diceCount)
                require(result.values.size == result.request.diceCount && result.values.all { it in 1..6 }) {
                    "骰子点数与数量不一致"
                }
                require(result.displayText == "${result.values.joinToString(" + ")} = ${result.values.sum()}") {
                    "骰子结果文字与点数总和不一致"
                }
            }
            ToolType.LETTER -> {
                require(result.selectedIndex == null && result.selectedItemId == null) { "字母结果不应包含选项索引" }
                require(result.values.size == 1 && result.values.single() in 0..25) { "字母结果不合法" }
                require(result.displayText == ('A' + result.values.single()).toString()) { "字母文字与数值不一致" }
            }
        }
    }

    fun decode(json: String): DrawResult {
        val snapshot = JSONObject(json)
        require(snapshot.getInt("snapshotVersion") == VERSION) { "不支持的历史快照版本" }
        val request = snapshot.getJSONObject("request")
        return DrawResult(
            request = DrawRequest(
                id = request.getString("id"),
                tool = ToolType.valueOf(request.getString("tool")),
                items = itemsFromArray(request.getJSONArray("items")),
                diceCount = request.getInt("diceCount"),
                createdAtMillis = request.getLong("createdAtMillis"),
            ),
            selectedItemId = if (snapshot.isNull("selectedItemId")) null else snapshot.getString("selectedItemId"),
            selectedIndex = if (snapshot.isNull("selectedIndex")) null else snapshot.getInt("selectedIndex"),
            values = snapshot.getJSONArray("values").let { values ->
                Collections.unmodifiableList(List(values.length()) { values.getInt(it) })
            },
            displayText = snapshot.getString("displayText"),
        ).also(::validate)
    }

    private fun itemsArray(items: List<ChoiceItem>): JSONArray = JSONArray().apply {
        items.forEachIndexed { index, item ->
            put(JSONObject().apply {
                put("id", item.id)
                put("label", item.label)
                put("position", index)
            })
        }
    }

    private fun itemsFromArray(array: JSONArray): List<ChoiceItem> = Collections.unmodifiableList(List(array.length()) { index ->
        val item = array.getJSONObject(index)
        require(item.getInt("position") == index) { "历史快照顺序不完整" }
        ChoiceItem(id = item.getString("id"), label = item.getString("label"))
    })
}
