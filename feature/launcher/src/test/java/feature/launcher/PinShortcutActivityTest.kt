package feature.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Bundle
import app.cash.turbine.test
import core.data.shortcuts.IStandaloneShortcutRepository
import core.data.testing.MainDispatcherRule
import core.data.testing.TEST_SDK
import core.data.testing.createStandaloneShortcutRepositoryMock
import io.mockk.coEvery
import io.mockk.coVerify
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

private const val TEST_SHORTCUT_ID: String = "test-shortcut"
private const val TEST_SHORTCUT_LABEL: String = "Test shortcut"

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
@OptIn(ExperimentalCoroutinesApi::class)
class PinShortcutActivityTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val context: Application = RuntimeEnvironment.getApplication()
    private val notifier: IPinShortcutNotifier = IPinShortcutNotifierImpl(context)
    private val mockRepository: IStandaloneShortcutRepository = createStandaloneShortcutRepositoryMock()

    @Before
    fun setUp() {
        ShadowToast.reset()
    }

    @Test
    fun `an invalid Shortcut request shows the add error and finishes`() = runPinTest {
        val inputIntent: Intent = createIntent(
            request = createPinRequest(requestType = LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT, isValid = false).request
        )

        val actualActivity: PinShortcutActivity = Robolectric.buildActivity(PinShortcutActivity::class.java, inputIntent).create().get()

        assertTrue(actualActivity.isFinishing)
        assertEquals(context.getString(R.string.couldnt_add_shortcut), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a non-Shortcut request finishes without a message`() = runPinTest {
        val inputIntent: Intent = createIntent(
            request = createPinRequest(requestType = LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET, isValid = true).request
        )

        val actualActivity: PinShortcutActivity = Robolectric.buildActivity(PinShortcutActivity::class.java, inputIntent).create().get()

        assertTrue(actualActivity.isFinishing)
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `an add continues after the dialog is recreated and accepts the Shortcut`() = runPinTest {
        val pinAdd: StartedPinAdd = startAdd(acceptResult = true)

        pinAdd.viewModel.addState.test {
            assertEquals(PinShortcutAddState.Adding, awaitItem())
            pinAdd.controller.recreate()
            val recreatedActivity: PinShortcutActivity = pinAdd.controller.get()

            assertTrue(recreatedActivity.isFinishing)
            assertSame(pinAdd.viewModel, recreatedActivity.viewModel)
            pinAdd.releaseAdd.complete(Unit)
            runCurrent()

            assertEquals(PinShortcutAddState.Added, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            mockRepository.add(
                match { key -> key.packageName == context.packageName && key.id == TEST_SHORTCUT_ID },
                any()
            )
        }
        assertEquals(1, pinAdd.request.acceptCalls.get())
        assertNull(ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a failed add after the dialog is recreated shows the add error`() = runPinTest {
        val pinAdd: StartedPinAdd = startAdd(acceptResult = false)

        pinAdd.viewModel.addState.test {
            assertEquals(PinShortcutAddState.Adding, awaitItem())
            pinAdd.controller.recreate()
            pinAdd.releaseAdd.complete(Unit)
            runCurrent()

            assertEquals(PinShortcutAddState.Failed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, pinAdd.request.acceptCalls.get())
        assertEquals(context.getString(R.string.couldnt_add_shortcut), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a failed add still shows the add error after the dialog is gone`() = runPinTest {
        val pinAdd: StartedPinAdd = startAdd(acceptResult = false)

        pinAdd.viewModel.addState.test {
            assertEquals(PinShortcutAddState.Adding, awaitItem())
            pinAdd.controller.destroy()
            pinAdd.releaseAdd.complete(Unit)
            runCurrent()

            assertEquals(PinShortcutAddState.Failed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, pinAdd.request.acceptCalls.get())
        assertEquals(context.getString(R.string.couldnt_add_shortcut), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `a thrown add still shows the add error after the dialog is gone`() = runPinTest {
        val pinAdd: StartedPinAdd = startAdd(acceptResult = true, failure = IllegalStateException("Add failed"))

        pinAdd.viewModel.addState.test {
            assertEquals(PinShortcutAddState.Adding, awaitItem())
            pinAdd.controller.destroy()
            pinAdd.releaseAdd.complete(Unit)
            runCurrent()

            assertEquals(PinShortcutAddState.Failed, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(0, pinAdd.request.acceptCalls.get())
        assertEquals(context.getString(R.string.couldnt_add_shortcut), ShadowToast.getTextOfLatestToast())
    }

    private fun runPinTest(testBody: suspend TestScope.() -> Unit): TestResult = runTest {
        startKoin {
            androidContext(context)
            modules(
                module {
                    single<IPinShortcutNotifier> { notifier }
                    viewModel {
                        PinShortcutViewModel(
                            standaloneShortcutRepository = mockRepository,
                            scope = backgroundScope,
                            notifier = notifier
                        )
                    }
                }
            )
        }
        try {
            testBody()
        } finally {
            stopKoin()
        }
    }

    private fun TestScope.startAdd(acceptResult: Boolean, failure: Exception? = null): StartedPinAdd {
        val releaseAdd: CompletableDeferred<Unit> = CompletableDeferred()
        coEvery { mockRepository.add(any(), any()) } coAnswers {
            releaseAdd.await()
            failure?.let { exception -> throw exception }
            secondArg<suspend () -> Boolean>().invoke()
        }
        val inputRequest: TestPinRequest = createValidPinRequest(acceptResult = acceptResult)
        val controller: ActivityController<PinShortcutActivity> =
            Robolectric.buildActivity(PinShortcutActivity::class.java, createIntent(inputRequest.request)).create()
        val actualActivity: PinShortcutActivity = controller.get()
        val viewModel: PinShortcutViewModel = actualActivity.viewModel
        actualActivity.addShortcut()
        runCurrent()
        return StartedPinAdd(
            controller = controller,
            viewModel = viewModel,
            request = inputRequest,
            releaseAdd = releaseAdd
        )
    }

    private fun createIntent(request: LauncherApps.PinItemRequest): Intent =
        Intent(context, PinShortcutActivity::class.java).putExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST, request)

    private fun createValidPinRequest(acceptResult: Boolean): TestPinRequest = createPinRequest(
        requestType = LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT,
        isValid = true,
        shortcut = createShortcutInfo(),
        acceptResult = acceptResult
    )

    private fun createShortcutInfo(): ShortcutInfo = ShortcutInfo.Builder(context, TEST_SHORTCUT_ID)
        .setShortLabel(TEST_SHORTCUT_LABEL)
        .setIntent(Intent(Intent.ACTION_VIEW).setPackage(context.packageName))
        .build()

    private fun createPinRequest(
        requestType: Int,
        isValid: Boolean,
        shortcut: ShortcutInfo? = null,
        acceptResult: Boolean = false
    ): TestPinRequest {
        val acceptCalls: AtomicInteger = AtomicInteger()
        val innerType: Class<*> = Class.forName("android.content.pm.IPinItemRequest")
        val mockInner: Any = Proxy.newProxyInstance(innerType.classLoader, arrayOf(innerType)) { _, method, _ ->
            when (method.name) {
                "isValid" -> isValid

                "getExtras" -> Bundle()

                "getShortcutInfo" -> shortcut

                "accept" -> {
                    acceptCalls.incrementAndGet()
                    acceptResult
                }

                else -> null
            }
        }
        val constructor = LauncherApps.PinItemRequest::class.java.getConstructor(innerType, Int::class.javaPrimitiveType)
        val request: LauncherApps.PinItemRequest = constructor.newInstance(mockInner, requestType)
        return TestPinRequest(request = request, acceptCalls = acceptCalls)
    }
}

private data class TestPinRequest(val request: LauncherApps.PinItemRequest, val acceptCalls: AtomicInteger)

private data class StartedPinAdd(
    val controller: ActivityController<PinShortcutActivity>,
    val viewModel: PinShortcutViewModel,
    val request: TestPinRequest,
    val releaseAdd: CompletableDeferred<Unit>
)
