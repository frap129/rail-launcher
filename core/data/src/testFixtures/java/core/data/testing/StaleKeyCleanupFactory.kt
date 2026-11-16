package core.data.testing

import core.data.apps.model.App
import core.data.cleanup.IStaleKeyCleanup
import core.data.cleanup.IStaleKeyCleanupImpl
import core.data.cleanup.IStaleKeyRow
import core.data.cleanup.IStoredKeyClassifier
import core.data.cleanup.IStoredKeyClassifierImpl
import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.profiles.ProfileKind
import core.data.profiles.toProfileToken
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.IStandaloneShortcutRepository
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope

/** The keys a stale-key cleanup kept for a Present, a gone and a Kept App. */
data class StaleKeyCleanupOutcome(val presentKey: String, val keptKey: String, val survivingKeys: Set<String>)

/**
 * Records a row for a Present, a gone and a Kept App with [record], runs a [cleanupRow] cleanup,
 * and returns the keys it kept, read with [storedKeys].
 */
suspend fun TestScope.staleKeyCleanupOutcome(
    record: suspend (key: String) -> Unit,
    storedKeys: suspend () -> Set<String>,
    cleanupRow: IStaleKeyRow
): StaleKeyCleanupOutcome {
    val inputPresent: App = createApp(name = "Present", packageName = "com.example.present")
    val inputGone: App = createApp(name = "Gone", packageName = "com.example.gone")
    val inputKept: App = createApp(
        name = "Kept",
        packageName = "com.example.kept",
        profileInfo = createProfileInfo(userId = WORK_USER_ID, kind = ProfileKind.Work)
    )
    listOf(inputPresent, inputGone, inputKept).forEach { app -> record(app.key) }
    val inputState: MutableStateFlow<LaunchableState> = MutableStateFlow(
        createLoadedLaunchableState(
            launchables = listOf(inputPresent),
            keptKeys = setOf(createUserHandle(userId = WORK_USER_ID).toProfileToken())
        )
    )
    createStaleKeyCleanup(
        launchableRepository = createLaunchableRepositoryMock(state = inputState),
        shortcutSource = createShortcutSourceMock(),
        rows = listOf(cleanupRow),
        scope = backgroundScope
    )
    finishPendingWork()
    return StaleKeyCleanupOutcome(
        presentKey = inputPresent.key,
        keptKey = inputKept.key,
        survivingKeys = storedKeys()
    )
}

/** The production stored-key classifier over [launchableRepository] and [shortcutSource]. */
fun createStoredKeyClassifier(
    launchableRepository: ILaunchableRepository,
    shortcutSource: IShortcutSource = createShortcutSourceMock()
): IStoredKeyClassifier = IStoredKeyClassifierImpl(
    launchableRepository = launchableRepository,
    shortcutSource = shortcutSource
)

/** The production stale-key cleanup over [classifier], with [rows] registered and its job on [scope]. */
fun createStaleKeyCleanup(classifier: IStoredKeyClassifier, rows: List<IStaleKeyRow>, scope: CoroutineScope): IStaleKeyCleanup =
    IStaleKeyCleanupImpl(classifier = classifier, scope = scope).apply {
        rows.forEach { row -> register(row) }
    }

/** The production stale-key cleanup over the production classifier for [launchableRepository] and [shortcutSource]. */
fun createStaleKeyCleanup(
    launchableRepository: ILaunchableRepository,
    shortcutSource: IShortcutSource,
    rows: List<IStaleKeyRow>,
    scope: CoroutineScope
): IStaleKeyCleanup = createStaleKeyCleanup(
    classifier = createStoredKeyClassifier(launchableRepository = launchableRepository, shortcutSource = shortcutSource),
    rows = rows,
    scope = scope
)

/** A relaxed [IStandaloneShortcutRepository] test double. */
fun createStandaloneShortcutRepositoryMock(): IStandaloneShortcutRepository = mockk(relaxed = true)
