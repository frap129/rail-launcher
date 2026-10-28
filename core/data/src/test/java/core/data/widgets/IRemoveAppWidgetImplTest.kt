package core.data.widgets

import core.data.testing.createAppWidgetHostMock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IRemoveAppWidgetImplTest {
    @Test
    fun `removes the stored widget, frees its system id and returns the id`() = runTest {
        val mockRepository: IWidgetAreaRepository = mockk()
        coEvery { mockRepository.removeWidget(widgetId = 2L) } returns AppWidgetId(9)
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock()
        val removeAppWidget: IRemoveAppWidget = IRemoveAppWidgetImpl(repository = mockRepository, host = mockHost)

        val actualRemoved: AppWidgetId = removeAppWidget(widgetId = 2L)!!

        assertEquals(AppWidgetId(9), actualRemoved)
        verify(exactly = 1) { mockHost.deleteId(id = AppWidgetId(9)) }
        coVerify(exactly = 1) { mockRepository.removeWidget(widgetId = 2L) }
    }

    @Test
    fun `returns null and keeps the system id when the widget is gone`() = runTest {
        val mockRepository: IWidgetAreaRepository = mockk()
        coEvery { mockRepository.removeWidget(widgetId = 99L) } returns null
        val mockHost: IAppWidgetHostSource = createAppWidgetHostMock()
        val removeAppWidget: IRemoveAppWidget = IRemoveAppWidgetImpl(repository = mockRepository, host = mockHost)

        val actualRemoved: AppWidgetId? = removeAppWidget(widgetId = 99L)

        assertNull(actualRemoved)
        verify(exactly = 0) { mockHost.deleteId(any()) }
        coVerify(exactly = 1) { mockRepository.removeWidget(widgetId = 99L) }
    }
}
