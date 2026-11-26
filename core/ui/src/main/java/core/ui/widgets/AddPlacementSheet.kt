package core.ui.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowsAltV
import compose.icons.fontawesomeicons.solid.PlusSquare
import core.ui.R as CoreUiR
import core.ui.composables.SheetActionRow
import core.ui.composables.SheetDragHandle
import core.ui.composables.SheetTextTitle
import core.ui.composables.sheetContainerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlacementSheet(canAddToStack: Boolean, onBelow: () -> Unit, onIntoStack: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        dragHandle = { SheetDragHandle() },
        onDismissRequest = onDismiss,
        containerColor = sheetContainerColor
    ) {
        AddPlacementContent(canAddToStack = canAddToStack, onBelow = onBelow, onIntoStack = onIntoStack)
    }
}

/** The Add widget sheet's placement choices, on the sheet's container colour. */
@Composable
internal fun AddPlacementContent(canAddToStack: Boolean, onBelow: () -> Unit, onIntoStack: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SheetTextTitle(title = stringResource(CoreUiR.string.add_widget))
        SheetActionRow(
            imageVector = FontAwesomeIcons.Solid.ArrowsAltV,
            label = stringResource(CoreUiR.string.add_below),
            onClick = onBelow,
            supportingText = stringResource(CoreUiR.string.add_below_supporting_text)
        )
        if (canAddToStack) {
            SheetActionRow(
                imageVector = FontAwesomeIcons.Solid.PlusSquare,
                label = stringResource(CoreUiR.string.add_next_to),
                onClick = onIntoStack,
                supportingText = stringResource(CoreUiR.string.add_next_to_supporting_text)
            )
        }
    }
}
