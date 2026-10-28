package core.data.testing

import core.data.widgets.AppWidgetId
import core.data.widgets.IRemoveAppWidget
import io.mockk.coEvery
import io.mockk.mockk

/** A MockK [IRemoveAppWidget] returning [result] for every removed Widget. */
fun createRemoveAppWidgetMock(result: AppWidgetId? = AppWidgetId(9)): IRemoveAppWidget {
    val mockRemoveAppWidget: IRemoveAppWidget = mockk()
    coEvery { mockRemoveAppWidget.invoke(any()) } returns result
    return mockRemoveAppWidget
}
