package core.data.widgets

import core.data.room.dao.WidgetAreaDao

/** Clamps a place that has dissolved to one Widget against that Widget's resize bounds. */
internal suspend fun clampDissolvedPlace(dao: WidgetAreaDao, host: IAppWidgetHostSource, placeId: Long) {
    val place: WidgetPlaceRecord = dao.getPlace(placeId) ?: return
    val widgets: List<WidgetRecord> = dao.getWidgetsForPlace(placeId)
    if (widgets.size != 1) {
        return
    }
    val widget: WidgetRecord = widgets.first()
    if (widget.kind == WidgetKind.HEADER) {
        dao.setPlaceHeight(placeId = placeId, heightDp = null)
        return
    }
    val appWidgetId: Int = widget.appWidgetId ?: return
    val info: WidgetProviderInfo = host.providerInfo(AppWidgetId(appWidgetId)) ?: return
    val heightDp: Int = place.heightDp ?: return
    val bounds: ResizeBounds = ResizeBounds.from(info)
    dao.setPlaceHeight(placeId = placeId, heightDp = bounds.clamp(heightDp = heightDp, homeHeightDp = Int.MAX_VALUE))
}
