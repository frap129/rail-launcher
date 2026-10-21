package core.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderSettingsTest {
    @Test
    fun `allows hiding the clock while the battery is always visible`() {
        val settings: HeaderSettings = HeaderSettings(
            clockVisible = true,
            dateVisible = true,
            batteryVisibility = BatteryVisibility.ALWAYS
        )

        val updated: HeaderSettings = settings.withClockVisible(visible = false)

        assertFalse(updated.clockVisible)
        assertEquals(BatteryVisibility.ALWAYS, updated.batteryVisibility)
    }

    @Test
    fun `rejects hiding the clock when it is the only visible part`() {
        val settings: HeaderSettings = HeaderSettings(
            clockVisible = true,
            dateVisible = false,
            batteryVisibility = BatteryVisibility.CHARGING_OR_LOW
        )

        assertEquals(settings, settings.withClockVisible(visible = false))
    }

    @Test
    fun `restores the clock when the battery stops being a standing part`() {
        val batteryOnly: HeaderSettings = HeaderSettings(
            clockVisible = false,
            dateVisible = false,
            batteryVisibility = BatteryVisibility.ALWAYS
        )

        val updated: HeaderSettings = batteryOnly.withBatteryVisibility(BatteryVisibility.NEVER)

        assertTrue(updated.clockVisible)
        assertFalse(updated.dateVisible)
        assertEquals(BatteryVisibility.NEVER, updated.batteryVisibility)
    }

    @Test
    fun `keeps a battery-only Header while the battery stays always visible`() {
        val batteryOnly: HeaderSettings = HeaderSettings(
            clockVisible = false,
            dateVisible = false,
            batteryVisibility = BatteryVisibility.NEVER
        )

        val updated: HeaderSettings = batteryOnly.withBatteryVisibility(BatteryVisibility.ALWAYS)

        assertFalse(updated.clockVisible)
        assertFalse(updated.dateVisible)
        assertEquals(BatteryVisibility.ALWAYS, updated.batteryVisibility)
    }
}
