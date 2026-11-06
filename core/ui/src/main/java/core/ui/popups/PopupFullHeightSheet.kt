package core.ui.popups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import core.ui.composables.SheetDragHandle

/** The full-height bottom sheet shared by the Pop-up editor and pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PopupFullHeightSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { SheetDragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxHeight(), content = content)
    }
}
