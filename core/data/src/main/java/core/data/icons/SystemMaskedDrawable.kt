package core.data.icons

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/** Draws [icon] clipped to the system adaptive icon mask. */
internal class SystemMaskedDrawable(private val icon: Drawable) : Drawable() {
    private val mask: Path = Path()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        icon.bounds = bounds
        mask.setSystemIconMask(bounds = bounds)
    }

    override fun draw(canvas: Canvas) {
        val checkpoint: Int = canvas.save()
        canvas.clipPath(mask)
        icon.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    override fun setAlpha(alpha: Int) {
        icon.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        icon.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
