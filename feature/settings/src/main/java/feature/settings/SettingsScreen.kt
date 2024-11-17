package feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.composables.core.Menu
import com.composables.core.MenuButton
import com.composables.core.MenuContent
import com.composables.core.MenuItem
import com.composables.core.rememberMenuState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Icons
import compose.icons.fontawesomeicons.solid.Palette
import core.data.icons.model.IconPack
import core.ui.model.data.Destination
import org.koin.androidx.compose.koinViewModel

val settingsDestination = Destination(
    route = "settings",
    content = { navController, _ ->
        SettingsScreen(navController)
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = koinViewModel()) {
    val uiState = viewModel.uiState.collectAsState()
    val theme = MaterialTheme.colorScheme

    Scaffold(
        modifier = Modifier.background(theme.surface),
        topBar = {
            TopAppBar(
                title = {
                    val title = when (uiState.value) {
                        SettingsUiState.Main -> stringResource(R.string.settings)
                        is SettingsUiState.Appearance -> stringResource(R.string.appearance)
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (uiState.value) {
                                SettingsUiState.Main -> navController.navigateUp()
                                is SettingsUiState.Appearance -> viewModel.toMain()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (uiState.value) {
                SettingsUiState.Main -> SettingsMainScreen(
                    toAppearance = { viewModel.toAppearance() }
                )

                is SettingsUiState.Appearance -> SettingsAppearanceScreen(
                    iconPacks = (uiState.value as SettingsUiState.Appearance).iconPacks,
                    selectedIconPack = (uiState.value as SettingsUiState.Appearance).selectedIconPack,
                    onIconPackSelected = { iconPack -> viewModel.setIconPack(iconPack) }
                )
            }
        }
    }
}

@Composable
fun SettingsMainScreen(toAppearance: () -> Unit) {
    LazyColumn {
        item {
            ListItem(
                modifier = Modifier.clickable { toAppearance.invoke() },
                headlineContent = { Text(stringResource(R.string.appearance)) },
                supportingContent = { Text(stringResource(R.string.appearance_summary)) },
                leadingContent = {
                    Icon(
                        modifier = Modifier.size(64.dp).padding(16.dp),
                        imageVector = FontAwesomeIcons.Solid.Palette,
                        contentDescription = stringResource(R.string.appearance)
                    )
                }
            )
        }
    }
}

@Composable
fun SettingsAppearanceScreen(iconPacks: Set<IconPack>, selectedIconPack: IconPack, onIconPackSelected: (IconPack) -> Unit) {
    val theme = MaterialTheme.colorScheme
    val state = rememberMenuState(expanded = false)
    Menu(state = state) {
        MenuButton {
            ListItem(
                headlineContent = { Text(stringResource(R.string.icon_pack)) },
                supportingContent = { Text(selectedIconPack.name) },
                leadingContent = {
                    Icon(
                        modifier = Modifier.size(64.dp).padding(16.dp),
                        imageVector = FontAwesomeIcons.Solid.Icons,
                        contentDescription = stringResource(R.string.icon_pack)
                    )
                }
            )
        }

        MenuContent(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardColors(
                    containerColor = theme.surfaceVariant,
                    contentColor = theme.onSurfaceVariant,
                    disabledContentColor = theme.onPrimaryContainer,
                    disabledContainerColor = theme.primaryContainer
                )
            ) {
                iconPacks.forEach { iconPack ->
                    MenuItem(onClick = { onIconPackSelected.invoke(iconPack) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                modifier = Modifier.size(64.dp).padding(8.dp),
                                model = iconPack.icon,
                                contentDescription = iconPack.name
                            )
                            Text(
                                modifier = Modifier.padding(end = 16.dp),
                                text = iconPack.name
                            )
                        }
                    }
                }
            }
        }
    }
}
