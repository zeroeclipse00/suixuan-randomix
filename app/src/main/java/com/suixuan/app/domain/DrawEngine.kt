package com.suixuan.app.domain

import java.util.Collections
import kotlin.random.Random

fun interface RandomSource {
    /** Returns an unbiased integer in [0, bound); bound must be positive. */
    fun nextInt(bound: Int): Int
}

object DefaultRandomSource : RandomSource {
    override fun nextInt(bound: Int): Int = Random.Default.nextInt(bound)
}

/** Pure generation: callers create operation IDs and persist a returned result before revealing it. */
class DrawEngine(private val randomSource: RandomSource = DefaultRandomSource) {
    fun generate(request: DrawRequest): DrawResult {
        require(request.id.isNotBlank()) { "抽取 ID 不能为空。" }
        val snapshot = request.copy(
            items = when (request.tool) {
                ToolType.WHEEL -> InputValidator.wheelItems(request.items)
                ToolType.MAGIC_BALL -> InputValidator.magicAnswers(request.items)
                else -> Collections.unmodifiableList(request.items.map { it.copy() })
            },
            diceCount = if (request.tool == ToolType.DICE) {
                InputValidator.diceCount(request.diceCount)
            } else {
                request.diceCount
            },
        )
        return when (snapshot.tool) {
            ToolType.WHEEL, ToolType.MAGIC_BALL -> {
                val index = if (snapshot.items.size == 1) 0 else nextIndex(snapshot.items.size)
                val item = snapshot.items[index]
                DrawResult(snapshot, selectedItemId = item.id, selectedIndex = index, displayText = item.label)
            }
            ToolType.COIN -> {
                val face = nextIndex(2)
                DrawResult(snapshot, values = listOf(face), displayText = if (face == 0) "正面" else "反面")
            }
            ToolType.DICE -> {
                val values = Collections.unmodifiableList(List(snapshot.diceCount) { nextIndex(6) + 1 })
                DrawResult(snapshot, values = values, displayText = "${values.joinToString(" + ")} = ${values.sum()}")
            }
            ToolType.LETTER -> {
                val index = nextIndex(26)
                DrawResult(snapshot, values = listOf(index), displayText = ('A' + index).toString())
            }
        }
    }

    private fun nextIndex(bound: Int): Int {
        val index = randomSource.nextInt(bound)
        require(index in 0 until bound) { "随机源返回了范围外的值。" }
        return index
    }
}
