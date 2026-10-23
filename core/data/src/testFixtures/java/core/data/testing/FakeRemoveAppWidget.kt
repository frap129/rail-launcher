package core.data.testing

import core.data.widgets.AppWidgetId
import core.data.widgets.IRemoveAppWidget

class FakeRemoveAppWidget(private val result: AppWidgetId? = AppWidgetId(9)) : IRemoveAppWidget {
    val removedWidgetIds: MutableList<Long> = mutableListOf()

    override suspend fun invoke(widgetId: Long): AppWidgetId? {
        removedWidgetIds += widgetId
        return result
    }
}
