package feature.settings

import android.annotation.SuppressLint
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDeepLink
import androidx.navigation.navArgument
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
import compose.icons.fontawesomeicons.solid.Th
import core.data.icons.model.IconPack
import core.ui.R as CoreUiR
import core.ui.model.data.Destination
import org.koin.androidx.compose.koinViewModel

private const val SCREEN_ARGUMENT = "screen"
private const val SETTINGS_ROUTE = "settings"

@SuppressLint("RestrictedApi")
val settingsDestination = Destination(
    route = "$SETTINGS_ROUTE/{$SCREEN_ARGUMENT}",
    content = { navController, backStackEntry ->
        SettingsScreen(navController, backStackEntry)
    },
    arguments = listOf(
        navArgument(SCREEN_ARGUMENT) {
            defaultValue = SettingsScreen.Main.key
        }
    ),
    deepLinks = listOf(
        NavDeepLink("rail-launcher://$SETTINGS_ROUTE/{$SCREEN_ARGUMENT}")
    ),
    enterTransition = { fadeIn(animationSpec = tween(durationMillis = 150)) },
    exitTransition = { fadeOut(animationSpec = tween(durationMillis = 100, delayMillis = 100)) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, backStackEntry: NavBackStackEntry, viewModel: SettingsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val theme = MaterialTheme.colorScheme
    val screen: SettingsScreen = remember {
        SettingsScreen.entries.find { it.key == backStackEntry.arguments?.getString(SCREEN_ARGUMENT) } ?: SettingsScreen.Main
    }

    Scaffold(
        modifier = Modifier.background(theme.surface),
        topBar = {
            TopAppBar(
                title = {
                    val title = when (screen) {
                        SettingsScreen.Main -> stringResource(R.string.settings)
                        SettingsScreen.Appearance -> stringResource(R.string.appearance)
                        SettingsScreen.Widgets -> stringResource(R.string.widgets)
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() }
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
            when (screen) {
                SettingsScreen.Main -> SettingsMainScreen(
                    toAppearance = { navController.navigate(route = "$SETTINGS_ROUTE/${SettingsScreen.Appearance.key}") },
                    toWidgets = { navController.navigate(route = "$SETTINGS_ROUTE/${SettingsScreen.Widgets.key}") }
                )

                SettingsScreen.Appearance -> SettingsAppearanceScreen(
                    iconPacks = uiState.iconPacks,
                    selectedIconPack = uiState.selectedIconPack,
                    onIconPackSelected = { iconPack -> viewModel.setIconPack(iconPack) }
                )

                SettingsScreen.Widgets -> SettingsWidgetsScreen()
            }
        }
    }
}

@Composable
fun SettingsMainScreen(toAppearance: () -> Unit, toWidgets: () -> Unit) {
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
        item {
            ListItem(
                modifier = Modifier.clickable { toWidgets.invoke() },
                headlineContent = { Text(stringResource(R.string.widgets)) },
                supportingContent = { Text(stringResource(R.string.widgets_summary)) },
                leadingContent = {
                    Icon(
                        modifier = Modifier.size(64.dp).padding(16.dp),
                        imageVector = FontAwesomeIcons.Solid.Th,
                        contentDescription = stringResource(R.string.widgets)
                    )
                }
            )
        }
    }
}

@Composable
fun SettingsWidgetsScreen(viewModel: WidgetSettingsViewModel = koinViewModel()) {
    val uiState: WidgetSettingsUiState by viewModel.uiState.collectAsState()
    LazyColumn {
        item {
            Text(
                text = stringResource(R.string.header),
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.show_header),
                checked = uiState.headerShown,
                onCheckedChange = viewModel::setHeaderShown
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.clock),
                checked = uiState.clockVisible,
                enabled = uiState.headerShown && uiState.canHideClock,
                supportingText = if (uiState.headerShown && !uiState.canHideClock) {
                    stringResource(CoreUiR.string.header_last_part_supporting_text)
                } else {
                    null
                },
                onCheckedChange = viewModel::setClockVisible
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.date),
                checked = uiState.dateVisible,
                enabled = uiState.headerShown && uiState.canHideDate,
                supportingText = if (uiState.headerShown && !uiState.canHideDate) {
                    stringResource(CoreUiR.string.header_last_part_supporting_text)
                } else {
                    null
                },
                onCheckedChange = viewModel::setDateVisible
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null
) {
    ListItem(
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange
        ),
        headlineContent = { Text(title) },
        supportingContent = if (supportingText != null) {
            { Text(supportingText) }
        } else {
            null
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    )
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
