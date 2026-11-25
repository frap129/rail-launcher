package core.ui.popups

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
import core.util.toNormalizedDrawableName

private val packIconSize = 64.dp
private val packIconPadding = 8.dp
private val packGridSpacing = 8.dp
private val packSearchVerticalPadding = 4.dp
private val packCategoryTopPadding = 16.dp
private val galleryProgressSize = 24.dp

/** Hosts the Icon picker; renders nothing while it is closed. */
@Composable
fun IconPickerHost(iconPicker: IIconPicker) {
    val state: IconPickerState by iconPicker.iconPickerState.collectAsState()
    val galleryLauncher: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?> =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            iconPicker.selectGalleryImage(uri = uri)
        }
    when (val pickerState: IconPickerState = state) {
        IconPickerState.Closed -> Unit

        is IconPickerState.Listing -> IconPickerListingSheet(
            state = pickerState,
            onOriginal = iconPicker::resetIcon,
            onGallery = {
                iconPicker.startGalleryPick()
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onIcon = iconPicker::selectIcon,
            onPack = iconPicker::openPack,
            onDismiss = iconPicker::cancelPicking
        )

        is IconPickerState.Pack -> IconPickerPackSheet(
            state = pickerState,
            onQuery = iconPicker::setQuery,
            onIcon = iconPicker::selectIcon,
            onBack = { iconPicker.backBeforeItemMenu() },
            onDismiss = iconPicker::cancelPicking
        )
    }
}

@Composable
private fun IconPickerListingSheet(
    state: IconPickerState.Listing,
    onOriginal: () -> Unit,
    onGallery: () -> Unit,
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
            item(key = "gallery-icon") {
                GalleryRow(isImporting = state.isImportingImage, onClick = onGallery)
            }
            if (state.imageImportFailed) {
                item(key = "gallery-icon-failed") {
                    Text(
                        text = stringResource(R.string.couldnt_use_image),
                        modifier = Modifier.padding(horizontal = sheetHorizontalPadding),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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
        BackHandler(onBack = onBack)
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
                        PickerIcon(icon = icon, contentDescription = describePickerIcon(icon), onClick = { onIcon(icon) })
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
                            PickerIcon(icon = icon, contentDescription = describePickerIcon(icon), onClick = { onIcon(icon) })
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
                    contentDescription = describePickerIcon(icon),
                    onClick = { if (packIcon == null) onOriginal() else onIcon(packIcon) }
                )
            }
        }
    }
}

@Composable
private fun GalleryRow(isImporting: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isImporting, role = Role.Button, onClick = onClick)
            .padding(horizontal = sheetHorizontalPadding, vertical = sheetVerticalPadding)
    ) {
        Box(modifier = Modifier.size(pickerRowIconSize), contentAlignment = Alignment.Center) {
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.size(galleryProgressSize))
            } else {
                MaterialIcon(
                    painter = painterResource(R.drawable.gallery_icon),
                    contentDescription = null,
                    modifier = Modifier.size(pickerRowIconSize),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.width(pickerRowIconGap))
        Text(text = stringResource(R.string.gallery), style = MaterialTheme.typography.bodyLarge)
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

/** [icon]'s accessible name, reading a drawable name without underscores and naming the original icon. */
@Composable
private fun describePickerIcon(icon: Icon): String =
    (icon as? Icon.PackDrawableIcon)?.drawableName?.toNormalizedDrawableName() ?: stringResource(R.string.original_icon)

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
