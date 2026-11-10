package feature.launcher

import android.content.ClipboardManager
import android.content.Context
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class CalculatorClipboardTest {
    @Test
    fun `copies a Calculator row result to the clipboard`() {
        val inputContext: Context = RuntimeEnvironment.getApplication()

        val actualCopied: Boolean = copyCalculatorRowResult(context = inputContext, result = "3,5")

        val actualClipboard: ClipboardManager = requireNotNull(inputContext.getSystemService(ClipboardManager::class.java))
        assertTrue(actualCopied)
        assertEquals("3,5", actualClipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }
}
