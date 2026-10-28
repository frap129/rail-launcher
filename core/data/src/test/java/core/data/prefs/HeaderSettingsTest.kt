package core.data.prefs

import core.data.folders.Folder
import core.data.profiles.ProfileToken
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createRailLaunchable
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
    fun `reports a Tap action as removable only when its Launchable is missing`() {
        val inputState = createLoadedLaunchableState(launchables = listOf(createRailLaunchable(name = "Maps", deeplink = "rail://maps")))
        val inputMissingSetting: HeaderTapSetting? = null
        assertFalse(HeaderTapSetting.Launchable(key = "rail://maps").isRemovable(state = inputState))
        assertTrue(HeaderTapSetting.Launchable(key = "rail://uninstalled").isRemovable(state = inputState))
        assertFalse(HeaderTapSetting.Nothing.isRemovable(state = inputState))
        assertFalse(HeaderTapSetting.SystemDefault.isRemovable(state = inputState))
        assertFalse(inputMissingSetting.isRemovable(state = inputState))
    }

    @Test
    fun `reports a Tap action as displayable only when its Launchable is present`() {
        val inputState = createLoadedLaunchableState(launchables = listOf(createRailLaunchable(name = "Maps", deeplink = "rail://maps")))
        val inputFolder: Folder = Folder(folderId = 1L)
        val inputFolderState = createLaunchableState(groups = emptyList(), folders = listOf(inputFolder))
        val inputLoadingState = createLaunchableState(groups = emptyList(), isLoaded = false)
        assertTrue(HeaderTapSetting.Launchable(key = "rail://maps").isDisplayable(state = inputState))
        assertFalse(HeaderTapSetting.Launchable(key = "rail://uninstalled").isDisplayable(state = inputState))
        assertFalse(HeaderTapSetting.Launchable(key = inputFolder.key).isDisplayable(state = inputFolderState))
        assertTrue(HeaderTapSetting.Nothing.isDisplayable(state = inputState))
        assertTrue(HeaderTapSetting.SystemDefault.isDisplayable(state = inputState))
        assertTrue(HeaderTapSetting.Launchable(key = "rail://uninstalled").isDisplayable(state = inputLoadingState))
    }

    @Test
    fun `keeps a Tap action pointing into a Locked Profile`() {
        val inputSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = "com.example.secret_UserHandle{11}")
        val inputLockedState = createLaunchableState(
            groups = emptyList(),
            keptKeys = setOf(ProfileToken("UserHandle{11}"))
        )

        assertFalse(inputSetting.isDisplayable(state = inputLockedState))
        assertFalse(inputSetting.isRemovable(state = inputLockedState))
        assertTrue(inputSetting.isRemovable(state = createLaunchableState(groups = emptyList())))
    }
}
