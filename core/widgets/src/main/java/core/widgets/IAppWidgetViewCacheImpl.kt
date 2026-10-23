package core.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup
import core.data.widgets.AppWidgetId

/** One RailAppWidgetHostView per App Widget id, kept for the process lifetime. */
class IAppWidgetViewCacheImpl(private val context: Context, private val host: AppWidgetHost) : IAppWidgetViewCache {
    private val views: MutableMap<Int, RailAppWidgetHostView> = mutableMapOf()
    private val reportedSizes: MutableMap<Int, ReportedSize> = mutableMapOf()

    override fun getOrCreate(id: AppWidgetId): RailAppWidgetHostView? {
        views[id.value]?.let { view -> return view }
        val info: AppWidgetProviderInfo = AppWidgetManager.getInstance(context).getAppWidgetInfo(id.value) ?: return null
        val view: RailAppWidgetHostView = host.createView(context, id.value, info) as RailAppWidgetHostView
        view.setPadding(0, 0, 0, 0)
        views[id.value] = view
        return view
    }

    override fun takeForAttach(id: AppWidgetId): RailAppWidgetHostView? {
        val view: RailAppWidgetHostView = views[id.value] ?: return null
        view.detachFromParent()
        return view
    }

    override fun remove(id: AppWidgetId) {
        views.remove(id.value)?.let { view ->
            view.onLongPress = null
            view.detachFromParent()
        }
        reportedSizes.remove(id.value)
    }

    override fun reportSizeIfChanged(id: AppWidgetId, widthDp: Int, heightDp: Int) {
        val view: RailAppWidgetHostView = views[id.value] ?: return
        val reported: ReportedSize? = reportedSizes[id.value]
        if (reported != null && reported.heightDp == heightDp && kotlin.math.abs(reported.widthDp - widthDp) <= 1) {
            return
        }
        val options: Bundle = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        }
        view.updateAppWidgetSize(options, listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
        reportedSizes[id.value] = ReportedSize(widthDp = widthDp, heightDp = heightDp)
    }

    override fun updateSize(ids: List<AppWidgetId>, widthDp: Int, heightDp: Int) {
        ids.forEach { id -> reportSizeIfChanged(id = id, widthDp = widthDp, heightDp = heightDp) }
    }
}

private data class ReportedSize(val widthDp: Int, val heightDp: Int)

private fun RailAppWidgetHostView.detachFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}
