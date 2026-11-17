package core.ui.composables

import core.data.testing.TEST_SDK
import core.data.testing.createApp
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
