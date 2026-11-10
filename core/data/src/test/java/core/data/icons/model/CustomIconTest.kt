package core.data.icons.model

import core.data.testing.TEST_CUSTOM_ICON_DRAWABLE_NAME
import core.data.testing.TEST_GALLERY_ICON_FILE_NAME
import core.data.testing.TEST_ICON_PACK_PACKAGE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomIconTest {
    @Test
    fun `formats Custom icons as their stored form`() {
        assertEquals("pack-drawable:com.example.iconpack:calendar", createPackDrawableCustomIcon().format())
        assertEquals("image:gallery-icon.png", createImageCustomIcon().format())
    }

    @Test
    fun `parses a stored pack drawable Custom icon`() {
        assertEquals(
            createPackDrawableCustomIcon(),
            CustomIcon.parse(value = "pack-drawable:com.example.iconpack:calendar")
        )
    }

    @Test
    fun `parses a stored image Custom icon`() {
        assertEquals(createImageCustomIcon(), CustomIcon.parse(value = "image:gallery-icon.png"))
    }

    @Test
    fun `round trips Custom icons through the stored form`() {
        val inputIcons: List<CustomIcon> = listOf(createPackDrawableCustomIcon(), createImageCustomIcon())

        inputIcons.forEach { inputIcon ->
            assertEquals(inputIcon, CustomIcon.parse(value = inputIcon.format()))
        }
    }

    @Test
    fun `round trips Custom icons through their Icons`() {
        val inputPackDrawable: CustomIcon.PackDrawable = createPackDrawableCustomIcon()
        val inputImage: CustomIcon.Image = createImageCustomIcon()

        assertEquals(inputPackDrawable, inputPackDrawable.toIcon().toCustomIcon())
        assertEquals(inputImage, inputImage.toIcon().toCustomIcon())
    }

    @Test
    fun `parses no Custom icon from null`() {
        assertNull(CustomIcon.parse(value = null))
    }

    @Test
    fun `parses no Custom icon from an unknown or incomplete encoding`() {
        assertNull(CustomIcon.parse(value = "picture:/tmp/icon.png"))
        assertNull(CustomIcon.parse(value = "pack-drawable:"))
        assertNull(CustomIcon.parse(value = "pack-drawable:$TEST_ICON_PACK_PACKAGE"))
        assertNull(CustomIcon.parse(value = "image:"))
    }
}

private fun createPackDrawableCustomIcon(): CustomIcon.PackDrawable =
    CustomIcon.PackDrawable(packPackageName = TEST_ICON_PACK_PACKAGE, drawableName = TEST_CUSTOM_ICON_DRAWABLE_NAME)

private fun createImageCustomIcon(): CustomIcon.Image = CustomIcon.Image(fileName = TEST_GALLERY_ICON_FILE_NAME)
