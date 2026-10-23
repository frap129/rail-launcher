package core.data.widgets

import core.data.icons.model.Icon
import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResizeBoundsTest {
    @Test
    fun `floors the minimum height at 48dp`() {
        val actualBounds: ResizeBounds = ResizeBounds.from(info(minHeightDp = 20, minResizeHeightDp = 0))

        assertEquals(48, actualBounds.minDp)
        assertEquals(null, actualBounds.maxDp)
        assertTrue(actualBounds.isResizable)
    }

    @Test
    fun `prefers a declared minimum resize height`() {
        val actualBounds: ResizeBounds = ResizeBounds.from(info(minHeightDp = 48, minResizeHeightDp = 72))

        assertEquals(72, actualBounds.minDp)
    }

    @Test
    fun `caps an unbounded maximum at the Home height`() {
        val actualBounds: ResizeBounds = ResizeBounds(minDp = 48, maxDp = null)

        assertEquals(400, actualBounds.clamp(heightDp = 500, homeHeightDp = 400))
        assertEquals(300, actualBounds.clamp(heightDp = 300, homeHeightDp = 400))
    }

    @Test
    fun `uses a declared maximum before the Home height`() {
        val actualBounds: ResizeBounds = ResizeBounds(minDp = 48, maxDp = 200)

        assertEquals(200, actualBounds.clamp(heightDp = 500, homeHeightDp = 400))
        assertEquals(48, actualBounds.clamp(heightDp = 20, homeHeightDp = 400))
    }

    @Test
    fun `snaps heights to the 8dp grid`() {
        val actualBounds: ResizeBounds = ResizeBounds(minDp = 48, maxDp = null)

        assertEquals(48, actualBounds.snapToStepGrid(heightDp = 49))
        assertEquals(56, actualBounds.snapToStepGrid(heightDp = 52))
        assertEquals(104, actualBounds.snapToStepGrid(heightDp = 100))
    }

    @Test
    fun `rejects a widget without vertical resizing`() {
        assertFalse(ResizeBounds.from(info(isVerticallyResizable = false)).isResizable)
    }

    @Test
    fun `rejects a range whose maximum is not taller than its minimum`() {
        assertFalse(ResizeBounds.from(info(minResizeHeightDp = 72, maxResizeHeightDp = 72)).isResizable)
        assertFalse(ResizeBounds.from(info(minResizeHeightDp = 72, maxResizeHeightDp = 64)).isResizable)
    }

    @Test
    fun `stack bounds intersect the pages' ranges`() {
        val actualBounds: ResizeBounds = ResizeBounds.stackBounds(
            listOf(ResizeBounds(minDp = 48, maxDp = 200), ResizeBounds(minDp = 80, maxDp = 160))
        )

        assertEquals(80, actualBounds.minDp)
        assertEquals(160, actualBounds.maxDp)
        assertTrue(actualBounds.isResizable)
    }

    @Test
    fun `stack bounds ignore unbounded pages`() {
        val actualBounds: ResizeBounds = ResizeBounds.stackBounds(
            listOf(ResizeBounds(minDp = 48, maxDp = null), ResizeBounds(minDp = 72, maxDp = 120))
        )

        assertEquals(72, actualBounds.minDp)
        assertEquals(120, actualBounds.maxDp)
    }

    @Test
    fun `stack bounds with an empty intersection are not resizable at the highest minimum`() {
        val actualBounds: ResizeBounds = ResizeBounds.stackBounds(
            listOf(ResizeBounds(minDp = 48, maxDp = 100), ResizeBounds(minDp = 120, maxDp = 200))
        )

        assertEquals(120, actualBounds.minDp)
        assertEquals(120, actualBounds.maxDp)
        assertFalse(actualBounds.isResizable)
    }

    @Test
    fun `stack bounds include a header page's measured minimum`() {
        val actualBounds: ResizeBounds = ResizeBounds.stackBounds(
            listOf(ResizeBounds(minDp = 96, maxDp = null, isResizable = false), ResizeBounds(minDp = 48, maxDp = 160))
        )

        assertEquals(96, actualBounds.minDp)
        assertEquals(160, actualBounds.maxDp)
    }

    private fun info(
        minHeightDp: Int = 48,
        minResizeHeightDp: Int = 0,
        maxResizeHeightDp: Int? = null,
        isVerticallyResizable: Boolean = true
    ): WidgetProviderInfo = WidgetProviderInfo(
        provider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
        profileSerial = 0L,
        appPackageName = "com.example",
        appLabel = "Example",
        appIcon = Icon.ApplicationIcon(packageName = "com.example"),
        label = "Example Widget",
        description = null,
        defaultSize = WidgetSize(widthDp = 110, heightDp = minHeightDp),
        cellSize = null,
        minHeightDp = minHeightDp,
        minResizeHeightDp = minResizeHeightDp,
        maxResizeHeightDp = maxResizeHeightDp,
        needsConfiguration = false,
        isReconfigurable = false,
        hasConfigureActivity = false,
        isWorkProfile = false,
        isVerticallyResizable = isVerticallyResizable
    )
}
