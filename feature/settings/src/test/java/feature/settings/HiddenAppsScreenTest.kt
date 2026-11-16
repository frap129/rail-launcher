package feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import core.data.apps.model.App
import core.data.launchables.model.Launchable
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.ui.composables.getString
import core.ui.model.data.SETTINGS_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val HIDDEN_APP_NAME: String = "Maps"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class HiddenAppsScreenTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `lists each Hidden app with an app-specific Unhide action`() {
        val inputHiddenApp: App = createApp(name = HIDDEN_APP_NAME)
        var actualUnhidden: Launchable? = null
        composeRule.setContent {
            HiddenAppsContent(
                hiddenApps = listOf(inputHiddenApp),
                onUnhide = { launchable -> actualUnhidden = launchable }
            )
        }

        composeRule.onNodeWithText(HIDDEN_APP_NAME).assertIsDisplayed()
        composeRule.onNodeWithText(getString(R.string.unhide)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(getString(R.string.unhide_app, HIDDEN_APP_NAME))
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle { assertEquals(inputHiddenApp, actualUnhidden) }
    }

    @Test
    fun `shows the empty state when nothing is Hidden`() {
        composeRule.setContent {
            HiddenAppsContent(hiddenApps = emptyList(), onUnhide = {})
        }

        composeRule.onNodeWithText(getString(R.string.no_hidden_apps)).assertIsDisplayed()
    }

    @Test
    fun `shows no empty state until the Launchable State loads`() {
        composeRule.setContent {
            HiddenAppsContent(hiddenApps = null, onUnhide = {})
        }

        composeRule.onNodeWithText(getString(R.string.no_hidden_apps)).assertDoesNotExist()
    }

    @Test
    fun `opens the Hidden apps destination from the main Settings row`() {
        val inputViewModel: SettingsViewModel = createSettingsViewModel()
        composeRule.setContent {
            val navController: NavHostController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = "$SETTINGS_ROUTE/${SettingsScreen.Main.key}"
            ) {
                composable(
                    route = settingsDestination.route,
                    arguments = settingsDestination.arguments
                ) { backStackEntry ->
                    SettingsScreen(
                        navController = navController,
                        backStackEntry = backStackEntry,
                        viewModel = inputViewModel
                    )
                }
            }
        }

        composeRule.onNodeWithText(getString(R.string.hidden_apps)).assertIsDisplayed().performClick()

        composeRule.onNodeWithText(getString(R.string.no_hidden_apps)).assertIsDisplayed()
    }
}
