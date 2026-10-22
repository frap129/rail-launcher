package feature.widgets

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.launchables.model.Launchable
import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetWidth
import core.ui.composables.OutlinedText
import core.ui.layout.homeEdgePadding
import core.ui.layout.homeRailTouchInset
import core.ui.util.consumeTouches
import core.ui.util.performLongPressHaptic
import kotlin.math.abs
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

private val emptyWidgetAreaHeight: Dp = 96.dp
private const val RESIZE_TOOLBAR_SPACE_DP: Int = 128
private val stackIndicatorHeight: Dp = ResizeBounds.STACK_INDICATOR_HEIGHT_DP.dp
private val stackIndicatorDotSize: Dp = 6.dp
private val stackIndicatorGap: Dp = 6.dp

/** The standalone Widget Area, rendered above Favorites on Home. */
@Composable
fun WidgetArea(modifier: Modifier = Modifier, menuEnabled: Boolean = true, viewModel: WidgetAreaViewModel = koinViewModel()) {
    val state: WidgetAreaState by viewModel.widgetAreaState.collectAsState()
    val resize: ResizeState? by viewModel.resizeState.collectAsState()
    val editDraft: WidgetEditDraft? by viewModel.editState.collectAsState()
    val context: Context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val openHeaderMenu: (Long) -> Unit = { widgetId ->
        if (menuEnabled) {
            hapticFeedback.performLongPressHaptic()
            viewModel.openHeaderMenu(widgetId = widgetId)
        }
    }
    val openEmptyAreaMenu: () -> Unit = {
        if (menuEnabled) {
            hapticFeedback.performLongPressHaptic()
            viewModel.openEmptyAreaMenu()
        }
    }
    val placeContent: @Composable (WidgetPlaceModel, Int?, Boolean, Boolean, Modifier) -> Unit =
        { place, heightDp, placeMenuEnabled, placeIsResizing, placeModifier ->
            WidgetPlaceContent(
                place = place,
                heightDp = heightDp,
                menuEnabled = placeMenuEnabled,
                isResizing = placeIsResizing,
                stackIndicatorVisible = state.stackIndicatorVisible,
                onTapTarget = context::openHeaderTapTarget,
                onHeaderLongPress = openHeaderMenu,
                onHeaderHeight = viewModel::setHeaderHeight,
                onAppWidgetLongPress = viewModel::openAppWidgetMenu,
                onAppWidgetUnpause = viewModel::unpauseWorkProfile,
                onPageSettled = viewModel::onPageSettled,
                userScrollEnabled = placeMenuEnabled && !placeIsResizing,
                modifier = placeModifier
            )
        }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val homeHeightDp: Int = maxHeight.value.roundToInt()
        val widgetAreaWidthDp: Int = (maxWidth.value - homeEdgePadding.value - homeRailTouchInset.value).roundToInt()
        LaunchedEffect(homeHeightDp) {
            viewModel.setHomeHeight(heightDp = homeHeightDp)
        }
        LaunchedEffect(widgetAreaWidthDp) {
            viewModel.setWidgetAreaWidth(widthDp = widgetAreaWidthDp)
        }
        if (resize != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .consumeTouches(onGestureEnd = viewModel::finishResize)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = homeEdgePadding,
                    top = homeEdgePadding,
                    end = homeEdgePadding
                )
        ) {
            if (state.hasWidgets) {
                if (state.isEditing) {
                    EditableWidgetArea(
                        places = state.places,
                        expandedPlaceIds = editDraft?.expandedPlaceIds.orEmpty(),
                        maxStackPages = state.maxStackPages,
                        onMovePlace = viewModel::moveWidgetPlace,
                        onRemovePlace = viewModel::removeWidgetPlaceInEdit,
                        onToggleExpand = viewModel::toggleStackExpanded,
                        onMovePage = viewModel::movePage,
                        onRemovePage = viewModel::removePageInEdit,
                        onSplitPage = viewModel::splitPage,
                        onMergePlace = viewModel::mergePlace,
                        placeContent = { place, contentModifier ->
                            placeContent(place.copy(widthMode = WidgetWidth.Inset), place.heightDp, false, false, contentModifier)
                        }
                    )
                } else {
                    state.places.forEach { place ->
                        val placeResize: ResizeState? = resize?.takeIf { resizing -> resizing.placeId == place.id }
                        if (placeResize != null) {
                            ResizeOverlay(
                                state = placeResize,
                                onDrag = viewModel::dragResize,
                                onStep = viewModel::stepResize,
                                onReset = viewModel::resetResize,
                                onDone = viewModel::finishResize,
                                toolbarAbove = placeResize.draftHeightDp > homeHeightDp - RESIZE_TOOLBAR_SPACE_DP
                            ) {
                                placeContent(
                                    place,
                                    placeResize.draftHeightDp,
                                    false,
                                    true,
                                    Modifier.consumeTouches()
                                )
                            }
                        } else {
                            val dimmed: Boolean = resize != null
                            placeContent(
                                place,
                                place.heightDp,
                                menuEnabled && !dimmed,
                                false,
                                Modifier
                                    .alpha(if (dimmed) RESIZE_DIMMED_ALPHA else 1f)
                                    .then(
                                        if (dimmed) {
                                            Modifier.consumeTouches(onGestureEnd = viewModel::finishResize)
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }
                    }
                }
            } else {
                EmptyWidgetArea(
                    menuEnabled = menuEnabled && resize == null && !state.isEditing,
                    showNoWidgets = state.isEditing,
                    onLongPress = openEmptyAreaMenu
                )
            }
        }
    }
}

@Composable
private fun EmptyWidgetArea(menuEnabled: Boolean, showNoWidgets: Boolean, onLongPress: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = WidgetWidth.Inset.endPadding())
            .height(emptyWidgetAreaHeight)
            .pointerInput(menuEnabled) {
                if (menuEnabled) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
            }
    ) {
        if (showNoWidgets) {
            OutlinedText(
                text = stringResource(R.string.no_widgets),
                modifier = Modifier.align(Alignment.CenterStart),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun WidgetPlaceContent(
    place: WidgetPlaceModel,
    heightDp: Int?,
    menuEnabled: Boolean,
    isResizing: Boolean,
    stackIndicatorVisible: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onHeaderLongPress: (Long) -> Unit,
    onHeaderHeight: (Int) -> Unit,
    onAppWidgetLongPress: (Long) -> Unit,
    onAppWidgetUnpause: (Long) -> Unit,
    onPageSettled: (Long, Int) -> Unit,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val endPadding: Dp = place.widthMode.endPadding()
    if (place.isStack) {
        WidgetStack(
            place = place,
            heightDp = heightDp,
            menuEnabled = menuEnabled,
            isResizing = isResizing,
            indicatorVisible = stackIndicatorVisible,
            onTapTarget = onTapTarget,
            onHeaderLongPress = onHeaderLongPress,
            onHeaderHeight = onHeaderHeight,
            onAppWidgetLongPress = onAppWidgetLongPress,
            onAppWidgetUnpause = onAppWidgetUnpause,
            onPageSettled = onPageSettled,
            userScrollEnabled = menuEnabled && !isResizing,
            modifier = modifier.padding(end = endPadding)
        )
    } else {
        Column(modifier = modifier.fillMaxWidth().padding(end = endPadding)) {
            place.widgets.forEach { widget ->
                WidgetPageContent(
                    widget = widget,
                    heightDp = heightDp,
                    menuEnabled = menuEnabled,
                    isResizing = isResizing,
                    onTapTarget = onTapTarget,
                    onHeaderLongPress = onHeaderLongPress,
                    onHeaderHeight = onHeaderHeight,
                    onAppWidgetLongPress = onAppWidgetLongPress,
                    onAppWidgetUnpause = onAppWidgetUnpause
                )
            }
        }
    }
}

@Composable
private fun WidgetStack(
    place: WidgetPlaceModel,
    heightDp: Int?,
    menuEnabled: Boolean,
    isResizing: Boolean,
    indicatorVisible: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onHeaderLongPress: (Long) -> Unit,
    onHeaderHeight: (Int) -> Unit,
    onAppWidgetLongPress: (Long) -> Unit,
    onAppWidgetUnpause: (Long) -> Unit,
    onPageSettled: (Long, Int) -> Unit,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val pageCount: Int = place.widgets.size
    val pagerState = rememberPagerState(initialPage = place.currentPage.coerceIn(0, pageCount - 1)) { pageCount }
    val totalHeight: Dp = (heightDp ?: ResizeBounds.MIN_HEIGHT_DP).dp
    val indicatorHeight: Dp = if (indicatorVisible) stackIndicatorHeight else 0.dp
    val pageHeight: Dp = (totalHeight - indicatorHeight).coerceAtLeast(1.dp)
    val pageHeightDp: Int = pageHeight.value.roundToInt()
    LaunchedEffect(place.currentPage, pageCount) {
        val target: Int = place.currentPage.coerceIn(0, pageCount - 1)
        if (!pagerState.isScrollInProgress && pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        onPageSettled(place.id, pagerState.settledPage)
    }
    val dominanceFilter: NestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && abs(available.y) >= abs(available.x)) {
                    Offset(available.x, 0f)
                } else {
                    Offset.Zero
                }
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(pageHeight)
                .nestedScroll(dominanceFilter),
            userScrollEnabled = userScrollEnabled,
            key = { page -> place.widgets[page].id }
        ) { page ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                WidgetPageContent(
                    widget = place.widgets[page],
                    heightDp = pageHeightDp,
                    menuEnabled = menuEnabled,
                    isResizing = isResizing,
                    onTapTarget = onTapTarget,
                    onHeaderLongPress = onHeaderLongPress,
                    onHeaderHeight = onHeaderHeight,
                    onAppWidgetLongPress = onAppWidgetLongPress,
                    onAppWidgetUnpause = onAppWidgetUnpause
                )
            }
        }
        if (indicatorVisible) {
            StackPageIndicator(
                pageCount = pageCount,
                currentPage = pagerState.currentPage
            )
        }
    }
}

@Composable
private fun StackPageIndicator(pageCount: Int, currentPage: Int) {
    val description: String = stringResource(R.string.stack_page_of, currentPage + 1, pageCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stackIndicatorHeight)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .padding(horizontal = stackIndicatorGap / 2)
                    .size(stackIndicatorDotSize)
                    .background(
                        color = if (page == currentPage) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun WidgetPageContent(
    widget: WidgetModel,
    heightDp: Int?,
    menuEnabled: Boolean,
    isResizing: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onHeaderLongPress: (Long) -> Unit,
    onHeaderHeight: (Int) -> Unit,
    onAppWidgetLongPress: (Long) -> Unit,
    onAppWidgetUnpause: (Long) -> Unit
) {
    when (widget) {
        is HeaderModel -> {
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size -> onHeaderHeight(with(density) { size.height.toDp().value.roundToInt() }) }
            ) {
                HeaderContent(
                    model = widget,
                    menuEnabled = menuEnabled,
                    interactive = menuEnabled,
                    onTapTarget = onTapTarget,
                    onLongPress = { onHeaderLongPress(widget.id) }
                )
            }
        }

        is AppWidgetModel -> AppWidgetContent(
            widget = widget,
            heightDp = heightDp,
            menuEnabled = menuEnabled,
            isResizing = isResizing,
            onLongPress = onAppWidgetLongPress,
            onUnpause = onAppWidgetUnpause
        )
    }
}

private fun WidgetWidth.endPadding(): Dp = when (this) {
    WidgetWidth.Inset -> homeRailTouchInset - homeEdgePadding
    WidgetWidth.UnderRail -> 0.dp
}

private fun Context.openHeaderTapTarget(target: HeaderTapTarget) {
    when (target) {
        HeaderTapTarget.None -> Unit

        is HeaderTapTarget.LaunchLaunchable -> openLaunchable(target.launchable)

        HeaderTapTarget.SystemAlarms -> openSystemIntent(
            intent = Intent(AlarmClock.ACTION_SHOW_ALARMS),
            target = target,
            messageResource = R.string.no_clock_app
        )

        is HeaderTapTarget.SystemCalendar -> openSystemIntent(
            intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${target.atEpochMillis}")),
            target = target,
            messageResource = R.string.no_calendar_app
        )

        HeaderTapTarget.SystemBatteryUsage -> openSystemIntent(
            intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            target = target,
            messageResource = R.string.no_battery_usage_app
        )

        is HeaderTapTarget.OpenEvent -> openEvent(target)
    }
}

private fun Context.openEvent(target: HeaderTapTarget.OpenEvent) {
    if (target.isWorkProfile) {
        try {
            val opened: Boolean = CalendarContract.startViewCalendarEventInManagedProfile(
                this,
                target.eventId,
                target.beginMillis,
                target.endMillis,
                false,
                0
            )
            if (!opened) {
                Toast.makeText(this, R.string.no_calendar_app, Toast.LENGTH_SHORT).show()
            }
        } catch (exception: SecurityException) {
            Toast.makeText(this, R.string.no_calendar_app, Toast.LENGTH_SHORT).show()
            Timber.w(exception, "Could not open work-profile event %s", target.eventId)
        }
        return
    }
    val intent: Intent = Intent(Intent.ACTION_VIEW)
        .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, target.eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, target.beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, target.endMillis)
    openSystemIntent(intent = intent, target = target, messageResource = R.string.no_calendar_app)
}

private fun Context.openLaunchable(launchable: Launchable) {
    try {
        launchable.launch(this)
    } catch (exception: SecurityException) {
        showLaunchFailure(launchable = launchable, exception = exception)
    } catch (exception: ActivityNotFoundException) {
        showLaunchFailure(launchable = launchable, exception = exception)
    }
}

private fun Context.showLaunchFailure(launchable: Launchable, exception: Exception) {
    Toast.makeText(this, getString(R.string.cannot_open_launchable, launchable.name), Toast.LENGTH_SHORT).show()
    Timber.w(exception, "Could not open Launchable %s", launchable.key)
}

private fun Context.openSystemIntent(intent: Intent, target: HeaderTapTarget, messageResource: Int) {
    try {
        startActivity(intent)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(this, messageResource, Toast.LENGTH_SHORT).show()
        Timber.w(exception, "No activity for %s", target)
    }
}
