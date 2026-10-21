package core.data.icons

import android.content.ComponentName
import coil3.request.Options
import core.data.icons.model.Icon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val TEST_SDK: Int = 35
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IconKeyerTest {
    private val keyer: IconKeyer = IconKeyer()
    private val options: Options = Options(RuntimeEnvironment.getApplication())
    private val componentName: ComponentName = ComponentName(APPLICATION_PACKAGE, ACTIVITY_CLASS)

    @Test
    fun `equal rail icons produce equal cache keys`() {
        val inputIcon: Icon = Icon.RailIcon(RAIL_ICON_RES_ID)
        val inputSameIcon: Icon = Icon.RailIcon(RAIL_ICON_RES_ID)
        val expectedKey: String = keyer.key(inputSameIcon, options)
        val actualKey: String = keyer.key(inputIcon, options)
        assertEquals(expectedKey, actualKey)
    }

    @Test
    fun `equal application icons produce equal cache keys`() {
        val inputIcon: Icon = Icon.ApplicationIcon(APPLICATION_PACKAGE)
        val inputSameIcon: Icon = Icon.ApplicationIcon(APPLICATION_PACKAGE)
        val expectedKey: String = keyer.key(inputSameIcon, options)
        val actualKey: String = keyer.key(inputIcon, options)
        assertEquals(expectedKey, actualKey)
    }

    @Test
    fun `equal activity icons produce equal cache keys`() {
        val inputIcon: Icon = Icon.ActivityIcon(componentName)
        val inputSameIcon: Icon = Icon.ActivityIcon(componentName)
        val expectedKey: String = keyer.key(inputSameIcon, options)
        val actualKey: String = keyer.key(inputIcon, options)
        assertEquals(expectedKey, actualKey)
    }

    @Test
    fun `equal icon pack icons produce equal cache keys`() {
        val inputIcon: Icon = Icon.PackIcon(componentName, ICON_PACK_PACKAGE, PACK_ICON_RES_ID)
        val inputSameIcon: Icon = Icon.PackIcon(componentName, ICON_PACK_PACKAGE, PACK_ICON_RES_ID)
        val expectedKey: String = keyer.key(inputSameIcon, options)
        val actualKey: String = keyer.key(inputIcon, options)
        assertEquals(expectedKey, actualKey)
    }

    @Test
    fun `different rail icons produce different cache keys`() {
        val inputIcon: Icon = Icon.RailIcon(RAIL_ICON_RES_ID)
        val inputOtherIcon: Icon = Icon.RailIcon(SECOND_RAIL_ICON_RES_ID)
        val actualKey: String = keyer.key(inputIcon, options)
        val actualOtherKey: String = keyer.key(inputOtherIcon, options)
        assertNotEquals(actualKey, actualOtherKey)
    }

    @Test
    fun `different application icons produce different cache keys`() {
        val inputIcon: Icon = Icon.ApplicationIcon(APPLICATION_PACKAGE)
        val inputOtherIcon: Icon = Icon.ApplicationIcon(SECOND_APPLICATION_PACKAGE)
        val actualKey: String = keyer.key(inputIcon, options)
        val actualOtherKey: String = keyer.key(inputOtherIcon, options)
        assertNotEquals(actualKey, actualOtherKey)
    }

    @Test
    fun `different activity icons produce different cache keys`() {
        val inputIcon: Icon = Icon.ActivityIcon(componentName)
        val inputOtherIcon: Icon = Icon.ActivityIcon(ComponentName(APPLICATION_PACKAGE, SECOND_ACTIVITY_CLASS))
        val actualKey: String = keyer.key(inputIcon, options)
        val actualOtherKey: String = keyer.key(inputOtherIcon, options)
        assertNotEquals(actualKey, actualOtherKey)
    }

    @Test
    fun `icon pack icons with different packs produce different cache keys`() {
        val inputIcon: Icon = Icon.PackIcon(componentName, ICON_PACK_PACKAGE, PACK_ICON_RES_ID)
        val inputOtherIcon: Icon = Icon.PackIcon(componentName, SECOND_ICON_PACK_PACKAGE, PACK_ICON_RES_ID)
        val actualKey: String = keyer.key(inputIcon, options)
        val actualOtherKey: String = keyer.key(inputOtherIcon, options)
        assertNotEquals(actualKey, actualOtherKey)
    }

    @Test
    fun `icon pack icons with different resources produce different cache keys`() {
        val inputIcon: Icon = Icon.PackIcon(componentName, ICON_PACK_PACKAGE, PACK_ICON_RES_ID)
        val inputOtherIcon: Icon = Icon.PackIcon(componentName, ICON_PACK_PACKAGE, SECOND_PACK_ICON_RES_ID)
        val actualKey: String = keyer.key(inputIcon, options)
        val actualOtherKey: String = keyer.key(inputOtherIcon, options)
        assertNotEquals(actualKey, actualOtherKey)
    }

    @Test
    fun `icons of different types produce different cache keys`() {
        val inputIcon: Icon = Icon.RailIcon(SHARED_IDENTITY)
        val inputOtherIcon: Icon = Icon.ApplicationIcon(SHARED_IDENTITY.toString())
        val actualKey: String = keyer.key(inputIcon, options)
        val actualOtherKey: String = keyer.key(inputOtherIcon, options)
        assertNotEquals(actualKey, actualOtherKey)
    }
}
