package core.data.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import core.data.testing.TEST_GALLERY_ICON_SIZE
import core.data.testing.TEST_SDK
import java.io.File
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val SOURCE_IMAGE_WIDTH: Int = 400
private const val SOURCE_IMAGE_HEIGHT: Int = 200
private const val SOURCE_BAND_WIDTH: Int = 100

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IIconImageImporterImplTest {
    private val inputContext = RuntimeEnvironment.getApplication()
    private val inputImageStore: IIconImageStore = IIconImageStoreImpl(context = inputContext)
    private val importer: IIconImageImporter = IIconImageImporterImpl(
        context = inputContext,
        imageStore = inputImageStore,
        dispatcher = UnconfinedTestDispatcher()
    )

    @Test
    fun `center crops and scales a gallery image to a square PNG`() = runTest {
        val inputUri: Uri = Uri.fromFile(createSourceImage())

        val actualFileName: String? = importer.importImage(uri = inputUri)

        assertNotNull(actualFileName)
        assertTrue(actualFileName!!.endsWith(".png"))
        val actualFile: File = requireNotNull(iconImageFile(context = inputContext, fileName = actualFileName))
        assertTrue(actualFile.isFile)
        val actualBitmap: Bitmap = requireNotNull(BitmapFactory.decodeFile(actualFile.absolutePath))
        assertEquals(TEST_GALLERY_ICON_SIZE, actualBitmap.width)
        assertEquals(TEST_GALLERY_ICON_SIZE, actualBitmap.height)
        assertEquals(Color.GREEN, actualBitmap.getPixel(TEST_GALLERY_ICON_SIZE / 4, TEST_GALLERY_ICON_SIZE / 2))
        assertEquals(Color.GREEN, actualBitmap.getPixel(TEST_GALLERY_ICON_SIZE / 2, TEST_GALLERY_ICON_SIZE / 2))
        assertEquals(Color.GREEN, actualBitmap.getPixel(TEST_GALLERY_ICON_SIZE * 3 / 4, TEST_GALLERY_ICON_SIZE / 2))
    }

    @Test
    fun `stores each import under a different name`() = runTest {
        val inputUri: Uri = Uri.fromFile(createSourceImage())

        val actualFirst: String? = importer.importImage(uri = inputUri)
        val actualSecond: String? = importer.importImage(uri = inputUri)

        assertNotNull(actualFirst)
        assertNotNull(actualSecond)
        assertNotEquals(actualFirst, actualSecond)
    }

    @Test
    fun `returns null when the image cannot be read`() = runTest {
        val inputUri: Uri = Uri.fromFile(File(inputContext.cacheDir, "missing-image.png"))

        val actualFileName: String? = importer.importImage(uri = inputUri)

        assertNull(actualFileName)
    }

    private fun createSourceImage(): File {
        val inputBitmap: Bitmap = Bitmap.createBitmap(SOURCE_IMAGE_WIDTH, SOURCE_IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val inputPixels: IntArray = IntArray(SOURCE_IMAGE_WIDTH * SOURCE_IMAGE_HEIGHT) { index ->
            val x: Int = index % SOURCE_IMAGE_WIDTH
            when {
                x < SOURCE_BAND_WIDTH -> Color.RED
                x < SOURCE_BAND_WIDTH * 3 -> Color.GREEN
                else -> Color.BLUE
            }
        }
        inputBitmap.setPixels(inputPixels, 0, SOURCE_IMAGE_WIDTH, 0, 0, SOURCE_IMAGE_WIDTH, SOURCE_IMAGE_HEIGHT)
        val file: File = File(inputContext.cacheDir, "source-${UUID.randomUUID()}.png")
        file.outputStream().use { output -> inputBitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        inputBitmap.recycle()
        return file
    }
}
