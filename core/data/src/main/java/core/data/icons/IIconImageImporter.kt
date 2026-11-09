package core.data.icons

import android.net.Uri

/** Imports a gallery image into Rail's private icons directory as a square Custom icon. */
interface IIconImageImporter {
    /** Stores [uri]'s image and returns its file name, or null when the image cannot be read or written. */
    suspend fun importImage(uri: Uri): String?

    /** Deletes an imported image the Icon picker will not use. */
    fun deleteImage(fileName: String)
}
