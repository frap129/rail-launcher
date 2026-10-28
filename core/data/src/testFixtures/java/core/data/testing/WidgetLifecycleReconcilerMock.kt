package core.data.testing

import core.data.widgets.AppWidgetId
import core.data.widgets.IWidgetLifecycleReconciler
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A MockK [IWidgetLifecycleReconciler] emitting [removedIds]. */
fun createWidgetLifecycleReconcilerMock(removedIds: Flow<AppWidgetId> = emptyFlow()): IWidgetLifecycleReconciler {
    val mockWidgetLifecycleReconciler: IWidgetLifecycleReconciler = mockk()
    every { mockWidgetLifecycleReconciler.removedIds } returns removedIds
    return mockWidgetLifecycleReconciler
}
