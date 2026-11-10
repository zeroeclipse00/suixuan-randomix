package com.suixuan.app.domain

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelGeometryTest {
    @Test
    fun `every chosen sector reaches the top pointer from negative and multi turn starts`() {
        for (count in listOf(1, 2, 3, 7, 20)) {
            for (index in 0 until count) {
                for (start in listOf(-1080f, -55f, 0f, 735f, 1080f)) {
                    val rotation = WheelGeometry.finalRotation(index, count, start)
                    val center = -90.0 + (index + 0.5) * 360.0 / count
                    val error = circularDistance(center + rotation, -90.0)
                    assertTrue("N=$count index=$index start=$start error=$error", error < 0.001)
                    assertTrue("The wheel must continue clockwise", rotation >= start)
                    if (count > 1) assertTrue(rotation - start >= 3 * 360f)
                }
            }
        }
    }

    @Test
    fun `cosmetic full turns change travel but not the selected outcome`() {
        val short = WheelGeometry.finalRotation(2, 7, 123f, fullTurns = 0)
        val long = WheelGeometry.finalRotation(2, 7, 123f, fullTurns = 3)
        assertEquals(1080f, long - short, 0.001f)
        assertEquals(0.0, circularDistance(short.toDouble(), long.toDouble()), 0.001)
    }

    @Test
    fun `single item omits cosmetic complete turns`() {
        assertEquals(
            WheelGeometry.finalRotation(0, 1, fullTurns = 0),
            WheelGeometry.finalRotation(0, 1, fullTurns = 3),
            0f,
        )
    }

    @Test
    fun `geometry rejects missing sectors invalid indices and invalid animation inputs`() {
        assertThrows(IllegalArgumentException::class.java) { WheelGeometry.finalRotation(0, 0) }
        assertThrows(IllegalArgumentException::class.java) { WheelGeometry.finalRotation(-1, 3) }
        assertThrows(IllegalArgumentException::class.java) { WheelGeometry.finalRotation(3, 3) }
        assertThrows(IllegalArgumentException::class.java) { WheelGeometry.finalRotation(0, 3, fullTurns = -1) }
        for (start in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { WheelGeometry.finalRotation(0, 3, start) }
        }
    }

    private fun circularDistance(a: Double, b: Double): Double {
        val delta = abs((a - b) % 360.0)
        return minOf(delta, 360.0 - delta)
    }
}
