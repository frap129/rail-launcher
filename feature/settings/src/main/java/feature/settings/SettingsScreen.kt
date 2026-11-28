package feature.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Insets
import android.os.Process
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import compose.icons.fontawesomeicons.solid.Calendar
import compose.icons.fontawesomeicons.solid.Clock
import compose.icons.fontawesomeicons.solid.EyeSlash
import compose.icons.fontawesomeicons.solid.HandPointer
import compose.icons.fontawesomeicons.solid.Icons
import compose.icons.fontawesomeicons.solid.Palette
import compose.icons.fontawesomeicons.solid.Search
import compose.icons.fontawesomeicons.solid.Th
import compose.icons.fontawesomeicons.solid.TrashAlt
import core.data.calendar.CalendarInfo
import core.data.calendar.ICalendarPermissionGate
import core.data.icons.model.IconPack
import core.data.prefs.AppListOrder
import core.data.prefs.BatteryVisibility
import core.data.prefs.EventSettings
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSize
import core.data.prefs.RailSide
import core.data.prefs.ScrubEdges
import core.data.prefs.TimeFormat
import core.data.prefs.canSetBatteryVisibility
import core.data.widgets.MediaPlayerPlacement
import core.data.widgets.WidgetAreaHeightLimit
import core.data.widgets.WidgetAreaSettings
import core.data.widgets.WidgetSpacing
import core.ui.R as CoreUiR
import core.ui.composables.CalendarPermissionResumeRefresh
import core.ui.composables.NotificationAccessRow
import core.ui.composables.OnResume
import core.ui.composables.PermissionDeniedDialog
import core.ui.composables.SettingsActionRow
import core.ui.composables.SettingsConfirmedDestructiveRow
import core.ui.composables.SettingsEmptyState
import core.ui.composables.SettingsNavigationRow
import core.ui.composables.SettingsSectionTitle
import core.ui.composables.SettingsSnackbarHost
import core.ui.composables.SettingsSwitchRow
import core.ui.composables.SingleChoiceDialog
import core.ui.composables.SingleChoiceOption
import core.ui.composables.WarningRow
import core.ui.composables.rememberPermissionResultLauncher
import core.ui.composables.settingsContentPadding
import core.ui.composables.sheetContainerColor
import core.ui.layout.resolveRotateHome
import core.ui.model.data.Destination
import core.ui.model.data.HOME_ADD_WIDGET_REQUEST_KEY
import core.ui.model.data.LAUNCHER_ROUTE
import core.ui.model.data.OneShotRequest
import core.ui.model.data.POPUPS_SETTINGS_ROUTE
import core.ui.model.data.SEARCH_SETTINGS_ROUTE
import core.ui.model.data.SETTINGS_ROUTE
import core.ui.util.formatDpLabel
import core.ui.widgets.HeaderClockStyleChooser
import core.ui.widgets.HeaderTapTargetPart
import core.ui.widgets.IAppWidgetViewCache
import core.ui.widgets.MoveWidgetAreaSlider
import core.ui.widgets.NotificationAccessDialog
import core.ui.widgets.TapActionPartUi
import core.ui.widgets.TapActionPickerContent
import core.ui.widgets.TapActionPickerState
import core.ui.widgets.labelResource
import core.ui.widgets.openRailNotificationListenerSettings
import core.util.RECENT_INSTALL_WINDOW_DAYS
import core.util.openApplicationDetailsSettings
import core.util.openDefaultHomeSettings
import core.util.readAppVersionName
import core.util.requestUninstall
import core.util.restartRail
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private const val SCREEN_ARGUMENT = "screen"
private const val SECTION_ARGUMENT = "section"
private const val MEDIA_SECTION = "media"
private const val WIDGET_AREA_SECTION_ROWS = 5
private const val HEADER_SECTION_ROWS = 5
private const val EVENTS_SECTION_ROWS = 2
private const val APP_WIDGET_HEADER_ROWS = 2
private const val STACKS_SECTION_ROWS = 3

private val exclusionsHeaderStartPadding: Dp = 4.dp
private val exclusionsHeaderPadding: Dp = 8.dp
private val exclusionsAppIconSize: Dp = 40.dp

private fun mediaSectionIndex(appWidgetCount: Int): Int = WIDGET_AREA_SECTION_ROWS +
    HEADER_SECTION_ROWS +
    EVENTS_SECTION_ROWS +
    APP_WIDGET_HEADER_ROWS +
    appWidgetCount +
    STACKS_SECTION_ROWS

@SuppressLint("RestrictedApi")
val settingsDestination = Destination(
    route = "$SETTINGS_ROUTE/{$SCREEN_ARGUMENT}?$SECTION_ARGUMENT={$SECTION_ARGUMENT}",
    content = { navController, backStackEntry ->
        SettingsScreen(navController, backStackEntry)
    },
    arguments = listOf(
        navArgument(SCREEN_ARGUMENT) {
            defaultValue = SettingsScreen.Main.key
        },
        navArgument(SECTION_ARGUMENT) {
            nullable = true
            defaultValue = null
        }
    ),
    deepLinks = listOf(
        NavDeepLink("rail-launcher://$SETTINGS_ROUTE/{$SCREEN_ARGUMENT}")
    ),
    enterTransition = { fadeIn(animationSpec = tween(durationMillis = 150)) },
    exitTransition = { fadeOut(animationSpec = tween(durationMillis = 100, delayMillis = 100)) }
)

/**
 * Leaves Settings for Home and asks it to open the App Widget picker.
 *
 * A deep link can open Settings without a Home entry below it, so fall back
 * to clearing the Settings entries and navigating to Home.
 */
private fun NavController.startAddWidgetOnHome() {
    val hasHomeOnBackStack: Boolean = popBackStack(LAUNCHER_ROUTE, inclusive = false)
    if (!hasHomeOnBackStack) {
        val settingsId: Int? = currentDestination?.id
        while (settingsId != null && currentDestination?.id == settingsId && popBackStack()) {
            // Keep popping until every Settings screen is gone.
        }
        navigate(LAUNCHER_ROUTE)
    }
    OneShotRequest(
        savedStateHandle = getBackStackEntry(LAUNCHER_ROUTE).savedStateHandle,
        key = HOME_ADD_WIDGET_REQUEST_KEY
    ).request()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, backStackEntry: NavBackStackEntry, viewModel: SettingsViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val hasStoppedSuggestions: Boolean by viewModel.hasStoppedSuggestions.collectAsState()
    val context: Context = LocalContext.current
    val theme = MaterialTheme.colorScheme
    val screen: SettingsScreen = remember {
        SettingsScreen.entries.find { it.key == backStackEntry.arguments?.getString(SCREEN_ARGUMENT) } ?: SettingsScreen.Main
    }
    val section: String? = backStackEntry.arguments?.getString(SECTION_ARGUMENT)

    Scaffold(
        modifier = Modifier.background(theme.surface),
        topBar = {
            TopAppBar(
                title = {
                    val title = when (screen) {
                        SettingsScreen.Main -> stringResource(CoreUiR.string.settings)
                        SettingsScreen.Appearance -> stringResource(R.string.appearance)
                        SettingsScreen.Widgets -> stringResource(R.string.widgets)
                        SettingsScreen.Popups -> stringResource(R.string.gestures_and_popups)
                        SettingsScreen.Search -> stringResource(CoreUiR.string.search)
                        SettingsScreen.HiddenApps -> stringResource(R.string.hidden_apps)
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
                            contentDescription = stringResource(CoreUiR.string.back)
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
                    hidePrivateSpaceWhenLocked = uiState.hidePrivateSpaceWhenLocked,
                    isPrivateSpaceAvailable = uiState.isPrivateSpaceAvailable,
                    toAppearance = { navController.navigate(route = "$SETTINGS_ROUTE/${SettingsScreen.Appearance.key}") },
                    toWidgets = { navController.navigate(route = "$SETTINGS_ROUTE/${SettingsScreen.Widgets.key}") },
                    toPopups = { navController.navigate(route = POPUPS_SETTINGS_ROUTE) },
                    toSearch = { navController.navigate(route = SEARCH_SETTINGS_ROUTE) },
                    toHiddenApps = { navController.navigate(route = "$SETTINGS_ROUTE/${SettingsScreen.HiddenApps.key}") },
                    onHidePrivateSpaceWhenLockedChange = viewModel::setHidePrivateSpaceWhenLocked,
                    versionName = readSettingsVersionName(),
                    advancedActions = SettingsAdvancedActions(
                        onRestartRail = context::restartRail,
                        onChangeDefaultLauncher = context::openDefaultHomeSettings,
                        onUninstallRail = { context.requestUninstall(context.packageName, Process.myUserHandle()) },
                        onOpenAppInfo = context::openApplicationDetailsSettings
                    )
                )

                SettingsScreen.Appearance -> SettingsAppearanceScreen(
                    iconPacks = uiState.iconPacks,
                    selectedIconPack = uiState.selectedIconPack,
                    railSide = uiState.railSide,
                    scrubEdges = uiState.scrubEdges,
                    appListOrder = uiState.appListOrder,
                    rankFoldersByUse = uiState.rankFoldersByUse,
                    showRecentlyInstalled = uiState.showRecentlyInstalled,
                    railHaptics = uiState.railHaptics,
                    showRail = uiState.showRail,
                    suggestOftenUsedApps = uiState.suggestOftenUsedApps,
                    rotateHome = uiState.rotateHome,
                    smallestScreenWidthDp = LocalConfiguration.current.smallestScreenWidthDp,
                    hasStoppedSuggestions = hasStoppedSuggestions,
                    actions = SettingsAppearanceActions(
                        onIconPackSelected = { iconPack -> viewModel.setIconPack(iconPack) },
                        onResetCustomIcons = viewModel::resetCustomIcons,
                        onAppListOrderSelected = viewModel::setAppListOrder,
                        onRankFoldersByUseChange = viewModel::setRankFoldersByUse,
                        onShowRecentlyInstalledChange = viewModel::setShowRecentlyInstalled,
                        onRailHapticsChange = viewModel::setRailHaptics,
                        onShowRailChange = viewModel::setShowRail,
                        onSuggestOftenUsedAppsChange = viewModel::setSuggestOftenUsedApps,
                        onRotateHomeChange = viewModel::setRotateHome,
                        onResetStoppedSuggestions = viewModel::resetStoppedSuggestions,
                        onRailSideSelected = viewModel::setRailSide,
                        onScrubEdgesSelected = viewModel::setScrubEdges
                    )
                )

                SettingsScreen.Widgets -> SettingsWidgetsScreen(
                    section = section,
                    onAddWidget = { navController.startAddWidgetOnHome() }
                )

                SettingsScreen.Popups -> SettingsPopupsScreen()

                SettingsScreen.Search -> SearchSettingsScreen()

                SettingsScreen.HiddenApps -> HiddenAppsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun readSettingsVersionName(): String {
    val context: Context = LocalContext.current
    val fallback: String = stringResource(R.string.version_unavailable)
    return remember(context, fallback) { context.readAppVersionName(fallback = fallback) }
}

@Composable
fun SettingsMainScreen(
    hidePrivateSpaceWhenLocked: Boolean,
    isPrivateSpaceAvailable: Boolean,
    toAppearance: () -> Unit,
    toWidgets: () -> Unit,
    toPopups: () -> Unit,
    toSearch: () -> Unit,
    toHiddenApps: () -> Unit,
    onHidePrivateSpaceWhenLockedChange: (Boolean) -> Unit,
    versionName: String,
    advancedActions: SettingsAdvancedActions
) {
    LazyColumn {
        item {
            SettingsNavigationRow(
                title = R.string.appearance,
                summary = R.string.appearance_summary,
                icon = FontAwesomeIcons.Solid.Palette,
                onClick = toAppearance
            )
        }
        item {
            SettingsNavigationRow(
                title = R.string.widgets,
                summary = R.string.widgets_summary,
                icon = FontAwesomeIcons.Solid.Th,
                onClick = toWidgets
            )
        }
        item {
            SettingsNavigationRow(
                title = R.string.gestures_and_popups,
                summary = R.string.gestures_and_popups_summary,
                icon = FontAwesomeIcons.Solid.HandPointer,
                onClick = toPopups
            )
        }
        item {
            SettingsNavigationRow(
                title = CoreUiR.string.search,
                summary = R.string.search_summary,
                icon = FontAwesomeIcons.Solid.Search,
                onClick = toSearch
            )
        }
        item {
            SettingsNavigationRow(
                title = R.string.hidden_apps,
                summary = R.string.hidden_apps_summary,
                icon = FontAwesomeIcons.Solid.EyeSlash,
                onClick = toHiddenApps
            )
        }
        if (isPrivateSpaceAvailable) {
            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.hide_private_space_when_locked),
                    checked = hidePrivateSpaceWhenLocked,
                    supportingText = stringResource(R.string.hide_private_space_when_locked_summary),
                    onCheckedChange = onHidePrivateSpaceWhenLockedChange
                )
            }
        }
        item { SettingsSectionTitle(R.string.advanced) }
        item { SettingsAdvancedRows(versionName = versionName, actions = advancedActions) }
    }
}

/** What the Advanced section asks its host to open or restart. */
data class SettingsAdvancedActions(
    val onRestartRail: () -> Unit,
    val onChangeDefaultLauncher: () -> Unit,
    val onUninstallRail: () -> Unit,
    val onOpenAppInfo: () -> Unit
)

@Composable
private fun SettingsAdvancedRows(versionName: String, actions: SettingsAdvancedActions) {
    Column {
        SettingsActionRow(
            title = stringResource(R.string.restart_rail),
            value = "",
            onClick = actions.onRestartRail
        )
        SettingsActionRow(
            title = stringResource(R.string.change_default_launcher),
            value = "",
            onClick = actions.onChangeDefaultLauncher
        )
        SettingsActionRow(
            title = stringResource(R.string.uninstall_rail),
            value = "",
            onClick = actions.onUninstallRail
        )
        SettingsActionRow(
            title = stringResource(CoreUiR.string.app_info),
            value = versionName,
            onClick = actions.onOpenAppInfo
        )
    }
}

@Composable
fun SettingsWidgetsScreen(section: String?, onAddWidget: () -> Unit, viewModel: WidgetSettingsViewModel = koinViewModel()) {
    val uiState: WidgetSettingsUiState by viewModel.uiState.collectAsState()
    val tapActionPicker: TapActionPickerState? by viewModel.tapActionPicker.collectAsState()
    val mediaPlayerExclusions: MediaPlayerExclusionsState? by viewModel.mediaPlayerExclusions.collectAsState()
    val context: Context = LocalContext.current
    val appWidgetViewCache: IAppWidgetViewCache = koinInject()
    val calendarPermissionGate: ICalendarPermissionGate = koinInject()
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var scrolledToMedia: Boolean by remember { mutableStateOf(false) }
    var showClockStyleChooser: Boolean by remember { mutableStateOf(false) }
    var openDialog: WidgetSettingsDialog? by remember { mutableStateOf(null) }
    var showPermissionDialog: Boolean by remember { mutableStateOf(false) }
    var showNotificationAccessDialog: Boolean by remember { mutableStateOf(false) }
    val permissionLauncher: ManagedActivityResultLauncher<String, Boolean> =
        rememberPermissionResultLauncher(permission = Manifest.permission.READ_CALENDAR) { granted, canAskAgain ->
            viewModel.onCalendarPermissionResult(granted = granted, canAskAgain = canAskAgain)
        }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WidgetSettingsEvent.RequestCalendarPermission -> permissionLauncher.launch(Manifest.permission.READ_CALENDAR)

                is WidgetSettingsEvent.ShowCalendarPermissionDenied -> showPermissionDialog = true

                WidgetSettingsEvent.RequestNotificationAccess -> showNotificationAccessDialog = true

                WidgetSettingsEvent.StartAddWidget -> onAddWidget()

                is WidgetSettingsEvent.AppWidgetRemoved -> appWidgetViewCache.remove(event.appWidgetId)

                is WidgetSettingsEvent.ShowMessage -> coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(event.message))
                }
            }
        }
    }
    CalendarPermissionResumeRefresh(calendarPermissionGate = calendarPermissionGate)
    OnResume {
        viewModel.refreshMediaAccess()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        WidgetSettingsList(
            uiState = uiState,
            viewModel = viewModel,
            listState = listState,
            onClockStyleClick = { showClockStyleChooser = true },
            onDialogClick = { dialog -> openDialog = dialog },
            onTapActionClick = viewModel::openTapActionPicker,
            onAddWidget = viewModel::openAddWidget,
            onNotificationAccessClick = { context.openRailNotificationListenerSettings() }
        )
        SettingsSnackbarHost(hostState = snackbarHostState)
    }
    LaunchedEffect(section, uiState.isLoaded, uiState.appWidgets.size) {
        if (section == MEDIA_SECTION && uiState.isLoaded && !scrolledToMedia) {
            listState.scrollToItem(index = mediaSectionIndex(appWidgetCount = uiState.appWidgets.size))
            scrolledToMedia = true
        }
    }
    if (showClockStyleChooser) {
        ClockStyleChooserSheet(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showClockStyleChooser = false }
        )
    }
    tapActionPicker?.let { picker ->
        TapActionPickerSheet(
            state = picker,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = viewModel::closeTapActionPicker
        )
    }
    openDialog?.let { dialog ->
        WidgetSettingsDialogContent(
            dialog = dialog,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { openDialog = null }
        )
    }
    mediaPlayerExclusions?.let { state ->
        MediaPlayerExclusionsDialog(
            state = state,
            onQueryChange = viewModel::setExclusionQuery,
            onExcludedChange = viewModel::setExcluded,
            onDismiss = viewModel::closeExcludedApps
        )
    }
    if (showPermissionDialog) {
        PermissionDeniedDialog(
            title = stringResource(CoreUiR.string.calendar_permission_denied_title),
            message = stringResource(CoreUiR.string.calendar_permission_denied_message),
            onDismiss = { showPermissionDialog = false },
            onOpenSettings = {
                context.openApplicationDetailsSettings()
                showPermissionDialog = false
            }
        )
    }
    if (showNotificationAccessDialog) {
        NotificationAccessDialog(onDismiss = { showNotificationAccessDialog = false })
    }
}

@Composable
private fun WidgetSettingsList(
    uiState: WidgetSettingsUiState,
    viewModel: WidgetSettingsViewModel,
    listState: LazyListState,
    onClockStyleClick: () -> Unit,
    onDialogClick: (WidgetSettingsDialog) -> Unit,
    onTapActionClick: (HeaderTapTargetPart) -> Unit,
    onAddWidget: () -> Unit,
    onNotificationAccessClick: () -> Unit
) {
    LazyColumn(state = listState) {
        item { SettingsSectionTitle(R.string.widget_area) }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.limit_widget_area_height),
                checked = uiState.heightLimitEnabled,
                onCheckedChange = viewModel::setHeightLimitEnabled,
                supportingText = stringResource(
                    if (uiState.heightLimitEnabled) {
                        R.string.widget_area_scrolls_when_needed
                    } else {
                        R.string.home_scrolls_as_one_page
                    }
                )
            )
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.maximum_height),
                value = stringResource(uiState.heightLimit.labelResource()),
                enabled = uiState.heightLimitEnabled,
                onClick = { onDialogClick(WidgetSettingsDialog.HeightLimit) }
            )
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.top_offset),
                value = formatDpLabel(uiState.topOffsetDp),
                onClick = { onDialogClick(WidgetSettingsDialog.TopOffset) }
            )
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.spacing_between_widgets),
                value = stringResource(uiState.widgetSpacing.labelResource()),
                onClick = { onDialogClick(WidgetSettingsDialog.WidgetSpacing) }
            )
        }
        item { SettingsSectionTitle(R.string.header) }
        item { HeaderVisibilityRows(uiState = uiState, viewModel = viewModel) }
        item {
            HeaderFormatRows(
                uiState = uiState,
                viewModel = viewModel,
                onClockStyleClick = onClockStyleClick,
                onDialogClick = onDialogClick
            )
        }
        item {
            HeaderLayoutRows(
                uiState = uiState,
                viewModel = viewModel,
                onDialogClick = onDialogClick
            )
        }
        item { HeaderTapActionRows(uiState = uiState, onTapActionClick = onTapActionClick) }
        item { SettingsSectionTitle(CoreUiR.string.upcoming_events) }
        item { EventSettingsRows(uiState = uiState, viewModel = viewModel, onDialogClick = onDialogClick) }
        item { SettingsSectionTitle(R.string.app_widgets) }
        item {
            SettingsActionRow(
                title = stringResource(CoreUiR.string.add_widget),
                value = "",
                enabled = true,
                onClick = onAddWidget
            )
        }
        items(items = uiState.appWidgets, key = { widget -> widget.widgetId }) { widget ->
            AppWidgetSettingsRow(
                widget = widget,
                onRemove = { viewModel.removeWidget(widget.widgetId) },
                onExtendUnderRailChange = { extendsUnderRail ->
                    viewModel.setExtendsUnderRail(widgetId = widget.widgetId, extendsUnderRail = extendsUnderRail)
                }
            )
        }
        item { SettingsSectionTitle(R.string.widget_stacks) }
        item {
            SettingsActionRow(
                title = stringResource(R.string.max_stack_pages),
                value = uiState.maxStackPages.toString(),
                enabled = true,
                onClick = { onDialogClick(WidgetSettingsDialog.MaxStackPages) },
                supportingText = stringResource(R.string.more_pages_use_memory)
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.page_indicator),
                checked = uiState.stackIndicatorVisible,
                onCheckedChange = viewModel::setStackIndicatorVisible
            )
        }
        item { SettingsSectionTitle(CoreUiR.string.media_player) }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.show_media_player),
                checked = uiState.mediaPlayerEnabled,
                supportingText = stringResource(R.string.media_player_summary),
                onCheckedChange = viewModel::setMediaPlayerEnabled
            )
        }
        item {
            NotificationAccessRow(
                hasAccess = uiState.notificationAccess,
                enabled = uiState.mediaPlayerEnabled,
                onClick = onNotificationAccessClick
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.show_when_paused),
                checked = uiState.showWhenPaused,
                enabled = uiState.mediaPlayerEnabled,
                onCheckedChange = viewModel::setShowWhenPaused
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.show_artwork),
                checked = uiState.showArtwork,
                enabled = uiState.mediaPlayerEnabled,
                onCheckedChange = viewModel::setShowArtwork
            )
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.position),
                value = stringResource(uiState.mediaPlayerPlacement.labelResource()),
                enabled = uiState.mediaPlayerEnabled,
                onClick = { onDialogClick(WidgetSettingsDialog.MediaPosition) }
            )
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.excluded_apps),
                value = "",
                supportingText = excludedAppsSummary(count = uiState.excludedMediaPlayerPackages.size),
                enabled = uiState.mediaPlayerEnabled,
                onClick = viewModel::openExcludedApps
            )
        }
    }
}

@Composable
private fun excludedAppsSummary(count: Int): String = if (count == 0) {
    stringResource(R.string.none)
} else {
    pluralStringResource(R.plurals.excluded_apps_count, count, count)
}

private fun MediaPlayerPlacement.labelResource(): Int = when (this) {
    MediaPlayerPlacement.BELOW_WIDGET_AREA -> R.string.below_widget_area
    MediaPlayerPlacement.TOP_OF_WIDGET_AREA -> R.string.top_of_widget_area
}

@Composable
private fun AppWidgetSettingsRow(widget: WidgetSettingsAppWidget, onRemove: () -> Unit, onExtendUnderRailChange: (Boolean) -> Unit) {
    var expanded by remember(widget.widgetId) { mutableStateOf(false) }
    Column {
        ListItem(
            modifier = Modifier.clickable { expanded = !expanded },
            headlineContent = { Text(widget.label) },
            supportingContent = if (widget.isWorkProfile) {
                { Text(stringResource(R.string.work_profile)) }
            } else {
                null
            },
            leadingContent = {
                AsyncImage(
                    model = widget.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            },
            trailingContent = {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = FontAwesomeIcons.Solid.TrashAlt,
                        contentDescription = stringResource(CoreUiR.string.remove_widget)
                    )
                }
            }
        )
        if (expanded) {
            SettingsSwitchRow(
                title = stringResource(CoreUiR.string.extend_under_rail),
                checked = widget.extendsUnderRail,
                onCheckedChange = onExtendUnderRailChange,
                enabled = widget.canExtendUnderRail,
                supportingText = stringResource(
                    if (widget.canExtendUnderRail) {
                        CoreUiR.string.extend_under_rail_supporting_text
                    } else {
                        CoreUiR.string.not_available_in_stack
                    }
                )
            )
        }
    }
}

@Composable
private fun EventSettingsRows(
    uiState: WidgetSettingsUiState,
    viewModel: WidgetSettingsViewModel,
    onDialogClick: (WidgetSettingsDialog) -> Unit
) {
    Column {
        SettingsSwitchRow(
            title = stringResource(R.string.show_upcoming_events),
            checked = uiState.eventsEnabled,
            onCheckedChange = viewModel::setEventsEnabled
        )
        if (uiState.eventsEnabled && !uiState.calendarAccess) {
            WarningRow(
                title = stringResource(CoreUiR.string.calendar_permission_denied_title),
                actionLabel = stringResource(CoreUiR.string.grant),
                onActionClick = { viewModel.setEventsEnabled(true) }
            )
        }
        SettingsActionRow(
            title = stringResource(R.string.event_lookahead),
            value = pluralStringResource(R.plurals.event_lookahead_hours, uiState.eventLookaheadHours, uiState.eventLookaheadHours),
            enabled = uiState.eventsEnabled,
            onClick = { onDialogClick(WidgetSettingsDialog.EventLookahead) }
        )
        SettingsActionRow(
            title = stringResource(R.string.event_count_label),
            value = pluralStringResource(R.plurals.event_count, uiState.eventMaxEvents, uiState.eventMaxEvents),
            enabled = uiState.eventsEnabled,
            onClick = { onDialogClick(WidgetSettingsDialog.EventCount) }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.include_all_day),
            checked = uiState.includeAllDay,
            enabled = uiState.eventsEnabled,
            onCheckedChange = viewModel::setEventIncludeAllDay
        )
        SettingsActionRow(
            title = stringResource(R.string.calendars),
            value = calendarSelectionSummary(uiState = uiState),
            enabled = uiState.eventsEnabled,
            onClick = { onDialogClick(WidgetSettingsDialog.Calendars) }
        )
    }
}

@Composable
private fun calendarSelectionSummary(uiState: WidgetSettingsUiState): String {
    if (uiState.calendars.isEmpty()) {
        return stringResource(R.string.no_calendars)
    }
    val selectedCount: Int = uiState.calendars.count { calendar -> calendar.calendarKey !in uiState.excludedCalendarIds }
    return if (selectedCount == uiState.calendars.size) {
        stringResource(R.string.all_calendars)
    } else {
        stringResource(R.string.calendars_selected, selectedCount, uiState.calendars.size)
    }
}

@Composable
private fun HeaderVisibilityRows(uiState: WidgetSettingsUiState, viewModel: WidgetSettingsViewModel) {
    Column {
        SettingsSwitchRow(
            title = stringResource(R.string.show_header),
            checked = uiState.headerShown,
            onCheckedChange = viewModel::setHeaderShown
        )
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

@Composable
private fun HeaderFormatRows(
    uiState: WidgetSettingsUiState,
    viewModel: WidgetSettingsViewModel,
    onClockStyleClick: () -> Unit,
    onDialogClick: (WidgetSettingsDialog) -> Unit
) {
    Column {
        SettingsActionRow(
            title = stringResource(CoreUiR.string.clock_style_title),
            value = stringResource(uiState.clockStyle.labelResource()),
            enabled = uiState.headerShown,
            onClick = onClockStyleClick
        )
        SettingsActionRow(
            title = stringResource(R.string.time_format),
            value = stringResource(uiState.timeFormat.labelResource()),
            enabled = uiState.headerShown,
            onClick = { onDialogClick(WidgetSettingsDialog.TimeFormat) }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.show_am_pm),
            checked = uiState.showAmPm,
            enabled = uiState.headerShown && uiState.canShowAmPm,
            supportingText = if (!uiState.canShowAmPm) {
                stringResource(R.string.only_in_12_hour)
            } else {
                null
            },
            onCheckedChange = viewModel::setShowAmPm
        )
        SettingsActionRow(
            title = stringResource(CoreUiR.string.battery),
            value = stringResource(uiState.batteryVisibility.labelResource()),
            enabled = uiState.headerShown,
            onClick = { onDialogClick(WidgetSettingsDialog.Battery) }
        )
    }
}

@Composable
private fun HeaderLayoutRows(
    uiState: WidgetSettingsUiState,
    viewModel: WidgetSettingsViewModel,
    onDialogClick: (WidgetSettingsDialog) -> Unit
) {
    val dateFormatLabel: String = uiState.dateFormatOptions
        .firstOrNull { option -> option.preset == uiState.dateFormat }
        ?.label
        .orEmpty()
    Column {
        SettingsActionRow(
            title = stringResource(R.string.date_format),
            value = dateFormatLabel,
            enabled = uiState.headerShown,
            onClick = { onDialogClick(WidgetSettingsDialog.DateFormat) }
        )
        SettingsAlignmentRow(
            alignment = uiState.alignment,
            enabled = uiState.headerShown,
            onAlignmentSelected = viewModel::setAlignment
        )
        SettingsActionRow(
            title = stringResource(R.string.size),
            value = stringResource(uiState.size.labelResource()),
            enabled = uiState.headerShown,
            onClick = { onDialogClick(WidgetSettingsDialog.Size) }
        )
    }
}

@Composable
private fun HeaderTapActionRows(uiState: WidgetSettingsUiState, onTapActionClick: (HeaderTapTargetPart) -> Unit) {
    Column {
        SettingsActionRow(
            title = stringResource(R.string.time_tap_action),
            value = uiState.timeTapAction.label.resolve(),
            enabled = uiState.headerShown,
            onClick = { onTapActionClick(HeaderTapTargetPart.Time) }
        )
        SettingsActionRow(
            title = stringResource(R.string.date_tap_action),
            value = uiState.dateTapAction.label.resolve(),
            enabled = uiState.headerShown,
            onClick = { onTapActionClick(HeaderTapTargetPart.Date) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TapActionPickerSheet(
    state: TapActionPickerState,
    uiState: WidgetSettingsUiState,
    viewModel: WidgetSettingsViewModel,
    onDismiss: () -> Unit
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val partUi: TapActionPartUi = when (state.part) {
        HeaderTapTargetPart.Time -> TapActionPartUi(
            selected = uiState.timeTapAction.setting,
            icon = FontAwesomeIcons.Solid.Clock,
            onSelect = viewModel::setTimeTapTarget
        )

        HeaderTapTargetPart.Date -> TapActionPartUi(
            selected = uiState.dateTapAction.setting,
            icon = FontAwesomeIcons.Solid.Calendar,
            onSelect = viewModel::setDateTapTarget
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = sheetContainerColor) {
        TapActionPickerContent(
            state = state,
            partUi = partUi,
            onQueryChange = viewModel::setTapActionQuery
        )
    }
}

@Composable
private fun TapActionLabel.resolve(): String = when (this) {
    is TapActionLabel.Resource -> stringResource(id)
    is TapActionLabel.Launchable -> name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockStyleChooserSheet(uiState: WidgetSettingsUiState, viewModel: WidgetSettingsViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        HeaderClockStyleChooser(
            selected = uiState.clockStyle,
            previews = uiState.clockStylePreviews,
            onSelect = { style ->
                viewModel.setClockStyle(style)
                onDismiss()
            }
        )
    }
}

@Composable
private fun WidgetSettingsDialogContent(
    dialog: WidgetSettingsDialog,
    uiState: WidgetSettingsUiState,
    viewModel: WidgetSettingsViewModel,
    onDismiss: () -> Unit
) {
    when (dialog) {
        WidgetSettingsDialog.TimeFormat -> SingleChoiceDialog(
            title = stringResource(R.string.time_format),
            options = TimeFormat.entries.map { format ->
                SingleChoiceOption(value = format, label = stringResource(format.labelResource()))
            },
            selected = uiState.timeFormat,
            onSelect = { format ->
                viewModel.setTimeFormat(format)
                onDismiss()
            },
            onDismiss = onDismiss
        )

        WidgetSettingsDialog.DateFormat -> SingleChoiceDialog(
            title = stringResource(R.string.date_format),
            options = uiState.dateFormatOptions.map { option ->
                SingleChoiceOption(value = option.preset, label = option.label)
            },
            selected = uiState.dateFormat,
            onSelect = { format ->
                viewModel.setDateFormat(format)
                onDismiss()
            },
            onDismiss = onDismiss
        )

        WidgetSettingsDialog.Size -> SingleChoiceDialog(
            title = stringResource(R.string.size),
            options = HeaderSize.entries.map { size ->
                SingleChoiceOption(value = size, label = stringResource(size.labelResource()))
            },
            selected = uiState.size,
            onSelect = { size ->
                viewModel.setSize(size)
                onDismiss()
            },
            onDismiss = onDismiss
        )

        WidgetSettingsDialog.Battery -> SingleChoiceDialog(
            title = stringResource(CoreUiR.string.battery),
            options = BatteryVisibility.entries.map { visibility ->
                SingleChoiceOption(
                    value = visibility,
                    label = stringResource(visibility.labelResource()),
                    enabled = canSetBatteryVisibility(
                        visibility = visibility,
                        clockVisible = uiState.clockVisible,
                        dateVisible = uiState.dateVisible,
                        clockStyle = uiState.clockStyle
                    )
                )
            },
            selected = uiState.batteryVisibility,
            onSelect = { visibility ->
                viewModel.setBatteryVisibility(visibility)
                onDismiss()
            },
            onDismiss = onDismiss
        )

        WidgetSettingsDialog.EventLookahead -> SingleChoiceDialog(
            title = stringResource(R.string.event_lookahead),
            options = EventSettings.LOOKAHEAD_OPTIONS.map { hours ->
                SingleChoiceOption(
                    value = hours,
                    label = pluralStringResource(R.plurals.event_lookahead_hours, hours, hours)
                )
            },
            selected = uiState.eventLookaheadHours,
            onSelect = { hours ->
                viewModel.setEventLookaheadHours(hours)
                onDismiss()
            },
            onDismiss = onDismiss
        )

        WidgetSettingsDialog.EventCount -> SingleChoiceDialog(
            title = stringResource(R.string.event_count_label),
            options = EventSettings.EVENT_COUNT_OPTIONS.map { count ->
                SingleChoiceOption(
                    value = count,
                    label = pluralStringResource(R.plurals.event_count, count, count)
                )
            },
            selected = uiState.eventMaxEvents,
            onSelect = { count ->
                viewModel.setEventMaxEvents(count)
                onDismiss()
            },
            onDismiss = onDismiss
        )

        WidgetSettingsDialog.MaxStackPages -> SingleChoiceDialog(
            title = stringResource(R.string.max_stack_pages),
            options = (WidgetAreaSettings.MIN_STACK_PAGES..WidgetAreaSettings.MAX_STACK_PAGES).map { pages ->
                SingleChoiceOption(value = pages, label = pages.toString())
            },
            selected = uiState.maxStackPages,
            onSelect = { pages ->
                viewModel.setMaxStackPages(pages)
                onDismiss()
            },
            onDismiss = onDismiss
        )

        WidgetSettingsDialog.HeightLimit -> SingleChoiceDialog(
            title = stringResource(R.string.maximum_height),
            options = WidgetAreaHeightLimit.entries.map { limit ->
                SingleChoiceOption(value = limit, label = stringResource(limit.labelResource()))
            },
            selected = uiState.heightLimit,
            onSelect = { limit ->
                viewModel.setHeightLimit(limit)
                onDismiss()
            },
            onDismiss = onDismiss
        )

        WidgetSettingsDialog.TopOffset -> TopOffsetDialog(
            offsetDp = uiState.topOffsetDp,
            homeHeightDp = uiState.homeHeightDp,
            onOffsetChange = viewModel::setTopOffsetDp,
            onDismiss = onDismiss
        )

        WidgetSettingsDialog.WidgetSpacing -> SingleChoiceDialog(
            title = stringResource(R.string.spacing_between_widgets),
            options = WidgetSpacing.entries.map { spacing ->
                SingleChoiceOption(value = spacing, label = stringResource(spacing.labelResource()))
            },
            selected = uiState.widgetSpacing,
            onSelect = { spacing ->
                viewModel.setWidgetSpacing(spacing)
                onDismiss()
            },
            onDismiss = onDismiss
        )

        WidgetSettingsDialog.Calendars -> CalendarSelectionDialog(
            calendars = uiState.calendars,
            excludedCalendarIds = uiState.excludedCalendarIds,
            onConfirm = { excludedCalendarIds ->
                viewModel.setExcludedCalendarIds(excludedCalendarIds)
                onDismiss()
            },
            onDismiss = onDismiss
        )

        WidgetSettingsDialog.MediaPosition -> SingleChoiceDialog(
            title = stringResource(R.string.position),
            options = MediaPlayerPlacement.entries.map { placement ->
                SingleChoiceOption(value = placement, label = stringResource(placement.labelResource()))
            },
            selected = uiState.mediaPlayerPlacement,
            onSelect = { placement ->
                viewModel.setPlacement(placement)
                onDismiss()
            },
            onDismiss = onDismiss
        )
    }
}

private enum class WidgetSettingsDialog {
    TimeFormat,
    DateFormat,
    Size,
    Battery,
    EventLookahead,
    EventCount,
    MaxStackPages,
    HeightLimit,
    TopOffset,
    WidgetSpacing,
    Calendars,
    MediaPosition
}

@Composable
private fun MediaPlayerExclusionsDialog(
    state: MediaPlayerExclusionsState,
    onQueryChange: (String) -> Unit,
    onExcludedChange: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        start = exclusionsHeaderStartPadding,
                        top = exclusionsHeaderPadding,
                        end = exclusionsHeaderPadding
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(CoreUiR.string.cancel))
                    }
                    Text(
                        text = stringResource(R.string.excluded_apps),
                        modifier = Modifier.padding(start = exclusionsHeaderPadding),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = settingsContentPadding,
                        vertical = exclusionsHeaderPadding
                    ),
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    singleLine = true
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = state.apps, key = { app -> app.packageName }) { app ->
                        ListItem(
                            modifier = Modifier.clickable { onExcludedChange(app.packageName, !app.isExcluded) },
                            headlineContent = { Text(app.label) },
                            leadingContent = {
                                AsyncImage(
                                    model = app.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(exclusionsAppIconSize)
                                )
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = app.isExcluded,
                                    onCheckedChange = { checked -> onExcludedChange(app.packageName, checked) }
                                )
                            }
                        )
                    }
                    if (state.isLoaded && state.apps.isEmpty()) {
                        item { SettingsEmptyState(text = R.string.no_apps_found, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSelectionDialog(
    calendars: List<CalendarInfo>,
    excludedCalendarIds: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val otherCalendars: String = stringResource(R.string.other_calendars)
    val workProfile: String = stringResource(R.string.work_profile)
    val workCalendar: String = stringResource(R.string.work_calendar)
    var selectedKeys: Set<String> by remember(calendars, excludedCalendarIds) {
        mutableStateOf(calendars.map { calendar -> calendar.calendarKey }.toSet() - excludedCalendarIds)
    }
    val groups: Map<String, List<CalendarInfo>> = calendars.groupBy { calendar ->
        calendar.accountGroupLabel(otherCalendars = otherCalendars, workProfile = workProfile)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendars)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                groups.forEach { (accountName, accountCalendars) ->
                    item(key = "account:$accountName") {
                        Text(
                            text = accountName,
                            modifier = Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    items(items = accountCalendars, key = { calendar -> calendar.calendarKey }) { calendar ->
                        CalendarSelectionRow(
                            calendar = calendar,
                            selected = calendar.calendarKey in selectedKeys,
                            onCheckedChange = { checked ->
                                selectedKeys = if (checked) {
                                    selectedKeys + calendar.calendarKey
                                } else {
                                    selectedKeys - calendar.calendarKey
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(calendars.map { calendar -> calendar.calendarKey }.toSet() - selectedKeys) }) {
                Text(stringResource(CoreUiR.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        }
    )
}

private fun CalendarInfo.accountGroupLabel(otherCalendars: String, workProfile: String): String =
    accountName.ifBlank { if (isWorkProfile) workProfile else otherCalendars }

private fun CalendarInfo.selectionLabel(otherCalendars: String, workCalendar: String): String =
    name.ifBlank { accountName.ifBlank { if (isWorkProfile) workCalendar else otherCalendars } }

@Composable
private fun CalendarSelectionRow(calendar: CalendarInfo, selected: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val otherCalendars: String = stringResource(R.string.other_calendars)
    val workCalendar: String = stringResource(R.string.work_calendar)
    val label: String = calendar.selectionLabel(otherCalendars = otherCalendars, workCalendar = workCalendar)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = null)
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(12.dp)
                .background(color = Color(calendar.color), shape = CircleShape)
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        if (calendar.isWorkProfile && label != workCalendar) {
            Text(
                text = stringResource(R.string.work_profile),
                modifier = Modifier.padding(start = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SettingsAlignmentRow(alignment: HeaderAlignment, enabled: Boolean, onAlignmentSelected: (HeaderAlignment) -> Unit) {
    SettingsSegmentedRow(
        title = stringResource(R.string.alignment),
        options = HeaderAlignment.entries,
        selected = alignment,
        onSelect = onAlignmentSelected,
        label = { option -> stringResource(option.labelResource()) },
        enabled = enabled
    )
}

@Composable
private fun <T> SettingsSegmentedRow(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    supportingText: String? = null,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (supportingText != null) {
            Text(
                text = supportingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    enabled = enabled
                ) {
                    Text(label(option))
                }
            }
        }
    }
}

@StringRes
private fun TimeFormat.labelResource(): Int = when (this) {
    TimeFormat.SYSTEM -> R.string.time_format_system
    TimeFormat.H12 -> R.string.time_format_12_hour
    TimeFormat.H24 -> R.string.time_format_24_hour
}

@StringRes
private fun HeaderAlignment.labelResource(): Int = when (this) {
    HeaderAlignment.START -> R.string.align_start
    HeaderAlignment.CENTER -> R.string.align_center
    HeaderAlignment.END -> R.string.align_end
}

@StringRes
private fun HeaderSize.labelResource(): Int = when (this) {
    HeaderSize.SMALL -> R.string.size_small
    HeaderSize.DEFAULT -> R.string.size_default
    HeaderSize.LARGE -> R.string.size_large
    HeaderSize.EXTRA_LARGE -> R.string.size_extra_large
}

@StringRes
private fun WidgetAreaHeightLimit.labelResource(): Int = when (this) {
    WidgetAreaHeightLimit.HALF -> R.string.height_half
    WidgetAreaHeightLimit.TWO_THIRDS -> R.string.height_two_thirds
    WidgetAreaHeightLimit.THREE_QUARTERS -> R.string.height_three_quarters
}

@StringRes
private fun WidgetSpacing.labelResource(): Int = when (this) {
    WidgetSpacing.NONE -> R.string.none
    WidgetSpacing.SMALL -> R.string.spacing_small
    WidgetSpacing.DEFAULT -> R.string.spacing_default
    WidgetSpacing.LARGE -> R.string.spacing_large
    WidgetSpacing.EXTRA_LARGE -> R.string.spacing_extra_large
}

@Composable
private fun TopOffsetDialog(offsetDp: Int, homeHeightDp: Int?, onOffsetChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val context: Context = LocalContext.current
    val maxOffsetDp: Int = remember(context, homeHeightDp) {
        homeHeightDp?.let(WidgetAreaSettings::maxOffsetDp) ?: displayMaxOffsetDp(context = context)
    }
    val currentOffsetDp: Int = offsetDp.coerceIn(0, maxOffsetDp)
    val valueLabel: String = formatDpLabel(currentOffsetDp)
    val step: Int = WidgetAreaSettings.OFFSET_STEP_DP
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.top_offset)) },
        text = {
            Column {
                MoveWidgetAreaSlider(
                    offsetDp = currentOffsetDp,
                    maxOffsetDp = maxOffsetDp,
                    canMoveUp = currentOffsetDp > 0,
                    canMoveDown = currentOffsetDp < maxOffsetDp,
                    onOffsetChange = onOffsetChange,
                    onMoveUp = { onOffsetChange((currentOffsetDp - step).coerceIn(0, maxOffsetDp)) },
                    onMoveDown = { onOffsetChange((currentOffsetDp + step).coerceIn(0, maxOffsetDp)) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = valueLabel, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { onOffsetChange(0) }) {
                        Text(text = stringResource(CoreUiR.string.reset))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(CoreUiR.string.done))
            }
        }
    )
}

private fun displayMaxOffsetDp(context: Context): Int {
    val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)
    val metrics: WindowMetrics = windowManager.currentWindowMetrics
    val insets: Insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
    val safeHeightPx: Int = metrics.bounds.height() - insets.top - insets.bottom
    val density: Float = context.resources.displayMetrics.density
    val safeHeightDp: Int = (safeHeightPx / density).toInt()
    return WidgetAreaSettings.maxOffsetDp(homeHeightDp = safeHeightDp)
}

/** What the Appearance screen asks its host to change. */
class SettingsAppearanceActions(
    val onIconPackSelected: (IconPack) -> Unit,
    val onResetCustomIcons: () -> Unit,
    val onAppListOrderSelected: (AppListOrder) -> Unit,
    val onRankFoldersByUseChange: (Boolean) -> Unit,
    val onShowRecentlyInstalledChange: (Boolean) -> Unit,
    val onRailHapticsChange: (Boolean) -> Unit,
    val onShowRailChange: (Boolean) -> Unit,
    val onSuggestOftenUsedAppsChange: (Boolean) -> Unit,
    val onRotateHomeChange: (Boolean) -> Unit,
    val onResetStoppedSuggestions: () -> Unit,
    val onRailSideSelected: (RailSide) -> Unit,
    val onScrubEdgesSelected: (ScrubEdges) -> Unit
)

@Composable
fun SettingsAppearanceScreen(
    iconPacks: Set<IconPack>,
    selectedIconPack: IconPack,
    railSide: RailSide,
    scrubEdges: ScrubEdges,
    appListOrder: AppListOrder,
    rankFoldersByUse: Boolean,
    showRecentlyInstalled: Boolean,
    railHaptics: Boolean,
    showRail: Boolean,
    suggestOftenUsedApps: Boolean,
    rotateHome: Boolean?,
    smallestScreenWidthDp: Int,
    hasStoppedSuggestions: Boolean,
    actions: SettingsAppearanceActions
) {
    val isRotateHomeEnabled: Boolean = resolveRotateHome(
        rotateHome = rotateHome,
        smallestScreenWidthDp = smallestScreenWidthDp
    )
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            IconPackRow(
                iconPacks = iconPacks,
                selectedIconPack = selectedIconPack,
                onIconPackSelected = actions.onIconPackSelected
            )
        }
        item {
            SettingsConfirmedDestructiveRow(
                title = stringResource(R.string.reset_custom_icons),
                dialogTitle = stringResource(R.string.reset_custom_icons_title),
                dialogBody = stringResource(R.string.reset_custom_icons_body),
                onConfirm = actions.onResetCustomIcons
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.rotate_home),
                checked = isRotateHomeEnabled,
                onCheckedChange = actions.onRotateHomeChange
            )
        }
        item {
            SettingsSegmentedRow(
                title = stringResource(R.string.app_list_order),
                options = AppListOrder.entries,
                selected = appListOrder,
                onSelect = actions.onAppListOrderSelected,
                label = { option -> stringResource(option.labelResource()) }
            )
        }
        if (appListOrder != AppListOrder.ALPHABETICAL) {
            item {
                SettingsSwitchRow(
                    title = stringResource(R.string.rank_folders_by_use),
                    checked = rankFoldersByUse,
                    onCheckedChange = actions.onRankFoldersByUseChange
                )
            }
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.show_recently_installed),
                checked = showRecentlyInstalled,
                supportingText = stringResource(R.string.show_recently_installed_summary, RECENT_INSTALL_WINDOW_DAYS),
                onCheckedChange = actions.onShowRecentlyInstalledChange
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.suggest_often_used_apps),
                checked = suggestOftenUsedApps,
                supportingText = stringResource(R.string.suggest_often_used_apps_summary),
                onCheckedChange = actions.onSuggestOftenUsedAppsChange
            )
        }
        if (hasStoppedSuggestions) {
            item {
                SettingsConfirmedDestructiveRow(
                    title = stringResource(R.string.reset_stopped_suggestions),
                    dialogTitle = stringResource(R.string.reset_stopped_suggestions_title),
                    dialogBody = stringResource(R.string.reset_stopped_suggestions_body),
                    onConfirm = actions.onResetStoppedSuggestions
                )
            }
        }
        item { SettingsSectionTitle(R.string.rail) }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.show_rail),
                checked = showRail,
                supportingText = stringResource(R.string.show_rail_summary),
                onCheckedChange = actions.onShowRailChange
            )
        }
        item {
            RailSideRow(
                railSide = railSide,
                onRailSideSelected = actions.onRailSideSelected
            )
        }
        item {
            ScrubEdgesRow(
                scrubEdges = scrubEdges,
                onScrubEdgesSelected = actions.onScrubEdgesSelected
            )
        }
        item {
            SettingsSwitchRow(
                title = stringResource(R.string.rail_haptics),
                checked = railHaptics,
                supportingText = stringResource(R.string.rail_haptics_summary),
                onCheckedChange = actions.onRailHapticsChange
            )
        }
    }
}

@Composable
private fun IconPackRow(iconPacks: Set<IconPack>, selectedIconPack: IconPack, onIconPackSelected: (IconPack) -> Unit) {
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

@Composable
private fun RailSideRow(railSide: RailSide, onRailSideSelected: (RailSide) -> Unit) {
    val isRtl: Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl
    SettingsSwitchRow(
        title = stringResource(R.string.show_rail_on_left),
        checked = railSide.isOnLeft(isRtl = isRtl),
        onCheckedChange = { showOnLeft -> onRailSideSelected(RailSide.forLeft(isOnLeft = showOnLeft, isRtl = isRtl)) }
    )
}

@Composable
private fun ScrubEdgesRow(scrubEdges: ScrubEdges, onScrubEdgesSelected: (ScrubEdges) -> Unit) {
    val isRtl: Boolean = LocalLayoutDirection.current == LayoutDirection.Rtl
    SettingsSegmentedRow(
        title = stringResource(R.string.scrub_edges),
        options = ScrubEdges.entries,
        selected = scrubEdges,
        onSelect = onScrubEdgesSelected,
        label = { option -> stringResource(option.labelResource(isRtl = isRtl)) },
        supportingText = stringResource(R.string.scrub_edges_summary)
    )
}

@StringRes
private fun AppListOrder.labelResource(): Int = when (this) {
    AppListOrder.ALPHABETICAL -> R.string.app_list_order_alphabetical
    AppListOrder.MOST_USED -> R.string.app_list_order_most_used
    AppListOrder.RECENTLY_USED -> R.string.app_list_order_recently_used
}

@StringRes
private fun ScrubEdges.labelResource(isRtl: Boolean): Int = when (this) {
    ScrubEdges.START -> if (isRtl) R.string.scrub_edges_right else R.string.scrub_edges_left
    ScrubEdges.END -> if (isRtl) R.string.scrub_edges_left else R.string.scrub_edges_right
    ScrubEdges.BOTH -> R.string.scrub_edges_both
}
