package com.suixuan.app.domain

/** Clockwise screen angles, top pointer and the first sector boundary both at -90 degrees. */
object WheelGeometry {
    const val POINTER_DEGREES = -90f
    const val FIRST_BOUNDARY_DEGREES = -90f

    fun sectorCenter(selectedIndex: Int, itemCount: Int): Float {
        validateIndex(selectedIndex, itemCount)
        return (FIRST_BOUNDARY_DEGREES + (selectedIndex + 0.5) * 360.0 / itemCount).toFloat()
    }

    /** The returned angle continues clockwise from startRotation, with cosmetic full turns. */
    fun finalRotation(
        selectedIndex: Int,
        itemCount: Int,
        startRotation: Float = 0f,
        fullTurns: Int = 3,
    ): Float {
        validateIndex(selectedIndex, itemCount)
        require(startRotation.isFinite()) { "起始角度必须是有限值。" }
        require(fullTurns >= 0) { "完整旋转圈数不能为负。" }
        val center = FIRST_BOUNDARY_DEGREES + (selectedIndex + 0.5) * 360.0 / itemCount
        val target = normalize(POINTER_DEGREES - center)
        val delta = normalize(target - normalize(startRotation.toDouble()))
        val turns = if (itemCount == 1) 0 else fullTurns
        val result = (startRotation.toDouble() + delta + turns * 360.0).toFloat()
        require(result.isFinite()) { "旋转角度超出支持范围。" }
        return result
    }

    private fun validateIndex(index: Int, count: Int) {
        require(count > 0) { "转盘必须至少有一个选项。" }
        require(index in 0 until count) { "选中索引超出候选范围。" }
    }

    private fun normalize(angle: Double): Double = ((angle % 360.0) + 360.0) % 360.0
}
