package core.data.icons

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import timber.log.Timber

private const val ICON_IMAGES_DIRECTORY: String = "icons"
private const val ICON_IMAGE_QUALITY: Int = 100

class IIconImageStoreImpl(context: Context) : IIconImageStore {
    private val directory: File = File(context.filesDir, ICON_IMAGES_DIRECTORY)

    override fun write(fileName: String, bitmap: Bitmap): Boolean {
        val file: File = resolveImageFile(fileName = fileName) ?: return false
        val written: Boolean = runCatching {
            directory.mkdirs()
            file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, ICON_IMAGE_QUALITY, output) }
        }.onFailure { exception -> Timber.w(exception, "Could not write an icon image") }
            .getOrDefault(false)
        if (!written) {
            file.delete()
        }
        return written
    }

    override fun hasImage(fileName: String): Boolean = resolveImageFile(fileName = fileName)?.isFile == true

    override fun delete(fileName: String) {
        resolveImageFile(fileName = fileName)?.delete()
    }

    private fun resolveImageFile(fileName: String): File? = iconImageFile(directory = directory, fileName = fileName)
}

/** The file for [fileName] in [context]'s private icons directory, or null when the name is unsafe. */
internal fun iconImageFile(context: Context, fileName: String): File? =
    iconImageFile(directory = File(context.filesDir, ICON_IMAGES_DIRECTORY), fileName = fileName)

private fun iconImageFile(directory: File, fileName: String): File? {
    val isSafeName: Boolean = fileName.isNotEmpty() &&
        fileName != "." &&
        fileName != ".." &&
        '/' !in fileName &&
        '\\' !in fileName
    return if (isSafeName) File(directory, fileName) else null
}
