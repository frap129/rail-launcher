package feature.widgets

import core.data.icons.model.Icon
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
        val bounds: ResizeBounds = ResizeBounds.from(info(minHeightDp = 20, minResizeHeightDp = 0))

        assertEquals(48, bounds.minDp)
        assertEquals(null, bounds.maxDp)
        assertTrue(bounds.isResizable)
    }

    @Test
    fun `prefers a declared minimum resize height`() {
        val bounds: ResizeBounds = ResizeBounds.from(info(minHeightDp = 48, minResizeHeightDp = 72))

        assertEquals(72, bounds.minDp)
    }

    @Test
    fun `caps an unbounded maximum at the Home height`() {
        val bounds: ResizeBounds = ResizeBounds(minDp = 48, maxDp = null)

        assertEquals(400, bounds.clamp(heightDp = 500, homeHeightDp = 400))
        assertEquals(300, bounds.clamp(heightDp = 300, homeHeightDp = 400))
    }

    @Test
    fun `uses a declared maximum before the Home height`() {
        val bounds: ResizeBounds = ResizeBounds(minDp = 48, maxDp = 200)

        assertEquals(200, bounds.clamp(heightDp = 500, homeHeightDp = 400))
        assertEquals(48, bounds.clamp(heightDp = 20, homeHeightDp = 400))
    }

    @Test
    fun `snaps heights to the 8dp grid`() {
        val bounds: ResizeBounds = ResizeBounds(minDp = 48, maxDp = null)

        assertEquals(48, bounds.snapToStepGrid(heightDp = 49))
        assertEquals(56, bounds.snapToStepGrid(heightDp = 52))
        assertEquals(104, bounds.snapToStepGrid(heightDp = 100))
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
