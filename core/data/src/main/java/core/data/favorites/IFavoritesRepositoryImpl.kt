package core.data.favorites

import core.data.launchables.ILaunchableRepository
import core.data.launchables.LaunchableState
import core.data.launchables.isKeptKey
import core.data.launchables.model.Launchable
import core.data.launchables.staleKeys
import core.data.reorderedPinned
import core.data.retryWithExponentialBackoff
import core.data.room.dao.FavoriteDao
import core.data.runCleanup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val AWAIT_RESOLUTION_TIMEOUT_MS: Long = 500L

private data class FavoriteResolution(
    val favorites: List<Launchable>,
    val staleKeys: List<String>,
    val launchableKeys: Set<String>,
    val isLoaded: Boolean
) {
    // Derived lookup index; recomputed with the resolution, so it is not part of equality.
    val favoriteKeys: Set<String> = favorites.mapTo(mutableSetOf()) { favorite -> favorite.key }
}

private val UNLOADED_RESOLUTION: FavoriteResolution = FavoriteResolution(
    favorites = emptyList(),
    staleKeys = emptyList(),
    launchableKeys = emptySet(),
    isLoaded = false
)

class IFavoritesRepositoryImpl(
    private val favoriteDao: FavoriteDao,
    private val launchableRepository: ILaunchableRepository,
    scope: CoroutineScope
) : IFavoritesRepository {
    private val resolution: StateFlow<FavoriteResolution> = combine(
        favoriteDao.observeAll(),
        launchableRepository.launchableState
    ) { records, state ->
        if (state.isLoaded) {
            resolveFavorites(records, state)
        } else {
            UNLOADED_RESOLUTION
        }
    }
        .retryWithExponentialBackoff()
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = UNLOADED_RESOLUTION)

    init {
        // Deleting stale rows keeps a reinstalled Launchable from restoring an old Favorite.
        scope.launch {
            resolution.collect { resolved -> removeStaleFavorites(resolved.staleKeys) }
        }
    }

    override val favorites: Flow<List<Launchable>> = resolution
        .map { resolved -> resolved.favorites }
        .distinctUntilChanged()

    override fun isFavorite(launchable: Launchable): Flow<Boolean> = resolution
        .map { resolved -> launchable.key in resolved.favoriteKeys }
        .distinctUntilChanged()

    override suspend fun add(launchable: Launchable) {
        favoriteDao.add(launchable.key)
        awaitFavorite(launchable = launchable, isFavorite = true)
    }

    override suspend fun remove(launchable: Launchable) {
        favoriteDao.remove(listOf(launchable.key))
        awaitFavorite(launchable = launchable, isFavorite = false)
    }

    override suspend fun reorder(favorites: List<Launchable>) {
        val state: LaunchableState = launchableRepository.launchableState.value
        val records: List<FavoriteRecord> = favoriteDao.observeAll()
            .first()
            .sortedBy { record -> record.position }
        val reordered: List<FavoriteRecord> = records.reorderedPinned(
            orderedKeys = favorites.map { favorite -> favorite.key },
            keyOf = { record -> record.key },
            isPinned = { record -> state.isKeptKey(record.key) }
        )
        favoriteDao.update(reordered.mapIndexed { index, record -> record.copy(position = index) })
    }

    // Writes reach the shared resolution asynchronously; wait briefly so a following
    // isFavorite read usually sees the write.
    private suspend fun awaitFavorite(launchable: Launchable, isFavorite: Boolean) {
        withTimeoutOrNull(AWAIT_RESOLUTION_TIMEOUT_MS) {
            resolution.first { resolved ->
                !resolved.isLoaded ||
                    (launchable.key in resolved.favoriteKeys) == isFavorite ||
                    launchable.key !in resolved.launchableKeys
            }
        }
    }

    private suspend fun removeStaleFavorites(staleKeys: List<String>) {
        if (staleKeys.isEmpty()) {
            return
        }
        runCleanup(message = "Failed to remove ${staleKeys.size} stale Favorites") { favoriteDao.remove(staleKeys) }
    }
}

private fun resolveFavorites(records: List<FavoriteRecord>, state: LaunchableState): FavoriteResolution {
    val launchablesByKey: Map<String, Launchable> = state.launchables.associateBy { it.key }
    val favorites: List<Launchable> = records
        .sortedBy { record -> record.position }
        .mapNotNull { record -> state.launchableFor(record.key) }
        .distinctBy { launchable -> launchable.key }
    val staleKeys: List<String> = state.staleKeys(records.map { record -> record.key })
    return FavoriteResolution(
        favorites = favorites,
        staleKeys = staleKeys,
        launchableKeys = launchablesByKey.keys,
        isLoaded = true
    )
}
