package core.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.ui.layout.compactIconSize

/** The horizontal inset M3 list items give their content, so sheet titles line up with the leading-icon column. */
val sheetRowContentInset: Dp = 16.dp

/** The container colour every row inside a bottom sheet paints, so the sheet keeps one background. */
@OptIn(ExperimentalMaterial3Api::class)
val sheetContainerColor: Color
    @Composable get() = BottomSheetDefaults.ContainerColor

/** The container colour a row paints outside a sheet, matching the M3 list-item default. */
val settingsRowContainerColor: Color
    @Composable get() = ListItemDefaults.containerColor

/** The test tag on a row's leading icon, used to measure where the row content starts. */
const val ROW_LEADING_ICON_TEST_TAG: String = "rowLeadingIcon"

/** The leading icon shared by settings and sheet rows. */
@Composable
internal fun RowLeadingIcon(imageVector: ImageVector) {
    Icon(
        modifier = Modifier
            .size(compactIconSize)
            .testTag(ROW_LEADING_ICON_TEST_TAG),
        imageVector = imageVector,
        contentDescription = null
    )
}

/** The shared M3 list-item row, painted with [containerColor], which defaults to the sheet's container colour. */
@Composable
fun SheetRow(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    containerColor: Color = sheetContainerColor
) {
    ListItem(
        modifier = modifier,
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = containerColor)
    )
}

/** One tappable action row inside a bottom sheet, with a leading icon and a label. */
@Composable
fun SheetActionRow(imageVector: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SheetRow(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        headlineContent = { Text(label) },
        leadingContent = { RowLeadingIcon(imageVector = imageVector) }
    )
}
