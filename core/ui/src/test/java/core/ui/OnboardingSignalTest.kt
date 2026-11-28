package core.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import core.data.prefs.IPreferencesRepository
import core.data.prefs.Settings
import core.data.testing.TEST_SDK
import core.data.testing.createPreferencesRepositoryMock
import core.ui.model.data.LAUNCHER_ROUTE
import core.ui.model.data.LauncherSignal
import core.ui.model.data.ONBOARDING_ROUTE
import core.ui.model.data.SETTINGS_ROUTE
import io.mockk.every
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SIGNAL_BUFFER_CAPACITY: Int = 1

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class OnboardingSignalTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private lateinit var actualNavController: NavHostController

    @Test
    fun `given first-run onboarding, when HOME arrives, then onboarding stays and a cold start still opens it`() {
        val inputSignals: MutableSharedFlow<LauncherSignal> = createSignalFlow()
        setSignalContent(inputSignals = inputSignals, inputStartDestination = ONBOARDING_ROUTE)
        val inputHome: LauncherSignal.Home = LauncherSignal.Home(shouldShowHome = true, wasFocused = true)
        assertTrue(inputSignals.tryEmit(inputHome))
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(ONBOARDING_ROUTE, actualNavController.currentBackStackEntry?.destination?.route)
            assertNull(actualNavController.previousBackStackEntry)
        }
        val expectedColdStartDestination: String = resolveStartDestination(
            isOnboardingCompleted = false,
            onboardingRoute = ONBOARDING_ROUTE,
            homeRoute = LAUNCHER_ROUTE
        )
        assertEquals(ONBOARDING_ROUTE, expectedColdStartDestination)
    }

    @Test
    fun `given completed first-run onboarding, when replayed from Settings and HOME arrives, then Home is shown`() {
        val inputSettingsFlow: MutableStateFlow<Settings> = MutableStateFlow(Settings())
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(settingsFlow = inputSettingsFlow)
        every { mockPreferencesRepository.setOnboardingCompleted(any()) } answers {
            inputSettingsFlow.value = inputSettingsFlow.value.copy(onboardingCompleted = firstArg<Boolean>())
        }
        val inputSignals: MutableSharedFlow<LauncherSignal> = createSignalFlow()
        setSignalContent(
            inputSignals = inputSignals,
            inputStartDestination = ONBOARDING_ROUTE,
            mockPreferencesRepository = mockPreferencesRepository
        )
        composeRule.runOnIdle {
            mockPreferencesRepository.setOnboardingCompleted(true)
            showDestination(
                navController = actualNavController,
                route = LAUNCHER_ROUTE,
                popUpToRoute = ONBOARDING_ROUTE
            )
            actualNavController.navigate(SETTINGS_ROUTE)
            actualNavController.navigate(ONBOARDING_ROUTE)
        }
        composeRule.waitForIdle()
        val inputHome: LauncherSignal.Home = LauncherSignal.Home(shouldShowHome = true, wasFocused = true)
        assertTrue(inputSignals.tryEmit(inputHome))
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(LAUNCHER_ROUTE, actualNavController.currentBackStackEntry?.destination?.route)
            assertNull(actualNavController.previousBackStackEntry)
        }
    }

    @Test
    fun `given onboarding replayed from Settings, when HOME arrives, then Home is shown`() {
        val inputSignals: MutableSharedFlow<LauncherSignal> = createSignalFlow()
        val mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock(
            settings = Settings(onboardingCompleted = true)
        )
        setSignalContent(
            inputSignals = inputSignals,
            inputStartDestination = LAUNCHER_ROUTE,
            mockPreferencesRepository = mockPreferencesRepository
        )
        composeRule.runOnIdle {
            actualNavController.navigate(SETTINGS_ROUTE)
            actualNavController.navigate(ONBOARDING_ROUTE)
        }
        composeRule.waitForIdle()
        val inputHome: LauncherSignal.Home = LauncherSignal.Home(shouldShowHome = true, wasFocused = true)
        assertTrue(inputSignals.tryEmit(inputHome))
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(LAUNCHER_ROUTE, actualNavController.currentBackStackEntry?.destination?.route)
            assertNull(actualNavController.previousBackStackEntry)
        }
    }

    private fun createSignalFlow(): MutableSharedFlow<LauncherSignal> = MutableSharedFlow(
        extraBufferCapacity = SIGNAL_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private fun setSignalContent(
        inputSignals: MutableSharedFlow<LauncherSignal>,
        inputStartDestination: String,
        mockPreferencesRepository: IPreferencesRepository = createPreferencesRepositoryMock()
    ) {
        composeRule.setContent {
            val navController: NavHostController = rememberNavController()
            SideEffect { actualNavController = navController }
            NavHost(
                navController = navController,
                startDestination = inputStartDestination
            ) {
                composable(LAUNCHER_ROUTE) {}
                composable(ONBOARDING_ROUTE) {}
                composable(SETTINGS_ROUTE) {}
            }
            val currentBackStackEntry: NavBackStackEntry? by navController.currentBackStackEntryAsState()
            val latestBackStackEntry: State<NavBackStackEntry?> = rememberUpdatedState(currentBackStackEntry)
            LauncherSignalEffect(
                navController = navController,
                homeRoute = LAUNCHER_ROUTE,
                onboardingRoute = ONBOARDING_ROUTE,
                preferencesRepository = mockPreferencesRepository,
                latestBackStackEntry = latestBackStackEntry,
                launcherSignals = inputSignals
            )
        }
        composeRule.waitForIdle()
    }
}
