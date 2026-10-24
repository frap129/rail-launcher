package feature.launcher.widgets

import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.PlacedWidget
import core.data.widgets.ResizeBounds
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.placeOf
import core.data.widgets.resizeBounds
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** Owns Resize: provider bounds, the draft height, persistence, and one size report per session. */
class IWidgetResizeControllerImpl(
    private val repository: IWidgetAreaRepository,
    private val appWidgetHost: IAppWidgetHostSource,
    private val settingsRepository: IWidgetSettingsRepository,
    private val homeEditState: HomeEditState
) : IWidgetResizeController {
    private val resize: MutableStateFlow<ResizeState?> = MutableStateFlow(null)
    override val resizeState: StateFlow<ResizeState?> = resize.asStateFlow()

    private val homeHeight: MutableStateFlow<Int?> = MutableStateFlow(null)
    override val homeHeightDp: StateFlow<Int?> = homeHeight.asStateFlow()

    private var widgetAreaWidthDp: Int = 0

    private var rawResizeHeightDp: Float = 0f
    private var resizingAppWidgetIds: List<AppWidgetId> = emptyList()

    /** Supplies the safe Home height so Resize can cap the widget and keep the handle reachable. */
    override fun setHomeHeight(heightDp: Int) {
        if (heightDp <= 0) {
            return
        }
        homeHeight.value = heightDp
        resize.update { state -> state?.withDraft(heightDp = state.draftHeightDp) }
    }

    /** Supplies the Widget Area width for the size report sent to the provider after Resize. */
    override fun setWidgetAreaWidth(widthDp: Int) {
        if (widthDp > 0) {
            widgetAreaWidthDp = widthDp
        }
    }

    /** Enters Resize for [placeId], returning false when the place is not resizable right now. */
    override suspend fun startResize(placeId: Long, headerHeightDp: Int?): Boolean {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return false
        }
        val place: WidgetPlace = repository.places.first().firstOrNull { place -> place.id == placeId } ?: return false
        if (place.widgets.none { widget -> widget is PlacedWidget.AppWidget }) {
            return false
        }
        val bounds: ResizeBounds = if (place.isStack) {
            ResizeBounds.stackBounds(place.widgets.map { widget -> widget.resizeBounds(headerHeightDp, ::providerInfoFor) })
        } else {
            val widget: PlacedWidget.AppWidget = place.widgets.filterIsInstance<PlacedWidget.AppWidget>().firstOrNull()
                ?.takeIf { widget -> widget.availability == WidgetAvailability.Available }
                ?: return false
            providerInfoFor(widget)?.let(ResizeBounds::from) ?: return false
        }
        if (!bounds.isResizable) {
            return false
        }
        if (!homeEditState.tryEnter(HomeEditMode.Resizing(placeId = placeId))) {
            return false
        }
        val defaults: List<Int> = defaultHeightsDp(place)
        val defaultHeightDp: Int = if (place.isStack) {
            defaults.maxOrNull() ?: bounds.minDp
        } else {
            defaults.firstOrNull() ?: bounds.minDp
        }
        val indicatorVisible: Boolean = place.isStack && settingsRepository.widgetAreaSettings.first().stackIndicatorVisible
        val draftHeightDp: Int = bounds.clamp(
            heightDp = place.heightDp ?: defaultHeightDp,
            homeHeightDp = homeHeight.value ?: Int.MAX_VALUE
        )
        rawResizeHeightDp = draftHeightDp.toFloat()
        resizingAppWidgetIds = place.widgets
            .filterIsInstance<PlacedWidget.AppWidget>()
            .map { placed -> AppWidgetId(placed.appWidgetId) }
        resize.value = ResizeState(
            placeId = placeId,
            bounds = bounds,
            defaultHeightDp = defaultHeightDp,
            draftHeightDp = draftHeightDp,
            indicatorVisible = indicatorVisible
        )
        return true
    }

    /** Enters Resize for the place holding [widgetId]. */
    override suspend fun startResizeForWidget(widgetId: Long, headerHeightDp: Int?): Boolean {
        val place: WidgetPlace = repository.places.first().placeOf(widgetId = widgetId) ?: return false
        return startResize(placeId = place.id, headerHeightDp = headerHeightDp)
    }

    /** Applies a fractional drag delta, snapping the accumulated height to the 8dp grid. */
    override fun dragResize(deltaDp: Float) {
        val state: ResizeState = resize.value ?: return
        if (deltaDp == 0f) {
            return
        }
        rawResizeHeightDp += deltaDp
        val next: ResizeState = state.withDraft(heightDp = state.bounds.snapToStepGrid(heightDp = rawResizeHeightDp.roundToInt()))
        rawResizeHeightDp = next.draftHeightDp.toFloat()
        resize.value = next
    }

    /** Returns the draft to the provider default, which [finishResize] then stores as no override. */
    override fun resetResize() {
        val state: ResizeState = resize.value ?: return
        rawResizeHeightDp = state.defaultHeightDp.toFloat()
        resize.value = state.withDraft(heightDp = state.defaultHeightDp)
    }

    /** Leaves Resize with the height persistence and size report it still needs. */
    override fun finishResize(): ResizeFinish? {
        val state: ResizeState = resize.value ?: return null
        resize.value = null
        homeEditState.exit(HomeEditMode.Resizing(placeId = state.placeId))
        val heightDp: Int = state.draftHeightDp
        val effectiveDefaultHeightDp: Int = state.bounds.clamp(
            heightDp = state.defaultHeightDp,
            homeHeightDp = homeHeight.value ?: Int.MAX_VALUE
        )
        val storedHeightDp: Int? = heightDp.takeUnless { draft -> draft == effectiveDefaultHeightDp }
        val appWidgetIds: List<AppWidgetId> = resizingAppWidgetIds
        resizingAppWidgetIds = emptyList()
        return ResizeFinish(
            placeId = state.placeId,
            storedHeightDp = storedHeightDp,
            appWidgetIds = appWidgetIds,
            widthDp = widgetAreaWidthDp,
            heightDp = pageHeightDp(heightDp = heightDp, indicatorVisible = state.indicatorVisible)
        )
    }

    override suspend fun persistResize(finish: ResizeFinish): WidgetSizeReport? {
        repository.setPlaceHeight(placeId = finish.placeId, heightDp = finish.storedHeightDp)
        if (finish.appWidgetIds.isEmpty() || finish.widthDp <= 0) {
            return null
        }
        return WidgetSizeReport(
            appWidgetIds = finish.appWidgetIds,
            widthDp = finish.widthDp,
            heightDp = finish.heightDp
        )
    }

    /** Leaves Resize without saving when the place disappears underneath the user. */
    override fun cancelResize() {
        val state: ResizeState = resize.value ?: return
        resize.value = null
        resizingAppWidgetIds = emptyList()
        homeEditState.exit(HomeEditMode.Resizing(placeId = state.placeId))
    }

    /** Clamps a stack that gained a page and returns the page size to report to its providers. */
    override suspend fun onStackGrown(placeId: Long, headerHeightDp: Int?): WidgetSizeReport? {
        clampStackHeight(placeId = placeId, headerHeightDp = headerHeightDp)
        return reportStackSize(placeId = placeId)
    }

    /** Clamps a stack's stored height after the Header is replaced by a page. */
    override suspend fun clampStackHeight(placeId: Long, headerHeightDp: Int?) {
        val place: WidgetPlace = repository.places.first().firstOrNull { candidate -> candidate.id == placeId } ?: return
        val bounds: ResizeBounds = ResizeBounds.stackBounds(
            place.widgets.map { widget -> widget.resizeBounds(headerHeightDp, ::providerInfoFor) }
        )
        val storedHeight: Int = place.heightDp ?: return
        val homeHeight: Int = homeHeight.value ?: Int.MAX_VALUE
        val clamped: Int = bounds.clamp(heightDp = storedHeight, homeHeightDp = homeHeight)
        if (clamped != storedHeight) {
            repository.setPlaceHeight(placeId = placeId, heightDp = clamped)
        }
    }

    private suspend fun reportStackSize(placeId: Long): WidgetSizeReport? {
        val place: WidgetPlace = repository.places.first().firstOrNull { candidate -> candidate.id == placeId } ?: return null
        val appWidgetIds: List<AppWidgetId> = place.widgets
            .filterIsInstance<PlacedWidget.AppWidget>()
            .map { widget -> AppWidgetId(widget.appWidgetId) }
        val defaults: List<Int> = defaultHeightsDp(place)
        val heightDp: Int = place.heightDp ?: defaults.maxOrNull() ?: return null
        val widthDp: Int = widgetAreaWidthDp
        if (appWidgetIds.isEmpty() || widthDp <= 0) {
            return null
        }
        val indicatorVisible: Boolean = place.isStack &&
            settingsRepository.widgetAreaSettings.first().stackIndicatorVisible
        val pageHeightDp: Int = pageHeightDp(heightDp = heightDp, indicatorVisible = indicatorVisible)
        return WidgetSizeReport(appWidgetIds = appWidgetIds, widthDp = widthDp, heightDp = pageHeightDp)
    }

    private fun defaultHeightsDp(place: WidgetPlace): List<Int> = place.widgets.mapNotNull { widget ->
        (widget as? PlacedWidget.AppWidget)?.let { appWidget ->
            providerInfoFor(appWidget)?.minHeightDp ?: appWidget.defaultHeightDp
        }
    }

    private fun pageHeightDp(heightDp: Int, indicatorVisible: Boolean): Int =
        (heightDp - if (indicatorVisible) ResizeBounds.STACK_INDICATOR_HEIGHT_DP else 0).coerceAtLeast(1)

    override fun onPlacesChanged(places: List<WidgetPlace>) {
        val state: ResizeState = resize.value ?: return
        if (places.none { place -> place.id == state.placeId }) {
            cancelResize()
        }
    }

    private fun providerInfoFor(widget: PlacedWidget.AppWidget): WidgetProviderInfo? =
        appWidgetHost.providerInfo(id = AppWidgetId(widget.appWidgetId))

    private fun ResizeState.withDraft(heightDp: Int): ResizeState {
        val homeHeightDp: Int = homeHeight.value ?: Int.MAX_VALUE
        return copy(draftHeightDp = bounds.clamp(heightDp = heightDp, homeHeightDp = homeHeightDp))
    }
}
