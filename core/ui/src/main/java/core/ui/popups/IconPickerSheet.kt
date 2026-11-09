package core.ui.popups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.icons.model.Icon
import core.data.icons.model.IconPack
import core.data.icons.model.IconPackCategory
import core.ui.R
import core.ui.composables.sheetHorizontalPadding
import core.ui.composables.sheetVerticalPadding

private val packIconSize = 64.dp
private val packIconPadding = 8.dp
private val packGridSpacing = 8.dp
private val packSearchVerticalPadding = 4.dp
private val packCategoryTopPadding = 16.dp

/** Hosts the Icon picker; renders nothing while it is closed. */
@Composable
fun IconPickerHost(iconPicker: IIconPicker) {
    val state: IconPickerState by iconPicker.iconPickerState.collectAsState()
    when (val pickerState: IconPickerState = state) {
        IconPickerState.Closed -> Unit

        is IconPickerState.Listing -> IconPickerListingSheet(
            state = pickerState,
            onOriginal = iconPicker::resetIcon,
            onIcon = iconPicker::selectIcon,
            onPack = iconPicker::openPack,
            onDismiss = iconPicker::cancelPicking
        )

        is IconPickerState.Pack -> IconPickerPackSheet(
            state = pickerState,
            onQuery = iconPicker::setQuery,
            onIcon = iconPicker::selectIcon,
            onBack = { iconPicker.backBeforeItemMenu() },
            onDismiss = { iconPicker.backBeforeItemMenu() }
        )
    }
}

@Composable
private fun IconPickerListingSheet(
    state: IconPickerState.Listing,
    onOriginal: () -> Unit,
    onIcon: (Icon.PackDrawableIcon) -> Unit,
    onPack: (IconPack.CustomIconPack) -> Unit,
    onDismiss: () -> Unit
) {
    PopupFullHeightSheet(onDismissRequest = onDismiss) {
        PopupSheetTitleRow(title = stringResource(R.string.change_icon))
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (state.suggestions.isNotEmpty()) {
                item(key = "suggested-icons") {
                    SuggestedIconsRow(suggestions = state.suggestions, onOriginal = onOriginal, onIcon = onIcon)
                }
            }
            item(key = "original-icon") {
                PickerRow(
                    icon = state.owner.defaultIcon,
                    label = stringResource(R.string.original_icon),
                    onClick = onOriginal
                )
            }
            if (state.iconPacks.isEmpty()) {
                item(key = "no-icon-packs") {
                    PopupEmptyState(messageResId = R.string.no_icon_packs)
                }
            } else {
                listItems(items = state.iconPacks, key = { pack -> pack.packageName }) { pack ->
                    PickerRow(icon = pack.icon, label = pack.name, onClick = { onPack(pack) })
                }
            }
        }
    }
}

@Composable
private fun IconPickerPackSheet(
    state: IconPickerState.Pack,
    onQuery: (String) -> Unit,
    onIcon: (Icon.PackDrawableIcon) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    PopupFullHeightSheet(onDismissRequest = onDismiss) {
        PopupSheetTitleRow(
            title = state.iconPack.name,
            navigationAction = PopupSheetTitleAction(label = stringResource(R.string.back), onClick = onBack)
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sheetHorizontalPadding, vertical = packSearchVerticalPadding),
            placeholder = { Text(text = stringResource(R.string.search_icons)) },
            singleLine = true
        )
        val categories: List<IconPackCategory>? = state.categories
        val isSearching: Boolean = state.isFiltering
        if (categories == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = packIconSize),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(sheetHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(packGridSpacing),
                horizontalArrangement = Arrangement.spacedBy(packGridSpacing)
            ) {
                if (isSearching) {
                    gridItems(items = state.results, key = { icon -> icon.drawableName }) { icon ->
                        PickerIcon(icon = icon, contentDescription = icon.drawableName, onClick = { onIcon(icon) })
                    }
                } else {
                    categories.forEachIndexed { index, category ->
                        val categoryName: String? = category.name
                        if (categoryName != null) {
                            item(
                                key = "category:$index",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Text(
                                    text = categoryName,
                                    modifier = Modifier.padding(top = packCategoryTopPadding),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                        gridItems(items = category.icons, key = { icon -> icon.drawableName }) { icon ->
                            PickerIcon(icon = icon, contentDescription = icon.drawableName, onClick = { onIcon(icon) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedIconsRow(suggestions: List<Icon>, onOriginal: () -> Unit, onIcon: (Icon.PackDrawableIcon) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.suggested),
            modifier = Modifier.padding(horizontal = sheetHorizontalPadding, vertical = packSearchVerticalPadding),
            style = MaterialTheme.typography.titleSmall
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = sheetHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(packGridSpacing)
        ) {
            listItems(items = suggestions) { icon ->
                val packIcon: Icon.PackDrawableIcon? = icon as? Icon.PackDrawableIcon
                PickerIcon(
                    icon = icon,
                    contentDescription = packIcon?.drawableName ?: stringResource(R.string.original_icon),
                    onClick = { if (packIcon == null) onOriginal() else onIcon(packIcon) }
                )
            }
        }
    }
}

@Composable
private fun PickerRow(icon: Icon, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = sheetHorizontalPadding, vertical = sheetVerticalPadding)
    ) {
        AsyncImage(
            modifier = Modifier.size(pickerRowIconSize),
            model = icon,
            contentDescription = null
        )
        Spacer(Modifier.width(pickerRowIconGap))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PickerIcon(icon: Icon, contentDescription: String, onClick: () -> Unit) {
    AsyncImage(
        modifier = Modifier
            .size(packIconSize)
            .clip(RoundedCornerShape(packIconPadding))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(packIconPadding),
        model = icon,
        contentDescription = contentDescription
    )
}
