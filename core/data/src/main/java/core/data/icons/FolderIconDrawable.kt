package core.data.icons

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import core.data.R

private const val FOLDER_GLYPH_RATIO: Float = 0.5f

/** A Rail-drawn Folder icon: a system-mask-shaped tile with a centered folder glyph. */
internal class FolderIconDrawable(context: Context, shade: Int) : Drawable() {
    private val colors: AccentColors = accentColors(shade = shade)
    private val backgroundPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, colors.background)
    }
    private val glyph: Drawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.folder_icon)).mutate().apply {
        setTint(ContextCompat.getColor(context, colors.glyph))
    }
    private val mask: Path = Path()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val adaptiveIcon: AdaptiveIconDrawable = AdaptiveIconDrawable(
            ColorDrawable(Color.TRANSPARENT),
            ColorDrawable(Color.TRANSPARENT)
        ).apply { this.bounds = bounds }
        mask.set(adaptiveIcon.iconMask)
        val glyphSize: Int = (minOf(bounds.width(), bounds.height()) * FOLDER_GLYPH_RATIO).toInt()
        val left: Int = bounds.left + (bounds.width() - glyphSize) / 2
        val top: Int = bounds.top + (bounds.height() - glyphSize) / 2
        glyph.setBounds(left, top, left + glyphSize, top + glyphSize)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(mask, backgroundPaint)
        glyph.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        glyph.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        glyph.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private data class AccentColors(val background: Int, val glyph: Int)

private fun accentColors(shade: Int): AccentColors = when (shade) {
    0 -> AccentColors(android.R.color.system_accent1_100, android.R.color.system_accent1_700)
    1 -> AccentColors(android.R.color.system_accent2_100, android.R.color.system_accent2_700)
    else -> AccentColors(android.R.color.system_accent3_100, android.R.color.system_accent3_700)
}
