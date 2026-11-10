package com.suixuan.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DrawEngineTest {
    @Test
    fun `wheel samples one index and keeps caller ID time and snapshot order`() {
        val source = RecordingSource(2)
        val items = mutableListOf(ChoiceItem("z", "食堂"), ChoiceItem("a", "面馆"), ChoiceItem("x", "  盖饭  "))
        val request = DrawRequest("operation-1", ToolType.WHEEL, items, createdAtMillis = 1234L)
        val result = DrawEngine(source).generate(request)

        items[2] = ChoiceItem("replacement", "水饺")
        items.clear()

        assertEquals("operation-1", result.request.id)
        assertEquals(1234L, result.request.createdAtMillis)
        assertEquals(listOf("z", "a", "x"), result.request.items.map { it.id })
        assertEquals("x", result.selectedItemId)
        assertEquals(2, result.selectedIndex)
        assertEquals("盖饭", result.displayText)
        assertEquals(listOf(3), source.bounds)
        assertThrows(UnsupportedOperationException::class.java) {
            (result.request.items as MutableList<ChoiceItem>).clear()
        }
    }

    @Test
    fun `single wheel and single custom answer do not consume randomness`() {
        val source = RecordingSource()
        val engine = DrawEngine(source)
        for (tool in listOf(ToolType.WHEEL, ToolType.MAGIC_BALL)) {
            val result = engine.generate(DrawRequest("single-$tool", tool, listOf(ChoiceItem("only", "唯一项"))))
            assertEquals(0, result.selectedIndex)
            assertEquals("only", result.selectedItemId)
            assertEquals("唯一项", result.displayText)
        }
        assertEquals(emptyList<Int>(), source.bounds)
    }

    @Test
    fun `invalid requests fail before consuming a random value`() {
        val source = RecordingSource()
        val engine = DrawEngine(source)
        val cases = listOf(
            DrawRequest(" ", ToolType.COIN),
            DrawRequest("empty-wheel", ToolType.WHEEL),
            DrawRequest("empty-answers", ToolType.MAGIC_BALL),
            DrawRequest("bad-dice", ToolType.DICE, diceCount = 0),
            DrawRequest("too-many-dice", ToolType.DICE, diceCount = 4),
            DrawRequest("duplicates", ToolType.WHEEL, listOf(ChoiceItem("a", "tea"), ChoiceItem("b", " TEA "))),
        )
        cases.forEach { request -> assertThrows(IllegalArgumentException::class.java) { engine.generate(request) } }
        assertEquals(emptyList<Int>(), source.bounds)
    }

    @Test
    fun `coin maps each of two outcomes to its visible side`() {
        val source = RecordingSource(0, 1)
        val engine = DrawEngine(source)
        val heads = engine.generate(DrawRequest("heads", ToolType.COIN))
        val tails = engine.generate(DrawRequest("tails", ToolType.COIN))
        assertEquals(listOf(0), heads.values)
        assertEquals("正面", heads.displayText)
        assertEquals(listOf(1), tails.values)
        assertEquals("反面", tails.displayText)
        assertNull(heads.selectedItemId)
        assertEquals(listOf(2, 2), source.bounds)
    }

    @Test
    fun `each die consumes an independent value and sum matches ordered outcomes`() {
        for (count in 1..3) {
            val source = RecordingSource(0, 5, 2)
            val result = DrawEngine(source).generate(DrawRequest("dice-$count", ToolType.DICE, diceCount = count))
            val expected = listOf(1, 6, 3).take(count)
            assertEquals(expected, result.values)
            assertEquals(expected.joinToString(" + ") + " = ${expected.sum()}", result.displayText)
            assertEquals(List(count) { 6 }, source.bounds)
            assertThrows(UnsupportedOperationException::class.java) { (result.values as MutableList<Int>).clear() }
        }
    }

    @Test
    fun `letter endpoints are A and Z and use the complete alphabet bound`() {
        val source = RecordingSource(0, 25)
        val engine = DrawEngine(source)
        assertEquals("A", engine.generate(DrawRequest("a", ToolType.LETTER)).displayText)
        val last = engine.generate(DrawRequest("z", ToolType.LETTER))
        assertEquals("Z", last.displayText)
        assertEquals(listOf(25), last.values)
        assertEquals(listOf(26, 26), source.bounds)
    }

    @Test
    fun `magic ball only chooses from the supplied custom answer pool`() {
        val source = RecordingSource(1)
        val answers = listOf(ChoiceItem("walk", "散步"), ChoiceItem("music", "听音乐"))
        val result = DrawEngine(source).generate(DrawRequest("custom", ToolType.MAGIC_BALL, answers))
        assertEquals("music", result.selectedItemId)
        assertEquals("听音乐", result.displayText)
        assertEquals(listOf(2), source.bounds)
    }

    @Test
    fun `out of contract random source cannot create an invalid selected index`() {
        for (index in listOf(-1, 2)) {
            assertThrows(IllegalArgumentException::class.java) {
                DrawEngine(RecordingSource(index)).generate(DrawRequest("bad-$index", ToolType.COIN))
            }
        }
    }

    private class RecordingSource(vararg answers: Int) : RandomSource {
        private val iterator = answers.iterator()
        val bounds = mutableListOf<Int>()

        override fun nextInt(bound: Int): Int {
            bounds.add(bound)
            check(iterator.hasNext()) { "Unexpected extra random call with bound $bound" }
            return iterator.nextInt()
        }
    }
}
