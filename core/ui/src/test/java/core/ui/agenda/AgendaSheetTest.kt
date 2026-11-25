package core.ui.agenda

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import core.data.testing.TEST_SDK
import core.ui.R
import core.ui.composables.getString
import java.time.LocalDate
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
        createAgendaDayModel(
            date = LocalDate.of(2026, 10, 20),
            label = "Today",
            events = listOf(
                createAgendaEventModel(eventId = 1L, title = "Standup", timeText = "15:00"),
                createAgendaEventModel(eventId = 2L, title = "Review", timeText = "16:00")
            )
        ),
        createAgendaDayModel(
            date = LocalDate.of(2026, 10, 21),
            label = "Tomorrow",
            events = listOf(createAgendaEventModel(eventId = 3L, title = "Release", timeText = "09:00"))
        )
    )

    private val expectedTextsInReadingOrder: List<String> = inputDays.flatMap { day ->
        listOf(day.label) + day.events.flatMap { event -> listOfNotNull(event.title, event.timeText) }
    }

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

    @Test
    fun `announces the day headings as headings`() {
        setAgendaContent(hasPermission = true)

        for (inputDay in inputDays) {
            composeRule.onNodeWithText(inputDay.label, useUnmergedTree = true).assert(isHeading())
        }
    }

    @Test
    fun `reads the Agenda rows in order`() {
        setAgendaContent(hasPermission = true)

        val actualTexts: List<String> = composeRule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and hasAnyAncestor(hasTestTag(AGENDA_LIST_TEST_TAG)),
                useUnmergedTree = true
            )
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString(separator = "") { text -> text.text } }

        assertEquals(expectedTextsInReadingOrder, actualTexts)
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
