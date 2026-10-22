package feature.widgets

import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import core.data.widgets.AppWidgetId
import core.data.widgets.WidgetAvailability
import kotlin.math.roundToInt
import org.koin.compose.koinInject

/** The minimum height Rail gives an App Widget when its provider does not declare one. */
private const val MIN_APP_WIDGET_HEIGHT_DP: Int = 48

/** A host view for an App Widget, cached per system id for the process lifetime. */
@Composable
fun AppWidgetContent(
    widget: AppWidgetModel,
    heightDp: Int?,
    menuEnabled: Boolean,
    isResizing: Boolean = false,
    onLongPress: (Long) -> Unit,
    onUnpause: (Long) -> Unit,
    modifier: Modifier = Modifier,
    cache: AppWidgetViewCache = koinInject()
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val height: Int = heightDp ?: widget.minHeightDp.coerceAtLeast(MIN_APP_WIDGET_HEIGHT_DP)
    val view: RailAppWidgetHostView? = if (widget.availability == WidgetAvailability.Available) {
        cache.getOrCreate(AppWidgetId(widget.appWidgetId))
    } else {
        null
    }
    if (view == null) {
        AppWidgetPlaceholder(
            widget = widget,
            heightDp = height,
            menuEnabled = menuEnabled,
            onLongPress = onLongPress,
            onUnpause = onUnpause,
            modifier = modifier
        )
        return
    }
    LaunchedEffect(widget.appWidgetId) {
        cache.refresh(AppWidgetId(widget.appWidgetId))
    }
    DisposableEffect(widget.id) {
        onDispose { view.onLongPress = null }
    }
    var measuredWidthPx: Int by remember { mutableIntStateOf(0) }
    val disallowInterceptTouchEvent: RequestDisallowInterceptTouchEvent = remember { RequestDisallowInterceptTouchEvent() }
    LaunchedEffect(widget.appWidgetId, height, measuredWidthPx) {
        if (isResizing || measuredWidthPx <= 0) {
            return@LaunchedEffect
        }
        val widthDp: Int = with(density) { measuredWidthPx.toDp().value.roundToInt() }
        cache.reportSizeIfChanged(id = AppWidgetId(widget.appWidgetId), widthDp = widthDp, heightDp = height)
    }
    AndroidView(
        factory = { view },
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clipToBounds()
            .pointerInteropFilter(requestDisallowInterceptTouchEvent = disallowInterceptTouchEvent) { false }
            .onSizeChanged { size -> measuredWidthPx = size.width },
        update = { hostView ->
            hostView.onLongPress = if (menuEnabled) {
                { onLongPress(widget.id) }
            } else {
                null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hostView.setColorResources(AppWidgetViews.colorResources(context))
            }
        }
    )
}
