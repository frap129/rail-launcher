package core.data.search

import core.data.apps.model.App
import core.data.folders.Folder
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launches.LaunchCountRecord
import core.data.testing.TEST_FOURTEEN_DAYS_MS
import core.data.testing.TEST_NOW_EPOCH_MS
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createLaunchCountRecord
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createPausedWorkApp
import core.data.testing.createRailLaunchable
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val FAVORITE_SCORE: Double = 10.0
private const val BELOW_OVER_TARGET_MARGIN: Double = 0.1
private const val TOP_SCORE: Double = FAVORITE_SCORE * HOME_OVER_TARGET_SCORE_FACTOR
private const val SUGGESTION_LAUNCH_COUNT: Int = HOME_SUGGESTION_MINIMUM_LAUNCH_COUNT

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [TEST_SDK])
class HomeSuggestionsTest {
    @Test
    fun `fills Home up to 8 rows with the Favorites`() {
        val inputFavorites: List<App> = createApps(prefix = "Favorite", count = HOME_ROW_TARGET - 3)
        val inputCandidates: List<App> = createApps(prefix = "Candidate", count = 5)
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputFavorites + inputCandidates)
        val inputCounts: List<LaunchCountRecord> =
            inputFavorites.map { app ->
                createLaunchCountRecord(key = app.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = FAVORITE_SCORE)
            } +
                inputCandidates.mapIndexed { index, app ->
                    createLaunchCountRecord(
                        key = app.key,
                        launchCount = SUGGESTION_LAUNCH_COUNT,
                        recentUseScore =
                        10.0 - index
                    )
                }

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = inputFavorites,
            launchCounts = inputCounts,
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(inputCandidates.take(3), actualSuggestions)
    }

    @Test
    fun `suggests nothing for 8 Favorites without an over-target exception`() {
        val inputFavorites: List<App> = createApps(prefix = "Favorite", count = HOME_ROW_TARGET)
        val inputCandidate: App = createApp(name = "Candidate", packageName = "com.example.candidate")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputFavorites + inputCandidate)
        val inputCounts: List<LaunchCountRecord> =
            inputFavorites.map { app ->
                createLaunchCountRecord(key = app.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = FAVORITE_SCORE)
            } +
                createLaunchCountRecord(key = inputCandidate.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = TOP_SCORE - 1.0)

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = inputFavorites,
            launchCounts = inputCounts,
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `allows one extra row when the top candidate scores twice the median Favorite`() {
        val inputFavorites: List<App> = createApps(prefix = "Favorite", count = HOME_ROW_TARGET)
        val inputTop: App = createApp(name = "Top", packageName = "com.example.top")
        val inputSecond: App = createApp(name = "Second", packageName = "com.example.second")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputFavorites + inputTop + inputSecond)
        val inputCounts: List<LaunchCountRecord> =
            inputFavorites.map { app ->
                createLaunchCountRecord(key = app.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = FAVORITE_SCORE)
            } +
                createLaunchCountRecord(key = inputTop.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = TOP_SCORE) +
                createLaunchCountRecord(key = inputSecond.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = TOP_SCORE - 1.0)

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = inputFavorites,
            launchCounts = inputCounts,
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(listOf<Launchable>(inputTop), actualSuggestions)
    }

    @Test
    fun `adds the extra row to the remaining slots below 8 Favorites`() {
        val inputFavorites: List<App> = createApps(prefix = "Favorite", count = HOME_ROW_TARGET - 2)
        val inputCandidates: List<App> = createApps(prefix = "Candidate", count = 4)
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputFavorites + inputCandidates)
        val inputCounts: List<LaunchCountRecord> =
            inputFavorites.map { app ->
                createLaunchCountRecord(key = app.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = FAVORITE_SCORE)
            } +
                inputCandidates.mapIndexed { index, app ->
                    createLaunchCountRecord(
                        key = app.key,
                        launchCount = SUGGESTION_LAUNCH_COUNT,
                        recentUseScore =
                        30.0 - index
                    )
                }

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = inputFavorites,
            launchCounts = inputCounts,
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(inputCandidates.take(3), actualSuggestions)
    }

    @Test
    fun `allows no extra row when the top candidate scores less than twice the median Favorite`() {
        val inputFavorites: List<App> = createApps(prefix = "Favorite", count = HOME_ROW_TARGET - 1)
        val inputCandidates: List<App> = createApps(prefix = "Candidate", count = 3)
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputFavorites + inputCandidates)
        val inputCounts: List<LaunchCountRecord> =
            inputFavorites.map { app ->
                createLaunchCountRecord(key = app.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = FAVORITE_SCORE)
            } +
                inputCandidates.map { app ->
                    createLaunchCountRecord(
                        key = app.key,
                        launchCount = SUGGESTION_LAUNCH_COUNT,
                        recentUseScore =
                        TOP_SCORE - BELOW_OVER_TARGET_MARGIN
                    )
                }

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = inputFavorites,
            launchCounts = inputCounts,
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(inputCandidates.take(1), actualSuggestions)
    }

    @Test
    fun `takes the median of an even number of Favorites as the mean of the middle two`() {
        val inputFavorites: List<App> = createApps(prefix = "Favorite", count = HOME_ROW_TARGET)
        val inputTop: App = createApp(name = "Top", packageName = "com.example.top")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputFavorites + inputTop)
        val inputScores: List<Double> = listOf(1.0, 1.0, 1.0, 8.0, 12.0, 12.0, 12.0, 12.0)
        val inputCounts: List<LaunchCountRecord> =
            inputFavorites.zip(inputScores) { app, score ->
                createLaunchCountRecord(key = app.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = score)
            } +
                createLaunchCountRecord(key = inputTop.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = TOP_SCORE)

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = inputFavorites,
            launchCounts = inputCounts,
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(listOf<Launchable>(inputTop), actualSuggestions)
    }

    @Test
    fun `counts a Favorite with no Launch record as scoring zero in the median`() {
        val inputFavorites: List<App> = createApps(prefix = "Favorite", count = HOME_ROW_TARGET)
        val inputTop: App = createApp(name = "Top", packageName = "com.example.top")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputFavorites + inputTop)
        val inputCounts: List<LaunchCountRecord> =
            listOf(createLaunchCountRecord(key = inputTop.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = 0.5))

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = inputFavorites,
            launchCounts = inputCounts,
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(listOf<Launchable>(inputTop), actualSuggestions)
    }

    @Test
    fun `leaves out a Launchable launched fewer than 3 times`() {
        val inputBelow: App = createApp(name = "Below", packageName = "com.example.below")
        val inputAt: App = createApp(name = "At", packageName = "com.example.at")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputBelow, inputAt))
        val inputCounts: List<LaunchCountRecord> = listOf(
            createLaunchCountRecord(key = inputBelow.key, launchCount = SUGGESTION_LAUNCH_COUNT - 1, recentUseScore = 50.0),
            createLaunchCountRecord(key = inputAt.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = 1.0)
        )

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = emptyList(),
            launchCounts = inputCounts,
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(listOf<Launchable>(inputAt), actualSuggestions)
    }

    @Test
    fun `leaves out a Favorite`() {
        val inputFavorite: App = createApp(name = "Favorite", packageName = "com.example.favorite")
        val inputOther: App = createApp(name = "Other", packageName = "com.example.other")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputFavorite, inputOther))
        val inputCounts: List<LaunchCountRecord> =
            listOf(
                createLaunchCountRecord(key = inputFavorite.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = 9.0),
                createLaunchCountRecord(key = inputOther.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = 1.0)
            )

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = listOf(inputFavorite),
            launchCounts = inputCounts,
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(listOf<Launchable>(inputOther), actualSuggestions)
    }

    @Test
    fun `leaves out a Hidden app`() {
        val inputHidden: App = createApp(name = "Hidden", packageName = "com.example.hidden")
        val inputState: LaunchableState = createLoadedLaunchableState(
            launchables = listOf(inputHidden),
            hiddenKeys = setOf(inputHidden.key)
        )

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = emptyList(),
            launchCounts = listOf(
                createLaunchCountRecord(key = inputHidden.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = 9.0)
            ),
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `leaves out a private App of a Locked Private Space`() {
        val inputLockedPrivateKey: String = "com.example.private/.MainActivity_UserHandle{10}"
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = emptyList())
        val inputCounts: List<LaunchCountRecord> = listOf(
            createLaunchCountRecord(key = inputLockedPrivateKey, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = 9.0)
        )

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = emptyList(),
            launchCounts = inputCounts,
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `leaves out Rail itself because its key resolves to no Launchable`() {
        val inputRailKey: String = "dev.maples.rail/dev.maples.rail.MainActivity_UserHandle{0}"
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = emptyList())

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = emptyList(),
            launchCounts = listOf(createLaunchCountRecord(key = inputRailKey, launchCount = SUGGESTION_LAUNCH_COUNT)),
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `leaves out a Paused work App`() {
        val inputPaused: App = createPausedWorkApp()
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputPaused))

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = emptyList(),
            launchCounts = listOf(
                createLaunchCountRecord(key = inputPaused.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = 9.0)
            ),
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `leaves out an App in a Folder on Home`() {
        val inputMember: App = createApp(name = "Member", packageName = "com.example.member")
        val inputOther: App = createApp(name = "Other", packageName = "com.example.other")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputMember, inputOther))
        val inputCounts: List<LaunchCountRecord> =
            listOf(
                createLaunchCountRecord(key = inputMember.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = 9.0),
                createLaunchCountRecord(key = inputOther.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = 1.0)
            )

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = listOf(Folder(folderId = 1)),
            launchCounts = inputCounts,
            folderMemberKeys = setOf(inputMember.key),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(listOf<Launchable>(inputOther), actualSuggestions)
    }

    @Test
    fun `leaves out a Rail built-in Launchable`() {
        val inputBuiltin: Launchable = createRailLaunchable("Rail Settings")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputBuiltin))

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = emptyList(),
            launchCounts = listOf(
                createLaunchCountRecord(key = inputBuiltin.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = 9.0)
            ),
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `orders suggestions by Recent use score decayed to now`() {
        val inputOldHigh: App = createApp(name = "Old", packageName = "com.example.old")
        val inputRecent: App = createApp(name = "Recent", packageName = "com.example.recent")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputOldHigh, inputRecent))
        val inputCounts: List<LaunchCountRecord> = listOf(
            createLaunchCountRecord(
                key = inputOldHigh.key,
                launchCount = SUGGESTION_LAUNCH_COUNT,
                recentUseScore = 8.0,
                scoreUpdatedAt =
                TEST_NOW_EPOCH_MS - TEST_FOURTEEN_DAYS_MS
            ),
            createLaunchCountRecord(key = inputRecent.key, launchCount = SUGGESTION_LAUNCH_COUNT, recentUseScore = 3.0)
        )

        val actualSuggestions: List<Launchable> = inputState.homeSuggestions(
            favorites = emptyList(),
            launchCounts = inputCounts,
            folderMemberKeys = emptySet(),
            nowEpochMs = TEST_NOW_EPOCH_MS
        )

        assertEquals(listOf<Launchable>(inputRecent, inputOldHigh), actualSuggestions)
    }

    private fun createApps(prefix: String, count: Int): List<App> =
        (1..count).map { index -> createApp(name = "$prefix $index", packageName = "com.example.${prefix.lowercase()}$index") }
}
