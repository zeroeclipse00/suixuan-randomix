package com.suixuan.app.domain

import java.util.Collections
import java.util.Locale

/** Shared rules for forms, persisted drafts, and the draw engine. */
object InputValidator {
    const val MAX_WHEEL_ITEMS = 20
    const val MAX_WHEEL_LABEL_CODE_POINTS = 30
    const val MAX_MAGIC_ANSWERS = 30
    const val MAX_MAGIC_LABEL_CODE_POINTS = 80

    fun wheelItems(items: List<ChoiceItem>, allowEmpty: Boolean = false): List<ChoiceItem> =
        validatedItems(items, MAX_WHEEL_ITEMS, MAX_WHEEL_LABEL_CODE_POINTS, allowEmpty)

    fun magicAnswers(items: List<ChoiceItem>, allowEmpty: Boolean = false): List<ChoiceItem> =
        validatedItems(items, MAX_MAGIC_ANSWERS, MAX_MAGIC_LABEL_CODE_POINTS, allowEmpty)

    fun diceCount(count: Int): Int {
        require(count in 1..3) { "骰子数量必须是 1 到 3。" }
        return count
    }

    fun normalizedLabel(label: String): String = label.trim()

    fun duplicateKey(label: String): String = normalizedLabel(label).lowercase(Locale.ROOT)

    private fun validatedItems(
        items: List<ChoiceItem>,
        maxCount: Int,
        maxLabelCodePoints: Int,
        allowEmpty: Boolean,
    ): List<ChoiceItem> {
        require(allowEmpty || items.isNotEmpty()) { "请先添加至少一个选项。" }
        require(items.size <= maxCount) { "最多可以添加 $maxCount 个选项。" }
        val ids = HashSet<String>()
        val labels = HashSet<String>()
        val snapshot = items.map { item ->
            require(item.id.isNotBlank()) { "选项 ID 不能为空。" }
            require(ids.add(item.id)) { "选项 ID 不能重复。" }
            val label = normalizedLabel(item.label)
            require(label.isNotEmpty()) { "选项内容不能为空。" }
            require(label.codePointCount(0, label.length) <= maxLabelCodePoints) {
                "每个选项最多 $maxLabelCodePoints 个字符。"
            }
            require(labels.add(duplicateKey(label))) { "选项内容不能重复（忽略首尾空白与大小写）。" }
            item.copy(label = label)
        }
        return Collections.unmodifiableList(snapshot)
    }
}
