package core.ui

import core.ui.model.data.LAUNCHER_ROUTE
import core.ui.model.data.ONBOARDING_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Test

class StartDestinationTest {
    @Test
    fun `onboarding starts before Home when it is not completed`() {
        val inputOnboardingCompleted: Boolean = false
        val inputOnboardingRoute: String = ONBOARDING_ROUTE
        val inputHomeRoute: String = LAUNCHER_ROUTE
        val actualStartDestination: String = resolveStartDestination(
            isOnboardingCompleted = inputOnboardingCompleted,
            onboardingRoute = inputOnboardingRoute,
            homeRoute = inputHomeRoute
        )
        assertEquals(inputOnboardingRoute, actualStartDestination)
    }

    @Test
    fun `Home starts when onboarding is completed`() {
        val inputOnboardingCompleted: Boolean = true
        val inputOnboardingRoute: String = ONBOARDING_ROUTE
        val inputHomeRoute: String = LAUNCHER_ROUTE
        val actualStartDestination: String = resolveStartDestination(
            isOnboardingCompleted = inputOnboardingCompleted,
            onboardingRoute = inputOnboardingRoute,
            homeRoute = inputHomeRoute
        )
        assertEquals(inputHomeRoute, actualStartDestination)
    }
}
