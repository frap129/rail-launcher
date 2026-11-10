package core.data.icons

import android.graphics.Bitmap

/** Stores the gallery images Rail uses as Custom icons in its private icons directory. */
interface IIconImageStore {
    /** Writes [bitmap] as a PNG named [fileName]; false when the write fails. */
    fun write(fileName: String, bitmap: Bitmap): Boolean

    /** Whether an image named [fileName] is stored. */
    fun hasImage(fileName: String): Boolean

    /** Deletes the image named [fileName]. */
    fun delete(fileName: String)

    /** Deletes every stored image. */
    fun deleteAll()
}
