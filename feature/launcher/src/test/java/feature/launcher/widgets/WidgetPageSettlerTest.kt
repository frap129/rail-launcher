package feature.launcher.widgets

import core.data.testing.FakeWidgetAreaRepository
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPageSettlerTest {
    @Test
    fun `stores only the last page settled within the debounce`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository()
        val settler: WidgetPageSettler = WidgetPageSettler(scope = backgroundScope, repository = mockRepository)

        settler.onPageSettled(placeId = 1L, page = 1)
        advanceTimeBy(100)
        settler.onPageSettled(placeId = 1L, page = 2)
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf(1L to 2), mockRepository.currentPageUpdates)
    }

    @Test
    fun `stores a page that settles back where it started`() = runTest {
        val mockRepository: FakeWidgetAreaRepository = FakeWidgetAreaRepository()
        val settler: WidgetPageSettler = WidgetPageSettler(scope = backgroundScope, repository = mockRepository)

        settler.onPageSettled(placeId = 1L, page = 1)
        advanceTimeBy(100)
        settler.onPageSettled(placeId = 1L, page = 0)
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf(1L to 0), mockRepository.currentPageUpdates)
    }
}
