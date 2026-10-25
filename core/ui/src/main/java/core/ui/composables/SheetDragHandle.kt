package core.ui.composables

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val sheetDragHandleHeight: Dp = 10.dp

/** The launcher's invisible sheet drag handle. */
@Composable
fun SheetDragHandle() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(sheetDragHandleHeight)
    )
}
