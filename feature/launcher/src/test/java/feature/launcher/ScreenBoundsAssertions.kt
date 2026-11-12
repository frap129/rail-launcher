package feature.launcher

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import org.junit.Assert.assertTrue

fun ComposeContentTestRule.screenBounds(): DpRect = onRoot().getUnclippedBoundsInRoot()

fun assertHorizontallyInsideScreen(bounds: DpRect, screenBounds: DpRect, message: String) {
    assertTrue(message, bounds.left >= screenBounds.left && bounds.right <= screenBounds.right)
}
