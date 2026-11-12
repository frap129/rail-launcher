package core.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.DpRect
import org.junit.Assert.assertEquals

/** A background no row container uses, so a row that stops painting its container fails its test. */
val rowTestBackgroundColor: Color = Color.Red

/** The theme and contrasting background shared by the sheet-row tests. */
@Composable
fun RowTestContent(content: @Composable () -> Unit) {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().background(rowTestBackgroundColor)) {
            Column(modifier = Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

/** The colour the node paints at the top of its bounds, above any text or icon. */
fun SemanticsNodeInteraction.topEdgeColor(): Color = captureToImage().toPixelMap().let { pixels ->
    pixels[pixels.width / 2, 1]
}

/** Asserts the node paints [expectedColor] at its top edge. */
fun SemanticsNodeInteraction.assertPaintsTopEdge(expectedColor: Color) {
    assertEquals(expectedColor, topEdgeColor())
}

/** Asserts [actionLabel] starts where [switchTitle] starts and has the same text height. */
fun assertActionLabelMatchesSwitchTitle(actionLabel: SemanticsNodeInteraction, switchTitle: SemanticsNodeInteraction) {
    val actualActionLabel: DpRect = actionLabel.getUnclippedBoundsInRoot()
    val actualSwitchTitle: DpRect = switchTitle.getUnclippedBoundsInRoot()
    assertEquals(actualSwitchTitle.left, actualActionLabel.left)
    assertEquals(actualSwitchTitle.bottom - actualSwitchTitle.top, actualActionLabel.bottom - actualActionLabel.top)
}

/** Asserts [actionRow] and [switchRow] span the same horizontal bounds. */
fun assertActionRowMatchesSwitchRow(actionRow: SemanticsNodeInteraction, switchRow: SemanticsNodeInteraction) {
    val actualActionRow: DpRect = actionRow.getUnclippedBoundsInRoot()
    val actualSwitchRow: DpRect = switchRow.getUnclippedBoundsInRoot()
    assertEquals(actualSwitchRow.left, actualActionRow.left)
    assertEquals(actualSwitchRow.right - actualSwitchRow.left, actualActionRow.right - actualActionRow.left)
}
