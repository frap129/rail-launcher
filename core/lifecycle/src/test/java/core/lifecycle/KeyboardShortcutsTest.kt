package core.lifecycle

import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import core.ui.model.data.KeyboardShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardShortcutsTest {
    @Test
    fun `lists every shortcut for each of its modifiers`() {
        val inputGroupLabel: String = "Rail"

        val actualGroup: KeyboardShortcutGroup = createKeyboardShortcutGroup(
            label = inputGroupLabel,
            resolveLabel = { shortcut -> shortcut.name }
        )

        assertEquals(inputGroupLabel, actualGroup.label.toString())
        assertEquals(KeyboardShortcut.entries.size * KeyboardShortcut.modifiers.size, actualGroup.items.size)
        KeyboardShortcut.entries.forEach { inputShortcut ->
            val actualShortcutInfos: List<KeyboardShortcutInfo> =
                actualGroup.items.filter { info -> info.keycode == inputShortcut.keyCode }
            assertEquals(KeyboardShortcut.modifiers.toSet(), actualShortcutInfos.map { info -> info.modifiers }.toSet())
            assertTrue(actualShortcutInfos.all { info -> info.label.toString() == inputShortcut.name })
        }
    }
}
