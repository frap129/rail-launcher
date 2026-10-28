package feature.launcher.widgets

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/** A MockK [IWidgetResizeController] over [resizeState], answering the scripted results. */
fun createWidgetResizeControllerMock(
    resizeState: MutableStateFlow<ResizeState?> = MutableStateFlow(null),
    startResult: Boolean = true,
    finish: ResizeFinish? = null,
    report: WidgetSizeReport? = null,
    stackGrownReport: WidgetSizeReport? = null
): IWidgetResizeController {
    val homeHeightDp: MutableStateFlow<Int?> = MutableStateFlow(null)
    val mockResizeController: IWidgetResizeController = mockk()
    every { mockResizeController.resizeState } returns resizeState
    every { mockResizeController.homeHeightDp } returns homeHeightDp
    every { mockResizeController.setHomeHeight(any()) } answers { homeHeightDp.value = firstArg() }
    every { mockResizeController.setWidgetAreaWidth(any()) } just Runs
    coEvery { mockResizeController.startResize(any(), any(), any()) } returns startResult
    coEvery { mockResizeController.startResizeForWidget(any(), any(), any()) } returns startResult
    every { mockResizeController.dragResize(any()) } just Runs
    every { mockResizeController.resetResize() } just Runs
    every { mockResizeController.finishResize() } answers {
        if (resizeState.value == null) {
            null
        } else {
            resizeState.value = null
            finish
        }
    }
    coEvery { mockResizeController.persistResize(any()) } returns report
    every { mockResizeController.cancelResize() } answers { resizeState.value = null }
    every { mockResizeController.onPlacesChanged(any(), any()) } just Runs
    coEvery { mockResizeController.onStackGrown(any(), any(), any()) } returns stackGrownReport
    coEvery { mockResizeController.clampStackHeight(any(), any(), any()) } just Runs
    return mockResizeController
}
