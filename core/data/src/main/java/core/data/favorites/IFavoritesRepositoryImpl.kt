package core.data.favorites

import core.data.launchables.ILaunchableRepository
import core.data.launchables.model.Launchable
import core.data.room.dao.FavoriteDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

private data class FavoriteResolution(val favorites: List<Launchable>, val staleKeys: List<String>)

class IFavoritesRepositoryImpl(private val favoriteDao: FavoriteDao, launchableRepository: ILaunchableRepository) :
    IFavoritesRepository {
    // Deleting stale rows keeps a reinstalled Launchable from restoring an old Favorite.
    override val favorites: Flow<List<Launchable>> = combine(
        favoriteDao.observeAll(),
        launchableRepository.launchableState
    ) { records, state ->
        if (state.isLoaded) {
            resolveFavorites(records, state.launchables)
        } else {
            FavoriteResolution(favorites = emptyList(), staleKeys = emptyList())
        }
    }
        .onEach { resolution -> if (resolution.staleKeys.isNotEmpty()) favoriteDao.remove(resolution.staleKeys) }
        .map { resolution -> resolution.favorites }

    override fun isFavorite(launchable: Launchable): Flow<Boolean> = favoriteDao.observeAll()
        .map { records -> records.any { record -> record.key == launchable.key } }
        .distinctUntilChanged()

    override suspend fun add(launchable: Launchable) {
        favoriteDao.add(launchable.key)
    }

    override suspend fun remove(launchable: Launchable) {
        favoriteDao.remove(listOf(launchable.key))
    }

    override suspend fun reorder(favorites: List<Launchable>) {
        favoriteDao.update(favorites.mapIndexed { index, launchable -> FavoriteRecord(key = launchable.key, position = index) })
    }
}

private fun resolveFavorites(records: List<FavoriteRecord>, launchables: List<Launchable>): FavoriteResolution {
    val launchablesByKey: Map<String, Launchable> = launchables.associateBy { it.key }
    val orderedRecords: List<FavoriteRecord> = records.sortedBy { it.position }
    return FavoriteResolution(
        favorites = orderedRecords.mapNotNull { record -> launchablesByKey[record.key] },
        staleKeys = orderedRecords.filter { record -> record.key !in launchablesByKey }.map { it.key }
    )
}
