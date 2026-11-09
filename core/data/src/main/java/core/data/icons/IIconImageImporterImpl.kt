package core.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val ICON_IMAGE_SIZE: Int = 192

class IIconImageImporterImpl(
    private val context: Context,
    private val imageStore: IIconImageStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IIconImageImporter {
    override suspend fun importImage(uri: Uri): String? {
        val fileName: String = "${UUID.randomUUID()}.png"
        return try {
            val written: Boolean = withContext(dispatcher) { writeIcon(uri = uri, fileName = fileName) }
            if (written) fileName else null
        } catch (exception: CancellationException) {
            withContext(NonCancellable) { imageStore.delete(fileName = fileName) }
            throw exception
        }
    }

    override fun deleteImage(fileName: String) {
        imageStore.delete(fileName = fileName)
    }

    private fun writeIcon(uri: Uri, fileName: String): Boolean {
        val icon: Bitmap = runCatching { uri.toSquareIcon() }
            .onFailure { exception -> Timber.w(exception, "Could not decode a gallery image") }
            .getOrNull() ?: return false
        return try {
            imageStore.write(fileName = fileName, bitmap = icon)
        } finally {
            icon.recycle()
        }
    }

    private fun Uri.toSquareIcon(): Bitmap {
        val decoded: Bitmap = decode(uri = this)
        val square: Bitmap = decoded.centerCropSquare()
        if (square !== decoded) {
            decoded.recycle()
        }
        val icon: Bitmap = square.scaleToIconSize()
        if (icon !== square) {
            square.recycle()
        }
        return icon
    }

    private fun decode(uri: Uri): Bitmap {
        val bounds: BitmapFactory.Options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error("The image could not be decoded")
        }
        val options: BitmapFactory.Options = BitmapFactory.Options().apply {
            inSampleSize = (minOf(bounds.outWidth, bounds.outHeight) / ICON_IMAGE_SIZE).coerceAtLeast(1)
            inScaled = false
        }
        return context.contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
            ?.applyExifOrientation(orientation = readOrientation(uri = uri))
            ?: error("The image could not be decoded")
    }

    private fun readOrientation(uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
}

/** Applies [orientation] to this image; returns this bitmap when it is already upright. */
private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
    val matrix: Matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)

        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)

        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)

        else -> return this
    }
    val oriented: Bitmap = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (oriented !== this) {
        recycle()
    }
    return oriented
}

private fun Bitmap.centerCropSquare(): Bitmap {
    val size: Int = minOf(width, height)
    if (width == size && height == size) {
        return this
    }
    return Bitmap.createBitmap(this, (width - size) / 2, (height - size) / 2, size, size)
}

private fun Bitmap.scaleToIconSize(): Bitmap = if (width == ICON_IMAGE_SIZE && height == ICON_IMAGE_SIZE) {
    this
} else {
    Bitmap.createScaledBitmap(this, ICON_IMAGE_SIZE, ICON_IMAGE_SIZE, true)
}
