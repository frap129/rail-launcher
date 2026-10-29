package core.data.popups

import android.content.ComponentName
import core.data.apps.model.App
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupOwnerTest {
    @Test
    fun `only the package representative owns the package's Shortcuts and notifications`() {
        val inputRepresentative: App = createApp(name = "Example", packageName = "com.example.app")
        val inputOtherActivity: App = inputRepresentative.copy(
            componentName = ComponentName(inputRepresentative.packageName, "${inputRepresentative.packageName}.OtherActivity"),
            isPackageRepresentative = false
        )

        val actualRepresentative: ShortcutTarget? = targetFor(owner = inputRepresentative)
        val actualOtherActivity: ShortcutTarget? = targetFor(owner = inputOtherActivity)

        assertEquals(inputRepresentative.packageName, actualRepresentative?.packageName)
        assertEquals(inputRepresentative.profile, actualRepresentative?.profile)
        assertNull(actualOtherActivity?.packageName)
        assertNull(actualOtherActivity?.profile)
    }
}
