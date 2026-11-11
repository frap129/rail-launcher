package core.ui.composables

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import core.data.testing.TEST_SDK
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TEXT_FIELD_TEST_TAG: String = "requestKeyboardFocusField"

private data class LifecycleFixture(val owner: LifecycleOwner, val registry: LifecycleRegistry)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class RequestKeyboardFocusTest {
    @get:Rule
    val composeRule: ComposeContentTestRule = createComposeRule()

    @Test
    fun `focuses the field and shows the keyboard once when its content attaches`() {
        val mockKeyboardController: SoftwareKeyboardController = createKeyboardControllerMock()
        val mockWindowInfo: WindowInfo = createWindowInfoMock { true }
        val inputLifecycle: LifecycleFixture = createLifecycleFixture(state = Lifecycle.State.RESUMED)

        setFocusContent(mockKeyboardController, mockWindowInfo, inputLifecycle.owner)

        composeRule.onNodeWithTag(TEXT_FIELD_TEST_TAG).assertIsFocused()
        verify(exactly = 1) { mockKeyboardController.show() }
    }

    @Test
    fun `shows the keyboard once each time its content resumes once the window has focus`() {
        val mockKeyboardController: SoftwareKeyboardController = createKeyboardControllerMock()
        val inputIsWindowFocused: MutableState<Boolean> = mutableStateOf(false)
        val mockWindowInfo: WindowInfo = createWindowInfoMock { inputIsWindowFocused.value }
        val inputLifecycle: LifecycleFixture = createLifecycleFixture(state = Lifecycle.State.CREATED)
        setFocusContent(mockKeyboardController, mockWindowInfo, inputLifecycle.owner)
        verify(exactly = 0) { mockKeyboardController.show() }

        composeRule.runOnIdle { inputLifecycle.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
        verify(exactly = 0) { mockKeyboardController.show() }

        composeRule.runOnIdle { inputIsWindowFocused.value = true }
        composeRule.waitForIdle()
        verify(exactly = 1) { mockKeyboardController.show() }

        composeRule.runOnIdle { inputLifecycle.registry.currentState = Lifecycle.State.CREATED }
        composeRule.runOnIdle { inputLifecycle.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
        verify(exactly = 2) { mockKeyboardController.show() }
    }

    @Test
    fun `does not show the keyboard again when the window regains focus without a resume`() {
        val mockKeyboardController: SoftwareKeyboardController = createKeyboardControllerMock()
        val inputIsWindowFocused: MutableState<Boolean> = mutableStateOf(true)
        val mockWindowInfo: WindowInfo = createWindowInfoMock { inputIsWindowFocused.value }
        val inputLifecycle: LifecycleFixture = createLifecycleFixture(state = Lifecycle.State.RESUMED)
        setFocusContent(mockKeyboardController, mockWindowInfo, inputLifecycle.owner)
        verify(exactly = 1) { mockKeyboardController.show() }

        composeRule.runOnIdle { inputIsWindowFocused.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { inputIsWindowFocused.value = true }
        composeRule.waitForIdle()

        verify(exactly = 1) { mockKeyboardController.show() }
    }

    @Test
    fun `defers the initial request until the window has focus`() {
        val mockKeyboardController: SoftwareKeyboardController = createKeyboardControllerMock()
        val inputIsWindowFocused: MutableState<Boolean> = mutableStateOf(false)
        val mockWindowInfo: WindowInfo = createWindowInfoMock { inputIsWindowFocused.value }
        val inputLifecycle: LifecycleFixture = createLifecycleFixture(state = Lifecycle.State.RESUMED)
        setFocusContent(mockKeyboardController, mockWindowInfo, inputLifecycle.owner)
        verify(exactly = 0) { mockKeyboardController.show() }

        composeRule.runOnIdle { inputIsWindowFocused.value = true }
        composeRule.waitForIdle()
        verify(exactly = 1) { mockKeyboardController.show() }

        composeRule.runOnIdle { inputIsWindowFocused.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { inputIsWindowFocused.value = true }
        composeRule.waitForIdle()
        verify(exactly = 1) { mockKeyboardController.show() }
    }

    private fun setFocusContent(
        mockKeyboardController: SoftwareKeyboardController,
        mockWindowInfo: WindowInfo,
        lifecycleOwner: LifecycleOwner
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalSoftwareKeyboardController provides mockKeyboardController,
                LocalWindowInfo provides mockWindowInfo,
                LocalLifecycleOwner provides lifecycleOwner
            ) {
                val focusRequester: FocusRequester = remember { FocusRequester() }
                RequestKeyboardFocus(focusRequester = focusRequester)
                BasicTextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .testTag(TEXT_FIELD_TEST_TAG)
                )
            }
        }
    }

    private fun createKeyboardControllerMock(): SoftwareKeyboardController = mockk(relaxed = true)

    private fun createWindowInfoMock(isWindowFocusedProvider: () -> Boolean): WindowInfo =
        mockk { every { isWindowFocused } answers { isWindowFocusedProvider() } }

    private fun createLifecycleFixture(state: Lifecycle.State): LifecycleFixture {
        val mockLifecycleOwner: LifecycleOwner = mockk()
        val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(mockLifecycleOwner)
        every { mockLifecycleOwner.lifecycle } returns lifecycleRegistry
        lifecycleRegistry.currentState = state
        return LifecycleFixture(owner = mockLifecycleOwner, registry = lifecycleRegistry)
    }
}
