package feature.settings

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import core.data.testing.TEST_SDK
import core.ui.R as CoreUiR
import core.ui.composables.getString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val INPUT_VERSION_NAME: String = "9.8.7"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class SettingsAdvancedSectionTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `given main Settings, when Advanced loads, then its labelled rows and version are shown`() {
        setMainContent()
        showLabel(getString(R.string.advanced))
        showAdvancedRow(getString(R.string.restart_rail))
        showAdvancedRow(getString(R.string.change_default_launcher))
        showAdvancedRow(getString(R.string.uninstall_rail))
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(getString(CoreUiR.string.app_info)))
        composeRule.onNode(
            hasText(getString(CoreUiR.string.app_info)) and hasText(INPUT_VERSION_NAME) and hasClickAction()
        ).assertIsDisplayed()
    }

    @Test
    fun `given Advanced settings, when the default launcher prompt loads, then it is shown on`() {
        setMainContent()
        showAdvancedRow(getString(R.string.default_launcher_prompt)).assertIsOn()
        composeRule.onNodeWithText(getString(R.string.default_launcher_prompt_summary)).assertIsDisplayed()
    }

    @Test
    fun `given Advanced settings, when the prompt is switched off, then the change is reported`() {
        var actualEnabled: Boolean? = null
        setMainContent(
            defaultLauncherPromptEnabled = true,
            advancedActions = createAdvancedActions(onDefaultLauncherPromptChange = { actualEnabled = it })
        )
        showAdvancedRow(getString(R.string.default_launcher_prompt)).performClick()
        composeRule.runOnIdle { assertEquals(false, actualEnabled) }
    }

    @Test
    fun `given Advanced settings, when the prompt is off, then its switch is shown off`() {
        setMainContent(defaultLauncherPromptEnabled = false)
        showAdvancedRow(getString(R.string.default_launcher_prompt)).assertIsOff()
    }

    @Test
    fun `given Advanced settings, when Replay onboarding is shown, then it has a labelled click action`() {
        setMainContent()
        showAdvancedRow(getString(R.string.replay_onboarding)).assertIsDisplayed()
    }

    @Test
    fun `given Advanced settings, when Replay onboarding is tapped, then onboarding is requested`() {
        var actualRequests: Int = 0
        setMainContent(advancedActions = createAdvancedActions(onReplayOnboarding = { actualRequests++ }))
        showAdvancedRow(getString(R.string.replay_onboarding)).performClick()
        composeRule.runOnIdle { assertEquals(1, actualRequests) }
    }

    @Test
    fun `given Advanced settings, when Restart Rail is tapped, then restart is requested`() {
        var actualRestarts: Int = 0
        setMainContent(advancedActions = createAdvancedActions(onRestartRail = { actualRestarts++ }))
        showAdvancedRow(getString(R.string.restart_rail)).performClick()
        composeRule.runOnIdle { assertEquals(1, actualRestarts) }
    }

    @Test
    fun `given Advanced settings, when Change default launcher is tapped, then the request is made`() {
        var actualRequests: Int = 0
        setMainContent(advancedActions = createAdvancedActions(onChangeDefaultLauncher = { actualRequests++ }))
        showAdvancedRow(getString(R.string.change_default_launcher)).performClick()
        composeRule.runOnIdle { assertEquals(1, actualRequests) }
    }

    @Test
    fun `given Advanced settings, when Uninstall Rail is tapped, then uninstall is requested`() {
        var actualRequests: Int = 0
        setMainContent(advancedActions = createAdvancedActions(onUninstallRail = { actualRequests++ }))
        showAdvancedRow(getString(R.string.uninstall_rail)).performClick()
        composeRule.runOnIdle { assertEquals(1, actualRequests) }
    }

    @Test
    fun `given Advanced settings, when App info is tapped, then Rail's App info is requested`() {
        var actualRequests: Int = 0
        setMainContent(advancedActions = createAdvancedActions(onOpenAppInfo = { actualRequests++ }))
        showAdvancedRow(getString(CoreUiR.string.app_info)).performClick()
        composeRule.runOnIdle { assertEquals(1, actualRequests) }
    }

    private fun showLabel(label: String): SemanticsNodeInteraction {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
        return composeRule.onNodeWithText(label).assertIsDisplayed()
    }

    private fun showAdvancedRow(label: String): SemanticsNodeInteraction {
        showLabel(label)
        return composeRule.onNode(hasText(label) and hasClickAction()).assertIsDisplayed()
    }

    private fun createAdvancedActions(
        onRestartRail: () -> Unit = {},
        onDefaultLauncherPromptChange: (Boolean) -> Unit = {},
        onReplayOnboarding: () -> Unit = {},
        onChangeDefaultLauncher: () -> Unit = {},
        onUninstallRail: () -> Unit = {},
        onOpenAppInfo: () -> Unit = {}
    ): SettingsAdvancedActions = SettingsAdvancedActions(
        onDefaultLauncherPromptChange = onDefaultLauncherPromptChange,
        onReplayOnboarding = onReplayOnboarding,
        onRestartRail = onRestartRail,
        onChangeDefaultLauncher = onChangeDefaultLauncher,
        onUninstallRail = onUninstallRail,
        onOpenAppInfo = onOpenAppInfo
    )

    private fun setMainContent(
        versionName: String = INPUT_VERSION_NAME,
        defaultLauncherPromptEnabled: Boolean = true,
        advancedActions: SettingsAdvancedActions = createAdvancedActions()
    ) {
        composeRule.setContent {
            SettingsMainScreen(
                hidePrivateSpaceWhenLocked = false,
                isPrivateSpaceAvailable = false,
                toAppearance = {},
                toWidgets = {},
                toPopups = {},
                toSearch = {},
                toHiddenApps = {},
                onHidePrivateSpaceWhenLockedChange = {},
                defaultLauncherPromptEnabled = defaultLauncherPromptEnabled,
                versionName = versionName,
                advancedActions = advancedActions
            )
        }
    }
}
