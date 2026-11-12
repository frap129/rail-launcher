package feature.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowUp
import compose.icons.fontawesomeicons.solid.Calculator
import compose.icons.fontawesomeicons.solid.Cog
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.Search
import compose.icons.fontawesomeicons.solid.User
import core.ui.R as CoreUiR
import core.ui.composables.SearchContactsSwitchRow
import core.ui.composables.SearchWebSuggestionsSwitchRow
import core.ui.composables.SettingsSwitchRow
import core.ui.composables.SheetActionRow
import core.ui.composables.SheetDragHandle
import core.ui.composables.sheetContainerColor
import core.ui.composables.sheetRowContentInset
import core.ui.popups.PopupSheetTitleRow
import core.ui.search.SearchContactsUiEvent
import kotlinx.coroutines.flow.Flow

/** What the Search options sheet asks its host to change. */
internal data class SearchOptionsActions(
    val onOpenSearchOnSwipeUpChange: (Boolean) -> Unit,
    val onSearchSuggestionsChange: (Boolean) -> Unit,
    val onSearchWebSuggestionsChange: (Boolean) -> Unit,
    val onSearchContactsChange: (Boolean) -> Unit,
    val onSearchCalculatorRowChange: (Boolean) -> Unit,
    val onContactsPermissionResult: (granted: Boolean, canAskAgain: Boolean) -> Unit,
    val onRefreshContactsPermission: () -> Unit,
    val onOpenSearchSettings: () -> Unit
)

/** The Search options sheet: the Search preferences consulted most while searching. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchOptionsSheet(
    options: SearchOptions,
    contactsEvents: Flow<SearchContactsUiEvent>,
    actions: SearchOptionsActions,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetContainerColor,
        dragHandle = { SheetDragHandle() }
    ) {
        SearchOptionsContent(options = options, contactsEvents = contactsEvents, actions = actions, onDismiss = onDismiss)
    }
}

/** The Search options sheet's rows, on the sheet's container colour. */
@Composable
internal fun SearchOptionsContent(
    options: SearchOptions,
    contactsEvents: Flow<SearchContactsUiEvent>,
    actions: SearchOptionsActions,
    onDismiss: () -> Unit
) {
    val containerColor: Color = sheetContainerColor
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        PopupSheetTitleRow(title = stringResource(CoreUiR.string.search), horizontalPadding = sheetRowContentInset)
        SearchWebSuggestionsSwitchRow(
            checked = options.searchWebSuggestions,
            searchEngine = options.searchEngine,
            onCheckedChange = actions.onSearchWebSuggestionsChange,
            leadingIcon = FontAwesomeIcons.Solid.Globe,
            containerColor = containerColor
        )
        SearchContactsSwitchRow(
            checked = options.searchContacts,
            events = contactsEvents,
            onCheckedChange = actions.onSearchContactsChange,
            onPermissionResult = actions.onContactsPermissionResult,
            onRefreshPermission = actions.onRefreshContactsPermission,
            leadingIcon = FontAwesomeIcons.Solid.User,
            containerColor = containerColor
        )
        SettingsSwitchRow(
            title = stringResource(CoreUiR.string.search_suggestions),
            checked = options.searchSuggestions,
            onCheckedChange = actions.onSearchSuggestionsChange,
            supportingText = stringResource(CoreUiR.string.search_suggestions_summary),
            leadingIcon = FontAwesomeIcons.Solid.Search,
            containerColor = containerColor
        )
        SettingsSwitchRow(
            title = stringResource(CoreUiR.string.search_calculator_row),
            checked = options.searchCalculatorRow,
            onCheckedChange = actions.onSearchCalculatorRowChange,
            supportingText = stringResource(CoreUiR.string.search_calculator_row_summary),
            leadingIcon = FontAwesomeIcons.Solid.Calculator,
            containerColor = containerColor
        )
        SettingsSwitchRow(
            title = stringResource(CoreUiR.string.search_swipe_up),
            checked = options.openSearchOnSwipeUp,
            onCheckedChange = actions.onOpenSearchOnSwipeUpChange,
            supportingText = stringResource(CoreUiR.string.search_swipe_up_summary),
            leadingIcon = FontAwesomeIcons.Solid.ArrowUp,
            containerColor = containerColor
        )
        SheetActionRow(
            imageVector = FontAwesomeIcons.Solid.Cog,
            label = stringResource(R.string.search_settings),
            onClick = {
                onDismiss()
                actions.onOpenSearchSettings()
            }
        )
    }
}
