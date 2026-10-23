package core.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderSettingsTest {
    @Test
    fun `allows hiding the clock while the battery is always visible`() {
        val inputSettings: HeaderSettings = HeaderSettings(
            clockVisible = true,
            dateVisible = true,
            batteryVisibility = BatteryVisibility.ALWAYS
        )

        val actualUpdated: HeaderSettings = inputSettings.withClockVisible(visible = false)

        assertFalse(actualUpdated.clockVisible)
        assertEquals(BatteryVisibility.ALWAYS, actualUpdated.batteryVisibility)
    }

    @Test
    fun `rejects hiding the clock when it is the only visible part`() {
        val inputSettings: HeaderSettings = HeaderSettings(
            clockVisible = true,
            dateVisible = false,
            batteryVisibility = BatteryVisibility.CHARGING_OR_LOW
        )

        assertEquals(inputSettings, inputSettings.withClockVisible(visible = false))
    }

    @Test
    fun `rejects hiding the last visible Header part`() {
        val inputSettings: HeaderSettings = HeaderSettings(
            clockVisible = false,
            dateVisible = false,
            batteryVisibility = BatteryVisibility.ALWAYS
        )
        val inputHiddenVisibilities: List<BatteryVisibility> = BatteryVisibility.entries
            .filter { visibility -> visibility != BatteryVisibility.ALWAYS }

        inputHiddenVisibilities.forEach { hiddenVisibility ->
            val actualSettings: HeaderSettings = inputSettings.withBatteryVisibility(hiddenVisibility)

            assertEquals(BatteryVisibility.ALWAYS, actualSettings.batteryVisibility)
            assertFalse(actualSettings.clockVisible)
            assertFalse(actualSettings.dateVisible)
        }
    }

    @Test
    fun `never re-enables a hidden clock when the battery visibility changes`() {
        val inputBaseSettings: HeaderSettings = HeaderSettings(
            clockVisible = false,
            dateVisible = true,
            batteryVisibility = BatteryVisibility.ALWAYS
        )
        val inputTransitions: List<Pair<BatteryVisibility, BatteryVisibility>> = listOf(
            BatteryVisibility.ALWAYS to BatteryVisibility.NEVER,
            BatteryVisibility.NEVER to BatteryVisibility.ALWAYS,
            BatteryVisibility.CHARGING_OR_LOW to BatteryVisibility.ALWAYS
        )

        inputTransitions.forEach { (inputVisibility, expectedVisibility) ->
            val inputSettings: HeaderSettings = inputBaseSettings.copy(batteryVisibility = inputVisibility)
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

    @Test
    fun `reports a Tap action as stale only when its Launchable key is missing`() {
        val inputValidKeys: Set<String> = setOf("rail://maps")
        val inputMissingSetting: HeaderTapSetting? = null
        assertFalse(HeaderTapSetting.Launchable(key = "rail://maps").isStale(validKeys = inputValidKeys))
        assertTrue(HeaderTapSetting.Launchable(key = "rail://uninstalled").isStale(validKeys = inputValidKeys))
        assertFalse(HeaderTapSetting.Nothing.isStale(validKeys = inputValidKeys))
        assertFalse(HeaderTapSetting.SystemDefault.isStale(validKeys = inputValidKeys))
        assertFalse(inputMissingSetting.isStale(validKeys = inputValidKeys))
    }
}
