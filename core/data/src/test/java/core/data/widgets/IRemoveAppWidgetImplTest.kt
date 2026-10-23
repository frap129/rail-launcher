package core.data.widgets

import core.data.testing.FakeAppWidgetHostSource
import core.data.testing.FakeWidgetAreaRepository
import core.data.testing.appWidgetPlace
import core.data.testing.headerPlace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IRemoveAppWidgetImplTest {
    @Test
    fun `removes the stored widget, frees its system id and returns the id`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(
            listOf(headerPlace, appWidgetPlace(placeId = 2L, widgetId = 2L, appWidgetId = 9))
        )
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val removeAppWidget: IRemoveAppWidget = IRemoveAppWidgetImpl(repository = repository, host = host)

        val removed: AppWidgetId = removeAppWidget(widgetId = 2L)!!

        assertEquals(AppWidgetId(9), removed)
        assertEquals(listOf(AppWidgetId(9)), host.deletedIds)
        assertEquals(listOf(1L), repository.places.first().map { place -> place.id })
    }

    @Test
    fun `returns null and keeps the system id when the widget is gone`() = runTest {
        val repository: FakeWidgetAreaRepository = FakeWidgetAreaRepository(listOf(headerPlace))
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val removeAppWidget: IRemoveAppWidget = IRemoveAppWidgetImpl(repository = repository, host = host)

        val removed: AppWidgetId? = removeAppWidget(widgetId = 99L)

        assertNull(removed)
        assertEquals(emptyList<AppWidgetId>(), host.deletedIds)
        assertEquals(listOf(1L), repository.places.first().map { place -> place.id })
    }
}
