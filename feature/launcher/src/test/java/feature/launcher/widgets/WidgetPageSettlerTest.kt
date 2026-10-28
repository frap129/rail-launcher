package feature.launcher.widgets

import core.data.testing.createWidgetAreaRepositoryMock
import core.data.widgets.IWidgetAreaRepository
import io.mockk.coVerify
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WidgetPageSettlerTest {
    private val mockRepository: IWidgetAreaRepository = createWidgetAreaRepositoryMock()

    @Test
    fun `stores only the last page settled within the debounce`() = runTest {
        val settler: WidgetPageSettler = WidgetPageSettler(scope = backgroundScope, repository = mockRepository)

        settler.onPageSettled(placeId = 1L, page = 1)
        advanceTimeBy(100)
        settler.onPageSettled(placeId = 1L, page = 2)
        advanceTimeBy(300)
        runCurrent()

        assertPageStored(page = 2)
    }

    @Test
    fun `stores a page that settles back where it started`() = runTest {
        val settler: WidgetPageSettler = WidgetPageSettler(scope = backgroundScope, repository = mockRepository)

        settler.onPageSettled(placeId = 1L, page = 1)
        advanceTimeBy(100)
        settler.onPageSettled(placeId = 1L, page = 0)
        advanceTimeBy(300)
        runCurrent()

        assertPageStored(page = 0)
    }

    /** Verifies the settler stored [page] for place 1 and no other page. */
    private fun assertPageStored(page: Int) {
        coVerify(exactly = 1) { mockRepository.setCurrentPage(placeId = 1L, page = page) }
        coVerify(exactly = 1) { mockRepository.setCurrentPage(any(), any()) }
    }
}
