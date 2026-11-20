package core.data.search

import core.data.apps.model.App
import core.data.folders.Folder
import core.data.launchables.LaunchableState
import core.data.launchables.model.Launchable
import core.data.launches.LaunchCountRecord
import core.data.popups.PopupItem
import core.data.testing.TEST_FOURTEEN_DAYS_MS
import core.data.testing.TEST_NOW_EPOCH_MS
import core.data.testing.TEST_SDK
import core.data.testing.createApp
import core.data.testing.createLaunchCountRecord
import core.data.testing.createLoadedLaunchableState
import core.data.testing.createPausedWorkApp
import core.data.testing.createRailLaunchable
import core.util.MILLIS_PER_WEEK
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val FAVORITE_SCORE: Double = 10.0
private const val SCORE_MARGIN: Double = 0.1
private const val TOP_SCORE: Double = FAVORITE_SCORE * HOME_OVER_TARGET_SCORE_FACTOR
private const val SUGGESTION_LAUNCH_COUNT: Int = HOME_SUGGESTION_MINIMUM_LAUNCH_COUNT
private const val OFTEN_LAUNCHED_COUNT: Int = SUGGESTION_LAUNCH_COUNT * 10

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
                createSuggestionRecord(key = app.key, recentUseScore = FAVORITE_SCORE)
            } +
                inputCandidates.mapIndexed { index, app ->
                    createSuggestionRecord(key = app.key, recentUseScore = 10.0 - index)
                }

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(favorites = inputFavorites, launchCounts = inputCounts)

        assertEquals(inputCandidates.take(3), actualSuggestions)
    }

    @Test
    fun `suggests nothing for 8 Favorites without an over-target exception`() {
        val inputFavorites: List<App> = createApps(prefix = "Favorite", count = HOME_ROW_TARGET)
        val inputCandidate: App = createApp(name = "Candidate", packageName = "com.example.candidate")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputFavorites + inputCandidate)
        val inputCounts: List<LaunchCountRecord> =
            inputFavorites.map { app ->
                createSuggestionRecord(key = app.key, recentUseScore = FAVORITE_SCORE)
            } +
                createSuggestionRecord(key = inputCandidate.key, recentUseScore = TOP_SCORE - 1.0)

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(favorites = inputFavorites, launchCounts = inputCounts)

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
                createSuggestionRecord(key = app.key, recentUseScore = FAVORITE_SCORE)
            } +
                createSuggestionRecord(key = inputTop.key, recentUseScore = TOP_SCORE) +
                createSuggestionRecord(key = inputSecond.key, recentUseScore = TOP_SCORE - 1.0)

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(favorites = inputFavorites, launchCounts = inputCounts)

        assertEquals(listOf<Launchable>(inputTop), actualSuggestions)
    }

    @Test
    fun `adds the extra row to the remaining slots below 8 Favorites`() {
        val inputFavorites: List<App> = createApps(prefix = "Favorite", count = HOME_ROW_TARGET - 2)
        val inputCandidates: List<App> = createApps(prefix = "Candidate", count = 4)
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputFavorites + inputCandidates)
        val inputCounts: List<LaunchCountRecord> =
            inputFavorites.map { app ->
                createSuggestionRecord(key = app.key, recentUseScore = FAVORITE_SCORE)
            } +
                inputCandidates.mapIndexed { index, app ->
                    createSuggestionRecord(key = app.key, recentUseScore = 30.0 - index)
                }

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(favorites = inputFavorites, launchCounts = inputCounts)

        assertEquals(inputCandidates.take(3), actualSuggestions)
    }

    @Test
    fun `allows no extra row when the top candidate scores less than twice the median Favorite`() {
        val inputFavorites: List<App> = createApps(prefix = "Favorite", count = HOME_ROW_TARGET - 1)
        val inputCandidates: List<App> = createApps(prefix = "Candidate", count = 3)
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputFavorites + inputCandidates)
        val inputCounts: List<LaunchCountRecord> =
            inputFavorites.map { app ->
                createSuggestionRecord(key = app.key, recentUseScore = FAVORITE_SCORE)
            } +
                inputCandidates.map { app ->
                    createSuggestionRecord(key = app.key, recentUseScore = TOP_SCORE - SCORE_MARGIN)
                }

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(favorites = inputFavorites, launchCounts = inputCounts)

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
                createSuggestionRecord(key = app.key, recentUseScore = score)
            } +
                createSuggestionRecord(key = inputTop.key, recentUseScore = TOP_SCORE)

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(favorites = inputFavorites, launchCounts = inputCounts)

        assertEquals(listOf<Launchable>(inputTop), actualSuggestions)
    }

    @Test
    fun `allows no extra row when no Favorite has a Launch record`() {
        val inputCandidates: List<App> = createApps(prefix = "Candidate", count = 2)

        val actualSuggestions: List<Launchable> =
            suggestionsWithOpenedFavorites(favoriteCount = HOME_ROW_TARGET, openedFavoriteCount = 0, candidates = inputCandidates)

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `allows no extra row when more than 8 Favorites have no Launch record`() {
        val inputCandidates: List<App> = createApps(prefix = "Candidate", count = 2)

        val actualSuggestions: List<Launchable> =
            suggestionsWithOpenedFavorites(favoriteCount = HOME_ROW_TARGET + 2, openedFavoriteCount = 0, candidates = inputCandidates)

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `allows no extra row when the median Favorite is unopened though some Favorites were opened`() {
        val inputCandidates: List<App> = createApps(prefix = "Candidate", count = 2)

        val actualSuggestions: List<Launchable> =
            suggestionsWithOpenedFavorites(favoriteCount = HOME_ROW_TARGET, openedFavoriteCount = 3, candidates = inputCandidates)

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `keeps the base slots below 8 Favorites when the median Favorite score is zero`() {
        val inputCandidates: List<App> = createApps(prefix = "Candidate", count = 4)

        val actualSuggestions: List<Launchable> =
            suggestionsWithOpenedFavorites(favoriteCount = HOME_ROW_TARGET - 3, openedFavoriteCount = 0, candidates = inputCandidates)

        assertEquals(inputCandidates.take(3), actualSuggestions)
    }

    @Test
    fun `leaves out a Launchable launched fewer than 3 times`() {
        val inputBelow: App = createApp(name = "Below", packageName = "com.example.below")
        val inputAt: App = createApp(name = "At", packageName = "com.example.at")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputBelow, inputAt))
        val inputCounts: List<LaunchCountRecord> = listOf(
            createSuggestionRecord(key = inputBelow.key, launchCount = SUGGESTION_LAUNCH_COUNT - 1, recentUseScore = 50.0),
            createSuggestionRecord(key = inputAt.key)
        )

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(launchCounts = inputCounts)

        assertEquals(listOf<Launchable>(inputAt), actualSuggestions)
    }

    @Test
    fun `leaves out a single stale Launchable however often it was launched`() {
        val inputStale: App = createApp(name = "Stale", packageName = "com.example.stale")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputStale))
        val inputCounts: List<LaunchCountRecord> = listOf(
            createSuggestionRecord(
                key = inputStale.key,
                launchCount = OFTEN_LAUNCHED_COUNT,
                scoreUpdatedAt =
                TEST_NOW_EPOCH_MS - TEST_FOURTEEN_DAYS_MS
            )
        )

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(launchCounts = inputCounts)

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `suggests a Launchable at exactly the minimum Recent use score`() {
        val inputAt: App = createApp(name = "At", packageName = "com.example.at")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputAt))
        val inputCounts: List<LaunchCountRecord> = listOf(
            createSuggestionRecord(
                key = inputAt.key,
                recentUseScore = 2 * HOME_SUGGESTION_MINIMUM_RECENT_USE_SCORE,
                scoreUpdatedAt = TEST_NOW_EPOCH_MS - MILLIS_PER_WEEK
            )
        )

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(launchCounts = inputCounts)

        assertEquals(listOf<Launchable>(inputAt), actualSuggestions)
    }

    @Test
    fun `lets a stale top candidate neither be suggested nor add an extra row`() {
        val inputFavorites: List<App> = createApps(prefix = "Favorite", count = HOME_ROW_TARGET)
        val inputStale: App = createApp(name = "Stale", packageName = "com.example.stale")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputFavorites + inputStale)
        val inputCounts: List<LaunchCountRecord> =
            inputFavorites.map { app -> createSuggestionRecord(key = app.key, recentUseScore = FAVORITE_SCORE) } +
                createSuggestionRecord(
                    key = inputStale.key,
                    launchCount = OFTEN_LAUNCHED_COUNT,
                    recentUseScore = TOP_SCORE * 4,
                    scoreUpdatedAt = TEST_NOW_EPOCH_MS - MILLIS_PER_WEEK * 8
                )

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(favorites = inputFavorites, launchCounts = inputCounts)

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `leaves out a Launchable just below the minimum Recent use score`() {
        val inputBelow: App = createApp(name = "Below", packageName = "com.example.below")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputBelow))

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(
            launchCounts = listOf(
                createSuggestionRecord(key = inputBelow.key, recentUseScore = HOME_SUGGESTION_MINIMUM_RECENT_USE_SCORE - SCORE_MARGIN)
            )
        )

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `leaves out a Launchable the user stopped suggesting`() {
        val inputStopped: App = createApp(name = "Stopped", packageName = "com.example.stopped")
        val inputOther: App = createApp(name = "Other", packageName = "com.example.other")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputStopped, inputOther))
        val inputCounts: List<LaunchCountRecord> = listOf(
            createSuggestionRecord(key = inputStopped.key, recentUseScore = 9.0),
            createSuggestionRecord(key = inputOther.key)
        )

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(
            launchCounts = inputCounts,
            stoppedKeys = setOf(inputStopped.key)
        )

        assertEquals(listOf<Launchable>(inputOther), actualSuggestions)
    }

    @Test
    fun `leaves out a Favorite`() {
        val inputFavorite: App = createApp(name = "Favorite", packageName = "com.example.favorite")
        val inputOther: App = createApp(name = "Other", packageName = "com.example.other")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputFavorite, inputOther))
        val inputCounts: List<LaunchCountRecord> =
            listOf(
                createSuggestionRecord(key = inputFavorite.key, recentUseScore = 9.0),
                createSuggestionRecord(key = inputOther.key)
            )

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(
            favorites = listOf(inputFavorite),
            launchCounts = inputCounts
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

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(
            launchCounts = listOf(
                createSuggestionRecord(key = inputHidden.key, recentUseScore = 9.0)
            )
        )

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `leaves out a private App of a Locked Private Space`() {
        val inputLockedPrivateKey: String = "com.example.private/.MainActivity_UserHandle{10}"
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = emptyList())
        val inputCounts: List<LaunchCountRecord> = listOf(
            createSuggestionRecord(key = inputLockedPrivateKey, recentUseScore = 9.0)
        )

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(launchCounts = inputCounts)

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `leaves out Rail itself because its key resolves to no Launchable`() {
        val inputRailKey: String = "dev.maples.rail/dev.maples.rail.MainActivity_UserHandle{0}"
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = emptyList())

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(
            launchCounts = listOf(createSuggestionRecord(key = inputRailKey))
        )

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `leaves out a Paused work App`() {
        val inputPaused: App = createPausedWorkApp()
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputPaused))

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(
            launchCounts = listOf(
                createSuggestionRecord(key = inputPaused.key, recentUseScore = 9.0)
            )
        )

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `leaves out an App in a Folder on Home`() {
        val inputMember: App = createApp(name = "Member", packageName = "com.example.member")
        val inputOther: App = createApp(name = "Other", packageName = "com.example.other")
        val inputFolder: Folder = Folder(folderId = 1)
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputMember, inputOther))
        val inputCounts: List<LaunchCountRecord> =
            listOf(
                createSuggestionRecord(key = inputMember.key, recentUseScore = 9.0),
                createSuggestionRecord(key = inputOther.key)
            )

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(
            favorites = listOf(inputFolder),
            launchCounts = inputCounts,
            folderMembers = mapOf(inputFolder.key to listOf(PopupItem.Launchable(launchable = inputMember)))
        )

        assertEquals(listOf<Launchable>(inputOther), actualSuggestions)
    }

    @Test
    fun `leaves out a Rail built-in Launchable`() {
        val inputBuiltin: Launchable = createRailLaunchable("Rail Settings")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputBuiltin))

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(
            launchCounts = listOf(
                createSuggestionRecord(key = inputBuiltin.key, recentUseScore = 9.0)
            )
        )

        assertEquals(emptyList<Launchable>(), actualSuggestions)
    }

    @Test
    fun `orders suggestions by Recent use score decayed to now`() {
        val inputOldHigh: App = createApp(name = "Old", packageName = "com.example.old")
        val inputRecent: App = createApp(name = "Recent", packageName = "com.example.recent")
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = listOf(inputOldHigh, inputRecent))
        val inputCounts: List<LaunchCountRecord> = listOf(
            createSuggestionRecord(
                key = inputOldHigh.key,
                recentUseScore = 8.0,
                scoreUpdatedAt = TEST_NOW_EPOCH_MS - TEST_FOURTEEN_DAYS_MS
            ),
            createSuggestionRecord(key = inputRecent.key, recentUseScore = 3.0)
        )

        val actualSuggestions: List<Launchable> = inputState.suggestionsAtNow(launchCounts = inputCounts)

        assertEquals(listOf<Launchable>(inputRecent, inputOldHigh), actualSuggestions)
    }

    private fun createSuggestionRecord(
        key: String,
        launchCount: Int = SUGGESTION_LAUNCH_COUNT,
        recentUseScore: Double = HOME_SUGGESTION_MINIMUM_RECENT_USE_SCORE,
        scoreUpdatedAt: Long = TEST_NOW_EPOCH_MS
    ): LaunchCountRecord = createLaunchCountRecord(
        key = key,
        launchCount = launchCount,
        recentUseScore = recentUseScore,
        scoreUpdatedAt = scoreUpdatedAt
    )

    private fun LaunchableState.suggestionsAtNow(
        launchCounts: List<LaunchCountRecord>,
        favorites: List<Launchable> = emptyList(),
        folderMembers: Map<String, List<PopupItem>> = emptyMap(),
        stoppedKeys: Set<String> = emptySet()
    ): List<Launchable> = homeSuggestions(
        favorites = favorites,
        launchCounts = launchCounts,
        folderMembers = folderMembers,
        stoppedKeys = stoppedKeys,
        nowEpochMs = TEST_NOW_EPOCH_MS
    )

    private fun suggestionsWithOpenedFavorites(favoriteCount: Int, openedFavoriteCount: Int, candidates: List<App>): List<Launchable> {
        val inputFavorites: List<App> = createApps(prefix = "Favorite", count = favoriteCount)
        val inputState: LaunchableState = createLoadedLaunchableState(launchables = inputFavorites + candidates)
        val inputCounts: List<LaunchCountRecord> =
            (inputFavorites.take(openedFavoriteCount).map { app -> app to FAVORITE_SCORE } + candidates.map { app -> app to TOP_SCORE })
                .map { (app, score) ->
                    createSuggestionRecord(key = app.key, recentUseScore = score)
                }
        return inputState.suggestionsAtNow(favorites = inputFavorites, launchCounts = inputCounts)
    }

    private fun createApps(prefix: String, count: Int): List<App> =
        (1..count).map { index -> createApp(name = "$prefix $index", packageName = "com.example.${prefix.lowercase()}$index") }
}
