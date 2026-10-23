package feature.launcher.widgets

import core.data.icons.model.Icon
import core.data.widgets.AppWidgetId
import core.data.widgets.InsertPosition
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Where a picked App Widget is inserted and whether the flow returns to Settings. */
data class AddWidgetOrigin(val insertAt: InsertPosition, val returnToSettings: Boolean = false)

/** The two placements offered when adding from an existing Widget. */
enum class AddWidgetPlacement {
    Below,
    IntoStack
}

/** What the Add widget menu action was opened from. */
sealed interface AddWidgetTarget {
    data class Header(val widgetId: Long?) : AddWidgetTarget

    data class Widget(val widgetId: Long) : AddWidgetTarget

    data object Empty : AddWidgetTarget
}

/** The allocated id and destination of an App Widget in the add flow. */
data class PendingWidget(
    val id: AppWidgetId,
    val provider: WidgetProvider,
    val profileSerial: Long,
    val needsConfiguration: Boolean,
    val defaultHeightDp: Int,
    val origin: AddWidgetOrigin
)

/** One app's App Widget providers in the picker. */
data class PickerGroup(
    val appPackageName: String,
    val appLabel: String,
    val appIcon: Icon,
    val isWorkProfile: Boolean,
    val widgets: List<WidgetProviderInfo>
)

sealed interface AddWidgetFlowState {
    data object Idle : AddWidgetFlowState

    /** The Add below / Add next to choice shown before the picker. */
    data class ChoosingPlacement(val origin: AddWidgetOrigin, val canAddToStack: Boolean) : AddWidgetFlowState

    data class Picking(val origin: AddWidgetOrigin, val query: String, val groups: List<PickerGroup>) : AddWidgetFlowState

    data class Binding(val widget: PendingWidget) : AddWidgetFlowState

    data class Configuring(val widget: PendingWidget) : AddWidgetFlowState
}

/** Owns the App Widget add flow: pick, bind, configure, place and restore. */
interface IWidgetAddController {
    val state: StateFlow<AddWidgetFlowState>

    /** Events the add flow raises for the launcher to run. */
    val events: Flow<WidgetAreaEvent>

    /** Opens the picker at [origin]; returns false while another Home edit mode is active. */
    fun start(origin: AddWidgetOrigin): Boolean

    /** Starts the add flow from the Widget menu; returns true only once the picker opens. */
    suspend fun startFromMenu(target: AddWidgetTarget): Boolean

    /** Applies the placement choice; returns true once the picker opens. */
    fun choosePlacement(placement: AddWidgetPlacement): Boolean

    fun setQuery(query: String)

    /** Keeps the picker groups in step with the host's provider list. */
    fun updateProviders(providers: List<WidgetProviderInfo>)

    /** Binds the picked provider; returns the stack that grew when it is placed immediately. */
    suspend fun pick(provider: WidgetProviderInfo): Long?

    suspend fun cancel()

    /** Completes the system bind dialog; returns the stack that grew when it is placed immediately. */
    suspend fun onBindResult(granted: Boolean): Long?

    /** Completes the provider configuration; returns the stack that grew when it is placed. */
    suspend fun onConfigureResult(ok: Boolean): Long?

    /** Restores an add flow interrupted by process recreation. */
    suspend fun restore()
}
