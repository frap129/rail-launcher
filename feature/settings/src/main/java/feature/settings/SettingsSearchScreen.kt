package feature.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** The Search section: the two independent entries that open Search from Home. */
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
    }
}
