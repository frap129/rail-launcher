package core.data.prefs

import core.data.apps.model.App
import core.data.folders.Folder
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createLaunchableState
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createRailLaunchable
import core.data.testing.packageLevelKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
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
    fun `clears only the Tap actions whose keys are removed`() {
        val inputSettings: HeaderSettings = HeaderSettings(
            timeTapTarget = HeaderTapSetting.Launchable(key = "rail://maps"),
            dateTapTarget = HeaderTapSetting.Launchable(key = "rail://uninstalled")
        )

        val actualSettings: HeaderSettings = inputSettings.withoutTapTargets(keys = setOf("rail://uninstalled"))

        assertEquals(HeaderTapSetting.Launchable(key = "rail://maps"), actualSettings.timeTapTarget)
        assertNull(actualSettings.dateTapTarget)
        assertEquals(
            HeaderTapSetting.Nothing,
            HeaderSettings(timeTapTarget = HeaderTapSetting.Nothing).withoutTapTargets(emptySet()).timeTapTarget
        )
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
    fun `clears a Tap action stored under a package-level key`() {
        val inputApp: App = createApp(packageName = "com.example.app")
        val inputState = createLoadedLaunchableState(launchables = listOf(inputApp))
        val inputKey: String = inputApp.packageLevelKey()
        val inputSetting: HeaderTapSetting = HeaderTapSetting.Launchable(key = inputKey)

        assertFalse(inputSetting.isDisplayable(state = inputState))
        assertNull(HeaderSettings(timeTapTarget = inputSetting).withoutTapTargets(keys = setOf(inputKey)).timeTapTarget)
    }
}
