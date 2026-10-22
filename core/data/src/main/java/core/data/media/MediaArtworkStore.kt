package core.data.media

import android.graphics.Bitmap
import android.net.Uri

/**
 * Holds the most recent MediaSession artwork in memory so the UI never
 * receives a Bitmap through state. Bounded because albums change often.
 */
class MediaArtworkStore(private val maxEntries: Int = MAX_ENTRIES) {
    private val entries: LinkedHashMap<String, Entry> = object : LinkedHashMap<String, Entry>(maxEntries, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > maxEntries
    }

    fun put(key: String, bitmap: Bitmap?, uri: Uri?): MediaArtwork? {
        if (bitmap == null && uri == null) {
            return null
        }
        synchronized(entries) {
            entries[key] = Entry(bitmap = bitmap, uri = uri)
        }
        return MediaArtwork(key = key)
    }

    fun get(artwork: MediaArtwork): Entry? = synchronized(entries) { entries[artwork.key] }

    class Entry(val bitmap: Bitmap?, val uri: Uri?)

    companion object {
        private const val MAX_ENTRIES: Int = 8
        private const val LOAD_FACTOR: Float = 0.75f
    }
}
