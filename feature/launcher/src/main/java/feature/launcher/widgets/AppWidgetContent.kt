package feature.launcher.widgets

import android.os.Build
import android.util.SparseIntArray
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import core.data.widgets.AppWidgetId
import core.data.widgets.WidgetAvailability
import core.widgets.AppWidgetModel
import core.widgets.AppWidgetViewCache
import core.widgets.RailAppWidgetHostView
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
    val uiMode: Int = LocalConfiguration.current.uiMode
    val height: Int = heightDp ?: widget.minHeightDp.coerceAtLeast(MIN_APP_WIDGET_HEIGHT_DP)
    val appWidgetId: AppWidgetId = remember(widget.appWidgetId) { AppWidgetId(widget.appWidgetId) }
    val view: RailAppWidgetHostView? = if (widget.availability == WidgetAvailability.Available) {
        remember(appWidgetId, cache) { cache.getOrCreate(appWidgetId) }
    } else {
        null
    }
    val colorResources: SparseIntArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        remember(context, uiMode) { AppWidgetViews.colorResources(context) }
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
    LaunchedEffect(view, colorResources) {
        if (colorResources != null) {
            view.setColorResources(colorResources)
        }
    }
    DisposableEffect(view) {
        onDispose {
            if (view.parent == null) {
                view.onLongPress = null
            }
        }
    }
    var measuredWidthPx: Int by remember { mutableIntStateOf(0) }
    val disallowInterceptTouchEvent: RequestDisallowInterceptTouchEvent = remember { RequestDisallowInterceptTouchEvent() }
    LaunchedEffect(appWidgetId, height, measuredWidthPx) {
        if (isResizing || measuredWidthPx <= 0) {
            return@LaunchedEffect
        }
        val widthDp: Int = with(density) { measuredWidthPx.toDp().value.roundToInt() }
        cache.reportSizeIfChanged(id = appWidgetId, widthDp = widthDp, heightDp = height)
    }
    AndroidView(
        factory = { cache.takeForAttach(appWidgetId) ?: view },
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
        }
    )
}
