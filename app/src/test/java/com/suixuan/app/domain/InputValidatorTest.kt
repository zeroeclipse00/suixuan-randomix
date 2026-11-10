package com.suixuan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InputValidatorTest {
    @Test
    fun `empty draft is allowed but cannot be drawn`() {
        assertEquals(emptyList<ChoiceItem>(), InputValidator.wheelItems(emptyList(), allowEmpty = true))
        assertThrows(IllegalArgumentException::class.java) { InputValidator.wheelItems(emptyList()) }
    }

    @Test
    fun `normalization preserves IDs order and internal spaces`() {
        val result = InputValidator.wheelItems(listOf(ChoiceItem("z", "  A  B  "), ChoiceItem("a", "食堂")))
        assertEquals(listOf("z", "a"), result.map { it.id })
        assertEquals(listOf("A  B", "食堂"), result.map { it.label })
    }

    @Test
    fun `blank IDs and labels and duplicate IDs are rejected`() {
        val cases = listOf(
            listOf(ChoiceItem("  ", "食堂")),
            listOf(ChoiceItem("a", " \n\t ")),
            listOf(ChoiceItem("a", "食堂"), ChoiceItem("a", "面馆")),
        )
        cases.forEach { items ->
            assertThrows(IllegalArgumentException::class.java) { InputValidator.wheelItems(items) }
        }
    }

    @Test
    fun `labels differing only in surrounding space and case are duplicates`() {
        assertThrows(IllegalArgumentException::class.java) {
            InputValidator.wheelItems(listOf(ChoiceItem("a", " Lunch "), ChoiceItem("b", "lunch")))
        }
        // Internal spaces remain significant, unlike surrounding whitespace.
        assertEquals(2, InputValidator.wheelItems(listOf(ChoiceItem("a", "A B"), ChoiceItem("b", "A  B"))).size)
    }

    @Test
    fun `wheel accepts twenty items and rejects twenty one`() {
        val items = (1..21).map { ChoiceItem("$it", "选项$it") }
        assertEquals(20, InputValidator.wheelItems(items.take(20)).size)
        assertThrows(IllegalArgumentException::class.java) { InputValidator.wheelItems(items) }
    }

    @Test
    fun `label limits count Unicode code points instead of UTF16 units`() {
        val emoji = "\uD83C\uDFB2"
        assertEquals(emoji.repeat(30), InputValidator.wheelItems(listOf(ChoiceItem("a", emoji.repeat(30)))).single().label)
        assertThrows(IllegalArgumentException::class.java) {
            InputValidator.wheelItems(listOf(ChoiceItem("a", emoji.repeat(31))))
        }
    }

    @Test
    fun `magic answers have their own thirty item and eighty code point limits`() {
        val items = (1..31).map { ChoiceItem("$it", "答案$it") }
        assertEquals(30, InputValidator.magicAnswers(items.take(30)).size)
        assertThrows(IllegalArgumentException::class.java) { InputValidator.magicAnswers(items) }
        assertEquals(80, InputValidator.magicAnswers(listOf(ChoiceItem("a", "答".repeat(80)))).single().label.length)
        assertThrows(IllegalArgumentException::class.java) {
            InputValidator.magicAnswers(listOf(ChoiceItem("a", "答".repeat(81))))
        }
    }

    @Test
    fun `returned list is a stable snapshot not an alias of input`() {
        val input = mutableListOf(ChoiceItem("a", "食堂"))
        val snapshot = InputValidator.wheelItems(input)
        input[0] = ChoiceItem("b", "面馆")
        input.clear()
        assertEquals(listOf(ChoiceItem("a", "食堂")), snapshot)
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot as MutableList<ChoiceItem>).add(ChoiceItem("c", "盖饭"))
        }
    }
}
