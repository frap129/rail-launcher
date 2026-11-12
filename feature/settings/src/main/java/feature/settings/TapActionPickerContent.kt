package feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.prefs.HeaderTapSetting
import core.ui.R as CoreUiR
import core.ui.composables.RowLeadingIcon
import core.ui.composables.SheetRow
import core.ui.composables.sheetRowContentInset

/** The selected tap action, its leading icon and how to change it for one Header part. */
internal data class TapActionPartUi(val selected: HeaderTapSetting, val icon: ImageVector, val onSelect: (HeaderTapSetting) -> Unit)

/** The Search field and tap action rows of the Header tap action picker, on the sheet's container colour. */
@Composable
internal fun TapActionPickerContent(state: TapActionPickerState, partUi: TapActionPartUi, onQueryChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxHeight()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = sheetRowContentInset),
            label = { Text(stringResource(CoreUiR.string.search)) },
            singleLine = true
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                TapActionRow(
                    label = stringResource(R.string.tap_action_system_default),
                    selected = partUi.selected == HeaderTapSetting.SystemDefault,
                    icon = {
                        RowLeadingIcon(imageVector = partUi.icon)
                    },
                    onClick = { partUi.onSelect(HeaderTapSetting.SystemDefault) }
                )
            }
            item {
                TapActionRow(
                    label = stringResource(R.string.tap_action_nothing),
                    selected = partUi.selected == HeaderTapSetting.Nothing,
                    onClick = { partUi.onSelect(HeaderTapSetting.Nothing) }
                )
            }
            item { HorizontalDivider() }
            if (!state.isLoaded) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(items = state.launchables, key = { launchable -> launchable.setting.key }) { launchable ->
                    TapActionRow(
                        label = launchable.name,
                        selected = partUi.selected == launchable.setting,
                        icon = {
                            AsyncImage(
                                modifier = Modifier.size(40.dp),
                                model = launchable.icon,
                                contentDescription = null
                            )
                        },
                        onClick = { partUi.onSelect(launchable.setting) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TapActionRow(label: String, selected: Boolean, onClick: () -> Unit, icon: (@Composable () -> Unit)? = null) {
    SheetRow(
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        headlineContent = { Text(label) },
        leadingContent = icon,
        trailingContent = if (selected) {
            { Icon(imageVector = Icons.Default.Check, contentDescription = null) }
        } else {
            null
        }
    )
}
