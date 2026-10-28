package core.data.widgets

import core.data.room.dao.WidgetAreaDao
import core.data.testing.InMemoryDatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAreaDaoTest : InMemoryDatabaseTest() {
    @Test
    fun `removeWidget deletes the widget and its empty place`() = runTest {
        val dao: WidgetAreaDao = database.getWidgetAreaDao()
        val placeId: Long = dao.insertPlace(WidgetPlaceRecord(position = 0))
        val widgetId: Long = dao.insertWidget(
            WidgetRecord(
                placeId = placeId,
                pagePosition = 0,
                kind = WidgetKind.APP_WIDGET,
                appWidgetId = 42,
                provider = "com.example/ExampleWidget",
                profileSerial = 0L
            )
        )

        val actualRemovedAppWidgetId: Int? = dao.removeWidget(widgetId)

        assertEquals(42, actualRemovedAppWidgetId)
        assertTrue(dao.observeWidgets().first().isEmpty())
        assertTrue(dao.observePlaces().first().isEmpty())
    }

    @Test
    fun `setPlaceHeight round-trips a height and null`() = runTest {
        val dao: WidgetAreaDao = database.getWidgetAreaDao()
        val placeId: Long = dao.insertPlace(WidgetPlaceRecord(position = 0))
        dao.insertWidget(WidgetRecord(placeId = placeId, pagePosition = 0, kind = WidgetKind.APP_WIDGET, appWidgetId = 42))

        dao.setPlaceHeight(placeId = placeId, heightDp = 120)
        assertEquals(120, dao.observePlaces().first().single().heightDp)

        dao.setPlaceHeight(placeId = placeId, heightDp = null)
        assertNull(dao.observePlaces().first().single().heightDp)
    }
}
