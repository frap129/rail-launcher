package feature.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import core.data.widgets.AppWidgetId
import org.koin.compose.koinInject

/** The minimum height Rail gives an App Widget when its provider does not declare one. */
private const val MIN_APP_WIDGET_HEIGHT_DP: Int = 48

/** A host view for an App Widget, cached per system id for the process lifetime. */
@Composable
fun AppWidgetContent(
    widget: AppWidgetModel,
    heightDp: Int?,
    menuEnabled: Boolean,
    onLongPress: (Long) -> Unit,
    modifier: Modifier = Modifier,
    cache: AppWidgetViewCache = koinInject()
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val height: Int = heightDp ?: widget.minHeightDp.coerceAtLeast(MIN_APP_WIDGET_HEIGHT_DP)
    val view: RailAppWidgetHostView? = cache.getOrCreate(AppWidgetId(widget.appWidgetId))
    if (view == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(height.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        return
    }
    DisposableEffect(widget.id) {
        onDispose { view.onLongPress = null }
    }
    AndroidView(
        factory = { view },
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clipToBounds()
            .onSizeChanged { size ->
                if (size.width <= 0 || size.height <= 0) {
                    return@onSizeChanged
                }
                val widthDp: Float = with(density) { size.width.toDp().value }
                val widgetHeightDp: Float = with(density) { size.height.toDp().value }
                val options: Bundle = Bundle().apply {
                    putInt(
                        AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
                    )
                }
                view.updateAppWidgetSize(options, listOf(SizeF(widthDp, widgetHeightDp)))
            },
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
