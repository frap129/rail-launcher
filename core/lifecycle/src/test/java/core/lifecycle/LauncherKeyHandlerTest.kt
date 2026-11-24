package core.lifecycle

import android.view.KeyEvent
import core.ui.model.data.KeyboardShortcut
import core.ui.model.data.LauncherSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LauncherKeyHandlerTest {
    @Test
    fun `Esc acts as Back`() {
        var actualBackCalls: Int = 0
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = true,
            isTypingAccepted = true,
            onBack = { actualBackCalls++ }
        )

        val actualHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = KeyEvent.KEYCODE_ESCAPE,
            event = createKeyEvent(keyCode = KeyEvent.KEYCODE_ESCAPE)
        )

        assertTrue(actualHandled)
        assertEquals(1, actualBackCalls)
    }

    @Test
    fun `a Ctrl Esc shortcut passes through`() {
        var actualBackCalls: Int = 0
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = true,
            isTypingAccepted = true,
            onBack = { actualBackCalls++ }
        )

        val actualHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = KeyEvent.KEYCODE_ESCAPE,
            event = createKeyEvent(keyCode = KeyEvent.KEYCODE_ESCAPE, metaState = KeyEvent.META_CTRL_ON)
        )

        assertFalse(actualHandled)
        assertEquals(0, actualBackCalls)
    }

    @Test
    fun `every listed shortcut fires for each of its modifiers`() {
        KeyboardShortcut.entries.forEach { inputShortcut ->
            inputShortcut.keyCodes.forEach { inputKeyCode ->
                KeyboardShortcut.modifiers.forEach { inputModifier ->
                    assertShortcutFires(
                        inputKeyCode = inputKeyCode,
                        inputMetaState = inputModifier,
                        expectedSignal = inputShortcut.signal
                    )
                }
            }
        }
    }

    @Test
    fun `Ctrl or Meta with each primary or numpad digit opens its row index`() {
        val expectedSignals: List<LauncherSignal> = listOf(
            LauncherSignal.OpenIndex(index = 0),
            LauncherSignal.OpenIndex(index = 1),
            LauncherSignal.OpenIndex(index = 2),
            LauncherSignal.OpenIndex(index = 3),
            LauncherSignal.OpenIndex(index = 4),
            LauncherSignal.OpenIndex(index = 5),
            LauncherSignal.OpenIndex(index = 6),
            LauncherSignal.OpenIndex(index = 7),
            LauncherSignal.OpenIndex(index = 8)
        )
        expectedSignals.forEachIndexed { index, expectedSignal ->
            val inputKeyCodes: List<Int> = listOf(KeyEvent.KEYCODE_1 + index, KeyEvent.KEYCODE_NUMPAD_1 + index)
            inputKeyCodes.forEach { inputKeyCode ->
                KeyboardShortcut.modifiers.forEach { inputModifier ->
                    assertShortcutFires(
                        inputKeyCode = inputKeyCode,
                        inputMetaState = inputModifier,
                        expectedSignal = expectedSignal
                    )
                }
            }
        }
    }

    @Test
    fun `other modifier combinations pass through`() {
        val inputMetaStates: List<Int> = listOf(
            KeyEvent.META_ALT_ON,
            KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON,
            KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
            KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON,
            KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
            KeyEvent.META_META_ON or KeyEvent.META_SHIFT_ON
        )

        KeyboardShortcut.entries.forEach { inputShortcut ->
            inputShortcut.keyCodes.forEach { inputKeyCode ->
                inputMetaStates.forEach { inputMetaState ->
                    assertPassesThrough(inputKeyCode = inputKeyCode, inputMetaState = inputMetaState)
                }
            }
        }
    }

    @Test
    fun `an unlisted modified key passes through`() {
        val inputKeyCode: Int = (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).first { keyCode ->
            KeyboardShortcut.entries.none { shortcut -> keyCode in shortcut.keyCodes }
        }

        assertPassesThrough(inputKeyCode = inputKeyCode, inputMetaState = KeyEvent.META_CTRL_ON)
        assertPassesThrough(inputKeyCode = inputKeyCode, inputMetaState = KeyEvent.META_META_ON)
    }

    @Test
    fun `every listed shortcut passes through outside the launcher`() {
        KeyboardShortcut.entries.forEach { inputShortcut ->
            inputShortcut.keyCodes.forEach { inputKeyCode ->
                KeyboardShortcut.modifiers.forEach { inputModifier ->
                    assertPassesThrough(
                        inputKeyCode = inputKeyCode,
                        inputMetaState = inputModifier,
                        isLauncherShown = false
                    )
                }
            }
        }
    }

    @Test
    fun `shortcuts are ignored while a text field has focus`() {
        KeyboardShortcut.entries.forEach { inputShortcut ->
            inputShortcut.keyCodes.forEach { inputKeyCode ->
                KeyboardShortcut.modifiers.forEach { inputModifier ->
                    assertPassesThrough(
                        inputKeyCode = inputKeyCode,
                        inputMetaState = inputModifier,
                        isTextFieldFocused = true
                    )
                }
            }
        }
    }

    @Test
    fun `a Typed character accepted by the launcher is consumed and signalled`() {
        assertTypedCharacterFires(inputKeyCode = KeyEvent.KEYCODE_A, expectedCharacter = "a")
    }

    @Test
    fun `plain digits still open Search with the digit`() {
        val expectedCharacters: List<String> = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
        expectedCharacters.forEachIndexed { index, expectedCharacter ->
            assertTypedCharacterFires(inputKeyCode = KeyEvent.KEYCODE_1 + index, expectedCharacter = expectedCharacter)
        }
    }

    @Test
    fun `a Typed character the launcher does not accept passes through`() {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = true,
            isTypingAccepted = false,
            onSignal = actualSignals::add
        )

        val actualHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = createKeyEvent(keyCode = KeyEvent.KEYCODE_A)
        )

        assertFalse(actualHandled)
        assertTrue(actualSignals.isEmpty())
    }

    @Test
    fun `typing outside the launcher passes through`() {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = false,
            isTypingAccepted = true,
            onSignal = actualSignals::add
        )

        val actualHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = createKeyEvent(keyCode = KeyEvent.KEYCODE_A)
        )

        assertFalse(actualHandled)
        assertTrue(actualSignals.isEmpty())
    }

    @Test
    fun `a Typed character and a Keyboard shortcut fire while a row has focus`() {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = true,
            isTypingAccepted = true,
            onSignal = actualSignals::add
        )

        // Row focus never reports text-field focus, so the launcher still accepts both keys.
        val actualTypedHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = createKeyEvent(keyCode = KeyEvent.KEYCODE_A)
        )
        val actualShortcutHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = KeyEvent.KEYCODE_F,
            event = createKeyEvent(keyCode = KeyEvent.KEYCODE_F, metaState = KeyEvent.META_CTRL_ON)
        )

        assertTrue(actualTypedHandled)
        assertTrue(actualShortcutHandled)
        assertEquals(
            listOf(LauncherSignal.TypedCharacter(character = "a"), LauncherSignal.OpenSearch),
            actualSignals
        )
    }

    @Test
    fun `arrow keys pass through to Row focus`() {
        listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN).forEach { inputKeyCode ->
            assertPassesThrough(inputKeyCode = inputKeyCode, inputMetaState = 0)
        }
    }

    @Test
    fun `a non-printing key passes through`() {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = true,
            isTypingAccepted = true,
            onSignal = actualSignals::add
        )

        val actualHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = KeyEvent.KEYCODE_ENTER,
            event = createKeyEvent(keyCode = KeyEvent.KEYCODE_ENTER)
        )

        assertFalse(actualHandled)
        assertTrue(actualSignals.isEmpty())
    }

    private fun assertTypedCharacterFires(inputKeyCode: Int, expectedCharacter: String) {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = true,
            isTypingAccepted = true,
            onSignal = actualSignals::add
        )
        val actualHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = inputKeyCode,
            event = createKeyEvent(keyCode = inputKeyCode)
        )
        assertTrue("Expected key code $inputKeyCode to be consumed", actualHandled)
        assertEquals(
            "Unexpected Typed character for key code $inputKeyCode",
            listOf(LauncherSignal.TypedCharacter(character = expectedCharacter)),
            actualSignals
        )
    }

    private fun assertShortcutFires(inputKeyCode: Int, inputMetaState: Int, expectedSignal: LauncherSignal) {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherKeyHandler: LauncherKeyHandler = createHandler(onSignal = actualSignals::add)

        val actualHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = inputKeyCode,
            event = createKeyEvent(keyCode = inputKeyCode, metaState = inputMetaState)
        )

        assertTrue(actualHandled)
        assertEquals(listOf(expectedSignal), actualSignals)
    }

    private fun assertPassesThrough(
        inputKeyCode: Int,
        inputMetaState: Int,
        isLauncherShown: Boolean = true,
        isTextFieldFocused: Boolean = false
    ) {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = isLauncherShown,
            isTextFieldFocused = isTextFieldFocused,
            onSignal = actualSignals::add
        )

        val actualHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = inputKeyCode,
            event = createKeyEvent(keyCode = inputKeyCode, metaState = inputMetaState)
        )

        assertFalse(actualHandled)
        assertTrue(actualSignals.isEmpty())
    }

    private fun createHandler(
        isLauncherShown: Boolean = true,
        isTypingAccepted: Boolean = false,
        isTextFieldFocused: Boolean = false,
        onBack: () -> Unit = {},
        onSignal: (LauncherSignal) -> Unit = {}
    ): LauncherKeyHandler = LauncherKeyHandler(
        isLauncherShown = { isLauncherShown },
        isTypingAccepted = { isTypingAccepted },
        isTextFieldFocused = { isTextFieldFocused },
        onBack = onBack,
        onSignal = onSignal
    )

    private fun createKeyEvent(keyCode: Int, metaState: Int = 0): KeyEvent = KeyEvent(
        0L,
        0L,
        KeyEvent.ACTION_DOWN,
        keyCode,
        0,
        metaState,
        0,
        0,
        0
    )
}
