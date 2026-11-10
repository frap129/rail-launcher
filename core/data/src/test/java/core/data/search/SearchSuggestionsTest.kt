package core.data.search

import core.data.apps.model.App
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createLoadedLaunchableState
import core.util.MILLIS_PER_DAY
import core.util.MILLIS_PER_MINUTE
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val NOW_EPOCH_MS: Long = 1_791_720_000_000L
private const val THREE_DAYS_MS: Long = 3L * MILLIS_PER_DAY
private const val FIFTEEN_MINUTES_MS: Long = 15L * MILLIS_PER_MINUTE

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class SearchSuggestionsTest {
    @Test
    fun `includes an App installed exactly 3 days ago and leaves out an older one`() {
        val inputBoundary: App = createApp(
            name = "Boundary",
            packageName = "com.example.boundary",
            firstInstallTime = NOW_EPOCH_MS - THREE_DAYS_MS
        )
        val inputOlder: App = createApp(
            name = "Older",
            packageName = "com.example.older",
            firstInstallTime = NOW_EPOCH_MS - THREE_DAYS_MS - 1
        )
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputOlder, inputBoundary))

        val actualSuggestions: List<Launchable> = inputState.searchSuggestions(history = emptyList(), nowEpochMs = NOW_EPOCH_MS)

        assertEquals(listOf<Launchable>(inputBoundary), actualSuggestions)
    }

    @Test
    fun `includes only the 5 most recently installed Apps`() {
        val inputAppByIndex: Map<Int, App> = (1..6).associateWith { index ->
            createApp(
                name = "App $index",
                packageName = "com.example.app$index",
                firstInstallTime = NOW_EPOCH_MS - index * MILLIS_PER_MINUTE
            )
        }
        val inputApps: List<App> = listOf(
            inputAppByIndex.getValue(4),
            inputAppByIndex.getValue(2),
            inputAppByIndex.getValue(6),
            inputAppByIndex.getValue(1),
            inputAppByIndex.getValue(5),
            inputAppByIndex.getValue(3)
        )
        val expectedApps: List<App> = (1..5).map { index -> inputAppByIndex.getValue(index) }
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputApps)

        val actualSuggestions: List<Launchable> = inputState.searchSuggestions(history = emptyList(), nowEpochMs = NOW_EPOCH_MS)

        assertEquals(expectedApps, actualSuggestions)
    }

    @Test
    fun `includes the Launchable opened exactly 15 minutes ago`() {
        val inputApp: App = createApp(name = "Boundary", packageName = "com.example.boundary")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputApp))
        val inputHistory: List<SearchHistoryRecord> = listOf(
            historyRecord(
                key = inputApp.key,
                launchCount = 1,
                lastLaunchedAt = NOW_EPOCH_MS - FIFTEEN_MINUTES_MS
            )
        )

        val actualSuggestions: List<Launchable> = inputState.searchSuggestions(history = inputHistory, nowEpochMs = NOW_EPOCH_MS)

        assertEquals(listOf<Launchable>(inputApp), actualSuggestions)
    }

    @Test
    fun `leaves out a Launchable opened more than 15 minutes ago`() {
        val inputApp: App = createApp(name = "Older", packageName = "com.example.older")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputApp))
        val inputHistory: List<SearchHistoryRecord> = listOf(
            historyRecord(
                key = inputApp.key,
                launchCount = 0,
                lastLaunchedAt = NOW_EPOCH_MS - FIFTEEN_MINUTES_MS - 1
            )
        )

        val actualSuggestions: List<Launchable> = inputState.searchSuggestions(history = inputHistory, nowEpochMs = NOW_EPOCH_MS)

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `ranks the most frequently opened Launchables first and breaks ties in App List order`() {
        val inputAlpha: App = createApp(name = "Alpha", packageName = "com.example.alpha")
        val inputBeta: App = createApp(name = "Beta", packageName = "com.example.beta")
        val inputGamma: App = createApp(name = "Gamma", packageName = "com.example.gamma")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputAlpha, inputBeta, inputGamma))
        val inputHistory: List<SearchHistoryRecord> = listOf(
            historyRecord(key = inputBeta.key, launchCount = 3),
            historyRecord(key = inputAlpha.key, launchCount = 3),
            historyRecord(key = inputGamma.key, launchCount = 5)
        )

        val actualSuggestions: List<Launchable> = inputState.searchSuggestions(history = inputHistory, nowEpochMs = NOW_EPOCH_MS)

        assertEquals(listOf(inputGamma, inputAlpha, inputBeta), actualSuggestions)
    }

    @Test
    fun `includes exactly 5 frequently opened Launchables`() {
        val inputApps: List<App> = (1..5).map { index -> createApp(name = "App $index", packageName = "com.example.app$index") }
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputApps)
        val inputHistory: List<SearchHistoryRecord> = inputApps.map { app -> historyRecord(key = app.key, launchCount = 1) }

        val actualSuggestions: List<Launchable> = inputState.searchSuggestions(history = inputHistory, nowEpochMs = NOW_EPOCH_MS)

        assertEquals(inputApps, actualSuggestions)
    }

    @Test
    fun `leaves out a sixth frequently opened Launchable`() {
        val inputApps: List<App> = (1..6).map { index -> createApp(name = "App $index", packageName = "com.example.app$index") }
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputApps)
        val inputHistory: List<SearchHistoryRecord> = inputApps.mapIndexed { index, app ->
            historyRecord(key = app.key, launchCount = inputApps.size - index)
        }

        val actualSuggestions: List<Launchable> = inputState.searchSuggestions(history = inputHistory, nowEpochMs = NOW_EPOCH_MS)

        assertEquals(inputApps.take(5), actualSuggestions)
    }

    @Test
    fun `leaves out a removed recently installed App`() {
        val inputApp: App = createApp(
            name = "Recent",
            packageName = "com.example.recent",
            firstInstallTime = NOW_EPOCH_MS - MILLIS_PER_MINUTE
        )
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputApp))
        val inputHistory: List<SearchHistoryRecord> = listOf(
            historyRecord(key = inputApp.key, launchCount = 1, lastLaunchedAt = NOW_EPOCH_MS, isRemoved = true)
        )

        val actualSuggestions: List<Launchable> = inputState.searchSuggestions(history = inputHistory, nowEpochMs = NOW_EPOCH_MS)

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `leaves out a removed frequently opened Launchable`() {
        val inputApp: App = createApp(name = "Frequent", packageName = "com.example.frequent")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputApp))
        val inputHistory: List<SearchHistoryRecord> = listOf(
            historyRecord(key = inputApp.key, launchCount = 5, isRemoved = true)
        )

        val actualSuggestions: List<Launchable> = inputState.searchSuggestions(history = inputHistory, nowEpochMs = NOW_EPOCH_MS)

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `leaves out a removed Launchable that was the last opened`() {
        val inputApp: App = createApp(name = "Last", packageName = "com.example.last")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputApp))
        val inputHistory: List<SearchHistoryRecord> = listOf(
            historyRecord(key = inputApp.key, launchCount = 1, lastLaunchedAt = NOW_EPOCH_MS, isRemoved = true)
        )

        val actualSuggestions: List<Launchable> = inputState.searchSuggestions(history = inputHistory, nowEpochMs = NOW_EPOCH_MS)

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `skips history keys that no Launchable resolves`() {
        val inputApp: App = createApp(name = "Present", packageName = "com.example.present")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputApp))
        val inputHistory: List<SearchHistoryRecord> = listOf(
            historyRecord(key = "com.example.gone/.MainActivity_UserHandle{0}", launchCount = 9, lastLaunchedAt = NOW_EPOCH_MS)
        )

        val actualSuggestions: List<Launchable> = inputState.searchSuggestions(history = inputHistory, nowEpochMs = NOW_EPOCH_MS)

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `lists a Launchable once under the first suggestion group`() {
        val inputApp: App = createApp(
            name = "Popular",
            packageName = "com.example.popular",
            firstInstallTime = NOW_EPOCH_MS - MILLIS_PER_MINUTE
        )
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputApp))
        val inputHistory: List<SearchHistoryRecord> = listOf(
            historyRecord(key = inputApp.key, launchCount = 9, lastLaunchedAt = NOW_EPOCH_MS - MILLIS_PER_MINUTE)
        )

        val actualSuggestions: List<Launchable> = inputState.searchSuggestions(history = inputHistory, nowEpochMs = NOW_EPOCH_MS)

        assertEquals(listOf<Launchable>(inputApp), actualSuggestions)
    }

    private fun historyRecord(key: String, launchCount: Int, lastLaunchedAt: Long = 0, isRemoved: Boolean = false): SearchHistoryRecord =
        SearchHistoryRecord(
            key = key,
            launchCount = launchCount,
            lastLaunchedAt = lastLaunchedAt,
            isRemoved = isRemoved
        )
}
