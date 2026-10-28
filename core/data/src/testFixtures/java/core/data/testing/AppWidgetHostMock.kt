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

/** The mutable host with the mock over it. */
class AppWidgetHostFixture(val state: AppWidgetHostState, val mockAppWidgetHost: IAppWidgetHostSource)

/** An [AppWidgetHostFixture] offering [providers], binding [bindResults] in turn and holding [providerInfos]. */
fun createAppWidgetHostFixture(
    providers: List<WidgetProviderInfo> = emptyList(),
    bindResults: List<Boolean> = emptyList(),
    providerInfos: Map<Int, WidgetProviderInfo> = emptyMap()
): AppWidgetHostFixture {
    val state: AppWidgetHostState = AppWidgetHostState(providers = providers, bindResults = bindResults)
    providerInfos.forEach { (appWidgetId, info) -> state.providerInfoById[AppWidgetId(appWidgetId)] = info }
    return AppWidgetHostFixture(state = state, mockAppWidgetHost = createAppWidgetHostMock(state))
}

/** A MockK [IAppWidgetHostSource], as a [createAppWidgetHostFixture] builds it. */
fun createAppWidgetHostMock(
    providers: List<WidgetProviderInfo>,
    bindResults: List<Boolean> = emptyList(),
    providerInfos: Map<Int, WidgetProviderInfo> = emptyMap()
): IAppWidgetHostSource =
    createAppWidgetHostFixture(providers = providers, bindResults = bindResults, providerInfos = providerInfos).mockAppWidgetHost

/** A MockK [IAppWidgetHostSource] offering [info] as provider [TEST_APP_WIDGET_ID], plus [providerInfos]. */
fun createAppWidgetHostMock(
    info: WidgetProviderInfo,
    providerInfos: Map<Int, WidgetProviderInfo> = emptyMap(),
    bindResults: List<Boolean> = emptyList()
): IAppWidgetHostSource = createAppWidgetHostMock(
    providers = listOf(info),
    bindResults = bindResults,
    providerInfos = mapOf(TEST_APP_WIDGET_ID to info) + providerInfos
)
