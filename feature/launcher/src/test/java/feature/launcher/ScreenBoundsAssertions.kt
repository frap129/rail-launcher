package feature.launcher

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import core.ui.layout.compactIconSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

fun ComposeContentTestRule.screenBounds(): DpRect = onRoot().getUnclippedBoundsInRoot()

fun assertHorizontallyInsideScreen(bounds: DpRect, screenBounds: DpRect, message: String) {
    assertTrue(message, bounds.left >= screenBounds.left && bounds.right <= screenBounds.right)
}

fun assertCompactIconSize(icon: SemanticsNodeInteraction) {
    val actualBounds: DpRect = icon.getUnclippedBoundsInRoot()
    assertEquals(compactIconSize, actualBounds.width)
    assertEquals(compactIconSize, actualBounds.height)
}
