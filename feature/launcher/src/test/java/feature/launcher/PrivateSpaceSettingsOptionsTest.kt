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
class PrivateSpaceSettingsOptionsTest {
    @Test
    @Config(sdk = [Build.VERSION_CODES.BAKLAVA])
    fun `opts the system screen into a visible background start on Android 16`() {
        val actualOptions: ActivityOptions? = privateSpaceSettingsOptions()

        assertEquals(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE,
            actualOptions?.pendingIntentBackgroundActivityStartMode
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
    fun `leaves the launch unchanged before the Private Space settings exist`() {
        val actualOptions: ActivityOptions? = privateSpaceSettingsOptions()

        assertNull(actualOptions)
    }
}
