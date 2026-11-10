package feature.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import core.ui.model.data.SEARCH_ENTRY_URI

/** The Search section: the two independent ways to open Search from Home. */
@Composable
fun SettingsSearchScreen(
    openSearchOnSwipeUp: Boolean,
    openSearchOnHomePress: Boolean,
    onOpenSearchOnSwipeUpChange: (Boolean) -> Unit,
    onOpenSearchOnHomePressChange: (Boolean) -> Unit
) {
    LazyColumn {
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.search_swipe_up),
                checked = openSearchOnSwipeUp,
                supportingText = stringResource(R.string.search_swipe_up_summary),
                onCheckedChange = onOpenSearchOnSwipeUpChange
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.search_home_press),
                checked = openSearchOnHomePress,
                supportingText = stringResource(R.string.search_home_press_summary),
                onCheckedChange = onOpenSearchOnHomePressChange
            )
        }
        item {
            ListItem(
                headlineContent = { Text(text = stringResource(R.string.search_entry)) },
                supportingContent = {
                    Text(text = stringResource(R.string.search_entry_summary, SEARCH_ENTRY_URI))
                }
            )
        }
    }
}
