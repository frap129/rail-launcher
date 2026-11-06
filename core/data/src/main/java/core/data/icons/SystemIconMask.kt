package core.data.icons

import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable

/** Sets this Path to the system adaptive icon mask for [bounds]. */
internal fun Path.setSystemIconMask(bounds: Rect) {
    val adaptiveIcon: AdaptiveIconDrawable = AdaptiveIconDrawable(
        ColorDrawable(Color.TRANSPARENT),
        ColorDrawable(Color.TRANSPARENT)
    ).apply { this.bounds = bounds }
    set(adaptiveIcon.iconMask)
}
