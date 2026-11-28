package dev.maples.rail

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import core.lifecycle.MainApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = MainApplication::class, sdk = [35])
class MainActivityOrientationTest {
    @Test
    fun `keeps the same Activity instance when the requested orientation changes`() {
        val controller: ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java).setup()
        val expectedActivity: MainActivity = controller.get()
        var actualComposeOrientation: Int? = null
        expectedActivity.setContent {
            val configuration: Configuration = LocalConfiguration.current
            SideEffect { actualComposeOrientation = configuration.orientation }
        }
        shadowOf(Looper.getMainLooper()).idle()
        expectedActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val inputConfiguration: Configuration = Configuration(expectedActivity.resources.configuration).apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 800
            screenHeightDp = 400
            smallestScreenWidthDp = 400
            screenLayout = Configuration.SCREENLAYOUT_LONG_NO
        }
        try {
            controller.configurationChange(inputConfiguration)
            shadowOf(Looper.getMainLooper()).idle()

            val actualActivity: MainActivity = controller.get()
            assertSame(expectedActivity, actualActivity)
            assertEquals(Configuration.ORIENTATION_LANDSCAPE, actualActivity.resources.configuration.orientation)
            assertEquals(Configuration.ORIENTATION_LANDSCAPE, actualComposeOrientation)
        } finally {
            controller.destroy()
        }
    }
}
