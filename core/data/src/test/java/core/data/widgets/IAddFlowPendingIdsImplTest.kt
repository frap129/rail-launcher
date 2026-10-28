package core.data.widgets

import core.data.testing.InMemoryDatabaseTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IAddFlowPendingIdsImplTest : InMemoryDatabaseTest() {
    @Test
    fun `replaces the stored ids atomically`() = databaseTest {
        val actualPendingIds: IAddFlowPendingIds = IAddFlowPendingIdsImpl(database.getPendingAppWidgetDao())

        actualPendingIds.set(setOf(AppWidgetId(1), AppWidgetId(2)))
        assertEquals(setOf(AppWidgetId(1), AppWidgetId(2)), actualPendingIds.get())

        actualPendingIds.set(setOf(AppWidgetId(2)))
        assertEquals(setOf(AppWidgetId(2)), actualPendingIds.get())

        actualPendingIds.set(emptySet())
        assertTrue(actualPendingIds.get().isEmpty())
    }
}
