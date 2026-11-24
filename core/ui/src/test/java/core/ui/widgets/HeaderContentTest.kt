package core.ui.widgets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import core.data.prefs.ClockStyle
import core.data.prefs.HeaderSettings
import core.data.testing.TEST_SDK
import core.data.testing.createClockReading
import core.data.testing.createDatePatternSourceMock
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class HeaderContentTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `shows the weekday while the time is hidden`() {
        val inputModel: HeaderModel = HeaderFormatter(createDatePatternSourceMock()).format(
            id = 1L,
            reading = createClockReading(instant = Instant.parse("2026-10-20T14:05:00Z")),
            settings = HeaderSettings(clockStyle = ClockStyle.WEEKDAY, clockVisible = false)
        )

        composeRule.setContent {
            HeaderContent(model = inputModel, menuEnabled = false, onTapTarget = {}, onLongPress = {})
        }

        composeRule.onNodeWithContentDescription("Tuesday").assertIsDisplayed()
    }
}
