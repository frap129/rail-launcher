package core.data.shortcuts

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.Process
import core.data.apps.model.AppProfile
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class ShortcutLaunchableTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val inputOwner: AppProfile = AppProfile(packageName = "com.example.app", profile = Process.myUserHandle())

    @Test
    fun `falls back from the long label to the short label to the package`() {
        assertEquals(
            "Long label",
            shortcut(shortLabel = "Short label", longLabel = "Long label").toShortcutLaunchable(inputOwner).defaultName
        )
        assertEquals(
            "Short label",
            shortcut(shortLabel = "Short label", longLabel = null).toShortcutLaunchable(inputOwner).defaultName
        )
        assertEquals(
            inputOwner.packageName,
            shortcut(shortLabel = null, longLabel = null).toShortcutLaunchable(inputOwner).defaultName
        )
    }

    private fun shortcut(shortLabel: String?, longLabel: String?): ShortcutInfo = ShortcutInfo.Builder(context, FIRST_SHORTCUT_ID)
        .apply {
            shortLabel?.let { label -> setShortLabel(label) }
            longLabel?.let { label -> setLongLabel(label) }
            setIntent(Intent(Intent.ACTION_VIEW).setPackage(inputOwner.packageName))
        }
        .build()
}
