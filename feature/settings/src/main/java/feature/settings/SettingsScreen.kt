package feature.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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
import compose.icons.fontawesomeicons.solid.Icons
import compose.icons.fontawesomeicons.solid.Palette
import compose.icons.fontawesomeicons.solid.Th
import compose.icons.fontawesomeicons.solid.TrashAlt
import core.data.calendar.CalendarInfo
import core.data.icons.model.IconPack
import core.data.prefs.BatteryVisibility
import core.data.prefs.EventSettings
import core.data.prefs.HeaderAlignment
import core.data.prefs.HeaderSize
import core.data.prefs.HeaderTapSetting
import core.data.prefs.TimeFormat
import core.data.widgets.WidgetAreaHeightLimit
import core.data.widgets.WidgetAreaSettings
import core.ui.R as CoreUiR
import core.ui.model.data.Destination
import core.ui.model.data.HOME_ADD_WIDGET_REQUEST_KEY
import core.util.openApplicationDetailsSettings
import feature.widgets.AppWidgetViewCache
import feature.widgets.HeaderClockStyleChooser
import feature.widgets.labelResource
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

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

                SettingsScreen.Widgets -> SettingsWidgetsScreen(
                    onAddWidget = {
                        navController.getBackStackEntry("launcher").savedStateHandle[HOME_ADD_WIDGET_REQUEST_KEY] = true
                        navController.popBackStack("launcher", inclusive = false)
                    }
                )
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
fun SettingsWidgetsScreen(onAddWidget: () -> Unit, viewModel: WidgetSettingsViewModel = koinViewModel()) {
    val uiState: WidgetSettingsUiState by viewModel.uiState.collectAsState()
    val tapActionPicker: TapActionPickerState? by viewModel.tapActionPicker.collectAsState()
    val context: Context = LocalContext.current
    val appWidgetViewCache: AppWidgetViewCache = koinInject()
    val snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val activity: Activity? = remember(context) { context.findActivity() }
    var showClockStyleChooser: Boolean by remember { mutableStateOf(false) }
    var openDialog: WidgetSettingsDialog? by remember { mutableStateOf(null) }
    var showPermissionDialog: Boolean by remember { mutableStateOf(false) }
    val permissionLauncher: ManagedActivityResultLauncher<String, Boolean> =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val canAskAgain: Boolean = activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) == true
            viewModel.onCalendarPermissionResult(granted = granted, canAskAgain = canAskAgain)
        }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WidgetSettingsEvent.RequestCalendarPermission -> permissionLauncher.launch(Manifest.permission.READ_CALENDAR)

                is WidgetSettingsEvent.ShowCalendarPermissionDenied -> showPermissionDialog = true

                WidgetSettingsEvent.StartAddWidget -> onAddWidget()

                is WidgetSettingsEvent.AppWidgetRemoved -> appWidgetViewCache.remove(event.appWidgetId)

                is WidgetSettingsEvent.ShowMessage -> coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(event.message))
                }
            }
        }
    }
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer: LifecycleEventObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshCalendarPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        WidgetSettingsList(
            uiState = uiState,
            viewModel = viewModel,
            onClockStyleClick = { showClockStyleChooser = true },
            onDialogClick = { dialog -> openDialog = dialog },
            onTapActionClick = viewModel::openTapActionPicker,
            onAddWidget = viewModel::openAddWidget
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
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
    if (showPermissionDialog) {
        CalendarPermissionDialog(
            onDismiss = { showPermissionDialog = false },
            onOpenSettings = {
                context.openApplicationDetailsSettings()
                showPermissionDialog = false
            }
        )
    }
}

@Composable
private fun WidgetSettingsList(
    uiState: WidgetSettingsUiState,
    viewModel: WidgetSettingsViewModel,
    onClockStyleClick: () -> Unit,
    onDialogClick: (WidgetSettingsDialog) -> Unit,
    onTapActionClick: (HeaderTapTargetPart) -> Unit,
    onAddWidget: () -> Unit
) {
    LazyColumn {
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
        item { SettingsSectionTitle(R.string.upcoming_events) }
        item { EventSettingsRows(uiState = uiState, viewModel = viewModel, onDialogClick = onDialogClick) }
        item { SettingsSectionTitle(R.string.app_widgets) }
        item {
            SettingsActionRow(
                title = stringResource(R.string.add_widget),
                value = "",
                enabled = true,
                onClick = onAddWidget
            )
        }
        items(items = uiState.appWidgets, key = { widget -> widget.widgetId }) { widget ->
            AppWidgetSettingsRow(
                widget = widget,
                onRemove = { viewModel.removeAppWidget(widget.widgetId) },
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
    }
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
                        contentDescription = stringResource(R.string.remove_widget)
                    )
                }
            }
        )
        if (expanded) {
            SettingsSwitchRow(
                title = stringResource(feature.widgets.R.string.extend_under_rail),
                checked = widget.extendsUnderRail,
                onCheckedChange = onExtendUnderRailChange,
                enabled = widget.canExtendUnderRail,
                supportingText = stringResource(
                    if (widget.canExtendUnderRail) {
                        feature.widgets.R.string.extend_under_rail_supporting_text
                    } else {
                        feature.widgets.R.string.not_available_in_stack
                    }
                )
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall
    )
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
            SettingsWarningRow(
                title = stringResource(R.string.calendar_access_off),
                actionLabel = stringResource(R.string.grant),
                onClick = { viewModel.setEventsEnabled(true) }
            )
        }
        SettingsActionRow(
            title = stringResource(R.string.event_lookahead),
            value = pluralStringResource(R.plurals.event_lookahead_hours, uiState.eventLookaheadHours, uiState.eventLookaheadHours),
            enabled = uiState.eventsEnabled,
            onClick = { onDialogClick(WidgetSettingsDialog.EventLookahead) }
        )
        SettingsActionRow(
            title = stringResource(R.string.event_count),
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
private fun SettingsWarningRow(title: String, actionLabel: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        trailingContent = {
            TextButton(onClick = onClick) {
                Text(actionLabel)
            }
        }
    )
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
            title = stringResource(R.string.clock_style),
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

private data class TapActionPartUi(val selected: HeaderTapSetting, val icon: ImageVector, val onSelect: (HeaderTapSetting) -> Unit)

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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxHeight()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setTapActionQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text(stringResource(R.string.tap_action_search)) },
                singleLine = true
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    TapActionRow(
                        label = stringResource(R.string.tap_action_system_default),
                        selected = partUi.selected == HeaderTapSetting.SystemDefault,
                        icon = {
                            Icon(
                                imageVector = partUi.icon,
                                contentDescription = null
                            )
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
}

@Composable
private fun TapActionRow(label: String, selected: Boolean, onClick: () -> Unit, icon: (@Composable () -> Unit)? = null) {
    ListItem(
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
                SingleChoiceOption(value = visibility, label = stringResource(visibility.labelResource()))
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
            title = stringResource(R.string.event_count),
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

        WidgetSettingsDialog.Calendars -> CalendarSelectionDialog(
            calendars = uiState.calendars,
            excludedCalendarIds = uiState.excludedCalendarIds,
            onConfirm = { excludedCalendarIds ->
                viewModel.setExcludedCalendarIds(excludedCalendarIds)
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
    Calendars
}

@Composable
private fun CalendarSelectionDialog(
    calendars: List<CalendarInfo>,
    excludedCalendarIds: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val otherCalendars: String = stringResource(R.string.other_calendars)
    var selectedKeys: Set<String> by remember(calendars, excludedCalendarIds) {
        mutableStateOf(calendars.map { calendar -> calendar.calendarKey }.toSet() - excludedCalendarIds)
    }
    val groups: Map<String, List<CalendarInfo>> = calendars.groupBy { calendar ->
        calendar.accountName.ifBlank { otherCalendars }
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
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        }
    )
}

@Composable
private fun CalendarSelectionRow(calendar: CalendarInfo, selected: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
            text = calendar.name,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        if (calendar.isWorkProfile) {
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
private fun CalendarPermissionDialog(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CoreUiR.string.calendar_permission_denied_title)) },
        text = { Text(stringResource(CoreUiR.string.calendar_permission_denied_message)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(CoreUiR.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        }
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return current as? Activity
}

private data class SingleChoiceOption<T>(val value: T, val label: String)

@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<SingleChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option.value == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option.value) }
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option.value == selected, onClick = null)
                        Text(text = option.label, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        }
    )
}

@Composable
private fun SettingsAlignmentRow(alignment: HeaderAlignment, enabled: Boolean, onAlignmentSelected: (HeaderAlignment) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = stringResource(R.string.alignment), style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            HeaderAlignment.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = alignment == option,
                    onClick = { onAlignmentSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = HeaderAlignment.entries.size),
                    enabled = enabled
                ) {
                    Text(stringResource(option.labelResource()))
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

@Composable
private fun SettingsActionRow(title: String, value: String, enabled: Boolean, onClick: () -> Unit, supportingText: String? = null) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = if (value.isNotEmpty() || supportingText != null) {
            { Text(value.ifEmpty { supportingText.orEmpty() }) }
        } else {
            null
        }
    )
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
