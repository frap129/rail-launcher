package feature.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import core.data.launchables.model.Launchable
import core.ui.composables.LaunchableIconArtwork
import org.koin.androidx.compose.koinViewModel

/** The Hidden apps screen: every Hidden app by resolved name and icon, each with an Unhide action. */
@Composable
fun HiddenAppsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val hiddenApps: List<Launchable>? by viewModel.hiddenApps.collectAsState()
    HiddenAppsContent(hiddenApps = hiddenApps, onUnhide = viewModel::unhide)
}

/** The Hidden apps list, its empty state once the Launchable State loads, or nothing until it does. */
@Composable
internal fun HiddenAppsContent(hiddenApps: List<Launchable>?, onUnhide: (Launchable) -> Unit) {
    if (hiddenApps == null) {
        return
    }
    LazyColumn {
        if (hiddenApps.isEmpty()) {
            item { SettingsEmptyState(text = R.string.no_hidden_apps) }
        } else {
            items(items = hiddenApps, key = { launchable -> launchable.key }) { launchable ->
                HiddenAppRow(launchable = launchable, onUnhide = { onUnhide(launchable) })
            }
        }
    }
}

@Composable
private fun HiddenAppRow(launchable: Launchable, onUnhide: () -> Unit) {
    val unhideLabel: String = stringResource(R.string.unhide_app, launchable.name)
    ListItem(
        headlineContent = { Text(launchable.name) },
        leadingContent = {
            LaunchableIconArtwork(launchable = launchable, contentDescription = null)
        },
        trailingContent = {
            TextButton(
                onClick = onUnhide,
                modifier = Modifier.semantics { contentDescription = unhideLabel }
            ) {
                Text(stringResource(R.string.unhide))
            }
        }
    )
}
