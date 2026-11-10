package core.lifecycle

import android.view.KeyEvent
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
    fun `a Typed character accepted by the launcher is consumed and signalled`() {
        val actualSignals: MutableList<LauncherSignal> = mutableListOf()
        val launcherKeyHandler: LauncherKeyHandler = createHandler(
            isLauncherShown = true,
            isTypingAccepted = true,
            onSignal = actualSignals::add
        )

        val actualHandled: Boolean = launcherKeyHandler.onKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = createKeyEvent(keyCode = KeyEvent.KEYCODE_A)
        )

        assertTrue(actualHandled)
        assertEquals(listOf(LauncherSignal.TypedCharacter(character = "a")), actualSignals)
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

    private fun createHandler(
        isLauncherShown: Boolean,
        isTypingAccepted: Boolean,
        onBack: () -> Unit = {},
        onSignal: (LauncherSignal) -> Unit = {}
    ): LauncherKeyHandler = LauncherKeyHandler(
        isLauncherShown = { isLauncherShown },
        isTypingAccepted = { isTypingAccepted },
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
