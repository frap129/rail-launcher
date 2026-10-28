package core.data.widgets

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import core.data.room.AppDatabase
import core.data.room.dao.WidgetAreaDao
import core.data.testing.InMemoryDatabaseTest
import core.data.testing.createAppWidgetHostMock
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class WidgetAreaSeedCallbackTest : InMemoryDatabaseTest() {
    override fun configureDatabaseBuilder(builder: RoomDatabase.Builder<AppDatabase>): RoomDatabase.Builder<AppDatabase> =
        builder.addCallback(WidgetAreaSeedCallback())

    @Test
    fun `fresh database seeds one place with a Header`() = databaseTest {
        assertSingleSeededHeader(database)
    }

    @Test
    fun `destructive migration seeds one place with a Header`() = databaseTest {
        val inputContext: Context = RuntimeEnvironment.getApplication()
        val inputDatabaseName: String = "widget-area-destructive-migration.db"
        inputContext.deleteDatabase(inputDatabaseName)
        inputContext.openOrCreateDatabase(inputDatabaseName, Context.MODE_PRIVATE, null).use { legacyDatabase ->
            legacyDatabase.version = 4
        }

        val actualDatabase: AppDatabase = Room.databaseBuilder(inputContext, AppDatabase::class.java, inputDatabaseName)
            .configureForTest()
            .addCallback(WidgetAreaSeedCallback())
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()
        try {
            assertSingleSeededHeader(actualDatabase)
        } finally {
            actualDatabase.close()
            inputContext.deleteDatabase(inputDatabaseName)
        }
    }

    private suspend fun assertSingleSeededHeader(database: AppDatabase) {
        val dao: WidgetAreaDao = database.getWidgetAreaDao()
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock()
        val reconciler: IWidgetLifecycleReconcilerImpl = IWidgetLifecycleReconcilerImpl(dao = dao, host = mockHost)
        reconciler.reconcileAtStartup(pendingIds = emptySet())
        val actualPlaces: List<WidgetPlace> = IWidgetAreaRepositoryImpl(dao, mockHost, reconciler).places.first()
        assertEquals(1, actualPlaces.size)
        val actualWidgets: List<PlacedWidget> = actualPlaces.single().widgets
        assertEquals(1, actualWidgets.size)
        assertTrue(actualWidgets.single() is PlacedWidget.Header)
    }
}
