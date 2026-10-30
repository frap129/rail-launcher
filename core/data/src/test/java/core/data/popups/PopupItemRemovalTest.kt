package core.data.popups

import core.data.apps.model.App
import core.data.testing.TEST_SDK
import core.data.testing.TimberLogRecorder
import core.data.testing.createPopupRepositoryMock
import core.data.testing.createPrivateApp
import io.mockk.coEvery
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class PopupItemRemovalTest {
    @Test
    fun `logs a private item removal failure without its package or key`() = runTest {
        val inputPrivateApp: App = createPrivateApp()
        val mockPopupRepository: IPopupRepository = createPopupRepositoryMock()
        coEvery {
            mockPopupRepository.removeItem(any(), any())
        } throws RuntimeException("Could not remove the item")
        val logRecorder: TimberLogRecorder = TimberLogRecorder()

        logRecorder.recordSuspending {
            removeAddedItem(
                popupRepository = mockPopupRepository,
                ownerKey = inputPrivateApp.key,
                itemKey = inputPrivateApp.key
            )
            runCurrent()
        }

        logRecorder.assertLoggedWithout(inputPrivateApp.key, inputPrivateApp.packageName)
    }
}
