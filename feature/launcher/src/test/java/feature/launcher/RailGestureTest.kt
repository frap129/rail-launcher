package feature.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RailGestureTest {
    @Test
    fun `closes an open card and suppresses its scroll`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        val actualAction: RailIndexAction = gesture.onScroll(card = popupCard(isOpen = true, onClose = { actualCloseCount++ }))
        assertEquals(RailIndexAction.Ignore, actualAction)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `suppresses the scrolls after the card closed`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        gesture.onScroll(card = popupCard(isOpen = true, onClose = { actualCloseCount++ }))
        val actualAction: RailIndexAction = gesture.onScroll(card = popupCard(onClose = { actualCloseCount++ }))
        assertEquals(RailIndexAction.Ignore, actualAction)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `closes a card once for its whole gesture`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        gesture.onScroll(card = popupCard(isOpen = true, onClose = { actualCloseCount++ }))
        val actualAction: RailIndexAction = gesture.onScroll(card = popupCard(isOpen = true, onClose = { actualCloseCount++ }))
        assertEquals(RailIndexAction.Ignore, actualAction)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `suppresses the scroll-ended callback of the gesture`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        gesture.onScroll(card = popupCard(isOpen = true, onClose = { actualCloseCount++ }))
        val actualAction: RailIndexAction = gesture.onScrollEnded(card = popupCard(onClose = { actualCloseCount++ }))
        assertEquals(RailIndexAction.Ignore, actualAction)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `re-arms the next gesture on scroll-ended`() {
        val gesture: RailGesture = RailGesture()
        gesture.onScroll(card = popupCard(isOpen = true))
        gesture.onScrollEnded(card = popupCard())
        val actualAction: RailIndexAction = gesture.onScroll(card = popupCard())
        assertEquals(RailIndexAction.Act, actualAction)
    }

    @Test
    fun `previews a scroll when no card is open`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        val actualAction: RailIndexAction = gesture.onScroll(card = popupCard(onClose = { actualCloseCount++ }))
        assertEquals(RailIndexAction.Act, actualAction)
        assertEquals(0, actualCloseCount)
    }

    @Test
    fun `releases a scroll when no card is open`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        val actualAction: RailIndexAction = gesture.onScrollEnded(card = popupCard(onClose = { actualCloseCount++ }))
        assertEquals(RailIndexAction.Act, actualAction)
        assertEquals(0, actualCloseCount)
    }

    @Test
    fun `re-arms a gesture restarted mid-touch`() {
        val gesture: RailGesture = RailGesture()
        gesture.onScroll(card = popupCard(isOpen = true))
        gesture.reset()
        val actualAction: RailIndexAction = gesture.onScroll(card = popupCard())
        assertEquals(RailIndexAction.Act, actualAction)
    }

    @Test
    fun `a downward Rail wheel step moves toward the last group`() {
        val gesture: RailGesture = RailGesture()
        val actualIndices: List<Int> = gesture.applyWheelDelta(inputDeltaY = 1f, currentMarkIndex = 1)
        assertEquals(listOf(2), actualIndices)
    }

    @Test
    fun `an upward Rail wheel step moves toward the first group`() {
        val gesture: RailGesture = RailGesture()
        val actualIndices: List<Int> = gesture.applyWheelDelta(inputDeltaY = -1f, currentMarkIndex = 1)
        assertEquals(listOf(0), actualIndices)
    }

    @Test
    fun `steps to the next group on a wheel notch`() {
        val gesture: RailGesture = RailGesture()
        val actualIndex: Int? = gesture.wheelStep(inputStep = 1)
        assertEquals(1, actualIndex)
    }

    @Test
    fun `steps one group per wheel notch`() {
        val gesture: RailGesture = RailGesture()
        val inputFirstIndex: Int? = gesture.wheelStep(inputStep = 1)
        val actualIndex: Int? = gesture.wheelStep(inputStep = 1, currentMarkIndex = requireNotNull(inputFirstIndex))
        assertEquals(2, actualIndex)
    }

    @Test
    fun `steps to the previous group on a wheel notch`() {
        val gesture: RailGesture = RailGesture()
        val actualIndex: Int? = gesture.wheelStep(inputStep = -1, currentMarkIndex = 1)
        assertEquals(0, actualIndex)
    }

    @Test
    fun `stops at the last group on a wheel notch`() {
        val gesture: RailGesture = RailGesture()
        val actualIndex: Int? = gesture.wheelStep(inputStep = 1, currentMarkIndex = 2)
        assertNull(actualIndex)
    }

    @Test
    fun `stops at the first group on a wheel notch`() {
        val gesture: RailGesture = RailGesture()
        val actualIndex: Int? = gesture.wheelStep(inputStep = -1)
        assertNull(actualIndex)
    }

    @Test
    fun `rejects a Rail wheel step that is not one mark`() {
        val gesture: RailGesture = RailGesture()
        listOf(0, 2, -2).forEach { inputStep: Int ->
            assertThrows(IllegalArgumentException::class.java) {
                gesture.wheelStep(inputStep = inputStep)
            }
        }
    }

    @Test
    fun `closes an open card on a wheel notch`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        val actualIndex: Int? = gesture.wheelStep(inputStep = 1, isOpen = true, onClose = { actualCloseCount++ })
        assertNull(actualIndex)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `steps on the wheel notch after closing a card`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        gesture.wheelStep(inputStep = 1, isOpen = true, onClose = { actualCloseCount++ })
        val actualIndex: Int? = gesture.wheelStep(inputStep = 1)
        assertEquals(1, actualIndex)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `ignores a wheel notch when the Rail has no marks`() {
        val gesture: RailGesture = RailGesture()
        val actualIndex: Int? = gesture.wheelStep(inputStep = 1, markCount = 0)
        assertNull(actualIndex)
    }

    @Test
    fun `ignores a zero wheel delta`() {
        val gesture: RailGesture = RailGesture()
        val actualIndices: List<Int> = gesture.applyWheelDelta(inputDeltaY = 0f)
        assertEquals(emptyList<Int>(), actualIndices)
    }

    @Test
    fun `adds fractional wheel deltas up to one Rail wheel step`() {
        val gesture: RailGesture = RailGesture()
        val actualFirstIndices: List<Int> = gesture.applyWheelDelta(inputDeltaY = 0.5f)
        val actualSecondIndices: List<Int> = gesture.applyWheelDelta(inputDeltaY = 0.5f)
        assertEquals(emptyList<Int>(), actualFirstIndices)
        assertEquals(listOf(1), actualSecondIndices)
    }

    @Test
    fun `keeps the fraction a whole notch leaves`() {
        val gesture: RailGesture = RailGesture()
        gesture.applyWheelDelta(inputDeltaY = 0.75f)
        val actualWholeIndices: List<Int> = gesture.applyWheelDelta(inputDeltaY = 0.75f)
        val actualRestIndices: List<Int> = gesture.applyWheelDelta(inputDeltaY = 0.5f, currentMarkIndex = 1)
        assertEquals(listOf(1), actualWholeIndices)
        assertEquals(listOf(2), actualRestIndices)
    }

    @Test
    fun `emits consecutive Rail wheel steps for a fast wheel delta`() {
        val gesture: RailGesture = RailGesture()
        val actualIndices: List<Int> = gesture.applyWheelDelta(inputDeltaY = 2f)
        assertEquals(listOf(1, 2), actualIndices)
    }

    @Test
    fun `starts consecutive Rail wheel steps from the shown group`() {
        val gesture: RailGesture = RailGesture()
        val actualIndices: List<Int> = gesture.applyWheelDelta(inputDeltaY = 2f, currentMarkIndex = 1, markCount = 4)
        assertEquals(listOf(2, 3), actualIndices)
    }

    @Test
    fun `drops the fraction when the wheel reverses`() {
        val gesture: RailGesture = RailGesture()
        gesture.applyWheelDelta(inputDeltaY = 1f)
        gesture.applyWheelDelta(inputDeltaY = 0.9f, currentMarkIndex = 1)
        val actualIndices: List<Int> = gesture.applyWheelDelta(inputDeltaY = -1f, currentMarkIndex = 1)
        assertEquals(listOf(0), actualIndices)
    }

    @Test
    fun `drops the fraction when the pointer leaves the Rail`() {
        val gesture: RailGesture = RailGesture()
        gesture.applyWheelDelta(inputDeltaY = 0.9f)
        gesture.resetWheelScroll()
        val actualIndices: List<Int> = gesture.applyWheelDelta(inputDeltaY = 0.2f)
        assertEquals(emptyList<Int>(), actualIndices)
    }

    @Test
    fun `clamps consecutive Rail wheel steps at the last group`() {
        val gesture: RailGesture = RailGesture()
        val actualIndices: List<Int> = gesture.applyWheelDelta(inputDeltaY = 5f)
        assertEquals(listOf(1, 2), actualIndices)
    }

    /** The marks a wheel delta of [inputDeltaY] moves the Rail to, over a [markCount]-mark Rail. */
    private fun RailGesture.applyWheelDelta(inputDeltaY: Float, currentMarkIndex: Int = 0, markCount: Int = TEST_MARK_COUNT): List<Int> =
        onWheelScroll(
            deltaY = inputDeltaY,
            wheelStep = RailWheelStep(
                currentMarkIndex = currentMarkIndex,
                markCount = markCount,
                card = popupCard()
            )
        )

    /** A wheel notch over a [markCount]-mark Rail, closing an open card through [onClose]. */
    private fun RailGesture.wheelStep(
        inputStep: Int,
        currentMarkIndex: Int = 0,
        markCount: Int = TEST_MARK_COUNT,
        isOpen: Boolean = false,
        onClose: () -> Unit = {}
    ): Int? = onWheelStep(
        step = inputStep,
        wheelStep = RailWheelStep(
            currentMarkIndex = currentMarkIndex,
            markCount = markCount,
            card = popupCard(isOpen = isOpen, onClose = onClose)
        )
    )

    private fun popupCard(isOpen: Boolean = false, onClose: () -> Unit = {}): RailPopupCard =
        RailPopupCard(isOpen = isOpen, close = onClose)

    private companion object {
        const val TEST_MARK_COUNT: Int = 3
    }
}
