package core.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import timber.log.Timber

private const val HOME_ICONS_DIRECTORY: String = "home-icons"

/** Bump when Rail's icon rendering changes, so stored bitmaps are never read with another rendering. */
private const val HOME_ICONS_RENDER_VERSION: Int = 1

/**
 * The quality `Bitmap.compress` takes for PNG output, which PNG ignores. One value keeps every
 * icon Rail writes encoded the same way.
 */
internal const val ICON_PNG_QUALITY: Int = 100

/** Writes [bitmap] as a PNG to [file], logging and returning false when it cannot. */
internal fun writeIconPng(file: File, bitmap: Bitmap): Boolean = runCatching {
    file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, ICON_PNG_QUALITY, output) }
}.onFailure { exception -> Timber.w(exception, "Could not write an icon image") }
    .getOrDefault(false)

/** This bitmap as [writeIconPng] stores and a read decodes it, or null when it cannot be encoded or decoded. */
internal fun Bitmap.afterPngRoundTrip(): Bitmap? = runCatching {
    val bytes: ByteArray = ByteArrayOutputStream().use { output ->
        compress(Bitmap.CompressFormat.PNG, ICON_PNG_QUALITY, output)
        output.toByteArray()
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.onFailure { exception -> Timber.w(exception, "Could not encode an icon for comparison") }
    .getOrNull()

/** Whether [name] names a file inside a directory without escaping it. */
internal fun isSafeFileName(name: String): Boolean = name.isNotEmpty() &&
    name != "." &&
    name != ".." &&
    '/' !in name &&
    '\\' !in name

/** The directory that holds the [HOME_ICONS_RENDER_VERSION] rendering of Rail's Home icons. */
internal fun homeIconsDirectory(context: Context): File = File(context.cacheDir, "$HOME_ICONS_DIRECTORY-v$HOME_ICONS_RENDER_VERSION")

/** The directories an earlier rendering of Rail's Home icons used, which this version replaces. */
internal fun earlierHomeIconsDirectories(context: Context): List<File> {
    val currentDirectoryName: String = homeIconsDirectory(context).name
    return context.cacheDir
        .listFiles { file -> file.name.startsWith(HOME_ICONS_DIRECTORY) && file.name != currentDirectoryName }
        .orEmpty()
        .toList()
}
