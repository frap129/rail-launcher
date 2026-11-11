package feature.settings

import android.Manifest
import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import core.data.prefs.SearchEngine
import core.ui.R as CoreUiR
import core.ui.composables.OnResume
import core.ui.composables.PermissionDeniedDialog
import core.ui.composables.rememberPermissionResultLauncher
import core.ui.model.data.SEARCH_ENTRY_URI
import core.util.SEARCH_TEMPLATE_PLACEHOLDER
import core.util.isValidSearchTemplate
import core.util.openApplicationDetailsSettings
import org.koin.androidx.compose.koinViewModel

/** The Search section: how Search opens, its rows, Search suggestions and the Search engine. */
@Composable
fun SettingsSearchScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val uiState: SettingsUiState by viewModel.uiState.collectAsState()
    val context: Context = LocalContext.current
    var isChoosingEngine: Boolean by remember { mutableStateOf(false) }
    var isEditingCustomEngine: Boolean by remember { mutableStateOf(false) }
    var showContactsPermissionDenied: Boolean by remember { mutableStateOf(false) }
    val contactsPermissionLauncher: ManagedActivityResultLauncher<String, Boolean> =
        rememberPermissionResultLauncher(permission = Manifest.permission.READ_CONTACTS) { granted, canAskAgain ->
            viewModel.onContactsPermissionResult(granted = granted, canAskAgain = canAskAgain)
        }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.RequestContactsPermission -> contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                SettingsEvent.ShowContactsPermissionDenied -> showContactsPermissionDenied = true
            }
        }
    }
    OnResume {
        viewModel.refreshContactsPermission()
    }
    LazyColumn {
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.search_swipe_up),
                checked = uiState.openSearchOnSwipeUp,
                supportingText = stringResource(R.string.search_swipe_up_summary),
                onCheckedChange = viewModel::setOpenSearchOnSwipeUp
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.search_home_press),
                checked = uiState.openSearchOnHomePress,
                supportingText = stringResource(R.string.search_home_press_summary),
                onCheckedChange = viewModel::setOpenSearchOnHomePress
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.search_suggestions),
                checked = uiState.searchSuggestions,
                supportingText = stringResource(R.string.search_suggestions_summary),
                onCheckedChange = viewModel::setSearchSuggestions
            )
        }
        item {
            val hasSuggestionEngine: Boolean = uiState.searchEngine.offersSuggestions
            SettingsSwitchRow(
                title = stringResource(R.string.search_web_suggestions),
                checked = uiState.searchWebSuggestions,
                supportingText = if (hasSuggestionEngine) {
                    stringResource(R.string.search_web_suggestions_summary)
                } else {
                    stringResource(R.string.search_web_suggestions_unavailable)
                },
                enabled = hasSuggestionEngine,
                onCheckedChange = viewModel::setSearchWebSuggestions
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.search_auto_launch),
                checked = uiState.searchAutoLaunch,
                supportingText = stringResource(R.string.search_auto_launch_summary),
                onCheckedChange = viewModel::setSearchAutoLaunch
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.search_bottom_anchored_results),
                checked = uiState.searchBottomAnchoredResults,
                supportingText = stringResource(R.string.search_bottom_anchored_results_summary),
                onCheckedChange = viewModel::setSearchBottomAnchoredResults
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.search_calculator_row),
                checked = uiState.searchCalculatorRow,
                supportingText = stringResource(R.string.search_calculator_row_summary),
                onCheckedChange = viewModel::setSearchCalculatorRow
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.search_web_row),
                checked = uiState.searchWebRow,
                supportingText = stringResource(R.string.search_web_row_summary),
                onCheckedChange = viewModel::setSearchWebRow
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.search_play_store_row),
                checked = uiState.searchPlayStoreRow,
                supportingText = stringResource(R.string.search_play_store_row_summary),
                onCheckedChange = viewModel::setSearchPlayStoreRow
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.search_contacts),
                checked = uiState.searchContacts,
                supportingText = stringResource(R.string.search_contacts_summary),
                onCheckedChange = viewModel::setSearchContacts
            )
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.search_engine),
                value = stringResource(uiState.searchEngine.labelResource()),
                onClick = { isChoosingEngine = true }
            )
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.search_custom_engine),
                value = uiState.searchEngineTemplate ?: stringResource(R.string.search_custom_engine_not_set),
                onClick = { isEditingCustomEngine = true }
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
    if (isChoosingEngine) {
        SingleChoiceDialog(
            title = stringResource(R.string.search_engine),
            options = SearchEngine.entries
                .filter { engine -> engine != SearchEngine.Custom }
                .map { engine -> SingleChoiceOption(value = engine, label = stringResource(engine.labelResource())) },
            selected = uiState.searchEngine,
            onSelect = { engine ->
                viewModel.setSearchEngine(engine)
                isChoosingEngine = false
            },
            onDismiss = { isChoosingEngine = false }
        )
    }
    if (isEditingCustomEngine) {
        SearchEngineTemplateDialog(
            initialTemplate = uiState.searchEngineTemplate,
            onSave = { template ->
                viewModel.setCustomSearchEngineTemplate(template)
                isEditingCustomEngine = false
            },
            onDismiss = { isEditingCustomEngine = false }
        )
    }
    if (showContactsPermissionDenied) {
        PermissionDeniedDialog(
            title = stringResource(R.string.search_contacts_permission_title),
            message = stringResource(R.string.search_contacts_permission_summary),
            onDismiss = { showContactsPermissionDenied = false },
            onOpenSettings = {
                showContactsPermissionDenied = false
                context.openApplicationDetailsSettings()
            }
        )
    }
}

@Composable
private fun SearchEngineTemplateDialog(initialTemplate: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var template: String by remember(initialTemplate) { mutableStateOf(initialTemplate.orEmpty()) }
    val isValid: Boolean = isValidSearchTemplate(template = template)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_custom_engine)) },
        text = {
            Column {
                Text(stringResource(R.string.search_custom_engine_help, SEARCH_TEMPLATE_PLACEHOLDER))
                OutlinedTextField(
                    value = template,
                    onValueChange = { changedTemplate -> template = changedTemplate },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = template.isNotEmpty() && !isValid,
                    supportingText = if (template.isNotEmpty() && !isValid) {
                        { Text(stringResource(R.string.search_custom_engine_invalid, SEARCH_TEMPLATE_PLACEHOLDER)) }
                    } else {
                        null
                    }
                )
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { onSave(template) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        }
    )
}

@StringRes
private fun SearchEngine.labelResource(): Int = when (this) {
    SearchEngine.Google -> R.string.search_engine_google
    SearchEngine.DuckDuckGo -> R.string.search_engine_duckduckgo
    SearchEngine.Brave -> R.string.search_engine_brave
    SearchEngine.Kagi -> R.string.search_engine_kagi
    SearchEngine.Startpage -> R.string.search_engine_startpage
    SearchEngine.Bing -> R.string.search_engine_bing
    SearchEngine.Perplexity -> R.string.search_engine_perplexity
    SearchEngine.Custom -> R.string.search_engine_custom
}
