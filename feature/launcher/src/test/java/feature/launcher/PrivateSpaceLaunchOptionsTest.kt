package feature.launcher

import android.app.ActivityOptions
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PrivateSpaceLaunchOptionsTest {
    @Test
    @Config(sdk = [Build.VERSION_CODES.BAKLAVA])
    fun `opts a system sender launch into a visible background start on Android 16`() {
        val actualOptions: ActivityOptions? = privateSpaceLaunchOptions()

        assertEquals(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE,
            actualOptions?.pendingIntentBackgroundActivityStartMode
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    fun `leaves the launch unchanged before the system offers the mode`() {
        val actualOptions: ActivityOptions? = privateSpaceLaunchOptions()

        assertNull(actualOptions)
    }
}
