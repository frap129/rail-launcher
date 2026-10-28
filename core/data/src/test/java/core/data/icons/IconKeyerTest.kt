package core.data.icons

import android.content.ComponentName
import android.os.UserHandle
import coil3.request.Options
import core.data.icons.model.Icon
import core.data.testing.TEST_SDK
import core.data.testing.WORK_USER_ID
import core.data.testing.createUserHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val RAIL_ICON_RES_ID: Int = 1
private const val SECOND_RAIL_ICON_RES_ID: Int = 2
private const val APPLICATION_PACKAGE: String = "com.example.app"
private const val SECOND_APPLICATION_PACKAGE: String = "com.example.other"
private const val ACTIVITY_CLASS: String = "com.example.app.MainActivity"
private const val SECOND_ACTIVITY_CLASS: String = "com.example.app.OtherActivity"
private const val ICON_PACK_PACKAGE: String = "com.example.iconpack"
private const val SECOND_ICON_PACK_PACKAGE: String = "com.example.othericonpack"
private const val PACK_ICON_RES_ID: Int = 10
private const val SECOND_PACK_ICON_RES_ID: Int = 11
private const val SHARED_IDENTITY: Int = 42
private const val SHORTCUT_PACKAGE: String = "com.example.app"
private const val SHORTCUT_ID: String = "shortcut-id"
private const val SECOND_SHORTCUT_ID: String = "second-shortcut-id"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IconKeyerTest {
    private val keyer: IconKeyer = IconKeyer()
    private val options: Options = Options(RuntimeEnvironment.getApplication())
    private val componentName: ComponentName = ComponentName(APPLICATION_PACKAGE, ACTIVITY_CLASS)
    private val profile: UserHandle = UserHandle.getUserHandleForUid(0)

    @Test
    fun `equal rail icons produce equal cache keys`() {
        assertEqualKeys(Icon.RailIcon(RAIL_ICON_RES_ID), Icon.RailIcon(RAIL_ICON_RES_ID))
    }

    @Test
    fun `equal application icons produce equal cache keys`() {
        assertEqualKeys(Icon.ApplicationIcon(APPLICATION_PACKAGE), Icon.ApplicationIcon(APPLICATION_PACKAGE))
    }

    @Test
    fun `equal activity icons produce equal cache keys`() {
        assertEqualKeys(Icon.ActivityIcon(componentName), Icon.ActivityIcon(componentName))
    }

    @Test
    fun `equal icon pack icons produce equal cache keys`() {
        assertEqualKeys(
            Icon.PackIcon(componentName, ICON_PACK_PACKAGE, PACK_ICON_RES_ID),
            Icon.PackIcon(componentName, ICON_PACK_PACKAGE, PACK_ICON_RES_ID)
        )
    }

    @Test
    fun `equal shortcut icons produce equal cache keys`() {
        assertEqualKeys(
            Icon.ShortcutIcon(SHORTCUT_PACKAGE, SHORTCUT_ID, profile),
            Icon.ShortcutIcon(SHORTCUT_PACKAGE, SHORTCUT_ID, profile)
        )
    }

    @Test
    fun `shortcut icons with different ids produce different cache keys`() {
        assertDifferentKeys(
            Icon.ShortcutIcon(SHORTCUT_PACKAGE, SHORTCUT_ID, profile),
            Icon.ShortcutIcon(SHORTCUT_PACKAGE, SECOND_SHORTCUT_ID, profile)
        )
    }

    @Test
    fun `equal Profile icons produce equal cache keys`() {
        assertEqualKeys(
            Icon.ProfileIcon(icon = Icon.ActivityIcon(componentName), profile = profile),
            Icon.ProfileIcon(icon = Icon.ActivityIcon(componentName), profile = profile)
        )
    }

    @Test
    fun `Profile icons with different Profiles produce different cache keys`() {
        val otherProfile: UserHandle = createUserHandle(userId = WORK_USER_ID)

        assertDifferentKeys(
            Icon.ProfileIcon(icon = Icon.ActivityIcon(componentName), profile = profile),
            Icon.ProfileIcon(icon = Icon.ActivityIcon(componentName), profile = otherProfile)
        )
    }

    @Test
    fun `Profile icons with different base icons produce different cache keys`() {
        assertDifferentKeys(
            Icon.ProfileIcon(icon = Icon.ActivityIcon(componentName), profile = profile),
            Icon.ProfileIcon(icon = Icon.ApplicationIcon(APPLICATION_PACKAGE), profile = profile)
        )
    }

    @Test
    fun `different rail icons produce different cache keys`() {
        assertDifferentKeys(Icon.RailIcon(RAIL_ICON_RES_ID), Icon.RailIcon(SECOND_RAIL_ICON_RES_ID))
    }

    @Test
    fun `different application icons produce different cache keys`() {
        assertDifferentKeys(Icon.ApplicationIcon(APPLICATION_PACKAGE), Icon.ApplicationIcon(SECOND_APPLICATION_PACKAGE))
    }

    @Test
    fun `different activity icons produce different cache keys`() {
        assertDifferentKeys(
            Icon.ActivityIcon(componentName),
            Icon.ActivityIcon(ComponentName(APPLICATION_PACKAGE, SECOND_ACTIVITY_CLASS))
        )
    }

    @Test
    fun `icon pack icons with different packs produce different cache keys`() {
        assertDifferentKeys(
            Icon.PackIcon(componentName, ICON_PACK_PACKAGE, PACK_ICON_RES_ID),
            Icon.PackIcon(componentName, SECOND_ICON_PACK_PACKAGE, PACK_ICON_RES_ID)
        )
    }

    @Test
    fun `icon pack icons with different resources produce different cache keys`() {
        assertDifferentKeys(
            Icon.PackIcon(componentName, ICON_PACK_PACKAGE, PACK_ICON_RES_ID),
            Icon.PackIcon(componentName, ICON_PACK_PACKAGE, SECOND_PACK_ICON_RES_ID)
        )
    }

    @Test
    fun `icons of different types produce different cache keys`() {
        assertDifferentKeys(Icon.RailIcon(SHARED_IDENTITY), Icon.ApplicationIcon(SHARED_IDENTITY.toString()))
    }

    private fun assertEqualKeys(icon: Icon, sameIcon: Icon) {
        assertEquals(keyer.key(icon, options), keyer.key(sameIcon, options))
    }

    private fun assertDifferentKeys(icon: Icon, otherIcon: Icon) {
        assertNotEquals(keyer.key(icon, options), keyer.key(otherIcon, options))
    }
}
