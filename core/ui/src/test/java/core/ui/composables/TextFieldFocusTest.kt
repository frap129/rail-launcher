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
        val inputLauncherInputState: MutableState<LauncherInputState> = createLauncherInputState()
        setFocusTrackingContent(
            inputIsSecondFieldShown = mutableStateOf(true),
            inputLauncherInputState = inputLauncherInputState
        )
        focusField(inputTestTag = FIRST_TEXT_FIELD_TAG)
        assertTrue(inputLauncherInputState.value.isTextFieldFocused.value)
    }

    @Test
    fun `moving focus between text fields keeps the focus reported`() {
        val inputLauncherInputState: MutableState<LauncherInputState> = createLauncherInputState()
        setFocusTrackingContent(
            inputIsSecondFieldShown = mutableStateOf(true),
            inputLauncherInputState = inputLauncherInputState
        )
        focusField(inputTestTag = FIRST_TEXT_FIELD_TAG)
        focusField(inputTestTag = SECOND_TEXT_FIELD_TAG)
        assertTrue(inputLauncherInputState.value.isTextFieldFocused.value)
    }

    @Test
    fun `a text field that leaves the screen stops reporting focus`() {
        val inputLauncherInputState: MutableState<LauncherInputState> = createLauncherInputState()
        val inputIsSecondFieldShown: MutableState<Boolean> = mutableStateOf(true)
        setFocusTrackingContent(
            inputIsFirstFieldShown = mutableStateOf(false),
            inputIsSecondFieldShown = inputIsSecondFieldShown,
            inputLauncherInputState = inputLauncherInputState
        )
        focusField(inputTestTag = SECOND_TEXT_FIELD_TAG)
        composeRule.runOnIdle { inputIsSecondFieldShown.value = false }
        composeRule.waitForIdle()
        assertFalse(inputLauncherInputState.value.isTextFieldFocused.value)
    }

    @Test
    fun `disposing a focused field clears the input state it registered with`() {
        val inputOriginalLauncherInputState: LauncherInputState = LauncherInputState()
        val inputReplacementLauncherInputState: LauncherInputState = LauncherInputState()
        val inputLauncherInputState: MutableState<LauncherInputState> = mutableStateOf(inputOriginalLauncherInputState)
        val inputIsFirstFieldShown: MutableState<Boolean> = mutableStateOf(true)
        setFocusTrackingContent(
            inputIsFirstFieldShown = inputIsFirstFieldShown,
            inputIsSecondFieldShown = mutableStateOf(false),
            inputLauncherInputState = inputLauncherInputState
        )
        focusField(inputTestTag = FIRST_TEXT_FIELD_TAG)
        assertTrue(inputOriginalLauncherInputState.isTextFieldFocused.value)
        composeRule.runOnIdle { inputLauncherInputState.value = inputReplacementLauncherInputState }
        composeRule.waitForIdle()
        composeRule.runOnIdle { inputIsFirstFieldShown.value = false }
        composeRule.waitForIdle()
        assertFalse(inputOriginalLauncherInputState.isTextFieldFocused.value)
        assertFalse(inputReplacementLauncherInputState.isTextFieldFocused.value)
    }

    private fun focusField(inputTestTag: String) {
        composeRule.onNodeWithTag(inputTestTag).requestFocus()
        composeRule.waitForIdle()
    }

    private fun setFocusTrackingContent(
        inputIsFirstFieldShown: MutableState<Boolean> = mutableStateOf(true),
        inputIsSecondFieldShown: MutableState<Boolean>,
        inputLauncherInputState: MutableState<LauncherInputState>
    ) {
        composeRule.setContent {
            val inputFields: List<Pair<String, MutableState<Boolean>>> = listOf(
                FIRST_TEXT_FIELD_TAG to inputIsFirstFieldShown,
                SECOND_TEXT_FIELD_TAG to inputIsSecondFieldShown
            )
            CompositionLocalProvider(LocalLauncherInputState provides inputLauncherInputState.value) {
                Column {
                    inputFields.forEach { (inputTestTag: String, inputIsFieldShown: MutableState<Boolean>) ->
                        if (inputIsFieldShown.value) {
                            BasicTextField(
                                value = "",
                                onValueChange = { _: String -> },
                                modifier = Modifier
                                    .tracksTextFieldFocus()
                                    .testTag(inputTestTag)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun createLauncherInputState(): MutableState<LauncherInputState> = mutableStateOf(LauncherInputState())
}
