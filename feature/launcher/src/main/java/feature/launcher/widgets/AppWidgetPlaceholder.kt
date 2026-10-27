package feature.launcher.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import core.data.widgets.WidgetAvailability
import core.ui.R as CoreUiR
import core.ui.composables.OutlinedText
import core.ui.util.performLongPressHaptic
import core.ui.widgets.AppWidgetModel

/** Shown in place of an App Widget whose provider cannot be rendered right now. */
@Composable
fun AppWidgetPlaceholder(
    widget: AppWidgetModel,
    heightDp: Int,
    menuEnabled: Boolean,
    onLongPress: (Long) -> Unit,
    onUnpause: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(widget.id, widget.availability, menuEnabled) {
                detectTapGestures(
                    onTap = {
                        if (widget.availability == WidgetAvailability.ProfilePaused) {
                            onUnpause(widget.id)
                        }
                    },
                    onLongPress = {
                        if (menuEnabled) {
                            hapticFeedback.performLongPressHaptic()
                            onLongPress(widget.id)
                        }
                    }
                )
            }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AsyncImage(
            model = widget.icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        OutlinedText(
            text = widget.label,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(supportingText(availability = widget.availability)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun supportingText(availability: WidgetAvailability): Int = when (availability) {
    WidgetAvailability.Updating -> CoreUiR.string.widget_updating

    WidgetAvailability.ProfilePaused -> CoreUiR.string.widget_work_profile_paused

    WidgetAvailability.Disabled -> CoreUiR.string.widget_disabled

    WidgetAvailability.Available,
    WidgetAvailability.Gone,
    WidgetAvailability.Unknown -> CoreUiR.string.widget_unavailable
}
