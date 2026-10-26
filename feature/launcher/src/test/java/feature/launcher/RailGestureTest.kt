package feature.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class RailGestureTest {
    @Test
    fun `closes an open card and suppresses its scroll`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0

        val actualAction: RailIndexAction = gesture.onScroll(isCardOpen = true, closeCard = { actualCloseCount++ })

        assertEquals(RailIndexAction.Ignore, actualAction)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `suppresses the scrolls after the card closed`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        gesture.onScroll(isCardOpen = true, closeCard = { actualCloseCount++ })

        val actualAction: RailIndexAction = gesture.onScroll(isCardOpen = false, closeCard = { actualCloseCount++ })

        assertEquals(RailIndexAction.Ignore, actualAction)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `closes a card once for its whole gesture`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        gesture.onScroll(isCardOpen = true, closeCard = { actualCloseCount++ })

        val actualAction: RailIndexAction = gesture.onScroll(isCardOpen = true, closeCard = { actualCloseCount++ })

        assertEquals(RailIndexAction.Ignore, actualAction)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `suppresses the scroll-ended callback of the gesture`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0
        gesture.onScroll(isCardOpen = true, closeCard = { actualCloseCount++ })

        val actualAction: RailIndexAction = gesture.onScrollEnded(isCardOpen = false, closeCard = { actualCloseCount++ })

        assertEquals(RailIndexAction.Ignore, actualAction)
        assertEquals(1, actualCloseCount)
    }

    @Test
    fun `re-arms the next gesture on scroll-ended`() {
        val gesture: RailGesture = RailGesture()
        gesture.onScroll(isCardOpen = true, closeCard = {})
        gesture.onScrollEnded(isCardOpen = false, closeCard = {})

        val actualAction: RailIndexAction = gesture.onScroll(isCardOpen = false, closeCard = {})

        assertEquals(RailIndexAction.Act, actualAction)
    }

    @Test
    fun `previews a scroll when no card is open`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0

        val actualAction: RailIndexAction = gesture.onScroll(isCardOpen = false, closeCard = { actualCloseCount++ })

        assertEquals(RailIndexAction.Act, actualAction)
        assertEquals(0, actualCloseCount)
    }

    @Test
    fun `releases a scroll when no card is open`() {
        val gesture: RailGesture = RailGesture()
        var actualCloseCount: Int = 0

        val actualAction: RailIndexAction = gesture.onScrollEnded(isCardOpen = false, closeCard = { actualCloseCount++ })

        assertEquals(RailIndexAction.Act, actualAction)
        assertEquals(0, actualCloseCount)
    }

    @Test
    fun `re-arms a gesture restarted mid-touch`() {
        val gesture: RailGesture = RailGesture()
        gesture.onScroll(isCardOpen = true, closeCard = {})
        gesture.reset()

        val actualAction: RailIndexAction = gesture.onScroll(isCardOpen = false, closeCard = {})

        assertEquals(RailIndexAction.Act, actualAction)
    }
}
