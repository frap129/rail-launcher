package core.ui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import core.data.testing.TEST_SDK
import core.ui.model.data.LauncherInputState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val FIRST_TEXT_FIELD_TAG: String = "firstTextField"
private const val SECOND_TEXT_FIELD_TAG: String = "secondTextField"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class TextFieldFocusTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `a focused text field reports focus`() {
        val inputLauncherInputState: LauncherInputState = LauncherInputState()
        setFocusTrackingContent(
            isSecondFieldShown = mutableStateOf(true),
            launcherInputState = inputLauncherInputState
        )

        composeRule.onNodeWithTag(FIRST_TEXT_FIELD_TAG).requestFocus()
        composeRule.waitForIdle()

        assertTrue(inputLauncherInputState.isTextFieldFocused.value)
    }

    @Test
    fun `moving focus between text fields keeps the focus reported`() {
        val inputLauncherInputState: LauncherInputState = LauncherInputState()
        setFocusTrackingContent(
            isSecondFieldShown = mutableStateOf(true),
            launcherInputState = inputLauncherInputState
        )
        composeRule.onNodeWithTag(FIRST_TEXT_FIELD_TAG).requestFocus()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SECOND_TEXT_FIELD_TAG).requestFocus()
        composeRule.waitForIdle()

        assertTrue(inputLauncherInputState.isTextFieldFocused.value)
    }

    @Test
    fun `a text field that leaves the screen stops reporting focus`() {
        val inputLauncherInputState: LauncherInputState = LauncherInputState()
        val inputIsSecondFieldShown: MutableState<Boolean> = mutableStateOf(true)
        setFocusTrackingContent(
            isFirstFieldShown = mutableStateOf(false),
            isSecondFieldShown = inputIsSecondFieldShown,
            launcherInputState = inputLauncherInputState
        )
        composeRule.onNodeWithTag(SECOND_TEXT_FIELD_TAG).requestFocus()
        composeRule.waitForIdle()

        composeRule.runOnIdle { inputIsSecondFieldShown.value = false }
        composeRule.waitForIdle()

        assertFalse(inputLauncherInputState.isTextFieldFocused.value)
    }

    private fun setFocusTrackingContent(
        isFirstFieldShown: MutableState<Boolean> = mutableStateOf(true),
        isSecondFieldShown: MutableState<Boolean>,
        launcherInputState: LauncherInputState
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalLauncherInputState provides launcherInputState) {
                Column {
                    if (isFirstFieldShown.value) {
                        BasicTextField(
                            value = "",
                            onValueChange = {},
                            modifier = Modifier
                                .tracksTextFieldFocus()
                                .testTag(FIRST_TEXT_FIELD_TAG)
                        )
                    }
                    if (isSecondFieldShown.value) {
                        BasicTextField(
                            value = "",
                            onValueChange = {},
                            modifier = Modifier
                                .tracksTextFieldFocus()
                                .testTag(SECOND_TEXT_FIELD_TAG)
                        )
                    }
                }
            }
        }
    }
}
