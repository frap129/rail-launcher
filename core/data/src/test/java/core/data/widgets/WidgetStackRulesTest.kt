package core.data.widgets

import core.data.testing.appWidgetPlace
import core.data.testing.headerPlace
import core.data.testing.popupStackPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStackRulesTest {
    @Test
    fun `a place can grow its stack while below the cap`() {
        val inputSinglePage: WidgetPlace = popupStackPlace(placeId = 1L, widgetIds = listOf(1L), appWidgetIds = listOf(9))
        val inputFullStack: WidgetPlace = popupStackPlace(placeId = 1L, widgetIds = listOf(1L, 2L), appWidgetIds = listOf(9, 10))

        assertTrue(inputSinglePage.canGrowStack(maxStackPages = 4))
        assertFalse(inputFullStack.canGrowStack(maxStackPages = 2))
    }

    @Test
    fun `a merge can grow a stack by several pages`() {
        assertTrue(canGrowStack(pageCount = 1, maxStackPages = 4, additionalPages = 2))
        assertFalse(canGrowStack(pageCount = 3, maxStackPages = 4, additionalPages = 2))
    }

    @Test
    fun `stack default height is the tallest page default`() {
        val inputWidgets: List<PlacedWidget> = listOf(
            appWidgetPlace(placeId = 1L, widgetId = 1L, defaultHeightDp = 80).widgets.single(),
            appWidgetPlace(placeId = 1L, widgetId = 2L, defaultHeightDp = 120).widgets.single()
        )

        assertEquals(120, inputWidgets.stackDefaultHeightDp { null })
    }

    @Test
    fun `single page default height is that page's default`() {
        val inputWidgets: List<PlacedWidget> = appWidgetPlace(placeId = 1L, widgetId = 1L, defaultHeightDp = 80).widgets

        assertEquals(80, inputWidgets.stackDefaultHeightDp { null })
    }

    @Test
    fun `stack default height is null without a page default`() {
        val inputWidgets: List<PlacedWidget> = appWidgetPlace(placeId = 1L, widgetId = 1L).widgets

        assertNull(inputWidgets.stackDefaultHeightDp { null })
    }

    @Test
    fun `the stack indicator shows only for a stack with the setting on`() {
        val inputStack: List<PlacedWidget> = popupStackPlace(placeId = 1L, widgetIds = listOf(1L, 2L), appWidgetIds = listOf(9, 10)).widgets
        val inputSinglePage: List<PlacedWidget> = appWidgetPlace(placeId = 1L, widgetId = 1L).widgets

        assertTrue(inputStack.stackIndicatorVisible(settingVisible = true))
        assertFalse(inputStack.stackIndicatorVisible(settingVisible = false))
        assertFalse(inputSinglePage.stackIndicatorVisible(settingVisible = true))
    }

    @Test
    fun `stack bounds intersect the pages`() {
        val inputWidgets: List<PlacedWidget> = listOf(
            appWidgetPlace(placeId = 1L, widgetId = 1L, defaultHeightDp = 80).widgets.single(),
            appWidgetPlace(placeId = 1L, widgetId = 2L, defaultHeightDp = 120).widgets.single()
        )

        val actualBounds: ResizeBounds = inputWidgets.stackResizeBounds { null }!!

        assertEquals(120, actualBounds.minDp)
        assertNull(actualBounds.maxDp)
    }

    @Test
    fun `single page bounds come from that page`() {
        val inputWidgets: List<PlacedWidget> = appWidgetPlace(placeId = 1L, widgetId = 1L, defaultHeightDp = 80).widgets

        val actualBounds: ResizeBounds = inputWidgets.stackResizeBounds { null }!!

        assertEquals(80, actualBounds.minDp)
    }

    @Test
    fun `a place resizes only when every page is available and its bounds are resizable`() {
        val inputBounds: ResizeBounds = ResizeBounds(minDp = 48, maxDp = 200)
        val inputPages: List<PlacedWidget> = popupStackPlace(placeId = 1L, widgetIds = listOf(1L, 2L), appWidgetIds = listOf(9, 10)).widgets
        val inputPausedPages: List<PlacedWidget> = inputPages.map { widget ->
            (widget as PlacedWidget.AppWidget).copy(availability = WidgetAvailability.ProfilePaused)
        }

        assertTrue(inputPages.canResizeStack(bounds = inputBounds))
        assertFalse(inputPausedPages.canResizeStack(bounds = inputBounds))
        assertFalse(inputPages.canResizeStack(bounds = ResizeBounds(minDp = 48, maxDp = 48, isResizable = false)))
        assertFalse(headerPlace.widgets.canResizeStack(bounds = inputBounds))
    }
}
