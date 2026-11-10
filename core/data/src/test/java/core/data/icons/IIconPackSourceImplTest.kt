package core.data.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import core.data.icons.model.Icon
import core.data.testing.MISSING_ICON_PACK_PACKAGE
import core.data.testing.TEST_ICON_PACK_PACKAGE
import core.data.testing.TEST_SDK
import core.util.DrawableCategory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

private const val DRAWABLE_XML_RES_ID: Int = 7
private const val DRAWABLE_RES_ID: Int = 42

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IIconPackSourceImplTest {
    private val mockContext: Context = mockk()
    private val mockPackageManager: PackageManager = mockk()
    private val mockResources: Resources = mockk()
    private val mockAssets: AssetManager = mockk()
    private val source: IIconPackSource = IIconPackSourceImpl(context = mockContext)

    init {
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.getResourcesForApplication(TEST_ICON_PACK_PACKAGE) } returns mockResources
        every { mockResources.assets } returns mockAssets
    }

    @Test
    fun `reads the drawable list grouped by its categories and drops names that do not resolve`() {
        every { mockResources.getIdentifier("drawable", "xml", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_XML_RES_ID
        every { mockResources.getXml(DRAWABLE_XML_RES_ID) } returns drawableListParser(
            DrawableElement.Category(name = "Calendar"),
            DrawableElement.Item(drawableName = "calendar"),
            DrawableElement.Item(drawableName = "missing"),
            DrawableElement.Category(name = "Communication"),
            DrawableElement.Item(drawableName = "mail")
        )
        every { mockResources.getIdentifier("calendar", "drawable", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_RES_ID
        every { mockResources.getIdentifier("missing", "drawable", TEST_ICON_PACK_PACKAGE) } returns 0
        every { mockResources.getIdentifier("mail", "drawable", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_RES_ID

        val actualCategories: List<DrawableCategory> = source.drawableCategories(packPackageName = TEST_ICON_PACK_PACKAGE)

        assertEquals(
            listOf(
                DrawableCategory(name = "Calendar", drawableNames = listOf("calendar")),
                DrawableCategory(name = "Communication", drawableNames = listOf("mail"))
            ),
            actualCategories
        )
        verify(exactly = 1) { mockPackageManager.getResourcesForApplication(TEST_ICON_PACK_PACKAGE) }
    }

    @Test
    fun `reads an untitled category from assets when the drawable list has no headings`() {
        every { mockResources.getIdentifier("drawable", "xml", TEST_ICON_PACK_PACKAGE) } returns 0
        every { mockAssets.open("drawable.xml") } returns stream(
            """
            <resources>
                <item drawable="phone"/>
            </resources>
            """.trimIndent()
        )
        every { mockResources.getIdentifier("phone", "drawable", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_RES_ID

        val actualCategories: List<DrawableCategory> = source.drawableCategories(packPackageName = TEST_ICON_PACK_PACKAGE)

        assertEquals(listOf(DrawableCategory(name = null, drawableNames = listOf("phone"))), actualCategories)
    }

    @Test
    fun `falls back to one untitled category of the unique appfilter drawable names when there is no drawable list`() {
        every { mockResources.getIdentifier("drawable", "xml", TEST_ICON_PACK_PACKAGE) } returns 0
        every { mockAssets.open("drawable.xml") } throws FileNotFoundException()
        every { mockResources.getIdentifier("appfilter", "xml", TEST_ICON_PACK_PACKAGE) } returns 0
        every { mockAssets.open("appfilter.xml") } returns stream(
            """
            <resources>
                <item component="ComponentInfo{com.example.app/com.example.app.MainActivity}" drawable="calendar"/>
                <item component="ComponentInfo{com.example.other/com.example.other.MainActivity}" drawable="calendar"/>
                <item component="ComponentInfo{com.example.third/com.example.third.MainActivity}" drawable="missing"/>
            </resources>
            """.trimIndent()
        )
        every { mockResources.getIdentifier("calendar", "drawable", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_RES_ID
        every { mockResources.getIdentifier("missing", "drawable", TEST_ICON_PACK_PACKAGE) } returns 0

        val actualCategories: List<DrawableCategory> = source.drawableCategories(packPackageName = TEST_ICON_PACK_PACKAGE)

        assertEquals(listOf(DrawableCategory(name = null, drawableNames = listOf("calendar"))), actualCategories)
    }

    @Test
    fun `falls back to appfilter when the drawable list names no drawables`() {
        every { mockResources.getIdentifier("drawable", "xml", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_XML_RES_ID
        every { mockResources.getXml(DRAWABLE_XML_RES_ID) } returns drawableListParser(DrawableElement.Category(name = "Calendar"))
        every { mockResources.getIdentifier("appfilter", "xml", TEST_ICON_PACK_PACKAGE) } returns 0
        every { mockAssets.open("appfilter.xml") } returns stream(
            """
            <resources>
                <item component="ComponentInfo{com.example.app/com.example.app.MainActivity}" drawable="calendar"/>
            </resources>
            """.trimIndent()
        )
        every { mockResources.getIdentifier("calendar", "drawable", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_RES_ID

        val actualCategories: List<DrawableCategory> = source.drawableCategories(packPackageName = TEST_ICON_PACK_PACKAGE)

        assertEquals(listOf(DrawableCategory(name = null, drawableNames = listOf("calendar"))), actualCategories)
    }

    @Test
    fun `maps App icons from appfilter and drops drawables that no longer resolve`() {
        every { mockResources.getIdentifier("appfilter", "xml", TEST_ICON_PACK_PACKAGE) } returns 0
        every { mockAssets.open("appfilter.xml") } returns stream(
            """
            <resources>
                <item component="ComponentInfo{com.example.app/com.example.app.MainActivity}" drawable="calendar"/>
                <item component="ComponentInfo{com.example.other/com.example.other.MainActivity}" drawable="missing"/>
            </resources>
            """.trimIndent()
        )
        every { mockResources.getIdentifier("calendar", "drawable", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_RES_ID
        every { mockResources.getIdentifier("missing", "drawable", TEST_ICON_PACK_PACKAGE) } returns 0

        val actualIcons: Set<Icon.PackIcon> = source.appIcons(packPackageName = TEST_ICON_PACK_PACKAGE)

        assertEquals(
            setOf(
                Icon.PackIcon(
                    componentName = ComponentName("com.example.app", "com.example.app.MainActivity"),
                    packPackageName = TEST_ICON_PACK_PACKAGE,
                    resId = DRAWABLE_RES_ID
                )
            ),
            actualIcons
        )
    }

    @Test
    fun `returns no categories for a missing pack`() {
        every {
            mockPackageManager.getResourcesForApplication(MISSING_ICON_PACK_PACKAGE)
        } throws PackageManager.NameNotFoundException()

        val actualCategories: List<DrawableCategory> = source.drawableCategories(packPackageName = MISSING_ICON_PACK_PACKAGE)

        assertEquals(emptyList<DrawableCategory>(), actualCategories)
    }

    @Test
    fun `reports whether a pack still provides a drawable`() {
        every { mockResources.getIdentifier("calendar", "drawable", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_RES_ID
        every { mockResources.getIdentifier("missing", "drawable", TEST_ICON_PACK_PACKAGE) } returns 0

        assertEquals(true, source.hasDrawable(TEST_ICON_PACK_PACKAGE, "calendar"))
        assertEquals(false, source.hasDrawable(TEST_ICON_PACK_PACKAGE, "missing"))
    }

    @Test
    fun `resolves a stored resource id to its drawable name`() {
        every { mockResources.getResourceEntryName(DRAWABLE_RES_ID) } returns "calendar"
        every { mockResources.getResourceEntryName(0) } throws Resources.NotFoundException()

        assertEquals("calendar", source.resolveDrawableName(TEST_ICON_PACK_PACKAGE, DRAWABLE_RES_ID))
        assertNull(source.resolveDrawableName(TEST_ICON_PACK_PACKAGE, 0))
    }

    @Test
    fun `returns no drawable name for a missing pack`() {
        every { mockPackageManager.getResourcesForApplication(MISSING_ICON_PACK_PACKAGE) } throws PackageManager.NameNotFoundException()

        assertNull(source.resolveDrawableName(MISSING_ICON_PACK_PACKAGE, DRAWABLE_RES_ID))
    }

    private fun drawableListParser(vararg elements: DrawableElement): XmlResourceParser {
        val parser: XmlResourceParser = mockk()
        val startEvents: List<Int> = List(elements.size) { XmlPullParser.START_TAG }
        every { parser.next() } returnsMany startEvents + XmlPullParser.END_DOCUMENT
        every { parser.eventType } returnsMany startEvents
        every { parser.name } returnsMany elements.map { element -> element.tag }
        every { parser.getAttributeValue(null, "drawable") } returnsMany elements.mapNotNull { element ->
            (element as? DrawableElement.Item)?.drawableName
        }
        every { parser.getAttributeValue(null, "name") } returnsMany elements.filterIsInstance<DrawableElement.Category>().map { element ->
            element.name
        }
        return parser
    }

    private fun stream(xml: String): ByteArrayInputStream = ByteArrayInputStream(xml.toByteArray())

    private sealed interface DrawableElement {
        val tag: String

        data class Category(val name: String?) : DrawableElement {
            override val tag: String = "category"
        }

        data class Item(val drawableName: String) : DrawableElement {
            override val tag: String = "item"
        }
    }
}
