package core.data.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAreaSettingsTest {
    @Test
    fun `defaults to four pages, a visible indicator, and a two thirds cap`() {
        val inputSettings: WidgetAreaSettings = WidgetAreaSettings()

        assertEquals(4, inputSettings.maxStackPages)
        assertTrue(inputSettings.stackIndicatorVisible)
        assertTrue(inputSettings.heightLimitEnabled)
        assertEquals(WidgetAreaHeightLimit.TWO_THIRDS, inputSettings.heightLimit)
    }

    @Test
    fun `exposes the cap as a Home height fraction`() {
        assertEquals(0.5f, WidgetAreaHeightLimit.HALF.fraction)
        assertEquals(2f / 3f, WidgetAreaHeightLimit.TWO_THIRDS.fraction)
        assertEquals(0.75f, WidgetAreaHeightLimit.THREE_QUARTERS.fraction)
    }

    @Test
    fun `round trips the height limit through a settings record`() {
        val inputSettings: WidgetAreaSettings = WidgetAreaSettings(
            heightLimitEnabled = false,
            heightLimit = WidgetAreaHeightLimit.THREE_QUARTERS
        )

        val actualRecord: WidgetAreaSettingsRecord = WidgetAreaSettingsRecord.from(inputSettings)

        assertEquals(inputSettings, actualRecord.toDomain())
        assertEquals("THREE_QUARTERS", actualRecord.heightLimit.name)
    }

    @Test
    fun `falls back to two thirds when reading an unknown stored limit`() {
        assertEquals(WidgetAreaHeightLimit.TWO_THIRDS, WidgetAreaHeightLimit.fromName("NOPE"))
        assertEquals(WidgetAreaHeightLimit.TWO_THIRDS, WidgetAreaHeightLimit.fromName(null))
    }

    @Test
    fun `rejects an out of range maximum`() {
        assertThrows(IllegalArgumentException::class.java) { WidgetAreaSettings(maxStackPages = 1) }
        assertThrows(IllegalArgumentException::class.java) { WidgetAreaSettings(maxStackPages = 9) }
    }

    @Test
    fun `coerces a corrupt stored range on read`() {
        assertEquals(8, WidgetAreaSettingsRecord(maxStackPages = 12).toDomain().maxStackPages)
        assertEquals(2, WidgetAreaSettingsRecord(maxStackPages = 0).toDomain().maxStackPages)
    }

    @Test
    fun `defaults to no offset and default widget spacing`() {
        val inputSettings: WidgetAreaSettings = WidgetAreaSettings()

        assertEquals(0, inputSettings.topOffsetDp)
        assertEquals(WidgetSpacing.DEFAULT, inputSettings.widgetSpacing)
    }

    @Test
    fun `round trips the offset and spacing through a settings record`() {
        val inputSettings: WidgetAreaSettings = WidgetAreaSettings(
            topOffsetDp = 24,
            widgetSpacing = WidgetSpacing.EXTRA_LARGE
        )

        val actualRecord: WidgetAreaSettingsRecord = WidgetAreaSettingsRecord.from(inputSettings)

        assertEquals(inputSettings, actualRecord.toDomain())
        assertEquals(24, actualRecord.topOffsetDp)
        assertEquals(32, actualRecord.widgetSpacing.dp)
    }

    @Test
    fun `coerces a corrupt stored offset and falls back for unknown spacing`() {
        assertEquals(0, WidgetAreaSettingsRecord(topOffsetDp = -8).toDomain().topOffsetDp)
        assertEquals(16, WidgetAreaSettingsRecord(topOffsetDp = 13).toDomain().topOffsetDp)
        assertEquals(WidgetSpacing.DEFAULT, WidgetSpacing.fromDp(12))
        assertEquals(WidgetSpacing.LARGE, WidgetSpacing.fromDp(24))
    }

    @Test
    fun `rejects an offset off the step grid`() {
        assertThrows(IllegalArgumentException::class.java) { WidgetAreaSettings(topOffsetDp = 4) }
        assertThrows(IllegalArgumentException::class.java) { WidgetAreaSettings(topOffsetDp = -8) }
    }
}
