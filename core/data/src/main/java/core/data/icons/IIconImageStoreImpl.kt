package core.data.icons

import android.content.Context
import android.graphics.Bitmap
import java.io.File

private const val ICON_IMAGES_DIRECTORY: String = "icons"

class IIconImageStoreImpl(context: Context) : IIconImageStore {
    private val directory: File = File(context.filesDir, ICON_IMAGES_DIRECTORY)

    override fun write(fileName: String, bitmap: Bitmap): Boolean {
        val file: File = resolveImageFile(fileName = fileName) ?: return false
        directory.mkdirs()
        val written: Boolean = writeIconPng(file = file, bitmap = bitmap)
        if (!written) {
            file.delete()
        }
        return written
    }

    override fun hasImage(fileName: String): Boolean = resolveImageFile(fileName = fileName)?.isFile == true

    override fun delete(fileName: String) {
        resolveImageFile(fileName = fileName)?.delete()
    }

    override fun deleteAll() {
        directory.listFiles()?.forEach { file -> file.delete() }
    }

    private fun resolveImageFile(fileName: String): File? = iconImageFile(directory = directory, fileName = fileName)
}

/** The file for [fileName] in [context]'s private icons directory, or null when the name is unsafe. */
internal fun iconImageFile(context: Context, fileName: String): File? =
    iconImageFile(directory = File(context.filesDir, ICON_IMAGES_DIRECTORY), fileName = fileName)

private fun iconImageFile(directory: File, fileName: String): File? =
    if (isSafeFileName(name = fileName)) File(directory, fileName) else null
