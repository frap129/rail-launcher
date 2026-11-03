package core.data.widgets

import core.data.icons.model.Icon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    @Test
    fun `addDraftPage appends within the cap and rejects at it`() {
        for (cap: Int in 2..8) {
            for (size: Int in 1 until cap) {
                val inputPlaces: List<PlaceDraft> = listOf(PlaceDraft(placeId = 1L, widgetIds = ids(count = size)))
                val expectedPlaces: List<PlaceDraft> =
                    listOf(PlaceDraft(placeId = 1L, widgetIds = ids(count = size) + 99L))

                val actualPlaces: List<PlaceDraft>? = addDraftPage(
                    places = inputPlaces,
                    placeId = 1L,
                    widgetId = 99L,
                    maxStackPages = cap
                )

                assertEquals(expectedPlaces, actualPlaces)
            }
            val inputFullStack: List<PlaceDraft> = listOf(PlaceDraft(placeId = 1L, widgetIds = ids(count = cap)))

            assertNull(addDraftPage(places = inputFullStack, placeId = 1L, widgetId = 99L, maxStackPages = cap))
        }
        assertNull(
            addDraftPage(
                places = listOf(PlaceDraft(placeId = 1L, widgetIds = ids(count = 1))),
                placeId = 2L,
                widgetId = 99L,
                maxStackPages = 8
            )
        )
    }

    @Test
    fun `mergeDraftPlaces merges a single page and the cap decides whether it may grow`() {
        for (cap: Int in 2..8) {
            for (size: Int in 1 until cap) {
                val inputTarget: PlaceDraft = PlaceDraft(placeId = 1L, widgetIds = ids(count = size))
                val inputSource: PlaceDraft = PlaceDraft(placeId = 2L, widgetIds = listOf(99L))
                val expectedPlaces: List<PlaceDraft> =
                    listOf(PlaceDraft(placeId = 1L, widgetIds = ids(count = size) + 99L))

                val actualPlaces: List<PlaceDraft>? =
                    mergeDraftPlaces(places = listOf(inputTarget, inputSource), sourcePlaceId = 2L, targetPlaceId = 1L)

                assertEquals(expectedPlaces, actualPlaces)
                assertTrue(canGrowStack(pageCount = size, maxStackPages = cap))
            }
            assertFalse(canGrowStack(pageCount = cap, maxStackPages = cap))
        }
        val inputMultiPageSource: PlaceDraft = PlaceDraft(placeId = 2L, widgetIds = listOf(98L, 99L))
        val inputTarget: PlaceDraft = PlaceDraft(placeId = 1L, widgetIds = ids(count = 1))

        assertNull(mergeDraftPlaces(places = listOf(inputTarget, inputMultiPageSource), sourcePlaceId = 2L, targetPlaceId = 1L))
    }

    @Test
    fun `splitDraftPage moves a page into a new place before the target`() {
        for (size: Int in 2..8) {
            val inputSource: PlaceDraft = PlaceDraft(placeId = 1L, widgetIds = ids(count = size))
            val inputTarget: PlaceDraft = PlaceDraft(placeId = 2L, widgetIds = listOf(99L))
            val expectedPlaces: List<PlaceDraft> = listOf(
                PlaceDraft(placeId = 1L, widgetIds = ids(count = size) - 2L),
                PlaceDraft(placeId = null, widgetIds = listOf(2L)),
                inputTarget
            )

            val actualPlaces: List<PlaceDraft>? =
                splitDraftPage(places = listOf(inputSource, inputTarget), placeId = 1L, widgetId = 2L, targetPlaceId = 2L)

            assertEquals(expectedPlaces, actualPlaces)
        }
        val inputSinglePage: PlaceDraft = PlaceDraft(placeId = 1L, widgetIds = listOf(3L))
        val inputTarget: PlaceDraft = PlaceDraft(placeId = 2L, widgetIds = listOf(99L))

        assertEquals(
            listOf(PlaceDraft(placeId = null, widgetIds = listOf(3L)), inputTarget),
            splitDraftPage(places = listOf(inputSinglePage, inputTarget), placeId = 1L, widgetId = 3L, targetPlaceId = 2L)
        )
        assertEquals(
            listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(1L, 2L)), inputTarget, PlaceDraft(placeId = null, widgetIds = listOf(3L))),
            splitDraftPage(
                places = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(1L, 2L, 3L)), inputTarget),
                placeId = 1L,
                widgetId = 3L,
                targetPlaceId = null
            )
        )
        assertNull(splitDraftPage(places = listOf(inputSinglePage), placeId = 1L, widgetId = 4L, targetPlaceId = null))
    }

    @Test
    fun `draft operations address a split place by its stable id`() {
        val inputSplit: PlaceDraft = PlaceDraft(placeId = null, widgetIds = listOf(5L, 6L))
        val inputTarget: PlaceDraft = PlaceDraft(placeId = 2L, widgetIds = listOf(9L))
        val splitPlaceId: Long = inputSplit.id
        val targetPlaceId: Long = inputTarget.id

        val actualSplit: List<PlaceDraft>? =
            splitDraftPage(places = listOf(inputSplit, inputTarget), placeId = splitPlaceId, widgetId = 6L, targetPlaceId = targetPlaceId)
        val actualRemoved: List<PlaceDraft>? =
            removeDraftPage(places = listOf(inputSplit, inputTarget), placeId = splitPlaceId, widgetId = 5L)
        val actualMoved: List<PlaceDraft>? =
            moveDraftPage(places = listOf(inputSplit, inputTarget), placeId = splitPlaceId, fromIndex = 0, toIndex = 1)
        val inputSingleSplit: PlaceDraft = PlaceDraft(placeId = null, widgetIds = listOf(7L))
        val actualMerged: List<PlaceDraft>? =
            mergeDraftPlaces(
                places = listOf(inputSingleSplit, inputTarget),
                sourcePlaceId = inputSingleSplit.id,
                targetPlaceId = targetPlaceId
            )

        assertEquals(
            listOf(PlaceDraft(placeId = null, widgetIds = listOf(5L)), PlaceDraft(placeId = null, widgetIds = listOf(6L)), inputTarget),
            actualSplit
        )
        assertEquals(listOf(PlaceDraft(placeId = null, widgetIds = listOf(6L)), inputTarget), actualRemoved)
        assertEquals(listOf(PlaceDraft(placeId = null, widgetIds = listOf(6L, 5L)), inputTarget), actualMoved)
        assertEquals(splitPlaceId, actualMoved?.first()?.id)
        assertEquals(listOf(inputTarget.copy(widgetIds = listOf(9L, 7L))), actualMerged)
    }

    @Test
    fun `removeDraftPage drops a page and a place left empty`() {
        for (size: Int in 2..8) {
            val inputPlaces: List<PlaceDraft> = listOf(PlaceDraft(placeId = 1L, widgetIds = ids(count = size)))
            val expectedPlaces: List<PlaceDraft> = listOf(PlaceDraft(placeId = 1L, widgetIds = ids(count = size) - 2L))

            val actualPlaces: List<PlaceDraft>? = removeDraftPage(places = inputPlaces, placeId = 1L, widgetId = 2L)

            assertEquals(expectedPlaces, actualPlaces)
        }
        assertEquals(
            emptyList<PlaceDraft>(),
            removeDraftPage(places = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(1L))), placeId = 1L, widgetId = 1L)
        )
        assertNull(removeDraftPage(places = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(1L))), placeId = 1L, widgetId = 2L))
    }

    @Test
    fun `moveDraftPage reorders every stack size and rejects outside the stack`() {
        for (size: Int in 2..8) {
            val inputWidgetIds: List<Long> = ids(count = size)
            val expectedWidgetIds: List<Long> = inputWidgetIds.drop(1) + inputWidgetIds.first()

            val actualPlaces: List<PlaceDraft>? = moveDraftPage(
                places = listOf(PlaceDraft(placeId = 1L, widgetIds = inputWidgetIds)),
                placeId = 1L,
                fromIndex = 0,
                toIndex = size - 1
            )

            assertEquals(listOf(PlaceDraft(placeId = 1L, widgetIds = expectedWidgetIds)), actualPlaces)
        }
        val inputPlaces: List<PlaceDraft> = listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(1L, 2L)))

        assertNull(moveDraftPage(places = inputPlaces, placeId = 1L, fromIndex = 0, toIndex = 2))
        assertNull(moveDraftPage(places = inputPlaces, placeId = 1L, fromIndex = -1, toIndex = 1))
        assertEquals(inputPlaces, moveDraftPage(places = inputPlaces, placeId = 1L, fromIndex = 1, toIndex = 1))
    }

    @Test
    fun `moveDraftPlace reorders every draft size and rejects outside it`() {
        for (size: Int in 2..8) {
            val inputPlaces: List<PlaceDraft> = (1..size).map { id -> PlaceDraft(placeId = id.toLong(), widgetIds = listOf(id.toLong())) }
            val expectedPlaces: List<PlaceDraft> = inputPlaces.drop(1) + inputPlaces.first()

            val actualPlaces: List<PlaceDraft>? = moveDraftPlace(places = inputPlaces, fromIndex = 0, toIndex = size - 1)

            assertEquals(expectedPlaces, actualPlaces)
        }
        val inputPlaces: List<PlaceDraft> =
            listOf(PlaceDraft(placeId = 1L, widgetIds = listOf(1L)), PlaceDraft(placeId = 2L, widgetIds = listOf(2L)))

        assertNull(moveDraftPlace(places = inputPlaces, fromIndex = 0, toIndex = 2))
        assertNull(moveDraftPlace(places = inputPlaces, fromIndex = -1, toIndex = 1))
        assertEquals(inputPlaces, moveDraftPlace(places = inputPlaces, fromIndex = 0, toIndex = 0))
    }

    @Test
    fun `removeDraftPlace drops the place and rejects an unknown one`() {
        val inputPlaces: List<PlaceDraft> = (1..8).map { id -> PlaceDraft(placeId = id.toLong(), widgetIds = listOf(id.toLong())) }

        assertEquals(inputPlaces.drop(1), removeDraftPlace(places = inputPlaces, placeId = 1L))
        assertNull(removeDraftPlace(places = inputPlaces, placeId = 99L))
    }

    @Test
    fun `normalization clamps every stack size to the page bounds`() {
        for (size: Int in 2..8) {
            val inputWidgets: List<WidgetRecord> = (1..size).map { id ->
                appWidgetRecord(id = id.toLong(), appWidgetId = id, defaultHeightDp = 80)
            }
            val providerInfos: Map<Int, WidgetProviderInfo> = (1..size).associate { id ->
                id to providerInfo(maxResizeHeightDp = if (id <= 2) 200 - id * 20 else 300)
            }
            val inputPlace: WidgetPlaceRecord = WidgetPlaceRecord(
                id = 1L,
                position = 0,
                heightDp = 500,
                currentPage = size
            )

            val actualLayout: WidgetPlaceLayout? =
                normalizePlace(inputPlace, inputWidgets) { widget -> providerInfos[widget.appWidgetId] }

            assertEquals(size - 1, actualLayout?.currentPage)
            assertEquals(160, actualLayout?.heightDp)
        }
    }

    @Test
    fun `normalization resets a lone Header height and clamps a lone App Widget`() {
        val inputHeaderPlace: WidgetPlaceRecord = WidgetPlaceRecord(id = 1L, position = 0, heightDp = 200, currentPage = 2)
        val inputHeader: WidgetRecord = WidgetRecord(id = 10L, placeId = 1L, pagePosition = 0, kind = WidgetKind.HEADER)

        val actualHeaderLayout: WidgetPlaceLayout? = normalizePlace(inputHeaderPlace, listOf(inputHeader)) { null }

        assertEquals(WidgetPlaceLayout(currentPage = 0, heightDp = null), actualHeaderLayout)

        val inputWidget: WidgetRecord = appWidgetRecord(id = 20L, appWidgetId = 7, defaultHeightDp = 80)
        val inputWidgetPlace: WidgetPlaceRecord = WidgetPlaceRecord(id = 1L, position = 0, heightDp = 500, currentPage = 3)

        val actualWidgetLayout: WidgetPlaceLayout? = normalizePlace(inputWidgetPlace, listOf(inputWidget)) { info ->
            providerInfo(minResizeHeightDp = 60, maxResizeHeightDp = 160).takeIf { info.appWidgetId == 7 }
        }

        assertEquals(WidgetPlaceLayout(currentPage = 0, heightDp = 160), actualWidgetLayout)
        assertEquals(
            WidgetPlaceLayout(currentPage = 0, heightDp = 80),
            normalizePlace(inputWidgetPlace.copy(heightDp = 40), listOf(inputWidget)) { null }
        )
        assertNull(normalizePlace(inputWidgetPlace, emptyList()) { null })
    }

    @Test
    fun `projected height clamps every stack size to the bounds and Home height`() {
        for (size: Int in 2..8) {
            val inputWidgets: List<PlacedWidget.AppWidget> = (1..size).map { id ->
                PlacedWidget.AppWidget(
                    id = id.toLong(),
                    appWidgetId = id,
                    provider = WidgetProvider(packageName = "com.example", className = "Widget"),
                    profileSerial = 0L,
                    extendsUnderRail = false,
                    defaultHeightDp = 80
                )
            }
            val inputPlace: WidgetPlace = WidgetPlace(id = 1L, heightDp = 500, currentPage = 0, widgets = inputWidgets)
            val providerInfos: Map<Int, WidgetProviderInfo> = (1..size).associate { id ->
                id to providerInfo(maxResizeHeightDp = 200)
            }

            assertEquals(
                200,
                projectedHeightDp(
                    place = inputPlace,
                    headerHeightDp = null,
                    homeHeightDp = 1000,
                    providerInfo = { widget -> providerInfos[widget.appWidgetId] }
                )
            )
            assertEquals(
                120,
                projectedHeightDp(
                    place = inputPlace,
                    headerHeightDp = null,
                    homeHeightDp = 120,
                    providerInfo = { widget -> providerInfos[widget.appWidgetId] }
                )
            )
        }
    }

    @Test
    fun `projected height fills a missing Pop-up height and keeps a Header's stored height`() {
        val inputWidget: PlacedWidget.AppWidget = PlacedWidget.AppWidget(
            id = 1L,
            appWidgetId = 7,
            provider = WidgetProvider(packageName = "com.example", className = "Widget"),
            profileSerial = 0L,
            extendsUnderRail = false,
            defaultHeightDp = 80
        )
        val inputPlace: WidgetPlace = WidgetPlace(id = 1L, heightDp = null, currentPage = 0, widgets = listOf(inputWidget))

        assertEquals(
            80,
            projectedHeightDp(
                place = inputPlace.copy(ownerKey = "owner-key"),
                headerHeightDp = null,
                homeHeightDp = 1000
            ) { null }
        )
        assertNull(projectedHeightDp(place = inputPlace, headerHeightDp = null, homeHeightDp = 1000) { null })
        assertEquals(
            200,
            projectedHeightDp(
                place = WidgetPlace(id = 1L, heightDp = 200, currentPage = 0, widgets = listOf(PlacedWidget.Header(id = 1L))),
                headerHeightDp = null,
                homeHeightDp = 1000
            ) { null }
        )
    }

    private fun ids(count: Int): List<Long> = (1..count).map { id -> id.toLong() }

    private fun appWidgetRecord(id: Long, appWidgetId: Int, defaultHeightDp: Int? = null): WidgetRecord = WidgetRecord(
        id = id,
        placeId = 1L,
        pagePosition = id.toInt() - 1,
        kind = WidgetKind.APP_WIDGET,
        appWidgetId = appWidgetId,
        provider = "com.example/Widget",
        profileSerial = 0L,
        defaultHeightDp = defaultHeightDp
    )

    private fun providerInfo(minResizeHeightDp: Int = 48, maxResizeHeightDp: Int? = null): WidgetProviderInfo = WidgetProviderInfo(
        provider = WidgetProvider(packageName = "com.example", className = "Widget"),
        profileSerial = 0L,
        appPackageName = "com.example",
        appLabel = "Example",
        appIcon = Icon.RailIcon(resId = 1),
        label = "Widget",
        description = null,
        defaultSize = WidgetSize(widthDp = 100, heightDp = 80),
        cellSize = null,
        minHeightDp = 48,
        minResizeHeightDp = minResizeHeightDp,
        maxResizeHeightDp = maxResizeHeightDp,
        needsConfiguration = false,
        isReconfigurable = false,
        hasConfigureActivity = false,
        isWorkProfile = false,
        isVerticallyResizable = maxResizeHeightDp != null
    )
}
