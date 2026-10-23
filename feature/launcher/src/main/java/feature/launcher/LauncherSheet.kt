package feature.launcher

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val sheetDragHandleHeight: Dp = 10.dp

@Composable
internal fun LauncherSheetDragHandle() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(sheetDragHandleHeight)
    )
}
