package feature.widgets

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import core.ui.layout.homeEdgePadding
import core.ui.layout.homeRailTouchInset
import core.ui.util.performLongPressHaptic
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

private val emptyWidgetAreaHeight: Dp = 96.dp

/** The standalone Widget Area, rendered above Favorites on Home. */
@Composable
fun WidgetArea(modifier: Modifier = Modifier, menuEnabled: Boolean = true, viewModel: WidgetAreaViewModel = koinViewModel()) {
    val state: WidgetAreaState by viewModel.widgetAreaState.collectAsState()
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = homeEdgePadding,
                top = homeEdgePadding,
                end = homeRailTouchInset
            )
    ) {
        if (state.hasWidgets) {
            state.places.forEach { place ->
                WidgetPlaceContent(
                    place = place,
                    menuEnabled = menuEnabled,
                    onTapTarget = context::openHeaderTapTarget,
                    onHeaderLongPress = openHeaderMenu
                )
            }
        } else {
            EmptyWidgetArea(menuEnabled = menuEnabled, onLongPress = openEmptyAreaMenu)
        }
    }
}

@Composable
private fun EmptyWidgetArea(menuEnabled: Boolean, onLongPress: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(emptyWidgetAreaHeight)
            .pointerInput(menuEnabled) {
                if (menuEnabled) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
            }
    )
}

@Composable
private fun WidgetPlaceContent(
    place: WidgetPlaceModel,
    menuEnabled: Boolean,
    onTapTarget: (HeaderTapTarget) -> Unit,
    onHeaderLongPress: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        place.widgets.forEach { widget ->
            when (widget) {
                is HeaderModel -> HeaderContent(
                    model = widget,
                    menuEnabled = menuEnabled,
                    onTapTarget = onTapTarget,
                    onLongPress = onHeaderLongPress
                )
            }
        }
    }
}

private fun Context.openHeaderTapTarget(target: HeaderTapTarget) {
    val intent: Intent = when (target) {
        HeaderTapTarget.SystemAlarms -> Intent(AlarmClock.ACTION_SHOW_ALARMS)

        is HeaderTapTarget.SystemCalendar -> Intent(
            Intent.ACTION_VIEW,
            Uri.parse("content://com.android.calendar/time/${target.atEpochMillis}")
        )
    }
    try {
        startActivity(intent)
    } catch (exception: ActivityNotFoundException) {
        val messageResource: Int = when (target) {
            HeaderTapTarget.SystemAlarms -> R.string.no_clock_app
            is HeaderTapTarget.SystemCalendar -> R.string.no_calendar_app
        }
        Toast.makeText(this, messageResource, Toast.LENGTH_SHORT).show()
        Timber.w(exception, "No activity for %s", target)
    }
}
