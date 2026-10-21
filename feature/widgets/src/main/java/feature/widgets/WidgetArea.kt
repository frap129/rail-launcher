package feature.widgets

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.data.launchables.model.Launchable
import core.ui.composables.OutlinedText
import core.ui.layout.homeEdgePadding
import core.ui.layout.homeRailTouchInset
import core.ui.util.consumeTouches
import core.ui.util.performLongPressHaptic
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

private val emptyWidgetAreaHeight: Dp = 96.dp
private const val RESIZE_TOOLBAR_SPACE_DP: Int = 128

/** The standalone Widget Area, rendered above Favorites on Home. */
@Composable
fun WidgetArea(modifier: Modifier = Modifier, menuEnabled: Boolean = true, viewModel: WidgetAreaViewModel = koinViewModel()) {
    val state: WidgetAreaState by viewModel.widgetAreaState.collectAsState()
    val resize: ResizeState? by viewModel.resizeState.collectAsState()
    val context: Context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val openHeaderMenu: () -> Unit = {
        if (menuEnabled) {
            hapticFeedback.performLongPressHaptic()
            viewModel.openHeaderMenu()
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
                onTapTarget = context::openHeaderTapTarget,
                onHeaderLongPress = openHeaderMenu,
                onAppWidgetLongPress = viewModel::openAppWidgetMenu,
                onAppWidgetUnpause = viewModel::unpauseWorkProfile,
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
                    end = homeRailTouchInset
                )
        ) {
            if (state.hasWidgets) {
                if (state.isEditing) {
                    EditableWidgetArea(
                        places = state.places,
                        onMovePlace = viewModel::moveWidgetPlace,
                        onRemovePlace = viewModel::removeWidgetPlaceInEdit,
                        placeContent = { place, contentModifier ->
                            placeContent(place, place.heightDp, false, false, contentModifier)
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
    onTapTarget: (HeaderTapTarget) -> Unit,
    onHeaderLongPress: () -> Unit,
    onAppWidgetLongPress: (Long) -> Unit,
    onAppWidgetUnpause: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        place.widgets.forEach { widget ->
            when (widget) {
                is HeaderModel -> HeaderContent(
                    model = widget,
                    menuEnabled = menuEnabled,
                    interactive = menuEnabled,
                    onTapTarget = onTapTarget,
                    onLongPress = onHeaderLongPress
                )

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
    }
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
