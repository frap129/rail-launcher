package core.data.testing

import core.data.cleanup.IStaleKeyCleanup
import core.data.cleanup.IStaleKeyCleanupImpl
import core.data.cleanup.IStaleKeyRow
import core.data.cleanup.IStoredKeyClassifier
import core.data.cleanup.IStoredKeyClassifierImpl
import core.data.launchables.ILaunchableRepository
import core.data.shortcuts.IShortcutSource
import core.data.shortcuts.IStandaloneShortcutRepository
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope

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
