package core.data.testing

import android.os.UserHandle
import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.WidgetAvailability
import core.data.widgets.WidgetProviderInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/** The mutable host state and recorded calls behind a [createAppWidgetHostMock] source. */
class AppWidgetHostState(providers: List<WidgetProviderInfo> = emptyList(), bindResults: List<Boolean> = emptyList()) {
    val providers: MutableStateFlow<List<WidgetProviderInfo>> = MutableStateFlow(providers)
    val providerInfoById: MutableMap<AppWidgetId, WidgetProviderInfo> = mutableMapOf()
    val profileSerials: MutableMap<UserHandle, Long> = mutableMapOf()
    val allocatedIds: MutableList<AppWidgetId> = mutableListOf()
    val deletedIds: MutableList<AppWidgetId> = mutableListOf()
    private val scriptedBindResults: ArrayDeque<Boolean> = ArrayDeque(bindResults)
    private var nextId: Int = 1

    fun allocateId(): AppWidgetId = AppWidgetId(nextId++).also { id -> allocatedIds.add(id) }

    fun deleteId(id: AppWidgetId) {
        deletedIds.add(id)
    }

    fun bindIfAllowed(): Boolean = scriptedBindResults.removeFirstOrNull() ?: true
}

/** A MockK [IAppWidgetHostSource] over [state] and [providerChangesFlow]. */
fun createAppWidgetHostMock(
    state: AppWidgetHostState = AppWidgetHostState(),
    boundIdsProvider: () -> Set<AppWidgetId> = { emptySet() },
    availabilityProvider: (AppWidgetId) -> WidgetAvailability = { WidgetAvailability.Available },
    providerChangesFlow: Flow<Unit> = emptyFlow()
): IAppWidgetHostSource {
    val mockAppWidgetHost: IAppWidgetHostSource = mockk()
    every { mockAppWidgetHost.allocateId() } answers { state.allocateId() }
    every { mockAppWidgetHost.deleteId(any()) } answers { state.deleteId(id = AppWidgetId(firstArg<Int>())) }
    every { mockAppWidgetHost.bindIfAllowed(any()) } answers { state.bindIfAllowed() }
    every { mockAppWidgetHost.providers } returns state.providers
    every { mockAppWidgetHost.providerChanges } returns providerChangesFlow
    every { mockAppWidgetHost.boundIds() } answers { boundIdsProvider() }
    every { mockAppWidgetHost.availability(any(), any(), any()) } answers {
        // MockK passes this value class argument as its underlying Int.
        availabilityProvider(AppWidgetId(firstArg<Int>()))
    }
    every { mockAppWidgetHost.providerInfo(any()) } answers { state.providerInfoById[AppWidgetId(firstArg<Int>())] }
    every { mockAppWidgetHost.profileSerial(any()) } answers { state.profileSerials[firstArg()] }
    return mockAppWidgetHost
}
