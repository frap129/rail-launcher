package feature.launcher.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavController
import core.data.homerole.IHomeRoleSource
import core.data.prefs.IPreferencesRepository
import core.data.testing.TEST_SDK
import core.data.testing.createPreferencesRepositoryMock
import core.ui.R as CoreUiR
import core.ui.composables.getString
import core.ui.model.data.LAUNCHER_ROUTE
import feature.launcher.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class OnboardingScreenTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `given each page, when it loads, then its guidance and labelled controls are visible`() {
        setOnboardingContent()
        OnboardingPage.entries.forEach { inputPage: OnboardingPage ->
            composeRule.onNodeWithText(getString(inputPage.title)).assertIsDisplayed()
            composeRule.onNodeWithText(getString(inputPage.body)).assertIsDisplayed()
            composeRule.onNodeWithText(getString(inputPage.title)).assert(
                SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit)
            )
            composeRule.onNodeWithText(getString(R.string.onboarding_skip)).assertHasClickAction()
            val expectedActionLabel: String = getString(
                if (inputPage == OnboardingPage.entries.last()) R.string.onboarding_finish else CoreUiR.string.next
            )
            composeRule.onNodeWithText(expectedActionLabel).assertHasClickAction()
            if (inputPage != OnboardingPage.entries.last()) {
                composeRule.onNodeWithText(expectedActionLabel).performClick()
            }
        }
    }

    @Test
    fun `given the first page, when it loads, then TalkBack gets the step progress`() {
        setOnboardingContent()
        val expectedProgress: String = getString(R.string.onboarding_step, 1, OnboardingPage.entries.size)
        composeRule.onNodeWithContentDescription(expectedProgress).assertIsDisplayed()
    }

    @Test
    fun `given a later page, when Back is tapped, then the previous page is shown`() {
        setOnboardingContent()
        repeat(OnboardingPage.Search.ordinal) {
            composeRule.onNodeWithText(getString(CoreUiR.string.next)).performClick()
        }
        composeRule.onNodeWithText(getString(CoreUiR.string.back)).performClick()
        composeRule.onNodeWithText(getString(OnboardingPage.Favorites.title)).assertIsDisplayed()
    }

    @Test
    fun `given a later page, when Skip is tapped, then onboarding completes`() {
        var actualCompletions: Int = 0
        setOnboardingContent(onComplete = { actualCompletions++ })
        repeat(OnboardingPage.PopUps.ordinal) {
            composeRule.onNodeWithText(getString(CoreUiR.string.next)).performClick()
        }
        composeRule.onNodeWithText(getString(R.string.onboarding_skip)).performClick()
        composeRule.runOnIdle { assertEquals(1, actualCompletions) }
    }

    @Test
    fun `given the first page, when Skip is tapped, then completion is stored and Home is shown`() {
        val mockHarness: OnboardingScreenHarness = createOnboardingScreenHarness()
        setOnboardingScreen(mockHarness = mockHarness)
        composeRule.onNodeWithText(getString(R.string.onboarding_skip)).performClick()
        verify(exactly = 1) { mockHarness.mockPreferencesRepository.setOnboardingCompleted(true) }
        verify(exactly = 1) { mockHarness.mockNavController.popBackStack(LAUNCHER_ROUTE, false) }
    }

    @Test
    fun `given the last page, when Finish setup is tapped, then the Home role is requested and Home is shown`() {
        val mockHarness: OnboardingScreenHarness = createOnboardingScreenHarness()
        val mockHomeRoleSource: IHomeRoleSource = mockk()
        every { mockHomeRoleSource.createHomeRoleRequestIntent() } returns null
        setOnboardingScreen(mockHarness = mockHarness, mockHomeRoleSource = mockHomeRoleSource)
        repeat(OnboardingPage.entries.lastIndex) {
            composeRule.onNodeWithText(getString(CoreUiR.string.next)).performClick()
        }
        composeRule.onNodeWithText(getString(R.string.onboarding_finish)).performClick()
        verify(exactly = 1) { mockHomeRoleSource.createHomeRoleRequestIntent() }
        verify(exactly = 1) { mockHarness.mockPreferencesRepository.setOnboardingCompleted(true) }
        verify(exactly = 1) { mockHarness.mockNavController.popBackStack(LAUNCHER_ROUTE, false) }
    }

    private fun setOnboardingContent(onComplete: () -> Unit = {}, onRequestHomeRole: () -> Unit = {}) {
        composeRule.setContent {
            MaterialTheme {
                OnboardingContent(
                    onComplete = onComplete,
                    onRequestHomeRole = onRequestHomeRole
                )
            }
        }
    }

    private fun setOnboardingScreen(mockHarness: OnboardingScreenHarness, mockHomeRoleSource: IHomeRoleSource = mockk(relaxed = true)) {
        composeRule.setContent {
            MaterialTheme {
                OnboardingScreen(
                    navController = mockHarness.mockNavController,
                    preferencesRepository = mockHarness.mockPreferencesRepository,
                    homeRoleSource = mockHomeRoleSource
                )
            }
        }
    }

    private fun createOnboardingScreenHarness(): OnboardingScreenHarness {
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
        val mockNavController: NavController = mockk(relaxed = true)
        every { mockNavController.currentBackStackEntry } returns null
        every { mockNavController.popBackStack(LAUNCHER_ROUTE, false) } returns true
        return OnboardingScreenHarness(
            mockPreferencesRepository = mockPreferencesRepository,
            mockNavController = mockNavController
        )
    }
}

private data class OnboardingScreenHarness(val mockPreferencesRepository: IPreferencesRepository, val mockNavController: NavController)
