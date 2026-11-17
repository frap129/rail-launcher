package core.ui.composables

import coil3.compose.AsyncImagePainter
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class LaunchableIconTest {
    @Test
    fun `greys the icon of a Suspended App only`() {
        assertNull(createApp().iconColorFilter)
        assertNotNull(createApp(isSuspended = true).iconColorFilter)
    }

    @Test
    fun `settles an icon that drew or failed`() {
        assertTrue(mockk<AsyncImagePainter.State.Success>().hasSettled())
        assertTrue(mockk<AsyncImagePainter.State.Error>().hasSettled())
    }

    @Test
    fun `does not settle an icon that is still loading`() {
        assertFalse(mockk<AsyncImagePainter.State.Loading>().hasSettled())
        assertFalse(AsyncImagePainter.State.Empty.hasSettled())
    }
}
