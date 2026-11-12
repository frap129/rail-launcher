package core.ui.composables

import android.Manifest
import android.content.Context
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import core.data.prefs.SearchEngine
import core.ui.R
import core.ui.search.SearchContactsUiEvent
import core.util.openApplicationDetailsSettings
import kotlinx.coroutines.flow.Flow

/** The Live web suggestions row, unavailable while the Search engine offers no suggestions. */
@Composable
fun SearchWebSuggestionsSwitchRow(
    checked: Boolean,
    searchEngine: SearchEngine,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: ImageVector? = null,
    containerColor: Color = settingsRowContainerColor
) {
    val hasSuggestionEngine: Boolean = searchEngine.offersSuggestions
    SettingsSwitchRow(
        title = stringResource(R.string.search_web_suggestions),
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = hasSuggestionEngine,
        supportingText = stringResource(
            if (hasSuggestionEngine) {
                R.string.search_web_suggestions_summary
            } else {
                R.string.search_web_suggestions_unavailable
            }
        ),
        leadingIcon = leadingIcon,
        containerColor = containerColor
    )
}

/**
 * The Contact results row, asking for the Contacts permission when it turns on and explaining a
 * permanent denial, like the Search settings screen.
 */
@Composable
fun SearchContactsSwitchRow(
    checked: Boolean,
    events: Flow<SearchContactsUiEvent>,
    onCheckedChange: (Boolean) -> Unit,
    onPermissionResult: (granted: Boolean, canAskAgain: Boolean) -> Unit,
    onRefreshPermission: () -> Unit,
    leadingIcon: ImageVector? = null,
    containerColor: Color = settingsRowContainerColor
) {
    val context: Context = LocalContext.current
    var showPermissionDenied: Boolean by remember { mutableStateOf(false) }
    val permissionLauncher: ManagedActivityResultLauncher<String, Boolean> =
        rememberPermissionResultLauncher(permission = Manifest.permission.READ_CONTACTS) { granted, canAskAgain ->
            onPermissionResult(granted, canAskAgain)
        }
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                SearchContactsUiEvent.RequestContactsPermission -> permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                SearchContactsUiEvent.ShowContactsPermissionDenied -> showPermissionDenied = true
            }
        }
    }
    OnResume {
        onRefreshPermission()
    }
    SettingsSwitchRow(
        title = stringResource(R.string.search_contacts),
        checked = checked,
        onCheckedChange = onCheckedChange,
        supportingText = stringResource(R.string.search_contacts_summary),
        leadingIcon = leadingIcon,
        containerColor = containerColor
    )
    if (showPermissionDenied) {
        PermissionDeniedDialog(
            title = stringResource(R.string.search_contacts_permission_title),
            message = stringResource(R.string.search_contacts_permission_summary),
            onDismiss = { showPermissionDenied = false },
            onOpenSettings = {
                showPermissionDenied = false
                context.openApplicationDetailsSettings()
            }
        )
    }
}
