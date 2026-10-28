package core.data.testing

import core.data.widgets.AppWidgetId
import core.data.widgets.IAppWidgetHostSource
import core.data.widgets.WidgetAvailability
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A MockK [IAppWidgetHostSource] backed by [boundIdsProvider], [availabilityProvider] and [providerChangesFlow]. */
fun createAppWidgetHostMock(
    boundIdsProvider: () -> Set<AppWidgetId> = { emptySet() },
    availabilityProvider: (AppWidgetId) -> WidgetAvailability = { WidgetAvailability.Available },
    providerChangesFlow: Flow<Unit> = emptyFlow()
): IAppWidgetHostSource {
    val mockAppWidgetHost: IAppWidgetHostSource = mockk()
    every { mockAppWidgetHost.boundIds() } answers { boundIdsProvider() }
    every { mockAppWidgetHost.deleteId(any()) } just Runs
    every { mockAppWidgetHost.availability(any(), any(), any()) } answers {
        // MockK passes this value class argument as its underlying Int.
        availabilityProvider(AppWidgetId(firstArg<Int>()))
    }
    every { mockAppWidgetHost.providerInfo(any()) } returns null
    every { mockAppWidgetHost.providerChanges } returns providerChangesFlow
    return mockAppWidgetHost
}
