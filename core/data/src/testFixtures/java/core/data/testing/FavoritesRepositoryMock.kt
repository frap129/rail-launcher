package core.data.testing

import core.data.favorites.FavoritesState
import core.data.favorites.IFavoritesRepository
import core.data.launchables.model.Launchable
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The Favorites a [createFavoritesRepositoryMock] repository reports. */
class FavoritesRepositoryState {
    /** The Favorites and their readiness, held as one value so a fixture cannot pair a list with a different readiness. */
    val favoritesState: MutableStateFlow<FavoritesState> = MutableStateFlow(FavoritesState(favorites = emptyList(), isLoaded = true))

    /** Whether an `isFavorite` read answers true. */
    val isFavorite: MutableStateFlow<Boolean> = MutableStateFlow(false)
}

/** Replaces the Favorites [this] reports, keeping their readiness. */
fun FavoritesRepositoryState.setFavorites(favorites: List<Launchable>) {
    favoritesState.update { state -> state.copy(favorites = favorites) }
}

/** Replaces the readiness [this] reports, keeping the Favorites. */
fun FavoritesRepositoryState.setLoaded(isLoaded: Boolean) {
    favoritesState.update { state -> state.copy(isLoaded = isLoaded) }
}

/** A MockK [IFavoritesRepository] over [state], with successful writes. */
fun createFavoritesRepositoryMock(state: FavoritesRepositoryState = FavoritesRepositoryState()): IFavoritesRepository {
    val mockFavoritesRepository: IFavoritesRepository = mockk()
    every { mockFavoritesRepository.favorites } returns state.favoritesState
        .map { favoritesState -> favoritesState.favorites }
        .distinctUntilChanged()
    every { mockFavoritesRepository.state } returns state.favoritesState
    every { mockFavoritesRepository.isFavorite(any()) } returns state.isFavorite
    coEvery { mockFavoritesRepository.add(any()) } just Runs
    coEvery { mockFavoritesRepository.remove(any()) } just Runs
    coEvery { mockFavoritesRepository.reorder(any()) } just Runs
    return mockFavoritesRepository
}

/** The mutable Favorites with the mock over them. */
class FavoritesRepositoryFixture(val state: FavoritesRepositoryState, val mockFavoritesRepository: IFavoritesRepository)

/** A [FavoritesRepositoryFixture] with empty Favorites for the test to fill in. */
fun createFavoritesRepositoryFixture(): FavoritesRepositoryFixture {
    val state: FavoritesRepositoryState = FavoritesRepositoryState()
    return FavoritesRepositoryFixture(state = state, mockFavoritesRepository = createFavoritesRepositoryMock(state))
}
