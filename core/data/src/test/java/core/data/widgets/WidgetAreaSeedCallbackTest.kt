package core.data.widgets

import android.content.Context
import androidx.room.Room
import core.data.room.AppDatabase
import core.data.room.dao.WidgetAreaDao
import core.data.testing.FakeAppWidgetHostSource
import core.data.testing.TEST_SDK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class WidgetAreaSeedCallbackTest {
    @Test
    fun `fresh database seeds one place with a Header`() = runTest {
        val database: AppDatabase = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .addCallback(WidgetAreaSeedCallback())
            .allowMainThreadQueries()
            .build()
        try {
            assertSingleSeededHeader(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun `destructive migration seeds one place with a Header`() = runTest {
        val context: Context = RuntimeEnvironment.getApplication()
        val databaseName: String = "widget-area-destructive-migration.db"
        context.deleteDatabase(databaseName)
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { legacyDatabase ->
            legacyDatabase.version = 3
        }

        val database: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addCallback(WidgetAreaSeedCallback())
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
        try {
            assertSingleSeededHeader(database)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun assertSingleSeededHeader(database: AppDatabase) {
        val dao: WidgetAreaDao = database.getWidgetAreaDao()
        val host: FakeAppWidgetHostSource = FakeAppWidgetHostSource()
        val reconciler: WidgetLifecycleReconciler = WidgetLifecycleReconciler(dao = dao, host = host)
        reconciler.reconcileAtStartup(pendingIds = emptySet())
        val places: List<WidgetPlace> = IWidgetAreaRepositoryImpl(dao, host, reconciler).places.first()
        assertEquals(1, places.size)
        val widgets: List<PlacedWidget> = places.single().widgets
        assertEquals(1, widgets.size)
        assertTrue(widgets.single() is PlacedWidget.Header)
    }
}
