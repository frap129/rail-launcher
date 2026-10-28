package core.data.testing

import core.data.widgets.PlacedWidget
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider

/** The App Widget id the Widget test fixtures use by default. */
const val TEST_APP_WIDGET_ID: Int = 9

val headerPlace: WidgetPlace = WidgetPlace(
    id = 1L,
    heightDp = null,
    currentPage = 0,
    widgets = listOf(PlacedWidget.Header(id = 1L))
)

fun appWidgetPlace(
    placeId: Long,
    widgetId: Long,
    appWidgetId: Int = TEST_APP_WIDGET_ID,
    profileSerial: Long = 0L,
    availability: WidgetAvailability = WidgetAvailability.Available,
    defaultHeightDp: Int? = null,
    heightDp: Int? = null,
    ownerKey: String? = null
): WidgetPlace = WidgetPlace(
    id = placeId,
    heightDp = heightDp,
    currentPage = 0,
    widgets = listOf(
        PlacedWidget.AppWidget(
            id = widgetId,
            appWidgetId = appWidgetId,
            provider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
            profileSerial = profileSerial,
            extendsUnderRail = false,
            availability = availability,
            defaultHeightDp = defaultHeightDp
        )
    ),
    ownerKey = ownerKey
)

fun stackPlace(): WidgetPlace = WidgetPlace(
    id = 1L,
    heightDp = null,
    currentPage = 0,
    widgets = listOf(
        PlacedWidget.Header(id = 1L),
        appWidgetPlace(placeId = 2L, widgetId = 2L).widgets.single()
    )
)

fun popupStackPlace(
    placeId: Long,
    widgetIds: List<Long> = listOf(6L, 7L),
    appWidgetIds: List<Int> = listOf(9, 10),
    heightDp: Int? = null,
    currentPage: Int = 0,
    ownerKey: String = "owner-key"
): WidgetPlace = WidgetPlace(
    id = placeId,
    heightDp = heightDp,
    currentPage = currentPage,
    widgets = widgetIds.zip(appWidgetIds).map { (widgetId, appWidgetId) ->
        appWidgetPlace(placeId = placeId, widgetId = widgetId, appWidgetId = appWidgetId).widgets.single()
    },
    ownerKey = ownerKey
)
