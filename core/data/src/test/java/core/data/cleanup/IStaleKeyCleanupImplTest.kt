package core.data.cleanup

import core.data.shortcuts.ShortcutKey
import core.data.shortcuts.ShortcutResolution
import core.data.testing.FIRST_SHORTCUT_ID
import core.data.testing.PERSONAL_USER_ID
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createLaunchableRepositoryMock
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createRailLaunchable
import core.data.testing.createShortcutSourceMock
import core.data.testing.createUserHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class IStaleKeyCleanupImplTest {
    @Test
    fun `does not delete anything before the Launchables load`() = runTest {
        val mockClassifier: FakeClassifier = FakeClassifier()
        val mockRow: RecordingRow = RecordingRow(keys = MutableStateFlow(setOf("key")))
        IStaleKeyCleanupImpl(classifier = mockClassifier, scope = backgroundScope).register(mockRow)
        finishCleanup()

        assertTrue(mockRow.deletedKeys.isEmpty())

        mockClassifier.states.value = mapOf("key" to StoredKeyState.Stale)
        finishCleanup()

        assertEquals(listOf(setOf("key")), mockRow.deletedKeys)
    }

    @Test
    fun `keeps present, kept and unresolved keys`() = runTest {
        val mockClassifier: FakeClassifier = FakeClassifier(
            states = MutableStateFlow(
                mapOf(
                    "present" to StoredKeyState.Present(launchable = createRailLaunchable(name = "Present")),
                    "kept" to StoredKeyState.Kept,
                    "unresolved" to StoredKeyState.Unresolved
                )
            )
        )
        val mockRow: RecordingRow = RecordingRow(keys = MutableStateFlow(setOf("present", "kept", "unresolved")))
        IStaleKeyCleanupImpl(classifier = mockClassifier, scope = backgroundScope).register(mockRow)
        finishCleanup()

        assertTrue(mockRow.deletedKeys.isEmpty())
    }

    @Test
    fun `deletes only the stale keys`() = runTest {
        val mockClassifier: FakeClassifier = FakeClassifier(
            states = MutableStateFlow(
                mapOf(
                    "present" to StoredKeyState.Present(launchable = createRailLaunchable(name = "Present")),
                    "kept" to StoredKeyState.Kept,
                    "unresolved" to StoredKeyState.Unresolved,
                    "stale" to StoredKeyState.Stale
                )
            )
        )
        val mockRow: RecordingRow = RecordingRow(keys = MutableStateFlow(setOf("present", "kept", "unresolved", "stale")))
        IStaleKeyCleanupImpl(classifier = mockClassifier, scope = backgroundScope).register(mockRow)
        finishCleanup()

        assertEquals(listOf(setOf("stale")), mockRow.deletedKeys)
    }

    @Test
    fun `keeps deleting the other rows after one delete fails`() = runTest {
        val mockClassifier: FakeClassifier = FakeClassifier(states = MutableStateFlow(mapOf("stale" to StoredKeyState.Stale)))
        val mockFailingRow: RecordingRow = RecordingRow(
            keys = MutableStateFlow(setOf("stale")),
            onDelete = { throw IllegalStateException("Could not delete") }
        )
        val mockRemainingRow: RecordingRow = RecordingRow(keys = MutableStateFlow(setOf("stale")))
        val cleanup: IStaleKeyCleanup = IStaleKeyCleanupImpl(classifier = mockClassifier, scope = backgroundScope)
        cleanup.register(mockFailingRow)
        cleanup.register(mockRemainingRow)
        finishCleanup()

        assertEquals(listOf(setOf("stale")), mockRemainingRow.deletedKeys)
    }

    @Test
    fun `cancellation stops the cleanup job`() = runTest {
        val mockClassifier: FakeClassifier = FakeClassifier(states = MutableStateFlow(mapOf("key" to StoredKeyState.Stale)))
        val mockRow: RecordingRow = RecordingRow(
            keys = MutableStateFlow(setOf("key")),
            onDelete = { throw CancellationException("Cancelled") }
        )
        IStaleKeyCleanupImpl(classifier = mockClassifier, scope = backgroundScope).register(mockRow)
        finishCleanup()

        mockClassifier.states.value = null
        finishCleanup()
        mockClassifier.states.value = mapOf("key" to StoredKeyState.Stale)
        finishCleanup()

        assertEquals(1, mockRow.deletedKeys.size)
    }

    @Test
    fun `runs the cleanup once with several collectors`() = runTest {
        val inputKey = ShortcutKey(
            packageName = "com.example.app",
            id = FIRST_SHORTCUT_ID,
            profile = createUserHandle(userId = PERSONAL_USER_ID)
        )
        val mockResolution: MutableStateFlow<ShortcutResolution> = MutableStateFlow(ShortcutResolution())
        val classifier: IStoredKeyClassifier = IStoredKeyClassifierImpl(
            launchableRepository = createLaunchableRepositoryMock(
                MutableStateFlow(createLoadedLaunchableState(launchables = listOf(createApp(packageName = "com.example.app"))))
            ),
            shortcutSource = createShortcutSourceMock(resolution = mockResolution)
        )
        val mockRow: RecordingRow = RecordingRow(keys = MutableStateFlow(setOf(inputKey.format())))
        backgroundScope.launch { classifier.observe(mockRow.keys).collect() }
        backgroundScope.launch { classifier.observe(mockRow.keys).collect() }
        IStaleKeyCleanupImpl(classifier = classifier, scope = backgroundScope).register(mockRow)
        mockResolution.value = ShortcutResolution(goneKeys = setOf(inputKey))
        finishCleanup()

        assertEquals(listOf(setOf(inputKey.format())), mockRow.deletedKeys)
    }

    private fun TestScope.finishCleanup() {
        advanceUntilIdle()
        runCurrent()
    }
}

private class FakeClassifier(val states: MutableStateFlow<Map<String, StoredKeyState>?> = MutableStateFlow(null)) : IStoredKeyClassifier {
    override fun observe(keys: Flow<Set<String>>): Flow<Map<String, StoredKeyState>?> = states
}

private class RecordingRow(override val keys: Flow<Set<String>>, private val onDelete: suspend (Set<String>) -> Unit = {}) : IStaleKeyRow {
    val deletedKeys: MutableList<Set<String>> = mutableListOf()

    override suspend fun delete(keys: Set<String>) {
        deletedKeys += keys
        onDelete(keys)
    }
}
