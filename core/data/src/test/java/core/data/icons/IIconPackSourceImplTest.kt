package core.data.icons

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import core.data.testing.TEST_ICON_PACK_PACKAGE
import core.data.testing.TEST_SDK
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
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
    fun `reads the drawable list from resources and drops names that do not resolve`() {
        every { mockResources.getIdentifier("drawable", "xml", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_XML_RES_ID
        every {
            mockPackageManager.getXml(TEST_ICON_PACK_PACKAGE, DRAWABLE_XML_RES_ID, null)
        } returns drawableListParser("calendar", "missing", "mail")
        every { mockResources.getIdentifier("calendar", "drawable", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_RES_ID
        every { mockResources.getIdentifier("missing", "drawable", TEST_ICON_PACK_PACKAGE) } returns 0
        every { mockResources.getIdentifier("mail", "drawable", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_RES_ID

        val actualNames: List<String> = source.drawableNames(packPackageName = TEST_ICON_PACK_PACKAGE)

        assertEquals(listOf("calendar", "mail"), actualNames)
    }

    @Test
    fun `reads the drawable list from assets when resources have none`() {
        every { mockResources.getIdentifier("drawable", "xml", TEST_ICON_PACK_PACKAGE) } returns 0
        every { mockAssets.open("drawable.xml") } returns stream(
            """
            <resources>
                <item drawable="phone"/>
            </resources>
            """.trimIndent()
        )
        every { mockResources.getIdentifier("phone", "drawable", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_RES_ID

        val actualNames: List<String> = source.drawableNames(packPackageName = TEST_ICON_PACK_PACKAGE)

        assertEquals(listOf("phone"), actualNames)
    }

    @Test
    fun `falls back to the unique appfilter drawable names when there is no drawable list`() {
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

        val actualNames: List<String> = source.drawableNames(packPackageName = TEST_ICON_PACK_PACKAGE)

        assertEquals(listOf("calendar"), actualNames)
    }

    @Test
    fun `reports whether a pack still provides a drawable`() {
        every { mockResources.getIdentifier("calendar", "drawable", TEST_ICON_PACK_PACKAGE) } returns DRAWABLE_RES_ID
        every { mockResources.getIdentifier("missing", "drawable", TEST_ICON_PACK_PACKAGE) } returns 0

        assertEquals(true, source.hasDrawable(TEST_ICON_PACK_PACKAGE, "calendar"))
        assertEquals(false, source.hasDrawable(TEST_ICON_PACK_PACKAGE, "missing"))
    }

    private fun drawableListParser(vararg drawableNames: String): XmlResourceParser {
        val parser: XmlResourceParser = mockk()
        val startEvents: List<Int> = List(drawableNames.size) { XmlPullParser.START_TAG }
        every { parser.next() } returnsMany startEvents + XmlPullParser.END_DOCUMENT
        every { parser.eventType } returnsMany startEvents
        every { parser.getAttributeValue(null, "drawable") } returnsMany drawableNames.toList()
        return parser
    }

    private fun stream(xml: String): ByteArrayInputStream = ByteArrayInputStream(xml.toByteArray())
}
