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
    fun `rejects hiding the last visible Header part`() {
        val inputSettings: HeaderSettings = HeaderSettings(
            clockVisible = false,
            dateVisible = false,
            batteryVisibility = BatteryVisibility.ALWAYS
        )
        val hiddenVisibilities: List<BatteryVisibility> = BatteryVisibility.entries
            .filter { visibility -> visibility != BatteryVisibility.ALWAYS }

        hiddenVisibilities.forEach { hiddenVisibility ->
            val actualSettings: HeaderSettings = inputSettings.withBatteryVisibility(hiddenVisibility)

            assertEquals(BatteryVisibility.ALWAYS, actualSettings.batteryVisibility)
            assertFalse(actualSettings.clockVisible)
            assertFalse(actualSettings.dateVisible)
        }
    }

    @Test
    fun `never re-enables a hidden clock when the battery visibility changes`() {
        val baseSettings: HeaderSettings = HeaderSettings(
            clockVisible = false,
            dateVisible = true,
            batteryVisibility = BatteryVisibility.ALWAYS
        )
        val transitions: List<Pair<BatteryVisibility, BatteryVisibility>> = listOf(
            BatteryVisibility.ALWAYS to BatteryVisibility.NEVER,
            BatteryVisibility.NEVER to BatteryVisibility.ALWAYS,
            BatteryVisibility.CHARGING_OR_LOW to BatteryVisibility.ALWAYS
        )

        transitions.forEach { (inputVisibility, expectedVisibility) ->
            val inputSettings: HeaderSettings = baseSettings.copy(batteryVisibility = inputVisibility)
            val actualSettings: HeaderSettings = inputSettings.withBatteryVisibility(expectedVisibility)

            assertFalse(actualSettings.clockVisible)
            assertEquals(expectedVisibility, actualSettings.batteryVisibility)
        }
    }

    @Test
    fun `allows a conditional battery only while another part is visible`() {
        assertTrue(canSetBatteryVisibility(BatteryVisibility.CHARGING_OR_LOW, clockVisible = false, dateVisible = true))
        assertFalse(canSetBatteryVisibility(BatteryVisibility.CHARGING_OR_LOW, clockVisible = false, dateVisible = false))
        assertTrue(canSetBatteryVisibility(BatteryVisibility.ALWAYS, clockVisible = false, dateVisible = false))
    }
}
