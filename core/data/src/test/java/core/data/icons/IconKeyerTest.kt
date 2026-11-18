package core.data.icons

import android.content.ComponentName
import android.os.UserHandle
import coil3.request.Options
import coil3.size.Size
import core.data.icons.model.Icon
import core.data.icons.model.UNKNOWN_APP_UPDATE_TIME
import core.data.testing.SECOND_ICON_PACK_PACKAGE
import core.data.testing.TEST_APP_UPDATE_TIME
import core.data.testing.TEST_CUSTOM_ICON_DRAWABLE_NAME
import core.data.testing.TEST_GALLERY_ICON_FILE_NAME
import core.data.testing.TEST_ICON_PACK_PACKAGE
import core.data.testing.TEST_ICON_SIZE_PX
import core.data.testing.TEST_RAIL_ICON_RES_ID
import core.data.testing.TEST_SDK
import core.data.testing.WORK_USER_ID
import core.data.testing.createUserHandle
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val SECOND_RAIL_ICON_RES_ID: Int = 2
private const val APPLICATION_PACKAGE: String = "com.example.app"
private const val SECOND_APPLICATION_PACKAGE: String = "com.example.other"
private const val ACTIVITY_CLASS: String = "com.example.app.MainActivity"
private const val SECOND_ACTIVITY_CLASS: String = "com.example.app.OtherActivity"
private const val PACK_ICON_RES_ID: Int = 10
private const val SECOND_PACK_ICON_RES_ID: Int = 11
private const val SHARED_IDENTITY: Int = 42
private const val SHORTCUT_PACKAGE: String = "com.example.app"
private const val SHORTCUT_ID: String = "shortcut-id"
private const val SECOND_SHORTCUT_ID: String = "second-shortcut-id"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IconKeyerTest {
    private val mockHomeIconCache: IHomeIconCache = mockk {
        every { generation(any()) } returns 0L
        every { configurationGeneration } returns 0L
    }
    private val keyer: IconKeyer = IconKeyer(homeIconCache = mockHomeIconCache)
    private val options: Options = Options(RuntimeEnvironment.getApplication())
    private val componentName: ComponentName = ComponentName(APPLICATION_PACKAGE, ACTIVITY_CLASS)
    private val profile: UserHandle = UserHandle.getUserHandleForUid(0)

    @Test
    fun `equal rail icons produce equal cache keys`() {
        assertEqualKeys(Icon.RailIcon(TEST_RAIL_ICON_RES_ID), Icon.RailIcon(TEST_RAIL_ICON_RES_ID))
    }

    @Test
    fun `equal application icons produce equal cache keys`() {
        assertEqualKeys(Icon.ApplicationIcon(APPLICATION_PACKAGE), Icon.ApplicationIcon(APPLICATION_PACKAGE))
    }

    @Test
    fun `equal activity icons produce equal cache keys`() {
        assertEqualKeys(
            Icon.ActivityIcon(componentName, UNKNOWN_APP_UPDATE_TIME),
            Icon.ActivityIcon(componentName, UNKNOWN_APP_UPDATE_TIME)
        )
    }

    @Test
    fun `equal icon pack icons produce equal cache keys`() {
        assertEqualKeys(
            Icon.PackIcon(componentName, TEST_ICON_PACK_PACKAGE, PACK_ICON_RES_ID),
            Icon.PackIcon(componentName, TEST_ICON_PACK_PACKAGE, PACK_ICON_RES_ID)
        )
    }

    @Test
    fun `equal pack drawable icons produce equal cache keys`() {
        assertEqualKeys(
            Icon.PackDrawableIcon(TEST_ICON_PACK_PACKAGE, TEST_CUSTOM_ICON_DRAWABLE_NAME),
            Icon.PackDrawableIcon(TEST_ICON_PACK_PACKAGE, TEST_CUSTOM_ICON_DRAWABLE_NAME)
        )
    }

    @Test
    fun `equal image icons produce equal cache keys`() {
        assertEqualKeys(Icon.ImageIcon(TEST_GALLERY_ICON_FILE_NAME), Icon.ImageIcon(TEST_GALLERY_ICON_FILE_NAME))
    }

    @Test
    fun `image icons with different files produce different cache keys`() {
        assertDifferentKeys(Icon.ImageIcon(TEST_GALLERY_ICON_FILE_NAME), Icon.ImageIcon("other-icon.png"))
    }

    @Test
    fun `an image icon and a pack drawable icon produce different cache keys`() {
        assertDifferentKeys(
            Icon.ImageIcon(TEST_CUSTOM_ICON_DRAWABLE_NAME),
            Icon.PackDrawableIcon(TEST_ICON_PACK_PACKAGE, TEST_CUSTOM_ICON_DRAWABLE_NAME)
        )
    }

    @Test
    fun `custom icon cache keys are their stored forms`() {
        assertEquals(
            "pack-drawable:com.example.iconpack:calendar",
            keyer.key(Icon.PackDrawableIcon(TEST_ICON_PACK_PACKAGE, TEST_CUSTOM_ICON_DRAWABLE_NAME), options)
        )
        assertEquals("image:gallery-icon.png", keyer.key(Icon.ImageIcon(TEST_GALLERY_ICON_FILE_NAME), options))
    }

    @Test
    fun `pack drawable icons with different packs or drawables produce different cache keys`() {
        assertDifferentKeys(
            Icon.PackDrawableIcon(TEST_ICON_PACK_PACKAGE, TEST_CUSTOM_ICON_DRAWABLE_NAME),
            Icon.PackDrawableIcon(SECOND_ICON_PACK_PACKAGE, TEST_CUSTOM_ICON_DRAWABLE_NAME)
        )
        assertDifferentKeys(
            Icon.PackDrawableIcon(TEST_ICON_PACK_PACKAGE, TEST_CUSTOM_ICON_DRAWABLE_NAME),
            Icon.PackDrawableIcon(TEST_ICON_PACK_PACKAGE, "mail")
        )
    }

    @Test
    fun `a pack drawable icon and a pack icon produce different cache keys`() {
        assertDifferentKeys(
            Icon.PackDrawableIcon(TEST_ICON_PACK_PACKAGE, TEST_CUSTOM_ICON_DRAWABLE_NAME),
            Icon.PackIcon(componentName, TEST_ICON_PACK_PACKAGE, PACK_ICON_RES_ID)
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
            Icon.ProfileIcon(icon = Icon.ActivityIcon(componentName, UNKNOWN_APP_UPDATE_TIME), profile = profile),
            Icon.ProfileIcon(icon = Icon.ActivityIcon(componentName, UNKNOWN_APP_UPDATE_TIME), profile = profile)
        )
    }

    @Test
    fun `Profile icons with different Profiles produce different cache keys`() {
        val otherProfile: UserHandle = createUserHandle(userId = WORK_USER_ID)

        assertDifferentKeys(
            Icon.ProfileIcon(icon = Icon.ActivityIcon(componentName, UNKNOWN_APP_UPDATE_TIME), profile = profile),
            Icon.ProfileIcon(icon = Icon.ActivityIcon(componentName, UNKNOWN_APP_UPDATE_TIME), profile = otherProfile)
        )
    }

    @Test
    fun `Profile icons with different base icons produce different cache keys`() {
        assertDifferentKeys(
            Icon.ProfileIcon(icon = Icon.ActivityIcon(componentName, UNKNOWN_APP_UPDATE_TIME), profile = profile),
            Icon.ProfileIcon(icon = Icon.ApplicationIcon(APPLICATION_PACKAGE), profile = profile)
        )
    }

    @Test
    fun `different rail icons produce different cache keys`() {
        assertDifferentKeys(Icon.RailIcon(TEST_RAIL_ICON_RES_ID), Icon.RailIcon(SECOND_RAIL_ICON_RES_ID))
    }

    @Test
    fun `different application icons produce different cache keys`() {
        assertDifferentKeys(Icon.ApplicationIcon(APPLICATION_PACKAGE), Icon.ApplicationIcon(SECOND_APPLICATION_PACKAGE))
    }

    @Test
    fun `different activity icons produce different cache keys`() {
        assertDifferentKeys(
            Icon.ActivityIcon(componentName, UNKNOWN_APP_UPDATE_TIME),
            Icon.ActivityIcon(ComponentName(APPLICATION_PACKAGE, SECOND_ACTIVITY_CLASS), UNKNOWN_APP_UPDATE_TIME)
        )
    }

    @Test
    fun `activity icons of different update times produce different cache keys`() {
        assertDifferentKeys(
            Icon.ActivityIcon(componentName = componentName, lastUpdateTime = TEST_APP_UPDATE_TIME),
            Icon.ActivityIcon(componentName = componentName, lastUpdateTime = TEST_APP_UPDATE_TIME + 1L)
        )
    }

    @Test
    fun `icon pack icons with different packs produce different cache keys`() {
        assertDifferentKeys(
            Icon.PackIcon(componentName, TEST_ICON_PACK_PACKAGE, PACK_ICON_RES_ID),
            Icon.PackIcon(componentName, SECOND_ICON_PACK_PACKAGE, PACK_ICON_RES_ID)
        )
    }

    @Test
    fun `icon pack icons with different resources produce different cache keys`() {
        assertDifferentKeys(
            Icon.PackIcon(componentName, TEST_ICON_PACK_PACKAGE, PACK_ICON_RES_ID),
            Icon.PackIcon(componentName, TEST_ICON_PACK_PACKAGE, SECOND_PACK_ICON_RES_ID)
        )
    }

    @Test
    fun `a revalidated icon produces a new cache key`() {
        val mockRevalidatingCache: IHomeIconCache = mockk()
        every { mockRevalidatingCache.generation(any()) } returns 0L
        every { mockRevalidatingCache.configurationGeneration } returns 0L
        val inputKeyer: IconKeyer = IconKeyer(homeIconCache = mockRevalidatingCache)
        val inputIcon: Icon = Icon.ActivityIcon(componentName, TEST_APP_UPDATE_TIME)
        val inputOptions: Options =
            Options(context = RuntimeEnvironment.getApplication(), size = Size(TEST_ICON_SIZE_PX, TEST_ICON_SIZE_PX))
        val expectedKey: String = inputKeyer.key(inputIcon, inputOptions)
        every { mockRevalidatingCache.generation(any()) } returns 1L
        assertNotEquals(expectedKey, inputKeyer.key(inputIcon, inputOptions))
    }

    @Test
    fun `a configuration change that can alter system icons produces a new cache key`() {
        val mockConfiguredCache: IHomeIconCache = mockk()
        every { mockConfiguredCache.generation(any()) } returns 0L
        every { mockConfiguredCache.configurationGeneration } returns 0L
        val inputKeyer: IconKeyer = IconKeyer(homeIconCache = mockConfiguredCache)
        val inputIcon: Icon = Icon.ActivityIcon(componentName, TEST_APP_UPDATE_TIME)
        val inputOptions: Options =
            Options(context = RuntimeEnvironment.getApplication(), size = Size(TEST_ICON_SIZE_PX, TEST_ICON_SIZE_PX))
        val expectedKey: String = inputKeyer.key(inputIcon, inputOptions)
        every { mockConfiguredCache.configurationGeneration } returns 1L
        assertNotEquals(expectedKey, inputKeyer.key(inputIcon, inputOptions))
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
