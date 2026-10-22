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
import core.data.widgets.resizeBounds
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The live vertical Resize mode of one Widget Area place. */
data class ResizeState(
    val placeId: Long,
    val bounds: ResizeBounds,
    val defaultHeightDp: Int,
    val draftHeightDp: Int,
    val canDecrease: Boolean,
    val canIncrease: Boolean,
    val indicatorVisible: Boolean = false
)

/** The direction of one Resize toolbar step. */
enum class ResizeStep {
    Decrease,
    Increase
}

/** Owns Resize: provider bounds, the draft height, persistence, and one size report per session. */
internal class WidgetResizeController(
    private val repository: IWidgetAreaRepository,
    private val appWidgetHost: IAppWidgetHostSource,
    private val settingsRepository: IWidgetSettingsRepository,
    private val homeEditState: HomeEditState,
    private val scope: CoroutineScope,
    private val onReportSize: (appWidgetIds: List<AppWidgetId>, widthDp: Int, heightDp: Int) -> Unit
) {
    private val resize: MutableStateFlow<ResizeState?> = MutableStateFlow(null)
    val resizeState: StateFlow<ResizeState?> = resize.asStateFlow()

    private val homeHeight: MutableStateFlow<Int?> = MutableStateFlow(null)
    val homeHeightDp: StateFlow<Int?> = homeHeight.asStateFlow()

    private var widgetAreaWidthDp: Int = 0
    val widgetAreaWidth: Int get() = widgetAreaWidthDp

    private var rawResizeHeightDp: Float = 0f
    private var resizingAppWidgetIds: List<AppWidgetId> = emptyList()

    init {
        scope.launch {
            repository.places.collect { places -> cancelIfGone(places) }
        }
    }

    /** Supplies the safe Home height so Resize can cap the widget and keep the handle reachable. */
    fun setHomeHeight(heightDp: Int) {
        if (heightDp <= 0) {
            return
        }
        homeHeight.value = heightDp
        resize.update { state -> state?.withDraft(heightDp = state.draftHeightDp) }
    }

    /** Supplies the Widget Area width for the size report sent to the provider after Resize. */
    fun setWidgetAreaWidth(widthDp: Int) {
        if (widthDp > 0) {
            widgetAreaWidthDp = widthDp
        }
    }

    /** Enters Resize for [placeId], returning false when the place is not resizable right now. */
    suspend fun startResize(placeId: Long, headerHeightDp: Int? = null): Boolean {
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
        val defaults: List<Int> = place.widgets.mapNotNull { widget ->
            (widget as? PlacedWidget.AppWidget)?.let { appWidget ->
                providerInfoFor(appWidget)?.minHeightDp ?: appWidget.defaultHeightDp
            }
        }
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
            canDecrease = false,
            canIncrease = false,
            indicatorVisible = indicatorVisible
        ).withDraft(heightDp = draftHeightDp)
        return true
    }

    /** Enters Resize for the place holding [widgetId]. */
    suspend fun startResizeForWidget(widgetId: Long, headerHeightDp: Int? = null): Boolean {
        val place: WidgetPlace = repository.places.first().firstOrNull { place ->
            place.widgets.any { widget -> widget.id == widgetId }
        } ?: return false
        return startResize(placeId = place.id, headerHeightDp = headerHeightDp)
    }

    /** Applies a fractional drag delta, snapping the accumulated height to the 8dp grid. */
    fun dragResize(deltaDp: Float) {
        val state: ResizeState = resize.value ?: return
        if (deltaDp == 0f) {
            return
        }
        rawResizeHeightDp += deltaDp
        val next: ResizeState = state.withDraft(heightDp = state.bounds.snapToStepGrid(heightDp = rawResizeHeightDp.roundToInt()))
        rawResizeHeightDp = next.draftHeightDp.toFloat()
        resize.value = next
    }

    fun stepResize(direction: ResizeStep) {
        val state: ResizeState = resize.value ?: return
        val delta: Int = when (direction) {
            ResizeStep.Decrease -> -ResizeBounds.STEP_DP
            ResizeStep.Increase -> ResizeBounds.STEP_DP
        }
        val heightDp: Int = state.draftHeightDp + delta
        rawResizeHeightDp = heightDp.toFloat()
        resize.value = state.withDraft(heightDp = heightDp)
    }

    fun resetResize() {
        val state: ResizeState = resize.value ?: return
        rawResizeHeightDp = state.defaultHeightDp.toFloat()
        resize.value = state.withDraft(heightDp = state.defaultHeightDp)
    }

    /** Persists the draft height and reports the shared page size once to every App Widget page. */
    fun finishResize() {
        val state: ResizeState = resize.value ?: return
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
        val pageHeightDp: Int = (heightDp - if (state.indicatorVisible) ResizeBounds.STACK_INDICATOR_HEIGHT_DP else 0)
            .coerceAtLeast(1)
        scope.launch {
            repository.setPlaceHeight(placeId = state.placeId, heightDp = storedHeightDp)
            if (appWidgetIds.isNotEmpty() && widgetAreaWidthDp > 0) {
                onReportSize(appWidgetIds, widgetAreaWidthDp, pageHeightDp)
            }
        }
    }

    /** Leaves Resize without saving when the place disappears underneath the user. */
    fun cancelResize() {
        val state: ResizeState = resize.value ?: return
        resize.value = null
        resizingAppWidgetIds = emptyList()
        homeEditState.exit(HomeEditMode.Resizing(placeId = state.placeId))
    }

    /** Consumes Back while resizing; returns false so the launcher can handle it otherwise. */
    fun onBack(): Boolean = consumeResize()

    /** Consumes the Home intent while resizing. */
    fun onHomeIntent(): Boolean = consumeResize()

    private fun consumeResize(): Boolean {
        if (resize.value == null) {
            return false
        }
        finishResize()
        return true
    }

    private fun cancelIfGone(places: List<WidgetPlace>) {
        val state: ResizeState = resize.value ?: return
        val place: WidgetPlace? = places.firstOrNull { place -> place.id == state.placeId }
        if (place == null) {
            cancelResize()
        }
    }

    private fun providerInfoFor(widget: PlacedWidget.AppWidget): WidgetProviderInfo? =
        appWidgetHost.providerInfo(id = AppWidgetId(widget.appWidgetId))

    private fun ResizeState.withDraft(heightDp: Int): ResizeState {
        val homeHeightDp: Int = homeHeight.value ?: Int.MAX_VALUE
        val draftHeightDp: Int = bounds.clamp(heightDp = heightDp, homeHeightDp = homeHeightDp)
        val upperBoundDp: Int = minOf(bounds.maxDp ?: homeHeightDp, homeHeightDp).coerceAtLeast(bounds.minDp)
        return copy(
            draftHeightDp = draftHeightDp,
            canDecrease = draftHeightDp > bounds.minDp,
            canIncrease = draftHeightDp < upperBoundDp
        )
    }
}
