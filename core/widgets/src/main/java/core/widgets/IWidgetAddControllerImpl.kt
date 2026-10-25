package core.widgets

import androidx.lifecycle.SavedStateHandle
import core.data.widgets.AddAppWidget
import core.data.widgets.AddedAppWidget
import core.data.widgets.AppWidgetId
import core.data.widgets.BindRequest
import core.data.widgets.IAddFlowPendingIds
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.IWidgetAreaRepository
import core.data.widgets.IWidgetSettingsRepository
import core.data.widgets.InsertPosition
import core.data.widgets.WidgetPlace
import core.data.widgets.WidgetProvider
import core.data.widgets.WidgetProviderInfo
import core.data.widgets.canGrowStack
import core.data.widgets.placeOf
import core.ui.R as CoreUiR
import core.ui.layout.HomeEditMode
import core.ui.layout.HomeEditState
import core.util.EventChannel
import java.text.Collator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private const val PENDING_PHASE_BINDING: String = "binding"
private const val PENDING_PHASE_CONFIGURING: String = "configuring"

/** Owns the App Widget add flow outside the ViewModel. */
class IWidgetAddControllerImpl(
    private val repository: IWidgetAreaRepository,
    private val appWidgetHost: IAppWidgetHostSource,
    private val settingsRepository: IWidgetSettingsRepository,
    private val homeEditState: HomeEditState,
    private val savedStateHandle: SavedStateHandle,
    private val pendingIds: IAddFlowPendingIds
) : IWidgetAddController {
    private val addFlow: MutableStateFlow<AddWidgetFlowState> = MutableStateFlow(AddWidgetFlowState.Idle)
    override val state: StateFlow<AddWidgetFlowState> = addFlow.asStateFlow()

    private val eventChannel: EventChannel<WidgetEvent> = EventChannel()
    override val events: Flow<WidgetEvent> = eventChannel.flow

    private var providers: List<WidgetProviderInfo> = emptyList()

    override fun start(origin: AddWidgetOrigin): Boolean {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return false
        }
        addFlow.value = AddWidgetFlowState.Picking(
            origin = origin,
            query = "",
            groups = pickerGroups(providers = providers, query = "", origin = origin)
        )
        return true
    }

    override suspend fun startFromMenu(target: AddWidgetTarget): Boolean {
        if (homeEditState.mode.value != HomeEditMode.None) {
            return false
        }
        val places: List<WidgetPlace> = repository.places.first()
        val place: WidgetPlace? = when (target) {
            is AddWidgetTarget.Header -> if (target.widgetId == null) {
                places.headerPlace()
            } else {
                places.placeOf(widgetId = target.widgetId)
            }

            is AddWidgetTarget.Widget -> places.placeOf(widgetId = target.widgetId)

            AddWidgetTarget.Empty -> null
        }
        if (place == null) {
            return start(origin = AddWidgetOrigin(insertAt = InsertPosition.End))
        }
        val origin: AddWidgetOrigin = AddWidgetOrigin(insertAt = InsertPosition.Below(placeId = place.id))
        val maxStackPages: Int = settingsRepository.widgetAreaSettings.first().maxStackPages
        if (place.canGrowStack(maxStackPages = maxStackPages)) {
            addFlow.value = AddWidgetFlowState.ChoosingPlacement(origin = origin, canAddToStack = true)
            return false
        }
        return start(origin = origin)
    }

    override fun choosePlacement(placement: AddWidgetPlacement): Boolean {
        val choosing: AddWidgetFlowState.ChoosingPlacement =
            addFlow.value as? AddWidgetFlowState.ChoosingPlacement ?: return false
        val origin: AddWidgetOrigin = when (placement) {
            AddWidgetPlacement.Below -> choosing.origin

            AddWidgetPlacement.IntoStack -> {
                val placeId: Long? = (choosing.origin.insertAt as? InsertPosition.Below)?.placeId
                if (choosing.canAddToStack && placeId != null) {
                    choosing.origin.copy(insertAt = InsertPosition.IntoStack(placeId = placeId))
                } else {
                    choosing.origin
                }
            }
        }
        return start(origin = origin)
    }

    override fun setQuery(query: String) {
        val picking: AddWidgetFlowState.Picking = addFlow.value as? AddWidgetFlowState.Picking ?: return
        addFlow.value = picking.copy(
            query = query,
            groups = pickerGroups(providers = providers, query = query, origin = picking.origin)
        )
    }

    override fun updateProviders(providers: List<WidgetProviderInfo>) {
        this.providers = providers
        val picking: AddWidgetFlowState.Picking = addFlow.value as? AddWidgetFlowState.Picking ?: return
        addFlow.value = picking.copy(groups = pickerGroups(providers = providers, query = picking.query, origin = picking.origin))
    }

    override suspend fun pick(provider: WidgetProviderInfo): Long? {
        val picking: AddWidgetFlowState.Picking = addFlow.value as? AddWidgetFlowState.Picking ?: return null
        val isStillOffered: Boolean = providers.any { available ->
            available.provider == provider.provider && available.profileSerial == provider.profileSerial
        }
        if (!isStillOffered) {
            addFlow.value = AddWidgetFlowState.Idle
            eventChannel.send(WidgetEvent.ShowMessage(message = CoreUiR.string.couldnt_add_widget))
            returnToSettingsIfRequested(origin = picking.origin)
            return null
        }
        val id: AppWidgetId = appWidgetHost.allocateId()
        val pending: PendingWidget = PendingWidget(
            id = id,
            provider = provider.provider,
            profileSerial = provider.profileSerial,
            needsConfiguration = provider.needsConfiguration,
            defaultHeightDp = provider.minHeightDp,
            origin = picking.origin
        )
        addFlow.value = AddWidgetFlowState.Binding(widget = pending)
        persistPending(pending = pending, phase = PENDING_PHASE_BINDING)
        if (addFlow.value != AddWidgetFlowState.Binding(widget = pending)) {
            return null
        }
        val request: BindRequest = BindRequest(id = id, provider = provider.provider, profileSerial = provider.profileSerial)
        if (appWidgetHost.bindIfAllowed(request = request)) {
            return continueAfterBind(pending = pending)
        }
        eventChannel.send(WidgetEvent.LaunchBind(request = request))
        return null
    }

    override suspend fun cancel() {
        when (val state: AddWidgetFlowState = addFlow.value) {
            AddWidgetFlowState.Idle -> Unit

            is AddWidgetFlowState.ChoosingPlacement -> {
                addFlow.value = AddWidgetFlowState.Idle
                returnToSettingsIfRequested(origin = state.origin)
            }

            is AddWidgetFlowState.Picking -> {
                addFlow.value = AddWidgetFlowState.Idle
                returnToSettingsIfRequested(origin = state.origin)
            }

            is AddWidgetFlowState.Binding -> cancelPending(pending = state.widget)

            is AddWidgetFlowState.Configuring -> cancelPending(pending = state.widget)
        }
    }

    override suspend fun onBindResult(granted: Boolean): Long? {
        val binding: AddWidgetFlowState.Binding = addFlow.value as? AddWidgetFlowState.Binding ?: return null
        return if (granted) {
            continueAfterBind(pending = binding.widget)
        } else {
            failAdd(id = binding.widget.id, returnToSettings = binding.widget.origin.returnToSettings)
            null
        }
    }

    override suspend fun onConfigureResult(ok: Boolean): Long? {
        val configuring: AddWidgetFlowState.Configuring = addFlow.value as? AddWidgetFlowState.Configuring ?: return null
        return if (ok) {
            placeWidget(pending = configuring.widget)
        } else {
            failAdd(id = configuring.widget.id, returnToSettings = configuring.widget.origin.returnToSettings)
            null
        }
    }

    override suspend fun restore() {
        val pendingId: Int = savedStateHandle.get<Int>(PENDING_APP_WIDGET_ID_KEY) ?: return
        val providerKey: String? = savedStateHandle.get<String>(PENDING_PROVIDER_KEY)
        val provider: WidgetProvider? = providerKey?.let(WidgetProvider::fromKey)
        val profileSerial: Long? = savedStateHandle.get<Long>(PENDING_PROFILE_SERIAL_KEY)
        if (provider == null || profileSerial == null) {
            failAdd(
                id = AppWidgetId(pendingId),
                returnToSettings = savedStateHandle.get<Boolean>(PENDING_RETURN_TO_SETTINGS_KEY) ?: false
            )
            return
        }
        val stackPlaceId: Long? = savedStateHandle.get<Long>(PENDING_INSERT_STACK_PLACE_ID_KEY)
        val belowPlaceId: Long? = savedStateHandle.get<Long>(PENDING_INSERT_BELOW_PLACE_ID_KEY)
        val insertAt: InsertPosition = when {
            stackPlaceId != null -> InsertPosition.IntoStack(placeId = stackPlaceId)
            belowPlaceId != null -> InsertPosition.Below(placeId = belowPlaceId)
            else -> InsertPosition.End
        }
        val pending: PendingWidget = PendingWidget(
            id = AppWidgetId(pendingId),
            provider = provider,
            profileSerial = profileSerial,
            needsConfiguration = savedStateHandle.get<Boolean>(PENDING_NEEDS_CONFIGURATION_KEY) ?: false,
            defaultHeightDp = savedStateHandle.get<Int>(PENDING_DEFAULT_HEIGHT_KEY) ?: 0,
            origin = AddWidgetOrigin(
                insertAt = insertAt,
                returnToSettings = savedStateHandle.get<Boolean>(PENDING_RETURN_TO_SETTINGS_KEY) ?: false,
                ownerKey = savedStateHandle.get<String>(PENDING_OWNER_KEY)
            )
        )
        addFlow.value = if (savedStateHandle.get<String>(PENDING_PHASE_KEY) == PENDING_PHASE_CONFIGURING) {
            AddWidgetFlowState.Configuring(widget = pending)
        } else {
            AddWidgetFlowState.Binding(widget = pending)
        }
        pendingIds.set(setOf(pending.id))
    }

    private suspend fun continueAfterBind(pending: PendingWidget): Long? {
        if (pending.needsConfiguration) {
            addFlow.value = AddWidgetFlowState.Configuring(widget = pending)
            persistPending(pending = pending, phase = PENDING_PHASE_CONFIGURING)
            if (addFlow.value != AddWidgetFlowState.Configuring(widget = pending)) {
                return null
            }
            eventChannel.send(WidgetEvent.LaunchConfigure(id = pending.id))
            return null
        }
        return placeWidget(pending = pending)
    }

    /** Places the Widget and returns the stack that grew, when it joined one. */
    private suspend fun placeWidget(pending: PendingWidget): Long? {
        val placed: Result<AddedAppWidget> = runCatching {
            repository.addAppWidget(
                request = AddAppWidget(
                    appWidgetId = pending.id,
                    provider = pending.provider,
                    profileSerial = pending.profileSerial,
                    insertAt = pending.origin.insertAt,
                    defaultHeightDp = pending.defaultHeightDp,
                    ownerKey = pending.origin.ownerKey
                )
            )
        }
        if (placed.isFailure) {
            failAdd(id = pending.id, returnToSettings = pending.origin.returnToSettings)
            return null
        }
        val added: AddedAppWidget = placed.getOrThrow()
        addFlow.value = AddWidgetFlowState.Idle
        clearPending()
        if (!pending.origin.returnToSettings && pending.origin.ownerKey == null) {
            eventChannel.send(WidgetEvent.ScrollToPlace(placeId = added.placeId))
        }
        returnToSettingsIfRequested(origin = pending.origin)
        return (pending.origin.insertAt as? InsertPosition.IntoStack)?.placeId
    }

    private suspend fun cancelPending(pending: PendingWidget) {
        appWidgetHost.deleteId(id = pending.id)
        addFlow.value = AddWidgetFlowState.Idle
        clearPending()
        returnToSettingsIfRequested(origin = pending.origin)
    }

    private suspend fun returnToSettingsIfRequested(origin: AddWidgetOrigin) {
        if (origin.returnToSettings) {
            eventChannel.send(WidgetEvent.ReturnToSettings)
        }
    }

    private suspend fun failAdd(id: AppWidgetId, returnToSettings: Boolean = false) {
        appWidgetHost.deleteId(id = id)
        addFlow.value = AddWidgetFlowState.Idle
        clearPending()
        eventChannel.send(WidgetEvent.ShowMessage(message = CoreUiR.string.couldnt_add_widget))
        if (returnToSettings) {
            eventChannel.send(WidgetEvent.ReturnToSettings)
        }
    }

    private suspend fun persistPending(pending: PendingWidget, phase: String) {
        savedStateHandle[PENDING_APP_WIDGET_ID_KEY] = pending.id.value
        savedStateHandle[PENDING_PROVIDER_KEY] = pending.provider.key
        savedStateHandle[PENDING_PROFILE_SERIAL_KEY] = pending.profileSerial
        savedStateHandle[PENDING_NEEDS_CONFIGURATION_KEY] = pending.needsConfiguration
        savedStateHandle[PENDING_DEFAULT_HEIGHT_KEY] = pending.defaultHeightDp
        savedStateHandle[PENDING_INSERT_BELOW_PLACE_ID_KEY] = (pending.origin.insertAt as? InsertPosition.Below)?.placeId
        savedStateHandle[PENDING_INSERT_STACK_PLACE_ID_KEY] = (pending.origin.insertAt as? InsertPosition.IntoStack)?.placeId
        savedStateHandle[PENDING_RETURN_TO_SETTINGS_KEY] = pending.origin.returnToSettings
        savedStateHandle[PENDING_OWNER_KEY] = pending.origin.ownerKey
        savedStateHandle[PENDING_PHASE_KEY] = phase
        pendingIds.set(setOf(pending.id))
    }

    private suspend fun clearPending() {
        savedStateHandle.remove<Int>(PENDING_APP_WIDGET_ID_KEY)
        savedStateHandle.remove<String>(PENDING_PROVIDER_KEY)
        savedStateHandle.remove<Long>(PENDING_PROFILE_SERIAL_KEY)
        savedStateHandle.remove<Boolean>(PENDING_NEEDS_CONFIGURATION_KEY)
        savedStateHandle.remove<Int>(PENDING_DEFAULT_HEIGHT_KEY)
        savedStateHandle.remove<Long>(PENDING_INSERT_BELOW_PLACE_ID_KEY)
        savedStateHandle.remove<Long>(PENDING_INSERT_STACK_PLACE_ID_KEY)
        savedStateHandle.remove<Boolean>(PENDING_RETURN_TO_SETTINGS_KEY)
        savedStateHandle.remove<String>(PENDING_OWNER_KEY)
        savedStateHandle.remove<String>(PENDING_PHASE_KEY)
        pendingIds.set(emptySet())
    }

    private fun pickerGroups(providers: List<WidgetProviderInfo>, query: String, origin: AddWidgetOrigin): List<PickerGroup> {
        val filter: String = query.trim()
        val collator: Collator = Collator.getInstance()
        val groups: List<PickerGroup> = providers
            .filter { provider ->
                filter.isEmpty() ||
                    provider.appLabel.contains(filter, ignoreCase = true) ||
                    provider.label.contains(filter, ignoreCase = true)
            }
            .groupBy { provider -> provider.appPackageName to provider.profileSerial }
            .map { (_, widgets) ->
                val first: WidgetProviderInfo = widgets.first()
                PickerGroup(
                    appPackageName = first.appPackageName,
                    appLabel = first.appLabel,
                    appIcon = first.appIcon,
                    isWorkProfile = first.isWorkProfile,
                    widgets = widgets.sortedWith(compareBy(collator) { widget -> widget.label })
                )
            }
            .sortedWith(compareBy(collator) { group -> group.appLabel })
        val (suggested: List<PickerGroup>, others: List<PickerGroup>) = groups.partition { group ->
            group.appPackageName == origin.suggestedPackageName
        }
        return suggested.map { group -> group.copy(isSuggested = true) } + others
    }

    internal companion object {
        const val PENDING_APP_WIDGET_ID_KEY: String = "pendingAppWidgetId"
        const val PENDING_PROVIDER_KEY: String = "pendingAppWidgetProvider"
        const val PENDING_PROFILE_SERIAL_KEY: String = "pendingAppWidgetProfileSerial"
        const val PENDING_NEEDS_CONFIGURATION_KEY: String = "pendingAppWidgetNeedsConfiguration"
        const val PENDING_DEFAULT_HEIGHT_KEY: String = "pendingAppWidgetDefaultHeight"
        const val PENDING_INSERT_BELOW_PLACE_ID_KEY: String = "pendingAppWidgetInsertBelowPlaceId"
        const val PENDING_INSERT_STACK_PLACE_ID_KEY: String = "pendingAppWidgetInsertStackPlaceId"
        const val PENDING_RETURN_TO_SETTINGS_KEY: String = "pendingAppWidgetReturnToSettings"
        const val PENDING_PHASE_KEY: String = "pendingAppWidgetPhase"
        const val PENDING_OWNER_KEY: String = "pendingAppWidgetOwner"
    }
}
