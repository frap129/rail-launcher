package feature.launcher.onboarding

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavController
import androidx.test.ext.junit.rules.ActivityScenarioRule
import core.data.calendar.ICalendarEventSource
import core.data.calendar.ICalendarPermissionGate
import core.data.homerole.IHomeRoleSource
import core.data.notifications.INotificationSource
import core.data.prefs.IPreferencesRepository
import core.data.prefs.ISearchSettings
import core.data.search.IContactSearchSource
import core.data.testing.TEST_SDK
import core.data.testing.createCalendarEventSourceMock
import core.data.testing.createCalendarPermissionGate
import core.data.testing.createContactSearchSourceMock
import core.data.testing.createPreferencesRepositoryMock
import core.data.testing.createSearchSettings
import core.ui.R as CoreUiR
import core.ui.composables.getString
import core.ui.model.data.LAUNCHER_ROUTE
import feature.launcher.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val PERMISSION_GRANT_RESULTS_EXTRA: String =
    "androidx.activity.result.contract.extra.PERMISSION_GRANT_RESULTS"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class OnboardingScreenTest {
    @get:Rule
    val composeRule: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity> =
        createAndroidComposeRule()

    @Test
    fun `given each page, when it loads, then its guidance and labelled controls are visible`() {
        setOnboardingContent(permissionState = grantedPermissionState())
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
            if (inputPage.permission != null) {
                composeRule.onNodeWithText(getString(CoreUiR.string.permission_granted)).assertIsDisplayed()
            }
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
    fun `given each missing permission, when its request is tapped, then the existing flow receives it`() {
        val actualRequests: MutableList<OnboardingPermission> = mutableListOf()
        setOnboardingContent(onRequestPermission = { inputPermission: OnboardingPermission -> actualRequests += inputPermission })
        repeat(OnboardingPage.NotificationAccess.ordinal) {
            composeRule.onNodeWithText(getString(CoreUiR.string.next)).performClick()
        }
        OnboardingPermission.entries.forEach { inputPermission: OnboardingPermission ->
            composeRule.onNodeWithContentDescription(getString(inputPermission.requestContentDescription)).assertHasClickAction()
            composeRule.onNodeWithContentDescription(getString(inputPermission.requestContentDescription)).performClick()
            composeRule.onNodeWithText(getString(CoreUiR.string.not_now)).performClick()
        }
        composeRule.runOnIdle { assertEquals(OnboardingPermission.entries.toList(), actualRequests) }
    }

    @Test
    fun `given each missing permission, when Not now is tapped, then onboarding continues without completing`() {
        var actualCompletions: Int = 0
        setOnboardingContent(onComplete = { actualCompletions++ })
        repeat(OnboardingPage.NotificationAccess.ordinal) {
            composeRule.onNodeWithText(getString(CoreUiR.string.next)).performClick()
        }
        OnboardingPage.entries.drop(OnboardingPage.NotificationAccess.ordinal).dropLast(1).forEach { inputPage: OnboardingPage ->
            composeRule.onNodeWithText(getString(CoreUiR.string.not_now)).performClick()
            val expectedNextPage: OnboardingPage = OnboardingPage.entries[inputPage.ordinal + 1]
            composeRule.onNodeWithText(getString(expectedNextPage.title)).assertIsDisplayed()
        }
        composeRule.onNodeWithText(getString(OnboardingPage.HomeRole.title)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, actualCompletions) }
    }

    @Test
    fun `given calendar and contacts are granted, when their pages load, then they report access`() {
        val mockHarness: OnboardingScreenHarness = createOnboardingScreenHarness()
        setOnboardingScreen(mockHarness = mockHarness)
        repeat(OnboardingPage.Calendar.ordinal) {
            advanceOnePage()
        }
        composeRule.onNodeWithText(getString(OnboardingPage.Calendar.title)).assertIsDisplayed()
        composeRule.onNodeWithText(getString(CoreUiR.string.permission_granted)).assertIsDisplayed()
        composeRule.onNodeWithText(getString(CoreUiR.string.next)).performClick()
        composeRule.onNodeWithText(getString(OnboardingPage.Contacts.title)).assertIsDisplayed()
        composeRule.onNodeWithText(getString(CoreUiR.string.permission_granted)).assertIsDisplayed()
    }

    @Test
    fun `given stale calendar access, when onboarding opens, then the current grant is shown`() {
        val inputPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock(
            permission = inputPermission,
            onRefresh = { inputPermission.value = true }
        )
        val mockHarness: OnboardingScreenHarness = createOnboardingScreenHarness(
            mockCalendarEventSource = mockCalendarEventSource
        )
        setOnboardingScreen(mockHarness = mockHarness)
        repeat(OnboardingPage.Calendar.ordinal) {
            advanceOnePage()
        }
        composeRule.onNodeWithText(getString(OnboardingPage.Calendar.title)).assertIsDisplayed()
        composeRule.onNodeWithText(getString(CoreUiR.string.permission_granted)).assertIsDisplayed()
    }

    @Test
    fun `given calendar is permanently denied, when its result returns, then App info can be opened`() {
        val inputPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)
        val mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock(permission = inputPermission)
        val mockHarness: OnboardingScreenHarness = createOnboardingScreenHarness(
            mockCalendarEventSource = mockCalendarEventSource
        )
        setOnboardingScreen(mockHarness = mockHarness)
        repeat(OnboardingPage.Calendar.ordinal) {
            advanceOnePage()
        }
        composeRule.onNodeWithContentDescription(getString(R.string.onboarding_calendar_request)).performClick()
        dispatchPermissionResult(Activity.RESULT_CANCELED)
        composeRule.onNodeWithText(getString(CoreUiR.string.calendar_permission_denied_title)).assertIsDisplayed()
        composeRule.onNodeWithText(getString(CoreUiR.string.open_settings)).performClick()
        val mockActivity: ComponentActivity = composeRule.activity
        val actualIntent: Intent = checkNotNull(shadowOf(mockActivity).nextStartedActivity)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, actualIntent.action)
        assertEquals(Uri.fromParts("package", mockActivity.packageName, null), actualIntent.data)
    }

    @Test
    fun `given contacts are granted, when the result returns, then Contact results is enabled`() {
        val inputPermission: MutableStateFlow<Boolean> = MutableStateFlow(false)
        var inputGrantOnRefresh: Boolean = false
        val mockContactSearchSource: IContactSearchSource = createContactSearchSourceMock(
            hasPermission = inputPermission,
            onRefresh = {
                if (inputGrantOnRefresh) {
                    inputPermission.value = true
                }
            }
        )
        val mockHarness: OnboardingScreenHarness = createOnboardingScreenHarness(
            mockContactSearchSource = mockContactSearchSource
        )
        setOnboardingScreen(mockHarness = mockHarness)
        repeat(OnboardingPage.Contacts.ordinal) {
            advanceOnePage()
        }
        composeRule.onNodeWithContentDescription(getString(R.string.onboarding_contacts_request)).performClick()
        inputGrantOnRefresh = true
        dispatchPermissionResult(
            resultCode = Activity.RESULT_OK,
            resultIntent = Intent().putExtra(
                PERMISSION_GRANT_RESULTS_EXTRA,
                intArrayOf(PackageManager.PERMISSION_GRANTED)
            )
        )
        verify(exactly = 1) { mockHarness.mockPreferencesRepository.setSearchContacts(true) }
        composeRule.onNodeWithText(getString(CoreUiR.string.permission_granted)).assertIsDisplayed()
    }

    @Test
    fun `given contacts are permanently denied, when the result returns, then the shared denial outcome shows the dialog`() {
        val mockContactSearchSource: IContactSearchSource = createContactSearchSourceMock()
        val mockHarness: OnboardingScreenHarness = createOnboardingScreenHarness(
            mockContactSearchSource = mockContactSearchSource
        )
        setOnboardingScreen(mockHarness = mockHarness)
        repeat(OnboardingPage.Contacts.ordinal) {
            advanceOnePage()
        }
        composeRule.onNodeWithContentDescription(getString(R.string.onboarding_contacts_request)).performClick()
        dispatchPermissionResult(Activity.RESULT_CANCELED)
        composeRule.onNodeWithText(getString(CoreUiR.string.search_contacts_permission_title)).assertIsDisplayed()
        verify(exactly = 1) { mockHarness.mockPreferencesRepository.setSearchContacts(false) }
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
        repeat(OnboardingPage.HomeRole.ordinal) {
            advanceOnePage()
        }
        composeRule.onNodeWithText(getString(R.string.onboarding_finish)).performClick()
        verify(exactly = 1) { mockHomeRoleSource.createHomeRoleRequestIntent() }
        verify(exactly = 1) { mockHarness.mockPreferencesRepository.setOnboardingCompleted(true) }
        verify(exactly = 1) { mockHarness.mockNavController.popBackStack(LAUNCHER_ROUTE, false) }
    }

    private fun setOnboardingContent(
        onComplete: () -> Unit = {},
        onRequestHomeRole: () -> Unit = {},
        onRequestPermission: (OnboardingPermission) -> Unit = {},
        permissionState: OnboardingPermissionState = OnboardingPermissionState()
    ) {
        composeRule.setContent {
            MaterialTheme {
                OnboardingContent(
                    onComplete = onComplete,
                    onRequestHomeRole = onRequestHomeRole,
                    onRequestPermission = onRequestPermission,
                    permissionState = permissionState
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
                    homeRoleSource = mockHomeRoleSource,
                    calendarEventSource = mockHarness.mockCalendarEventSource,
                    calendarPermissionGate = mockHarness.mockCalendarPermissionGate,
                    contactSearchSource = mockHarness.mockContactSearchSource,
                    searchSettings = mockHarness.mockSearchSettings,
                    notificationSource = mockHarness.mockNotificationSource
                )
            }
        }
    }

    private fun dispatchPermissionResult(resultCode: Int, resultIntent: Intent? = null) {
        val mockActivity: ComponentActivity = composeRule.activity
        val actualRequestCode: Int = checkNotNull(shadowOf(mockActivity).nextStartedActivityForResult).requestCode
        composeRule.runOnIdle {
            mockActivity.activityResultRegistry.dispatchResult(actualRequestCode, resultCode, resultIntent)
        }
    }

    private fun advanceOnePage() {
        val hasNotNowButton: Boolean = composeRule.onAllNodesWithText(getString(CoreUiR.string.not_now)).fetchSemanticsNodes().isNotEmpty()
        val expectedActionLabel: Int = if (hasNotNowButton) CoreUiR.string.not_now else CoreUiR.string.next
        composeRule.onNodeWithText(getString(expectedActionLabel)).performClick()
    }

    private fun grantedPermissionState(): OnboardingPermissionState = OnboardingPermissionState(
        hasNotificationAccess = true,
        hasCalendarAccess = true,
        hasContactsAccess = true
    )

    private fun createOnboardingScreenHarness(
        mockCalendarEventSource: ICalendarEventSource = createCalendarEventSourceMock(hasPermission = true),
        mockContactSearchSource: IContactSearchSource = createContactSearchSourceMock(hasPermission = MutableStateFlow(true)),
        mockNotificationSource: INotificationSource = mockk(relaxed = true)
    ): OnboardingScreenHarness {
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
        val mockNavController: NavController = mockk(relaxed = true)
        val mockCalendarPermissionGate: ICalendarPermissionGate = createCalendarPermissionGate(mockCalendarEventSource)
        val mockSearchSettings: ISearchSettings = createSearchSettings(
            mockPreferencesRepository = mockPreferencesRepository,
            mockContactSearchSource = mockContactSearchSource
        )
        every { mockNotificationSource.hasAccess } returns MutableStateFlow(true)
        every { mockNavController.currentBackStackEntry } returns null
        every { mockNavController.popBackStack(LAUNCHER_ROUTE, false) } returns true
        return OnboardingScreenHarness(
            mockPreferencesRepository = mockPreferencesRepository,
            mockNavController = mockNavController,
            mockCalendarEventSource = mockCalendarEventSource,
            mockCalendarPermissionGate = mockCalendarPermissionGate,
            mockContactSearchSource = mockContactSearchSource,
            mockSearchSettings = mockSearchSettings,
            mockNotificationSource = mockNotificationSource
        )
    }
}

private data class OnboardingScreenHarness(
    val mockPreferencesRepository: IPreferencesRepository,
    val mockNavController: NavController,
    val mockCalendarEventSource: ICalendarEventSource,
    val mockCalendarPermissionGate: ICalendarPermissionGate,
    val mockContactSearchSource: IContactSearchSource,
    val mockSearchSettings: ISearchSettings,
    val mockNotificationSource: INotificationSource
)
