package core.data.testing

import core.data.favorites.IFavoritesRepository
import core.data.launchables.model.Launchable
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/** The Favorites a [createFavoritesRepositoryMock] repository reports. */
class FavoritesRepositoryState {
    val favorites: MutableStateFlow<List<Launchable>> = MutableStateFlow(emptyList())

    /** Whether an `isFavorite` read answers true. */
    val isFavorite: MutableStateFlow<Boolean> = MutableStateFlow(false)
}

/** A MockK [IFavoritesRepository] over [state], with successful writes. */
fun createFavoritesRepositoryMock(state: FavoritesRepositoryState = FavoritesRepositoryState()): IFavoritesRepository {
    val mockFavoritesRepository: IFavoritesRepository = mockk()
    every { mockFavoritesRepository.favorites } returns state.favorites
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
