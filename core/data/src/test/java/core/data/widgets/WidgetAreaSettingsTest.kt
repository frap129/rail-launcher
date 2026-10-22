package core.data.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAreaSettingsTest {
    @Test
    fun `defaults to four pages, a visible indicator, and a two thirds cap`() {
        val settings: WidgetAreaSettings = WidgetAreaSettings()

        assertEquals(4, settings.maxStackPages)
        assertTrue(settings.stackIndicatorVisible)
        assertTrue(settings.heightLimitEnabled)
        assertEquals(WidgetAreaHeightLimit.TWO_THIRDS, settings.heightLimit)
    }

    @Test
    fun `exposes the cap as a Home height fraction`() {
        assertEquals(0.5f, WidgetAreaHeightLimit.HALF.fraction)
        assertEquals(2f / 3f, WidgetAreaHeightLimit.TWO_THIRDS.fraction)
        assertEquals(0.75f, WidgetAreaHeightLimit.THREE_QUARTERS.fraction)
    }

    @Test
    fun `round trips the height limit through a settings record`() {
        val settings: WidgetAreaSettings = WidgetAreaSettings(
            heightLimitEnabled = false,
            heightLimit = WidgetAreaHeightLimit.THREE_QUARTERS
        )

        val record: WidgetAreaSettingsRecord = WidgetAreaSettingsRecord.from(settings)

        assertEquals(settings, record.toDomain())
        assertEquals("THREE_QUARTERS", record.heightLimit.name)
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
}
