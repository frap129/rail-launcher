package feature.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RailGestureTest {
    @Test
    fun `closes an open card and suppresses its scroll`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0

        val actualAction: RailIndexAction = gesture.onScroll(index = 0, card = popupCard(isOpen = true, onClose = { actualCloseCount++ }))

        assertEquals(RailIndexAction.Ignore, actualAction)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `suppresses the scrolls after the card closed`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        gesture.onScroll(index = 0, card = popupCard(isOpen = true, onClose = { actualCloseCount++ }))

        val actualAction: RailIndexAction = gesture.onScroll(index = 1, card = popupCard(onClose = { actualCloseCount++ }))

        assertEquals(RailIndexAction.Ignore, actualAction)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `closes a card once for its whole gesture`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        gesture.onScroll(index = 0, card = popupCard(isOpen = true, onClose = { actualCloseCount++ }))

        val actualAction: RailIndexAction = gesture.onScroll(index = 1, card = popupCard(isOpen = true, onClose = { actualCloseCount++ }))

        assertEquals(RailIndexAction.Ignore, actualAction)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `suppresses the scroll-ended callback of the gesture`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        gesture.onScroll(index = 0, card = popupCard(isOpen = true, onClose = { actualCloseCount++ }))

        val actualAction: RailIndexAction = gesture.onScrollEnded(index = 1, card = popupCard(onClose = { actualCloseCount++ }))

        assertEquals(RailIndexAction.Ignore, actualAction)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `re-arms the next gesture on scroll-ended`() {
        val gesture: RailGesture = RailGesture()
        gesture.onScroll(index = 0, card = popupCard(isOpen = true))
        gesture.onScrollEnded(index = 0, card = popupCard())

        val actualAction: RailIndexAction = gesture.onScroll(index = 0, card = popupCard())

        assertEquals(RailIndexAction.Act, actualAction)
    }

    @Test
    fun `previews a scroll when no card is open`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0

        val actualAction: RailIndexAction = gesture.onScroll(index = 0, card = popupCard(onClose = { actualCloseCount++ }))

        assertEquals(RailIndexAction.Act, actualAction)
        assertEquals(0, actualCloseCount)
    }

    @Test
    fun `releases a scroll when no card is open`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0

        val actualAction: RailIndexAction = gesture.onScrollEnded(index = 0, card = popupCard(onClose = { actualCloseCount++ }))

        assertEquals(RailIndexAction.Act, actualAction)
        assertEquals(0, actualCloseCount)
    }

    @Test
    fun `re-arms a gesture restarted mid-touch`() {
        val gesture: RailGesture = RailGesture()
        gesture.onScroll(index = 0, card = popupCard(isOpen = true))
        gesture.reset()

        val actualAction: RailIndexAction = gesture.onScroll(index = 0, card = popupCard())

        assertEquals(RailIndexAction.Act, actualAction)
    }

    @Test
    fun `a downward wheel scroll steps toward the last group`() {
        val actualDirection: RailWheelDirection = RailWheelDirection.fromScrollDelta(deltaY = 1f)

        assertEquals(RailWheelDirection.Next, actualDirection)
    }

    @Test
    fun `an upward wheel scroll steps toward the first group`() {
        val actualDirection: RailWheelDirection = RailWheelDirection.fromScrollDelta(deltaY = -1f)

        assertEquals(RailWheelDirection.Previous, actualDirection)
    }

    @Test
    fun `steps to the next group on a wheel notch`() {
        val gesture: RailGesture = RailGesture()

        val actualIndex: Int? = gesture.wheelStep(direction = RailWheelDirection.Next)

        assertEquals(1, actualIndex)
    }

    @Test
    fun `steps one group per wheel notch`() {
        val gesture: RailGesture = RailGesture()
        gesture.wheelStep(direction = RailWheelDirection.Next)

        val actualIndex: Int? = gesture.wheelStep(direction = RailWheelDirection.Next)

        assertEquals(2, actualIndex)
    }

    @Test
    fun `steps to the previous group on a wheel notch`() {
        val gesture: RailGesture = RailGesture()
        gesture.wheelStep(direction = RailWheelDirection.Next)

        val actualIndex: Int? = gesture.wheelStep(direction = RailWheelDirection.Previous)

        assertEquals(0, actualIndex)
    }

    @Test
    fun `stops at the last group on a wheel notch`() {
        val gesture: RailGesture = RailGesture()
        gesture.wheelStep(direction = RailWheelDirection.Next)
        gesture.wheelStep(direction = RailWheelDirection.Next)

        val actualIndex: Int? = gesture.wheelStep(direction = RailWheelDirection.Next)

        assertNull(actualIndex)
    }

    @Test
    fun `stops at the first group on a wheel notch`() {
        val gesture: RailGesture = RailGesture()

        val actualIndex: Int? = gesture.wheelStep(direction = RailWheelDirection.Previous)

        assertNull(actualIndex)
    }

    @Test
    fun `steps from the last group the Rail showed`() {
        val gesture: RailGesture = RailGesture()
        gesture.onScroll(index = 2, card = popupCard())
        gesture.onScrollEnded(index = 2, card = popupCard())

        val actualIndex: Int? = gesture.wheelStep(direction = RailWheelDirection.Previous)

        assertEquals(1, actualIndex)
    }

    @Test
    fun `closes an open card on a wheel notch`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0

        val actualIndex: Int? = gesture.wheelStep(
            direction = RailWheelDirection.Next,
            isOpen = true,
            onClose = { actualCloseCount++ }
        )

        assertNull(actualIndex)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `steps on the wheel notch after closing a card`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        gesture.wheelStep(direction = RailWheelDirection.Next, isOpen = true, onClose = { actualCloseCount++ })

        val actualIndex: Int? = gesture.wheelStep(direction = RailWheelDirection.Next)

        assertEquals(1, actualIndex)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `ignores a wheel notch when the Rail has no marks`() {
        val gesture: RailGesture = RailGesture()

        val actualIndex: Int? = gesture.wheelStep(direction = RailWheelDirection.Next, markCount = 0)

        assertNull(actualIndex)
    }

    @Test
    fun `adds fractional wheel scrolls up to one notch`() {
        val gesture: RailGesture = RailGesture()

        val actualFirstIndices: List<Int> = gesture.wheelScroll(deltaY = 0.5f)
        val actualSecondIndices: List<Int> = gesture.wheelScroll(deltaY = 0.5f)

        assertEquals(emptyList<Int>(), actualFirstIndices)
        assertEquals(listOf(1), actualSecondIndices)
    }

    @Test
    fun `keeps the fraction a whole notch leaves`() {
        val gesture: RailGesture = RailGesture()
        gesture.wheelScroll(deltaY = 0.75f)

        val actualWholeIndices: List<Int> = gesture.wheelScroll(deltaY = 0.75f)
        val actualRestIndices: List<Int> = gesture.wheelScroll(deltaY = 0.5f)

        assertEquals(listOf(1), actualWholeIndices)
        assertEquals(listOf(2), actualRestIndices)
    }

    @Test
    fun `emits a step for every whole notch of a fast wheel scroll`() {
        val gesture: RailGesture = RailGesture()

        val actualIndices: List<Int> = gesture.wheelScroll(deltaY = 2f)

        assertEquals(listOf(1, 2), actualIndices)
    }

    @Test
    fun `drops the fraction when the wheel reverses`() {
        val gesture: RailGesture = RailGesture()
        gesture.wheelScroll(deltaY = 1f)
        gesture.wheelScroll(deltaY = 0.9f)

        val actualIndices: List<Int> = gesture.wheelScroll(deltaY = -1f)

        assertEquals(listOf(0), actualIndices)
    }

    @Test
    fun `drops the fraction when the pointer leaves the Rail`() {
        val gesture: RailGesture = RailGesture()
        gesture.wheelScroll(deltaY = 0.9f)
        gesture.resetWheelScroll()

        val actualIndices: List<Int> = gesture.wheelScroll(deltaY = 0.2f)

        assertEquals(emptyList<Int>(), actualIndices)
    }

    @Test
    fun `clamps a fast wheel scroll at the last group`() {
        val gesture: RailGesture = RailGesture()

        val actualIndices: List<Int> = gesture.wheelScroll(deltaY = 5f)

        assertEquals(listOf(1, 2), actualIndices)
    }

    /** The marks a wheel scroll of [deltaY] moves the Rail to, over a [markCount]-mark Rail. */
    private fun RailGesture.wheelScroll(deltaY: Float, markCount: Int = TEST_MARK_COUNT): List<Int> =
        onWheelScroll(deltaY = deltaY, markCount = markCount, card = popupCard())

    /** A wheel notch over a [markCount]-mark Rail, closing an open card through [onClose]. */
    private fun RailGesture.wheelStep(
        direction: RailWheelDirection,
        markCount: Int = TEST_MARK_COUNT,
        isOpen: Boolean = false,
        onClose: () -> Unit = {}
    ): Int? = onWheelStep(
        direction = direction,
        markCount = markCount,
        card = popupCard(isOpen = isOpen, onClose = onClose)
    )

    private fun popupCard(isOpen: Boolean = false, onClose: () -> Unit = {}): RailPopupCard =
        RailPopupCard(isOpen = isOpen, close = onClose)

    private companion object {
        const val TEST_MARK_COUNT: Int = 3
    }
}
