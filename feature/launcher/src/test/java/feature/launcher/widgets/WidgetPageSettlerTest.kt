package feature.launcher.widgets

import core.data.testing.WidgetAreaRepositoryState
import core.data.testing.createWidgetAreaRepositoryMock
import core.data.widgets.IWidgetAreaRepository
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPageSettlerTest {
    @Test
    fun `stores only the last page settled within the debounce`() = runTest {
        val inputRepositoryState: WidgetAreaRepositoryState = WidgetAreaRepositoryState()
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(inputRepositoryState)
        val settler: WidgetPageSettler = WidgetPageSettler(scope = backgroundScope, repository = mockRepository)

        settler.onPageSettled(placeId = 1L, page = 1)
        advanceTimeBy(100)
        settler.onPageSettled(placeId = 1L, page = 2)
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf(1L to 2), inputRepositoryState.currentPageUpdates)
    }

    @Test
    fun `stores a page that settles back where it started`() = runTest {
        val inputRepositoryState: WidgetAreaRepositoryState = WidgetAreaRepositoryState()
        val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock(inputRepositoryState)
        val settler: WidgetPageSettler = WidgetPageSettler(scope = backgroundScope, repository = mockRepository)

        settler.onPageSettled(placeId = 1L, page = 1)
        advanceTimeBy(100)
        settler.onPageSettled(placeId = 1L, page = 0)
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf(1L to 0), inputRepositoryState.currentPageUpdates)
    }
}
