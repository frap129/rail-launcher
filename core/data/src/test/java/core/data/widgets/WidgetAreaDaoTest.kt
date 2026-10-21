package core.data.widgets

import androidx.room.Room
import core.data.room.AppDatabase
import core.data.testing.TEST_SDK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class WidgetAreaDaoTest {
    @Test
    fun `addAppWidget inserts below the given place and shifts later places`() = runTest {
        val database: AppDatabase = inMemoryDatabase()
        try {
            val dao = database.getWidgetAreaDao()
            val headerPlaceId: Long = dao.insertPlace(WidgetPlaceRecord(position = 0))
            dao.insertWidget(WidgetRecord(placeId = headerPlaceId, pagePosition = 0, kind = WidgetKind.HEADER))
            val laterPlaceId: Long = dao.insertPlace(WidgetPlaceRecord(position = 1))
            dao.insertWidget(
                WidgetRecord(
                    placeId = laterPlaceId,
                    pagePosition = 0,
                    kind = WidgetKind.APP_WIDGET,
                    appWidgetId = 7,
                    provider = "com.example/Other",
                    profileSerial = 0L
                )
            )

            val insertedPlaceId: Long = dao.addAppWidget(
                appWidgetId = 41,
                provider = WidgetProvider(packageName = "com.example", className = "ExampleWidget"),
                profileSerial = 0L,
                belowPlaceId = headerPlaceId,
                defaultHeightDp = 48
            )

            val places: List<WidgetPlaceRecord> = dao.observePlaces().first()
            assertEquals(listOf(0, 1, 2), places.map { place -> place.position })
            assertEquals(listOf(headerPlaceId, insertedPlaceId, laterPlaceId), places.map { place -> place.id })
            val inserted: WidgetRecord = dao.observeWidgets().first().first { widget -> widget.appWidgetId == 41 }
            assertEquals(insertedPlaceId, inserted.placeId)
            assertTrue(inserted.kind == WidgetKind.APP_WIDGET)
            assertEquals(48, inserted.defaultHeightDp)
        } finally {
            database.close()
        }
    }

    @Test
    fun `removeWidget deletes the widget and its empty place`() = runTest {
        val database: AppDatabase = inMemoryDatabase()
        try {
            val dao = database.getWidgetAreaDao()
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

            val removedAppWidgetId: Int? = dao.removeWidget(widgetId)

            assertEquals(42, removedAppWidgetId)
            assertTrue(dao.observeWidgets().first().isEmpty())
            assertTrue(dao.observePlaces().first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun `setPlaceHeight round-trips a height and null`() = runTest {
        val database: AppDatabase = inMemoryDatabase()
        try {
            val dao = database.getWidgetAreaDao()
            val placeId: Long = dao.insertPlace(WidgetPlaceRecord(position = 0))
            dao.insertWidget(WidgetRecord(placeId = placeId, pagePosition = 0, kind = WidgetKind.APP_WIDGET, appWidgetId = 42))

            dao.setPlaceHeight(placeId = placeId, heightDp = 120)
            assertEquals(120, dao.observePlaces().first().single().heightDp)

            dao.setPlaceHeight(placeId = placeId, heightDp = null)
            assertNull(dao.observePlaces().first().single().heightDp)
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        AppDatabase::class.java
    )
        .allowMainThreadQueries()
        .build()
}
