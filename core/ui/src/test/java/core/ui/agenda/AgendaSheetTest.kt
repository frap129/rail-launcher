package core.ui.agenda

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import core.data.testing.TEST_SDK
import core.ui.R
import core.ui.calendar.OpenEventTarget
import core.ui.composables.getString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class AgendaSheetTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    private val inputDays: List<AgendaDayModel> = listOf(
        AgendaDayModel(
            label = "Today",
            events = listOf(
                AgendaEventModel(
                    title = "Standup",
                    timeText = "15:00",
                    color = Color(0xFF00FF00.toInt()),
                    tapTarget = OpenEventTarget(eventId = 1L, beginMillis = 0L, endMillis = 0L, isWorkProfile = false)
                )
            )
        )
    )

    @Test
    fun `offers the calendar permission instead of events without it`() {
        var actualGrantCount: Int = 0
        setAgendaContent(hasPermission = false, onGrantPermission = { actualGrantCount++ })

        composeRule.onNodeWithText(getString(R.string.calendar_permission_denied_title)).assertIsDisplayed()
        composeRule.onNodeWithText(getString(R.string.calendar_permission_needed)).assertIsDisplayed()
        composeRule.onNodeWithText("Standup").assertDoesNotExist()
        composeRule.onNodeWithText(getString(R.string.grant)).performClick()

        assertEquals(1, actualGrantCount)
    }

    @Test
    fun `shows the events without the permission action once calendar access is granted`() {
        setAgendaContent(hasPermission = true)

        composeRule.onNodeWithText(getString(R.string.calendar_permission_denied_title)).assertDoesNotExist()
        composeRule.onNodeWithText("Standup").assertIsDisplayed()
    }

    private fun setAgendaContent(hasPermission: Boolean, onGrantPermission: () -> Unit = {}) {
        composeRule.setContent {
            AgendaSheet(
                days = inputDays,
                isLoading = false,
                hasPermission = hasPermission,
                onOpenEvent = {},
                onOpenCalendar = {},
                onGrantPermission = onGrantPermission,
                onDismiss = {}
            )
        }
    }
}
