package core.data.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAreaSettingsTest {
    @Test
    fun `defaults to four pages and a visible indicator`() {
        val settings: WidgetAreaSettings = WidgetAreaSettings()

        assertEquals(4, settings.maxStackPages)
        assertTrue(settings.stackIndicatorVisible)
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
