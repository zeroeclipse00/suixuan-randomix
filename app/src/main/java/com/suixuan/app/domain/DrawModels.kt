package com.suixuan.app.domain

enum class ToolType { WHEEL, COIN, DICE, MAGIC_BALL, LETTER }

data class ChoiceItem(val id: String, val label: String)

data class DrawRequest(
    val id: String,
    val tool: ToolType,
    val items: List<ChoiceItem> = emptyList(),
    val diceCount: Int = 1,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

data class DrawResult(
    val request: DrawRequest,
    val selectedItemId: String? = null,
    val selectedIndex: Int? = null,
    val values: List<Int> = emptyList(),
    val displayText: String,
)
