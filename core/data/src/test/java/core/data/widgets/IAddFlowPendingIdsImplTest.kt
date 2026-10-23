package core.data.widgets

import core.data.room.AppDatabase
import core.data.testing.TEST_SDK
import core.data.testing.createInMemoryDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IAddFlowPendingIdsImplTest {
    @Test
    fun `replaces the stored ids atomically`() = runTest {
        val mockDatabase: AppDatabase = createInMemoryDatabase()
        try {
            val actualPendingIds: IAddFlowPendingIds = IAddFlowPendingIdsImpl(mockDatabase.getPendingAppWidgetDao())

            actualPendingIds.set(setOf(AppWidgetId(1), AppWidgetId(2)))
            assertEquals(setOf(AppWidgetId(1), AppWidgetId(2)), actualPendingIds.get())

            actualPendingIds.set(setOf(AppWidgetId(2)))
            assertEquals(setOf(AppWidgetId(2)), actualPendingIds.get())

            actualPendingIds.set(emptySet())
            assertTrue(actualPendingIds.get().isEmpty())
        } finally {
            mockDatabase.close()
        }
    }
}
