package core.data.icons

import android.graphics.Bitmap
import core.data.testing.TEST_SDK
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val ESCAPING_FILE_NAME: String = "../escape.png"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IIconImageStoreImplTest {
    private val inputContext = RuntimeEnvironment.getApplication()
    private val inputStore: IIconImageStore = IIconImageStoreImpl(context = inputContext)

    @Test
    fun `rejects a name that escapes the icons directory`() {
        val actualWritten: Boolean = inputStore.write(
            fileName = ESCAPING_FILE_NAME,
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        )

        assertFalse(actualWritten)
        assertFalse(inputStore.hasImage(fileName = ESCAPING_FILE_NAME))
        assertFalse(File(inputContext.filesDir, "escape.png").exists())
    }
}
