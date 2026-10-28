package core.data.testing

import core.data.favorites.IFavoritesRepository
import core.data.launchables.model.Launchable
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The mutable Favorites behind a [createFavoritesRepositoryMock] repository. */
class FavoritesRepositoryState {
    val favorites: MutableStateFlow<List<Launchable>> = MutableStateFlow(emptyList())
}

/** A MockK [IFavoritesRepository] over [state], appending, removing and reordering its Favorites. */
fun createFavoritesRepositoryMock(state: FavoritesRepositoryState = FavoritesRepositoryState()): IFavoritesRepository {
    val mockFavoritesRepository: IFavoritesRepository = mockk()
    every { mockFavoritesRepository.favorites } returns state.favorites
    every { mockFavoritesRepository.isFavorite(any()) } answers {
        val launchable: Launchable = firstArg()
        state.favorites.map { favorites -> favorites.any { favorite -> favorite.key == launchable.key } }
    }
    coEvery { mockFavoritesRepository.add(any()) } coAnswers {
        val launchable: Launchable = firstArg()
        state.favorites.update { favorites ->
            if (favorites.any { favorite -> favorite.key == launchable.key }) favorites else favorites + launchable
        }
    }
    coEvery { mockFavoritesRepository.remove(any()) } coAnswers {
        val launchable: Launchable = firstArg()
        state.favorites.update { favorites -> favorites.filterNot { favorite -> favorite.key == launchable.key } }
    }
    coEvery { mockFavoritesRepository.reorder(any()) } coAnswers {
        val ordered: List<Launchable> = firstArg()
        state.favorites.update { favorites ->
            ordered.filter { launchable -> favorites.any { favorite -> favorite.key == launchable.key } }
        }
    }
    return mockFavoritesRepository
}

/** The mutable Favorites with the mock over them. */
class FavoritesRepositoryFixture(val state: FavoritesRepositoryState, val mockFavoritesRepository: IFavoritesRepository)

/** A [FavoritesRepositoryFixture] with empty Favorites for the test to fill in. */
fun createFavoritesRepositoryFixture(): FavoritesRepositoryFixture {
    val state: FavoritesRepositoryState = FavoritesRepositoryState()
    return FavoritesRepositoryFixture(state = state, mockFavoritesRepository = createFavoritesRepositoryMock(state))
}
