package feature.launcher

import androidx.compose.ui.unit.DpRect
import org.junit.Assert.assertTrue

fun assertHorizontallyInsideScreen(bounds: DpRect, screenBounds: DpRect, message: String) {
    assertTrue(message, bounds.left >= screenBounds.left && bounds.right <= screenBounds.right)
}
