package core.lifecycle

import android.view.KeyEvent
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.ui.model.data.KeyboardShortcut
import core.ui.model.data.LauncherInputState
import core.ui.model.data.LauncherSignal
import core.ui.model.data.TextFieldFocusToken
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
    fun `Ctrl Shift slash opens the Keyboard shortcuts help sheet`() {
        assertShortcutFires(
            inputKeyCode = KeyEvent.KEYCODE_SLASH,
            inputMetaState = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
            expectedSignal = LauncherSignal.OpenKeyboardShortcutsHelp
        )
    }

    @Test
    fun `Meta slash passes through to the System shortcuts helper`() {
        assertPassesThrough(inputKeyCode = KeyEvent.KEYCODE_SLASH, inputMetaState = KeyEvent.META_META_ON)
    }

    @Test
    fun `a Ctrl Esc shortcut passes through`() {
        var actualBackCalls: Int = 0
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = true,
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
    fun `every Keyboard shortcut fires for each of its modifiers, including Tab`() {
        forEachShortcutInput { inputShortcut: KeyboardShortcut, inputKeyCode: Int, inputMetaState: Int ->
            assertShortcutFires(
                inputKeyCode = inputKeyCode,
                inputMetaState = inputMetaState,
                expectedSignal = inputShortcut.signal
            )
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
        val inputModifiers: List<Int> = listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_META_ON)
        expectedSignals.forEachIndexed { index, expectedSignal ->
            val inputKeyCodes: List<Int> = listOf(KeyEvent.KEYCODE_1 + index, KeyEvent.KEYCODE_NUMPAD_1 + index)
            inputKeyCodes.forEach { inputKeyCode ->
                inputModifiers.forEach { inputModifier ->
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
            KeyEvent.META_SHIFT_ON,
            KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON,
            KeyEvent.META_META_ON or KeyEvent.META_ALT_ON,
            KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON
        )

        forEachShortcutInput(
            resolveModifierStates = { _: KeyboardShortcut -> inputMetaStates }
        ) { _: KeyboardShortcut, inputKeyCode: Int, inputMetaState: Int ->
            assertPassesThrough(inputKeyCode = inputKeyCode, inputMetaState = inputMetaState)
        }
    }

    @Test
    fun `Shift combinations pass through for shortcuts that reject Shift`() {
        val inputMetaStates: List<Int> = listOf(
            KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
            KeyEvent.META_META_ON or KeyEvent.META_SHIFT_ON
        )
        forEachShortcutInput(
            inputShortcuts = KeyboardShortcut.entries.filterNot { shortcut: KeyboardShortcut -> shortcut.isShiftAllowed },
            resolveModifierStates = { _: KeyboardShortcut -> inputMetaStates }
        ) { _: KeyboardShortcut, inputKeyCode: Int, inputMetaState: Int ->
            assertPassesThrough(inputKeyCode = inputKeyCode, inputMetaState = inputMetaState)
        }
    }

    @Test
    fun `an unlisted modified key passes through`() {
        val inputKeyCode: Int = KeyEvent.KEYCODE_VOLUME_UP

        assertPassesThrough(inputKeyCode = inputKeyCode, inputMetaState = KeyEvent.META_CTRL_ON)
        assertPassesThrough(inputKeyCode = inputKeyCode, inputMetaState = KeyEvent.META_META_ON)
    }

    @Test
    fun `every Keyboard shortcut passes through outside the launcher`() {
        forEachShortcutInput { _: KeyboardShortcut, inputKeyCode: Int, inputMetaState: Int ->
            assertPassesThrough(
                inputKeyCode = inputKeyCode,
                inputMetaState = inputMetaState,
                isLauncherShown = false
            )
        }
    }

    @Test
    fun `Settings and Agenda shortcuts pass through in every Home edit mode`() {
        val inputShortcuts: List<KeyboardShortcut> = listOf(KeyboardShortcut.Settings, KeyboardShortcut.Agenda)
        val inputModes: List<HomeEditMode> = listOf(
            HomeEditMode.Favorites,
            HomeEditMode.Resizing(placeId = 7L),
            HomeEditMode.Widgets,
            HomeEditMode.MovingWidgetArea
        )
        inputModes.forEach { inputMode: HomeEditMode ->
            val inputHomeEditState: HomeEditState = HomeEditState().apply { tryEnter(inputMode) }
            forEachShortcutInput(inputShortcuts = inputShortcuts) { _: KeyboardShortcut, inputKeyCode: Int, inputMetaState: Int ->
                assertPassesThrough(
                    inputKeyCode = inputKeyCode,
                    inputMetaState = inputMetaState,
                    inputHomeEditState = inputHomeEditState
                )
            }
        }
    }

    @Test
    fun `Tab passes through when no Tab surface is available`() {
        assertPassesThrough(
            inputKeyCode = KeyEvent.KEYCODE_TAB,
            inputMetaState = 0,
            inputState = LauncherInputState()
        )
    }

    @Test
    fun `Keyboard shortcuts are ignored while a text field has focus`() {
        val inputState: LauncherInputState = createInputState(isTextFieldFocused = true)
        forEachShortcutInput { _: KeyboardShortcut, inputKeyCode: Int, inputMetaState: Int ->
            assertPassesThrough(
                inputKeyCode = inputKeyCode,
                inputMetaState = inputMetaState,
                inputState = inputState
            )
        }
    }

    @Test
    fun `every unmodified letter and digit opens Search with its character`() {
        val expectedKeyCharacters: List<Pair<Int, Char>> =
            ('a'..'z').map { inputCharacter: Char ->
                KeyEvent.KEYCODE_A + (inputCharacter - 'a') to inputCharacter
            } + ('0'..'9').map { inputCharacter: Char ->
                KeyEvent.KEYCODE_0 + (inputCharacter - '0') to inputCharacter
            }
        expectedKeyCharacters.forEach { inputKeyCharacter: Pair<Int, Char> ->
            assertTypedCharacterFires(
                inputKeyCode = inputKeyCharacter.first,
                expectedCharacter = inputKeyCharacter.second.toString()
            )
        }
    }

    @Test
    fun `a Typed character the launcher does not accept passes through`() {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = true,
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
        val inputState: LauncherInputState = createInputState(acceptsTyping = true)
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = false,
            inputState = inputState,
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
        val inputState: LauncherInputState = createInputState(acceptsTyping = true)
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = true,
            inputState = inputState,
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
            onSignal = actualSignals::add
        )

        val actualHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = KeyEvent.KEYCODE_ENTER,
            event = createKeyEvent(keyCode = KeyEvent.KEYCODE_ENTER)
        )

        assertFalse(actualHandled)
        assertTrue(actualSignals.isEmpty())
    }

    private fun forEachShortcutInput(
        inputShortcuts: Iterable<KeyboardShortcut> = KeyboardShortcut.entries,
        resolveModifierStates: (KeyboardShortcut) -> List<Int> = { shortcut: KeyboardShortcut -> shortcut.modifierStates },
        onShortcutInput: (KeyboardShortcut, Int, Int) -> Unit
    ) {
        inputShortcuts.forEach { inputShortcut: KeyboardShortcut ->
            val inputModifierStates: List<Int> = resolveModifierStates(inputShortcut)
            inputShortcut.keyCodes.forEach { inputKeyCode: Int ->
                inputModifierStates.forEach { inputMetaState: Int ->
                    onShortcutInput(inputShortcut, inputKeyCode, inputMetaState)
                }
            }
        }
    }

    private fun assertTypedCharacterFires(inputKeyCode: Int, expectedCharacter: String) {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val inputState: LauncherInputState = createInputState(acceptsTyping = true)
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = true,
            inputState = inputState,
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
        inputState: LauncherInputState = createInputState(),
        inputHomeEditState: HomeEditState = HomeEditState()
    ) {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = isLauncherShown,
            inputState = inputState,
            homeEditState = inputHomeEditState,
            onSignal = actualSignals::add
        )
        val actualHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = inputKeyCode,
            event = createKeyEvent(keyCode = inputKeyCode, metaState = inputMetaState)
        )
        assertFalse("Expected key code $inputKeyCode with meta state $inputMetaState to pass through", actualHandled)
        assertTrue(actualSignals.isEmpty())
    }

    private fun createHandler(
        isLauncherShown: Boolean = true,
        inputState: LauncherInputState = createInputState(),
        homeEditState: HomeEditState = HomeEditState(),
        onBack: () -> Unit = {},
        onSignal: (LauncherSignal) -> Unit = {}
    ): LauncherKeyHandler = LauncherKeyHandler(
        isLauncherShown = { isLauncherShown },
        inputState = inputState,
        homeEditState = homeEditState,
        onBack = onBack,
        onSignal = onSignal
    )

    private fun createInputState(
        acceptsTyping: Boolean = false,
        canToggleAppList: Boolean = true,
        isTextFieldFocused: Boolean = false
    ): LauncherInputState = LauncherInputState().apply {
        setAcceptsTyping(acceptsTyping = acceptsTyping)
        setCanToggleAppList(canToggleAppList = canToggleAppList)
        if (isTextFieldFocused) {
            setTextFieldFocused(token = TextFieldFocusToken(), isFocused = true)
        }
    }

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
